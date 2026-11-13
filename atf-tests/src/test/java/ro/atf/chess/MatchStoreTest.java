package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchStoreTest {
    private static final String LEGACY = "1|e2e4 e7e5";
    private static final String MATE = "1|f2f3 e7e5 g2g4 d8h4";
    private static final Instant START = Instant.parse("2026-11-11T18:30:00Z");
    private static final Instant FINISH = START.plusSeconds(125);

    @Test void savesTenGamesAndKeepsCreationOrderWhenTheOldestIsReopened(Checks check) {
        var disk = new MemoryPreferences();
        disk.data.put("match-checkpoint", LEGACY);
        var store = store(disk, START);
        store.migrateCompletedMatches();
        check.same("Legacy progress survives migration with no fabricated start time", List.of(LEGACY, 0L),
                List.of(store.readMatch(1).checkpoint(), store.startedAt(1)));
        var original = ChessMatch.restore(LEGACY);
        for (int slot = 2; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, original, true, true, true);
        String oldIdentity = store.archivedId(1);
        store.saveMatch(1, original, true, true, true);
        check.same("Reopening the oldest game preserves its age", 1, store.slotForNewMatch());

        var later = store(disk, FINISH);
        later.startMatch(later.slotForNewMatch(), ChessMatch.start(), false, false, false);
        var reopened = store(disk, FINISH.plusSeconds(500));
        check.same("The new save has its own identity, timestamp and move ownership", true,
                !oldIdentity.equals(reopened.archivedId(1)) && reopened.startedAt(1) == FINISH.toEpochMilli()
                        && reopened.activeMatchSlot() == 1 && reopened.readMatch(1).ply() == 0
                        && !reopened.matchBlackBelow() && !reopened.matchManualAi(1) && !reopened.matchManualUsed(1));
        check.same("All nine retained checkpoints and settings are unchanged", true,
                reopened.savedMatchSlots().subList(1, 10).stream().allMatch(slot -> original.checkpoint().equals(reopened.readMatch(slot).checkpoint())
                        && reopened.startedAt(slot) == START.toEpochMilli() && reopened.matchBlackBelow(slot) && reopened.matchManualUsed(slot)));
        check.same("Persisted order starts with the replacement and selects the second oldest next",
                List.of(List.of(1, 10, 9, 8, 7, 6, 5, 4, 3, 2), 2), List.of(reopened.savedMatchSlots(), reopened.slotForNewMatch()));
    }

    @Test void evictsTheOldestFinishedGameOnlyWhenTheReplacementFinishes(Checks check) {
        var disk = new MemoryPreferences();
        disk.data.put("match-checkpoint", MATE);
        var store = store(disk, START);
        store.migrateCompletedMatches();
        var mate = store.readMatch(1);
        String oldest = store.archivedId(1);
        store.migrateCompletedMatches();
        check.same("Legacy migration is idempotent and leaves the finish date unknown", List.of(oldest, 0L),
                List.of(store.archivedMatches().getFirst().id(), store.archivedMatches().getFirst().finishedAt()));
        check.same("The original review is stored with the finished game", true, store.saveReview(oldest, mate, GameReview.analyze(mate, ignored -> { })));
        var bookmark = new Snapshot(3, Branch.PLAYED, 1);
        check.same("The original bookmark belongs to its finished game", true, store.saveReviewBookmark(oldest, mate, bookmark));
        for (int slot = 2; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, mate, false, false, false);
        var firstNine = ids(store).subList(0, 9);
        int slot = store.slotForNewMatch();
        store.startMatch(slot, ChessMatch.start(), false, false, false);
        check.same("Recycling a slot preserves the earlier result and its review", true,
                slot == 1 && ids(store).size() == 10 && ids(store).getLast().equals(oldest) && store.readReview(oldest, mate) != null
                        && bookmark.equals(store.readReviewBookmark(oldest, mate)));

        var finished = store(disk, FINISH);
        finished.saveMatch(slot, mate, false, false, false);
        String newest = finished.archivedId(slot);
        var reopened = store(disk, FINISH.plusSeconds(300));
        reopened.saveMatch(slot, mate, false, false, false);
        check.same("The eleventh result keeps the other nine results and evicts only the oldest review", true,
                ids(reopened).getFirst().equals(newest) && ids(reopened).subList(1, 10).equals(firstNine)
                        && !reopened.hasArchived(oldest) && reopened.readReview(oldest, mate) == null);
        check.same("Eviction removes the bookmark in the same transaction", false, disk.data.containsKey(bookmarkKey(oldest)));
        check.same("A closed review cannot recreate the bookmark of an evicted game", false,
                reopened.saveReviewBookmark(oldest, mate, bookmark));
        check.same("Resaving a finished game preserves its original finish date", FINISH.toEpochMilli(), reopened.readArchivedMatch(newest).finishedAt());
    }

    @Test void reopensAnArchivedReviewBookmarkWithoutRewritingTheGameOrReport(Checks check) {
        var disk = new MemoryPreferences();
        var first = store(disk, START);
        var game = ChessMatch.restore(MATE);
        first.startMatch(1, game, false, false, false);
        String id = first.archivedId(1);
        first.saveReview(id, game, GameReview.analyze(game, ignored -> { }));
        check.same("An older archived report can reopen without a bookmark", null, first.readReviewBookmark(id, game));
        var archive = Map.copyOf(disk.data);
        int attempts = disk.commitAttempts;

        var bookmark = new Snapshot(3, Branch.PLAYED, 2);
        check.same("A review bookmark commits in one transaction", true, first.saveReviewBookmark(id, game, bookmark));
        var reopened = store(disk, FINISH);
        var withoutBookmark = new HashMap<>(disk.data);
        withoutBookmark.remove(bookmarkKey(id));
        check.same("Reopening with a new store restores the exact branch and step", bookmark, reopened.readReviewBookmark(id, game));
        check.same("Only the bookmark changes in the persisted data", List.of(archive, attempts + 1),
                List.of(withoutBookmark, disk.commitAttempts));

        int freeSlot = reopened.freeMatchSlot();
        var continuation = game.continueFrom(bookmark.ply() - 1);
        check.same("Continuing before the reviewed move opens a separate saved game", true,
                reopened.startMatch(freeSlot, continuation, false, false, false));
        check.same("Continuation preserves the archived result, report and bookmark", List.of(true, true, true, true),
                List.of(!id.equals(reopened.archivedId(freeSlot)), game.checkpoint().equals(reopened.readArchivedMatch(id).match().checkpoint()),
                        archive.get("match-archive-" + id + "-review").equals(disk.data.get("match-archive-" + id + "-review")),
                        bookmark.equals(reopened.readReviewBookmark(id, game))));

        disk.rejectCommit = true;
        check.same("A failed bookmark write is surfaced", false, reopened.saveReviewBookmark(id, game, new Snapshot(0, Branch.HISTORY, 0)));
        check.same("A failed bookmark write preserves the existing state", bookmark, reopened.readReviewBookmark(id, game));
    }

    @Test void rejectsBookmarksFromAnotherArchiveOrCheckpointAndMalformedPayloads(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk, START);
        var game = ChessMatch.restore(MATE);
        store.startMatch(1, game, false, false, false);
        store.startMatch(2, game, false, false, false);
        String first = store.archivedId(1);
        String duplicate = store.archivedId(2);
        var bookmark = new Snapshot(3, Branch.RECOMMENDED, 0);
        store.saveReviewBookmark(first, game, bookmark);

        String key = bookmarkKey(first);
        String encoded = (String) disk.data.get(key);
        disk.data.put(bookmarkKey(duplicate), encoded);
        check.same("The same checkpoint under another archive ID does not inherit a bookmark", null,
                store.readReviewBookmark(duplicate, game));
        var edited = ChessMatch.restore("1|f2f3 e7e6 g2g4 d8h4");
        disk.data.put("match-archive-" + first + "-checkpoint", edited.checkpoint());
        check.same("The previous bookmark does not apply to a changed checkpoint under the same ID", null,
                store.readReviewBookmark(first, edited));
        check.same("A stale checkpoint cannot write a bookmark for the edited archive", false,
                store.saveReviewBookmark(first, game, bookmark));

        disk.data.put("match-archive-" + first + "-checkpoint", game.checkpoint());
        disk.data.put(key, encoded.substring(0, encoded.lastIndexOf('|') + 1) + "65");
        check.same("A step beyond the cache line limit is rejected", null, store.readReviewBookmark(first, game));
        disk.data.put(key, encoded.replace("|RECOMMENDED|", "|UNKNOWN|"));
        check.same("An unknown branch leaves the review at its default position", null, store.readReviewBookmark(first, game));
        disk.data.put(key, 17);
        check.same("A preference of the wrong type is treated as absent", null, store.readReviewBookmark(first, game));
        check.same("An out of range ply cannot be written", false, store.saveReviewBookmark(first, game, new Snapshot(5, Branch.HISTORY, 0)));
        check.same("An absent archive cannot acquire a bookmark", false, store.saveReviewBookmark("missing", game, bookmark));
    }

    @Test void aReplacementSubmitsOneTransactionAndPropagatesTheStorageResult(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk, START);
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, ChessMatch.restore(MATE), false, false, false);
        var snapshot = Map.copyOf(disk.data);
        int attempts = disk.commitAttempts;
        disk.rejectCommit = true;
        check.same("The replacement propagates a failed transaction", false,
                store.startMatch(store.slotForNewMatch(), ChessMatch.start(), false, false, false));
        check.same("The fake rejects one batch without applying any of its changes", List.of(attempts + 1, true),
                List.of(disk.commitAttempts, snapshot.equals(disk.data)));
    }

    @Test void excludesAnUnfinishedCheckpointFromTheResultsList(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk, START);
        store.startMatch(1, ChessMatch.restore(MATE), false, false, false);
        store.startMatch(2, ChessMatch.restore(MATE), false, false, false);
        String broken = store.archivedId(2);
        disk.data.put("match-archive-" + broken + "-checkpoint", LEGACY);
        check.same("A damaged archive entry leaves the other result reachable", List.of(store.archivedId(1)), ids(store));
        check.same("The damaged result cannot be reopened by ID", null, store.readArchivedMatch(broken));
    }

    private static MatchStore store(MemoryPreferences disk, Instant instant) {
        return new MatchStore(disk, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String bookmarkKey(String id) { return "match-archive-" + id + "-review-bookmark"; }

    private static List<String> ids(MatchStore store) {
        return store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList();
    }
}
