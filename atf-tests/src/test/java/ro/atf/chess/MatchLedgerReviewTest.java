package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchLedgerReviewTest {
    private static final String LOCAL = "1|f2f3 e7e5 g2g4 d8h4";
    private static final String AI = "3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4";
    private static final String LEGACY_REVIEW = "standalone-import";
    private static final Snapshot PLAYED = new Snapshot(3, Branch.PLAYED, 2, true);
    private static final Snapshot HISTORY = new Snapshot(1, Branch.HISTORY, 0, false);
    private static final Map<String, String> ANALYSES = Map.of(LOCAL, analyze(LOCAL), AI, analyze(AI));

    @Test void preservesTwoSeparateReportsAndBookmarksInThePortableLedger(Checks check) {
        var ledger = open();
        var first = entry(ledger.matches(), LOCAL);
        var second = entry(ledger.matches(), AI);
        var store = ledger.matches();
        check.same("Both finished games initially have independent empty review records", List.of(record(null, null), record(null, null)),
                List.of(store.readReviewRecord(first.id()), store.readReviewRecord(second.id())));
        check.same("A bookmark cannot be written before the report exists", false, bookmark(store, first, PLAYED));
        check.same("Both browser-compatible reports are stored under their archive IDs", List.of(true, true),
                List.of(save(store, first), save(store, second)));
        check.same("Each archive can save its own distinct cursor", List.of(true, true),
                List.of(bookmark(store, first, PLAYED), bookmark(store, second, HISTORY)));

        var restored = reopen(ledger).matches();
        check.same("Restart preserves each report and intended bookmark exactly", List.of(first.record(PLAYED), second.record(HISTORY)),
                List.of(restored.readReviewRecord(first.id()), restored.readReviewRecord(second.id())));
        var firstReplay = new GameReviewSession(first.match(), restored.readReview(first.id(), first.match()),
                restored.readReviewBookmark(first.id(), first.match()));
        var secondReplay = new GameReviewSession(second.match(), restored.readReview(second.id(), second.match()),
                restored.readReviewBookmark(second.id(), second.match()));
        check.same("The first review restores its mating reply while the other restores its first recorded move",
                List.of(PLAYED, "d8h4", HISTORY, "f2f4"),
                List.of(firstReplay.snapshot(), firstReplay.lastMove().uci(), secondReplay.snapshot(), secondReplay.lastMove().uci()));
        check.same("Review and bookmark writes leave both source checkpoints untouched", List.of(LOCAL, AI),
                List.of(restored.readArchivedMatch(first.id()).match().checkpoint(), restored.readArchivedMatch(second.id()).match().checkpoint()));
    }

    @Test void rejectsWrongCheckpointAndLateWritesWithoutOverwritingAnExistingReview(Checks check) {
        var ledger = open();
        var store = ledger.matches();
        var first = entry(store, LOCAL);
        var second = entry(store, AI);
        String initial = ledger.snapshot();
        check.same("A legitimate report for another checkpoint cannot populate the selected archive", false,
                store.saveReviewRecord(first.id(), first.match(), second.review(), first.digest()));
        check.same("A request carrying a different source checkpoint is rejected", false,
                store.saveReviewRecord(first.id(), second.match(), second.review(), second.digest()));
        check.same("An inconsistent checkpoint digest cannot admit a report", false,
                store.saveReviewRecord(first.id(), first.match(), first.review(), second.digest()));
        check.same("The invalid attempts leave all archive data byte-identical", initial, ledger.snapshot());
        check.same("Writing the genuine report and bookmark succeeds", List.of(true, true),
                List.of(save(store, first), bookmark(store, first, PLAYED)));

        String committed = ledger.snapshot();
        check.same("A late analysis response cannot replace an already saved report", false, save(store, first));
        check.same("A bookmark update for another source cannot change the saved cursor", false,
                store.saveReviewBookmark(first.id(), second.match(), HISTORY, second.digest()));
        check.same("The correct source cannot write a bookmark under an unrelated digest", false,
                store.saveReviewBookmark(first.id(), first.match(), HISTORY, second.digest()));
        check.same("Late responses preserve the previously committed report, cursor and ledger", List.of(first.record(PLAYED), committed),
                List.of(store.readReviewRecord(first.id()), ledger.snapshot()));
    }

    @Test void archiveEvictionDeletesTheReviewAndBookmarkAndLateWritesCannotRecreateThem(Checks check) {
        var ledger = open();
        var store = ledger.matches();
        var evicted = entry(store, LOCAL);
        var remaining = entry(store, AI);
        for (var archive : List.of(evicted, remaining)) {
            check.same("The archive has a stored report and cursor before eviction", List.of(true, true),
                    List.of(save(store, archive), bookmark(store, archive, HISTORY)));
        }
        for (int index = 0; index < MatchArchive.LIMIT - 1; index++) {
            store.startMatch(store.slotForNewMatch(), evicted.match(), false, true, true);
        }
        var restored = reopen(ledger);
        var matches = restored.matches();
        check.same("The eleventh result evicts exactly the oldest of the two original archives", List.of(10, false, true),
                List.of(matches.archivedMatches().size(), matches.hasArchived(evicted.id()), matches.hasArchived(remaining.id())));
        check.same("The evicted archive no longer has a raw review record", null, matches.readReviewRecord(evicted.id()));
        var preferences = new MatchLedgerPreferences(restored.snapshot());
        String oldKey = "match-archive-" + evicted.id() + "-";
        check.same("Both persisted payload keys are removed on eviction", List.of(false, false),
                List.of(preferences.contains(oldKey + "review"), preferences.contains(oldKey + "review-bookmark")));
        check.same("The retained archive preserves both of its payloads", remaining.record(HISTORY), matches.readReviewRecord(remaining.id()));
        String before = restored.snapshot();
        check.same("Late analysis and cursor writes cannot resurrect an archive", List.of(false, false),
                List.of(save(matches, evicted), bookmark(matches, evicted, HISTORY)));
        check.same("Rejected writes leave the ledger untouched", before, restored.snapshot());
    }

    @Test void migratesOneLegacyReviewAndRebindsItsCursorToTheUniqueMatchingArchive(Checks check) {
        var ledger = open();
        var first = entry(ledger.matches(), LOCAL);
        var second = entry(ledger.matches(), AI);
        var old = GameReviewBookmark.encode(LEGACY_REVIEW, first.match(), PLAYED, first.digest());
        ledger.importLegacyReview(LEGACY_REVIEW, first.match(), first.review(), old, first.digest());
        var restored = reopen(ledger);
        check.same("Migration keeps the exact analysis and binds the cursor to the actual archive ID", first.record(PLAYED),
                restored.matches().readReviewRecord(first.id()));
        check.same("The other finished game remains independent", record(null, null), restored.matches().readReviewRecord(second.id()));
        check.same("The archive rejects the old standalone bookmark ID", null,
                GameReviewBookmark.decode(LEGACY_REVIEW, first.match(), restored.matches().readReviewRecord(first.id()).bookmark(), first.digest()));

        String initial = restored.snapshot();
        var unrelated = GameReviewBookmark.encode(LEGACY_REVIEW, second.match(), HISTORY, second.digest());
        restored.importLegacyReview(LEGACY_REVIEW, second.match(), second.review(), unrelated, second.digest());
        check.same("A leftover standalone review cannot migrate a second time after restart", initial, restored.snapshot());
    }

    @Test void migrationDoesNotOverwriteExistingReviewsOrBindAnAmbiguousCheckpoint(Checks check) {
        var ledger = open();
        var first = entry(ledger.matches(), LOCAL);
        save(ledger.matches(), first);
        bookmark(ledger.matches(), first, HISTORY);
        var competing = GameReviewBookmark.encode(LEGACY_REVIEW, first.match(), PLAYED, first.digest());
        ledger.importLegacyReview(LEGACY_REVIEW, first.match(), first.review(), competing, first.digest());
        check.same("Migration cannot reopen a cursor over an already stored archive review", first.record(HISTORY),
                ledger.matches().readReviewRecord(first.id()));

        var ambiguous = open();
        var store = ambiguous.matches();
        var original = entry(store, LOCAL);
        int slot = store.slotForNewMatch();
        store.startMatch(slot, first.match(), false, true, true);
        String duplicate = store.archivedId(slot);
        ambiguous.importLegacyReview(LEGACY_REVIEW, first.match(), first.review(), competing, first.digest());
        check.same("A legacy checkpoint shared by two archives cannot claim either identity", List.of(record(null, null), record(null, null)),
                List.of(store.readReviewRecord(original.id()), store.readReviewRecord(duplicate)));
    }

    @Test void aRejectedLegacyAttemptIsNotRetriedAndAnInvalidBookmarkHasNoEffect(Checks check) {
        var ledger = open();
        var first = entry(ledger.matches(), LOCAL);
        ledger.importLegacyReview(LEGACY_REVIEW, null, first.review(), null, first.digest());
        String rejected = ledger.snapshot();
        var restored = reopen(ledger);
        restored.importLegacyReview(LEGACY_REVIEW, first.match(), first.review(), null, first.digest());
        check.same("Rejecting invalid legacy data permanently finishes the migration attempt", rejected, restored.snapshot());
        check.same("A later valid leftover cannot populate the affected archive", record(null, null), restored.matches().readReviewRecord(first.id()));

        var other = open();
        var second = entry(other.matches(), AI);
        var wrongBookmark = GameReviewBookmark.encode(LEGACY_REVIEW, first.match(), PLAYED, first.digest());
        other.importLegacyReview(LEGACY_REVIEW, second.match(), second.review(), wrongBookmark, second.digest());
        check.same("A valid report with an unrelated bookmark opens at its default cursor", record(second.review(), null),
                other.matches().readReviewRecord(second.id()));
    }

    private static MatchLedger open() {
        var ids = new AtomicInteger();
        return MatchLedger.open(null, LOCAL, "ai|" + AI, () -> 1_794_830_400_000L, () -> "archive-" + ids.incrementAndGet());
    }

    private static MatchLedger reopen(MatchLedger ledger) {
        return MatchLedger.open(ledger.snapshot(), null, null, () -> 1_794_830_400_000L, () -> "unexpected-new-game");
    }

    private static Archive entry(MatchStore store, String checkpoint) {
        var row = store.archivedMatches().stream().filter(value -> value.match().checkpoint().equals(checkpoint)).findFirst().orElseThrow();
        var match = row.match();
        return new Archive(row.id(), match, ANALYSES.get(checkpoint), GameReviewCodec.checkpointDigest(match));
    }

    private static String analyze(String checkpoint) {
        var match = ChessMatch.restore(checkpoint);
        return GameReviewCodec.encode(match, GameReview.analyzeLine(match, ignored -> { }));
    }

    private static boolean save(MatchStore store, Archive archive) {
        return store.saveReviewRecord(archive.id(), archive.match(), archive.review(), archive.digest());
    }

    private static boolean bookmark(MatchStore store, Archive archive, Snapshot cursor) {
        return store.saveReviewBookmark(archive.id(), archive.match(), cursor, archive.digest());
    }

    private static MatchStore.ReviewRecord record(String review, String bookmark) { return new MatchStore.ReviewRecord(review, bookmark); }

    private record Archive(String id, ChessMatch match, String review, String digest) {
        MatchStore.ReviewRecord record(Snapshot cursor) {
            return MatchLedgerReviewTest.record(review, GameReviewBookmark.encode(id, match, cursor, digest));
        }
    }
}
