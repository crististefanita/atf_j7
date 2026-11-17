package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.MatchStore.GameOrigin;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchLedgerTest {
    private static final String ACTIVE = "1|f2f3 e7e5 g2g4";
    private static final String MATE = ACTIVE + " d8h4";
    private static final String OTHER = "3|WHITE|ADVANCED|0|d2d4 e7e5";
    private static final long TIME = 1_794_830_400_000L;

    @Test void migratesBothLegacyGamesExactlyOnceAndClassifiesEachByItsResult(Checks check) {
        var counter = new AtomicInteger();
        var first = open(null, MATE, "ai|" + OTHER, counter);
        var store = first.matches();
        check.same("Migration puts the active AI game only in Saved", List.of(2), store.savedMatchSlots());
        check.same("Migration retains the AI settings and exact active checkpoint", List.of(OTHER, false),
                List.of(store.readSavedMatch(2).checkpoint(), store.matchManualAi(2)));
        var archive = store.archivedMatches().getFirst();
        check.same("The finished manual title keeps its exact mate and outcome", List.of(MATE, ChessMatch.Result.BLACK_WINS),
                List.of(archive.match().checkpoint(), archive.match().result()));
        check.same("Two valid legacy sources obtain exactly two separate identities", List.of(1, 2, false),
                List.of(store.archivedMatches().size(), counter.get(), archive.id().equals(store.archivedId(2))));

        String saved = first.snapshot();
        var restored = open(saved, ACTIVE, "ai|1|e2e4", counter).matches();
        check.same("An existing ledger has priority over both leftover legacy values",
                List.of(List.of(2), archive.id(), MATE, OTHER, 2),
                List.of(restored.savedMatchSlots(), restored.archivedMatches().getFirst().id(),
                        restored.archivedMatches().getFirst().match().checkpoint(), restored.readSavedMatch(2).checkpoint(), counter.get()));
    }

    @Test void ignoresEachDamagedLegacyValueIndependentlyAndRejectsACorruptCanonicalLedger(Checks check) {
        var first = open(null, "invalid local", "ai|" + OTHER, new AtomicInteger()).matches();
        check.same("An invalid local source does not block the other AI game", 1, first.savedMatchSlots().size());
        check.same("The intact AI game retains its checkpoint", OTHER,
                first.readSavedMatch(first.savedMatchSlots().getFirst()).checkpoint());

        var second = open(null, ACTIVE, "1|d2d4", new AtomicInteger()).matches();
        check.same("A legacy AI value without its envelope cannot overwrite the valid local game",
                List.of(List.of(1), ACTIVE, true),
                List.of(second.savedMatchSlots(), second.readSavedMatch(1).checkpoint(), second.matchManualAi(1)));
        var counter = new AtomicInteger();
        check.rejects("A malformed canonical snapshot is rejected without falling back to stale data", IllegalArgumentException.class,
                () -> open("invalid canonical ledger", ACTIVE, "ai|" + OTHER, counter));
        check.same("A corrupt canonical snapshot allocates no new identities", 0, counter.get());
    }

    @Test void activeContinuationRewritesOnlyItsOwnSlotAndKeepsItsIdentityAndAge(Checks check) {
        var ledger = open(null, ACTIVE, "ai|" + OTHER, new AtomicInteger());
        var store = ledger.matches();
        String activeId = store.archivedId(1);
        long stamp = store.startedAt(1);
        check.same("Continuing a saved game reuses that slot", 1, ledger.continueGame(1, null, 2, false, true));
        check.same("Continuing a saved game truncates only its own history and keeps its identity, age and rank",
                List.of("1|f2f3 e7e5", activeId, stamp, List.of(2, 1), OTHER),
                List.of(store.readSavedMatch(1).checkpoint(), store.archivedId(1), store.startedAt(1),
                        store.savedMatchSlots(), store.readSavedMatch(2).checkpoint()));

        check.same("The active branch uses the displayed orientation",
                true, store.matchBlackBelow(1));
        String saved = ledger.snapshot();
        check.same("A missing slot and a forward-only seek cannot change saved games", List.of(0, 0),
                List.of(ledger.continueGame(3, null, 1, false, true), ledger.continueGame(1, null, 3, false, false)));
        check.same("Rejected continuation leaves the saved collection unchanged", saved, ledger.snapshot());
    }

    @Test void continuingAnArchiveCannotEvictActiveGamesAndCreatesANewIdentityWhenSpaceExists(Checks check) {
        var ledger = open(null, MATE, null, new AtomicInteger());
        var store = ledger.matches();
        String original = store.archivedMatches().getFirst().id();
        for (int index = 0; index < MatchSlots.LIMIT; index++) {
            store.startMatch(store.slotForNewMatch(), ChessMatch.restore(ACTIVE), false, true, false);
        }
        String full = ledger.snapshot();
        check.same("An archived game cannot displace one of ten active games", 0, ledger.continueGame(0, original, 2, false, true));
        check.same("Refusing a fork from a full collection preserves its exact state", full, ledger.snapshot());

        store.saveMatch(6, ChessMatch.restore(MATE), false, true, true);
        int free = ledger.continueGame(0, original, 2, false, true);
        check.same("Forking an archived game reuses the available slot", 6, free);
        check.same("It creates a fresh active game at the exact historical position", List.of(true, "1|f2f3 e7e5"),
                List.of(!original.equals(store.archivedId(free)), store.readSavedMatch(free).checkpoint()));
        check.same("The fork stores the preview orientation and manual mode while the archive keeps its orientation",
                List.of(true, true, true, false), List.of(store.matchBlackBelow(free), store.matchManualAi(free),
                        store.matchManualUsed(free), store.readArchivedMatch(original).blackBelow()));
        check.same("The archived result stays complete while the new game can continue", List.of(MATE, ChessMatch.Result.BLACK_WINS, true),
                List.of(store.readArchivedMatch(original).match().checkpoint(), store.readArchivedMatch(original).match().result(),
                        store.readSavedMatch(free).position().legalMoves().contains(Move.fromUci("d2d4"))));
    }

    @Test void preservesKindsFromTheOldSnapshotAndKeepsManualHistoryAcrossAContinuation(Checks check) {
        var first = open(null, null, null, new AtomicInteger());
        var legacy = first.matches();
        var checkpoint = ChessMatch.restore(ACTIVE);
        legacy.startMatch(1, checkpoint, false, true, true);
        legacy.startMatch(2, checkpoint, true, false, false);
        legacy.startMatch(3, ChessMatch.restore(MATE), false, true, true);
        legacy.startMatch(4, ChessMatch.restore(MATE), true, false, false);

        var restored = open(first.snapshot(), null, null, new AtomicInteger(4));
        var store = restored.matches();
        String localId = store.archivedId(3);
        String aiId = store.archivedId(4);
        check.same("The old active and finished formats classify otherwise identical checkpoints",
                List.of(GameOrigin.LOCAL, GameOrigin.AI, GameOrigin.LOCAL, GameOrigin.AI),
                List.of(store.matchOrigin(1), store.matchOrigin(2), store.archiveOrigin(store.readArchivedMatch(localId)),
                        store.archiveOrigin(store.readArchivedMatch(aiId))));
        check.same("The first manual save remembers an older AI game's origin before changing automation", true,
                store.saveMatch(2, checkpoint, true, store.matchOrigin(2), true, true));
        String original = store.archivedId(2);
        check.same("Continuing with automation reuses the AI slot", 2,
                restored.continueGame(2, null, 1, true, true));
        check.same("Restoring automation retains manual participation and the same AI game",
                List.of(original, GameOrigin.AI, false, true, "1|f2f3"),
                List.of(store.archivedId(2), store.matchOrigin(2), store.matchManualAi(2),
                        store.matchManualUsed(2), store.readSavedMatch(2).checkpoint()));

        int newSlot = restored.continueGame(0, aiId, 2, false, true);
        check.same("Selecting manual control for an archived AI continuation keeps its origin and leaves it eligible",
                List.of(true, GameOrigin.AI, true, false, GameOrigin.AI),
                List.of(!aiId.equals(store.archivedId(newSlot)), store.matchOrigin(newSlot),
                        store.matchManualAi(newSlot), store.matchManualUsed(newSlot), store.archiveOrigin(store.readArchivedMatch(aiId))));
    }

    private static MatchLedger open(String snapshot, String local, String ai, AtomicInteger ids) {
        return MatchLedger.open(snapshot, local, ai, () -> TIME, () -> "game-" + ids.incrementAndGet());
    }
}
