package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
import ro.atf.chess.MatchStore.GameOrigin;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchStoreTest {
    private static final String LEGACY = "1|e2e4 e7e5";
    private static final String MATE = "1|f2f3 e7e5 g2g4 d8h4";
    private static final Instant START = Instant.parse("2026-11-11T18:30:00Z");
    private static final Instant FINISH = START.plusSeconds(125);

    private record FlipCase(String id, String mode, String checkpoint, ChessMatch.Result result, int ply, int viewPly,
                            ChessPosition.Side sideToMove, String lastMove, boolean blackBelow, boolean flippedBlackBelow,
                            boolean reopenedBlackBelow, String continuationCheckpoint) { }

    @Test void flipsTheStoredBoardAndPreservesTheOriginalGame(Checks check) throws IOException {
        FlipCase[] cases;
        try (var fixture = MatchStoreTest.class.getResourceAsStream("/match-flip-cases.json")) {
            cases = new ObjectMapper().readValue(fixture, FlipCase[].class);
        }
        check.same("The shared fixture covers a current board, history and a finished preview",
                List.of("local-current", "ai-history", "finished-preview"), java.util.Arrays.stream(cases).map(FlipCase::id).toList());
        for (var example : cases) {
            var match = ChessMatch.restore(example.checkpoint());
            check.same(example.id() + " fixes the checkpoint, result, cursor move and turn",
                    List.of(example.checkpoint(), example.result(), example.ply(), example.lastMove(), example.sideToMove()),
                    List.of(match.checkpoint(), match.result(), match.ply(), match.moveAt(example.viewPly()).uci(), match.positionAt(example.viewPly()).turn()));

            var disk = new MemoryPreferences();
            var initial = store(disk, START);
            boolean manual = example.mode().equals("LOCAL");
            check.same(example.id() + " saves the original board", true,
                    initial.startMatch(1, match, example.blackBelow(), manual, manual));
            String identity = initial.archivedId(1);
            if (match.result() != ChessMatch.Result.IN_PROGRESS) {
                var preserved = store(disk, FINISH).readArchivedMatch(identity);
                check.same(example.id() + " retains its original orientation and source when reopened",
                        List.of(example.reopenedBlackBelow(), example.checkpoint(), identity),
                        List.of(preserved.blackBelow(), preserved.match().checkpoint(), preserved.id()));
                continue;
            }
            var expectedDisk = new HashMap<>(disk.data);
            expectedDisk.put("match-black-below", example.flippedBlackBelow());
            check.same(example.id() + " writes the orientation using the Android save path", true,
                    initial.saveMatch(1, initial.readMatch(1), example.flippedBlackBelow(), initial.matchManualAi(1)));
            check.same(example.id() + " writes only the orientation and preserves slot metadata", expectedDisk, disk.data);
            var reopened = store(disk, FINISH);
            check.same(example.id() + " restores the orientation, same checkpoint and same identity",
                    List.of(example.reopenedBlackBelow(), example.checkpoint(), identity),
                    List.of(reopened.matchBlackBelow(1), reopened.readSavedMatch(1).checkpoint(), reopened.archivedId(1)));

            disk.rejectCommit = true;
            check.same(example.id() + " reports a failed orientation save", false,
                    reopened.saveMatch(1, reopened.readMatch(1), example.blackBelow(), reopened.matchManualAi(1)));
            check.same(example.id() + " keeps the prior orientation after the failed save", expectedDisk, disk.data);
        }
    }

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

    @Test void persistsGameOriginWithManualControlThroughCompletionAndSlotReuse(Checks check) {
        var disk = new MemoryPreferences();
        var first = store(disk, START);
        var beforeMove = ChessMatch.restore("1|f2f3");
        check.same("A new AI game saves automatic control", true,
                first.startMatch(1, beforeMove, true, GameOrigin.AI, false, false));
        String aiId = first.archivedId(1);

        check.same("Entering manual control commits without moving or replacing the game", true,
                first.saveMatch(1, beforeMove, true, GameOrigin.AI, true, false));
        var reopened = store(disk, FINISH);
        check.same("Reload restores the original game and current mode without recording a manual move",
                List.of(aiId, beforeMove.checkpoint(), GameOrigin.AI, true, false, START.toEpochMilli()),
                List.of(reopened.archivedId(1), reopened.readSavedMatch(1).checkpoint(), reopened.matchOrigin(1),
                        reopened.matchManualAi(1), reopened.matchManualUsed(1), reopened.startedAt(1)));

        Map<String, Object> durable = Map.copyOf(disk.data);
        check.same("An existing AI game cannot be relabeled as local in place", false,
                reopened.saveMatch(1, beforeMove, true, GameOrigin.LOCAL, true, false));
        disk.rejectCommit = true;
        check.same("A failed save reports failure when returning to automatic control", false,
                reopened.saveMatch(1, beforeMove, true, GameOrigin.AI, false, false));
        check.same("A rejected change keeps the prior slot data", durable, disk.data);

        disk.rejectCommit = false;
        var afterManualMove = beforeMove.play(ChessPosition.Move.fromUci("e7e5"));
        check.same("A manual AI move records participation", true,
                reopened.saveMatch(1, afterManualMove, true, GameOrigin.AI, true, true));
        check.same("The archived result retains the recorded participation when automation is enabled", true,
                reopened.saveMatch(1, ChessMatch.restore(MATE), true, GameOrigin.AI, false, false));
        var archived = reopened.readArchivedMatch(aiId);
        check.same("Finished keeps the original AI identity, exact game and manual participation",
                List.of(aiId, MATE, GameOrigin.AI, true),
                List.of(archived.id(), archived.match().checkpoint(), reopened.archiveOrigin(archived), archived.manualUsed()));

        check.same("A newly used slot may hold a local game with the same checkpoint", true,
                reopened.startMatch(1, beforeMove, false, GameOrigin.LOCAL, true, true));
        var local = store(disk, FINISH);
        check.same("The new slot identity and archived AI origin stay independent",
                List.of(true, GameOrigin.LOCAL, GameOrigin.AI),
                List.of(!aiId.equals(local.archivedId(1)), local.matchOrigin(1), local.archiveOrigin(local.readArchivedMatch(aiId))));

        for (int index = 0; index < MatchArchive.LIMIT; index++) {
            check.same("Completing another local game records its origin", true,
                    local.startMatch(2, ChessMatch.restore(MATE), false, GameOrigin.LOCAL, true, true));
        }
        check.same("Eviction removes both the old archive and its origin metadata", List.of(false, false),
                List.of(local.hasArchived(aiId), disk.data.containsKey("match-archive-" + aiId + "-uses-ai")));
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
        disk.data.put(key, encoded.replace("|RECOMMENDED|0|", "|RECOMMENDED|65|"));
        check.same("A step beyond the cache line limit is rejected", null, store.readReviewBookmark(first, game));
        disk.data.put(key, encoded.replace("|RECOMMENDED|", "|UNKNOWN|"));
        check.same("An unknown branch leaves the review at its default position", null, store.readReviewBookmark(first, game));
        disk.data.put(key, encoded.replace("|true", "|yes"));
        check.same("A malformed panel flag cannot reopen the bookmark", null, store.readReviewBookmark(first, game));
        disk.data.put(key, 17);
        check.same("A preference of the wrong type is treated as absent", null, store.readReviewBookmark(first, game));
        check.same("An out of range ply cannot be written", false, store.saveReviewBookmark(first, game, new Snapshot(5, Branch.HISTORY, 0)));
        check.same("An absent archive cannot acquire a bookmark", false, store.saveReviewBookmark("missing", game, bookmark));
    }

    @Test void savesTheExplanationPanelInHistoryAndReadsLegacyBookmarks(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk, START);
        var game = ChessMatch.restore(MATE);
        store.startMatch(1, game, false, false, false);
        String id = store.archivedId(1);
        var open = new Snapshot(3, Branch.HISTORY, 0, true);
        var closed = new Snapshot(3, Branch.HISTORY, 0, false);
        check.same("Opening Why can be saved without changing the history cursor", true, store.saveReviewBookmark(id, game, open));
        var restarted = store(disk, FINISH);
        check.same("Restart restores Why on the recorded move", open, restarted.readReviewBookmark(id, game));
        check.same("Closing Why updates the same bookmark", true, restarted.saveReviewBookmark(id, game, closed));
        check.same("Another restart keeps Why closed", closed, store(disk, FINISH).readReviewBookmark(id, game));

        String key = bookmarkKey(id);
        String encoded = (String) disk.data.get(key);
        String legacy = "1" + encoded.substring(1, encoded.lastIndexOf('|'));
        disk.data.put(key, legacy);
        check.same("A legacy history bookmark opens with the panel closed", closed, restarted.readReviewBookmark(id, game));
        disk.data.put(key, legacy.replace("|HISTORY|0", "|PLAYED|1"));
        check.same("A legacy line bookmark retains the former open-panel behavior", new Snapshot(3, Branch.PLAYED, 1, true),
                restarted.readReviewBookmark(id, game));
        check.same("Panel changes preserve the original game", game.checkpoint(), restarted.readArchivedMatch(id).match().checkpoint());
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

    @Test void movesCompletedGamesOutOfSavedAfterCommitAndRestart(Checks check) {
        var disk = new MemoryPreferences();
        disk.data.put("match-checkpoint", MATE);
        var first = store(disk, START);
        first.migrateCompletedMatches();
        String legacy = first.archivedId(1);
        first.startMatch(2, ChessMatch.restore(LEGACY), false, false, false);
        first.startMatch(3, ChessMatch.start(), false, false, false);
        String ending = first.archivedId(3);
        disk.data.put("match-checkpoint-4", "broken");
        check.same("Saved exposes only playable checkpoints; Finished already contains the migrated result",
                List.of(List.of(3, 2), List.of(legacy)), List.of(first.savedMatchSlots(), ids(first)));

        disk.rejectCommit = true;
        check.same("A failed completion leaves the game active", false,
                first.saveMatch(3, ChessMatch.start().resign(), false, false, false));
        check.same("A failed completion publishes neither half of the transition", List.of(List.of(3, 2), List.of(legacy)),
                List.of(first.savedMatchSlots(), ids(first)));
        disk.rejectCommit = false;
        var finish = store(disk, FINISH);
        check.same("The result and checkpoint are saved together", true,
                finish.saveMatch(3, ChessMatch.start().resign(), false, false, false));

        var reopened = store(disk, FINISH.plusSeconds(1));
        reopened.migrateCompletedMatches();
        check.same("Restart keeps active and finished games in disjoint menus", List.of(List.of(2), List.of(ending, legacy)),
                List.of(reopened.savedMatchSlots(), ids(reopened)));
        check.same("A stale Saved row cannot reopen a finished game", null, reopened.readSavedMatch(3));
        check.same("The result keeps its ID, outcome and finish time", List.of(ChessMatch.Result.BLACK_WINS, FINISH.toEpochMilli()),
                List.of(reopened.readArchivedMatch(ending).match().result(), reopened.readArchivedMatch(ending).finishedAt()));
    }

    @Test void migratesMixedLegacySlotsOnceWhileKeepingDamagedEntriesOutOfBothMenus(Checks check) {
        var disk = new MemoryPreferences();
        var draw = ChessMatch.restore(LEGACY).offerDraw().checkpoint();
        String otherActive = "3|BLACK|ADVANCED|0|e2e4";
        disk.data.put("match-checkpoint", LEGACY);
        disk.data.put("match-checkpoint-2", MATE);
        disk.data.put("match-checkpoint-4", "damaged checkpoint");
        disk.data.put("match-checkpoint-7", draw);
        disk.data.put("match-checkpoint-10", otherActive);
        disk.data.put("active-match-slot", 2);
        disk.data.put("match-black-below-2", true);

        var migrated = store(disk, START);
        migrated.migrateCompletedMatches();
        check.same("Saved contains only the two playable legacy slots in their existing order", List.of(10, 1), migrated.savedMatchSlots());
        check.same("The migrated active checkpoints keep their exact moves and settings", List.of(otherActive, LEGACY),
                List.of(migrated.readSavedMatch(10).checkpoint(), migrated.readSavedMatch(1).checkpoint()));
        var archives = migrated.archivedMatches();
        check.same("Both finished legacy games retain exact outcomes and leave their finish dates unknown",
                List.of(List.of(MATE, ChessMatch.Result.BLACK_WINS, 0L), List.of(draw, ChessMatch.Result.AGREED_DRAW, 0L)),
                archives.stream().map(entry -> List.of(entry.match().checkpoint(), entry.match().result(), entry.finishedAt())).toList());
        check.same("Migration retains the archived board orientation", true, archives.getFirst().blackBelow());
        check.same("A malformed checkpoint cannot acquire a game identity", null, migrated.archivedId(4));

        var identities = ids(migrated);
        var snapshot = Map.copyOf(disk.data);
        var reopened = store(disk, FINISH);
        reopened.migrateCompletedMatches();
        check.same("Repeated migration after restart preserves identities and data byte for byte", List.of(identities, snapshot),
                List.of(ids(reopened), Map.copyOf(disk.data)));
    }

    @Test void reusesACompletedSlotBeforeEvictingAnActiveGame(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk, START);
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, ChessMatch.restore(LEGACY), false, false, false);
        check.same("Ten active games still replace the oldest when a new game starts", List.of(0, 1),
                List.of(store.freeMatchSlot(), store.slotForNewMatch()));
        store.saveMatch(6, ChessMatch.restore(LEGACY).offerDraw(), false, false, false);
        String finished = store.archivedId(6);
        check.same("A completed slot is available even if all ten checkpoints exist", List.of(6, 6),
                List.of(store.freeMatchSlot(), store.slotForNewMatch()));
        store.startMatch(store.slotForNewMatch(), ChessMatch.start(), false, false, false);
        var reopened = store(disk, FINISH);
        check.same("The new game, nine earlier active games and the old result all remain accessible", List.of(10, 6, 2, List.of(finished)),
                List.of(reopened.savedMatchSlots().size(), reopened.savedMatchSlots().getFirst(), reopened.readSavedMatch(1).ply(), ids(reopened)));
    }

    private static MatchStore store(MemoryPreferences disk, Instant instant) {
        return new MatchStore(disk, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String bookmarkKey(String id) { return "match-archive-" + id + "-review-bookmark"; }

    private static List<String> ids(MatchStore store) {
        return store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList();
    }
}
