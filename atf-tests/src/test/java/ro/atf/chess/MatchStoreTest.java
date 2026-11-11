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
        for (int slot = 2; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, mate, false, false, false);
        var firstNine = ids(store).subList(0, 9);
        int slot = store.slotForNewMatch();
        store.startMatch(slot, ChessMatch.start(), false, false, false);
        check.same("Recycling a slot preserves the earlier result and its review", true,
                slot == 1 && ids(store).size() == 10 && ids(store).getLast().equals(oldest) && store.readReview(oldest, mate) != null);

        var finished = store(disk, FINISH);
        finished.saveMatch(slot, mate, false, false, false);
        String newest = finished.archivedId(slot);
        var reopened = store(disk, FINISH.plusSeconds(300));
        reopened.saveMatch(slot, mate, false, false, false);
        check.same("The eleventh result keeps the other nine results and evicts only the oldest review", true,
                ids(reopened).getFirst().equals(newest) && ids(reopened).subList(1, 10).equals(firstNine)
                        && !reopened.hasArchived(oldest) && reopened.readReview(oldest, mate) == null);
        check.same("Resaving a finished game preserves its original finish date", FINISH.toEpochMilli(), reopened.readArchivedMatch(newest).finishedAt());
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

    private static MatchStore store(MemoryPreferences disk, Instant instant) {
        return new MatchStore(disk, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static List<String> ids(MatchStore store) {
        return store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList();
    }

    private static final class MemoryPreferences implements MatchStore.Preferences {
        private final Map<String, Object> data = new HashMap<>();
        private boolean rejectCommit;
        private int commitAttempts;

        @Override public String getString(String key, String fallback) { return (String) data.getOrDefault(key, fallback); }
        @Override public boolean getBoolean(String key, boolean fallback) { return (boolean) data.getOrDefault(key, fallback); }
        @Override public int getInt(String key, int fallback) { return (int) data.getOrDefault(key, fallback); }
        @Override public long getLong(String key, long fallback) { return (long) data.getOrDefault(key, fallback); }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public MatchStore.Editor edit() { return new Transaction(); }

        private final class Transaction implements MatchStore.Editor {
            private final Map<String, Object> writes = new HashMap<>();

            @Override public MatchStore.Editor putString(String key, String value) { writes.put(key, value); return this; }
            @Override public MatchStore.Editor putBoolean(String key, boolean value) { writes.put(key, value); return this; }
            @Override public MatchStore.Editor putInt(String key, int value) { writes.put(key, value); return this; }
            @Override public MatchStore.Editor putLong(String key, long value) { writes.put(key, value); return this; }
            @Override public MatchStore.Editor remove(String key) { writes.put(key, null); return this; }
            @Override public boolean commit() {
                commitAttempts++;
                if (rejectCommit) return false;
                writes.forEach((key, value) -> {
                    if (value == null) data.remove(key);
                    else data.put(key, value);
                });
                return true;
            }
        }
    }
}
