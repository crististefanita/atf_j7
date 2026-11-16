package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchLedgerPreferencesTest {
    private static final String ACTIVE = "1|f2f3 e7e5 g2g4";
    private static final String MATE = ACTIVE + " d8h4";
    private static final String ADVANCED = "3|WHITE|ADVANCED|0|d2d4 e7e5";
    private static final Clock TIME = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);

    @Test void supportsIsolatedTransactionsAndTypedValuesInAFreshSnapshot(Checks check) {
        var preferences = new MatchLedgerPreferences();
        var editor = preferences.edit().putString("game", "șah|♞\nlinia doi").putBoolean("orientation", true)
                .putInt("slot", 10).putLong("created", TIME.millis());
        check.same("Uncommitted changes are absent from the durable snapshot", false,
                new MatchLedgerPreferences(preferences.snapshot()).contains("game"));
        check.same("Committing publishes all four typed values together", true, editor.commit());
        String frozen = preferences.snapshot();
        var stored = new MatchLedgerPreferences(frozen);
        check.same("Reopening preserves text, direction, slot and epoch milliseconds",
                List.of("șah|♞\nlinia doi", true, 10, TIME.millis()), List.of(stored.getString("game", ""),
                        stored.getBoolean("orientation", false), stored.getInt("slot", 0), stored.getLong("created", 0)));

        var stale = stored.edit().putString("game", "stale write");
        check.same("Removing the game's text from a reopened copy commits", true, stored.edit().remove("game").commit());
        check.same("An editor based on the previous snapshot cannot undo that transaction", false, stale.commit());
        check.same("A reopened copy changes independently of the original and its serialized bytes",
                List.of(false, true, true), List.of(stored.contains("game"), preferences.contains("game"),
                        new MatchLedgerPreferences(frozen).contains("game")));
    }

    @Test void rejectsAnUnknownVersionAndPartialOrAppendedSnapshots(Checks check) {
        var preferences = new MatchLedgerPreferences();
        preferences.edit().putString("checkpoint", ACTIVE).commit();
        byte[] encoded = Base64.getDecoder().decode(preferences.snapshot());
        byte[] otherVersion = encoded.clone();
        otherVersion[3] = 2;
        check.rejects("Durable snapshots reject an unsupported schema version", IllegalArgumentException.class,
                () -> new MatchLedgerPreferences(Base64.getEncoder().encodeToString(otherVersion)));
        check.rejects("Durable snapshots reject an incomplete final value", IllegalArgumentException.class,
                () -> new MatchLedgerPreferences(Base64.getEncoder().encodeToString(Arrays.copyOf(encoded, encoded.length - 1))));
        check.rejects("Durable snapshots reject data appended after the final entry", IllegalArgumentException.class,
                () -> new MatchLedgerPreferences(Base64.getEncoder().encodeToString(Arrays.copyOf(encoded, encoded.length + 1))));
        check.same("Validation does not alter the original checkpoint", ACTIVE, preferences.getString("checkpoint", ""));
    }

    @Test void eachMenuUsesOnlyItsOwnCheckpointsAfterReloadAndAStaleSavedRowCannotBeSelected(Checks check) {
        var preferences = new MatchLedgerPreferences();
        var store = new MatchStore(preferences, TIME);
        var local = ChessMatch.restore(ACTIVE);
        var ai = ChessMatch.restore(ADVANCED);
        check.same("Creating a manual game and an AI game writes separate active slots", List.of(true, true),
                List.of(store.startMatch(1, local, false, true, false), store.startMatch(2, ai, false, false, false)));
        String localId = store.archivedId(1);
        String aiId = store.archivedId(2);
        check.same("Saved holds both active identities and Finished is empty", List.of(List.of(2, 1), List.of()),
                List.of(store.savedMatchSlots(), ids(store)));

        var opened = new MatchLedgerPreferences(preferences.snapshot());
        var resumed = new MatchStore(opened, TIME);
        check.same("Selecting the older saved game restores its move ownership", true, resumed.selectSaved(1));
        check.same("Selection preserves the creation order and game identities", List.of(1, List.of(2, 1), localId, aiId, true),
                List.of(resumed.activeMatchSlot(), resumed.savedMatchSlots(), resumed.archivedId(1), resumed.archivedId(2),
                        resumed.matchManualAi(1)));
        check.same("Recording mate finishes only the selected local game", true,
                resumed.saveMatch(1, local.play(Move.fromUci("d8h4")), false, true, true));

        var persisted = new MatchLedgerPreferences(opened.snapshot());
        var restored = new MatchStore(persisted, TIME);
        check.same("Saved excludes the completed game and Finished preserves its original identity and outcome",
                List.of(List.of(2), List.of(localId), MATE, ChessMatch.Result.BLACK_WINS),
                List.of(restored.savedMatchSlots(), ids(restored), restored.readArchivedMatch(localId).match().checkpoint(),
                        restored.readArchivedMatch(localId).match().result()));
        check.same("The other slot retains its own identity, AI settings and exact checkpoint", List.of(aiId, ADVANCED, false),
                List.of(restored.archivedId(2), restored.readSavedMatch(2).checkpoint(), restored.matchManualAi(2)));
        String committed = persisted.snapshot();
        check.same("A stale Saved row for the completed slot is rejected", false, restored.selectSaved(1));
        check.same("The stale selection leaves the durable ledger unchanged", committed, persisted.snapshot());
    }

    @Test void continuationReloadsAsANewGameWithoutChangingTheArchiveOrBookmark(Checks check) {
        var preferences = new MatchLedgerPreferences();
        var store = new MatchStore(preferences, TIME);
        var original = ChessMatch.restore(MATE);
        check.same("The finished source is available for review", true, store.startMatch(1, original, false, true, true));
        String sourceId = store.archivedId(1);
        var report = GameReview.analyze(original, ignored -> { });
        var bookmark = new Snapshot(2, Branch.HISTORY, 0);
        check.same("The archived report and its selected move are saved by source identity", List.of(true, true),
                List.of(store.saveReview(sourceId, original, report), store.saveReviewBookmark(sourceId, original, bookmark)));

        var continuation = original.continueFrom(bookmark.ply());
        check.same("The continuation starts at the selected historical position", "1|f2f3 e7e5", continuation.checkpoint());
        int slot = store.freeMatchSlot();
        check.same("The continuation can use a separate saved slot", true, slot > 0 &&
                store.startMatch(slot, continuation, false, true, true));
        String newId = store.archivedId(slot);
        check.same("The continuation receives a new game identity", true, !sourceId.equals(newId));
        check.same("An alternate legal move advances only the new game", true,
                store.saveMatch(slot, continuation.play(Move.fromUci("d2d4")), false, true, true));

        var restored = new MatchStore(new MatchLedgerPreferences(preferences.snapshot()), TIME);
        check.same("Restart restores the new active branch and the independent archived game",
                List.of(newId, "1|f2f3 e7e5 d2d4", MATE, List.of(sourceId)),
                List.of(restored.archivedId(slot), restored.readSavedMatch(slot).checkpoint(),
                        restored.readArchivedMatch(sourceId).match().checkpoint(), ids(restored)));
        check.same("Restart preserves the original review and history bookmark", List.of(report, bookmark),
                List.of(restored.readReview(sourceId, original), restored.readReviewBookmark(sourceId, original)));
    }

    @Test void activeSlotsAndFinishedHistoryEachRetainTenGamesAcrossSerialization(Checks check) {
        var preferences = new MatchLedgerPreferences();
        var store = new MatchStore(preferences, TIME);
        var active = ChessMatch.restore(ACTIVE);
        var mate = ChessMatch.restore(MATE);
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) store.startMatch(slot, active, false, true, false);
        check.same("Ten active games fill the library and a new game would replace the oldest", List.of(10, 1),
                List.of(store.savedMatchSlots().size(), store.slotForNewMatch()));
        check.same("Completing a slot makes it reusable before older active games are evicted", true,
                store.saveMatch(6, mate, false, true, true));
        String oldestFinished = store.archivedId(6);
        String otherActive = store.archivedId(1);
        check.same("The completed slot is reused first", 6, store.slotForNewMatch());

        for (int index = 0; index < MatchArchive.LIMIT; index++) {
            store.startMatch(6, mate, false, true, true);
        }
        String newestFinished = store.archivedId(6);
        store.startMatch(6, active, false, true, false);
        var restored = new MatchStore(new MatchLedgerPreferences(preferences.snapshot()), TIME);
        check.same("The saved games and the finished results each retain ten entries",
                List.of(10, 10, newestFinished, otherActive),
                List.of(restored.savedMatchSlots().size(), restored.archivedMatches().size(), ids(restored).getFirst(), restored.archivedId(1)));
        check.same("The oldest finished identity is evicted without touching the older active game", List.of(false, ACTIVE),
                List.of(restored.hasArchived(oldestFinished), restored.readSavedMatch(1).checkpoint()));
    }

    private static List<String> ids(MatchStore store) { return store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList(); }
}
