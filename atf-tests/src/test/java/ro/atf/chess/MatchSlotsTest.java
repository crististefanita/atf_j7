package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchSlotsTest {
    @Test void allocatesTenIsolatedSlotsAndReadsTheLegacyCheckpoint(Checks check) {
        var keys = new HashSet<String>();
        var checkpoints = new HashMap<String, String>();
        String legacy = "1|e2e4";
        checkpoints.put("match-checkpoint", legacy);
        for (int slot = 2; slot <= MatchSlots.LIMIT; slot++) {
            check.same("The next unused slot is " + slot, slot,
                    MatchSlots.firstFree(candidate -> checkpoints.containsKey(MatchSlots.key("match-checkpoint", candidate))));
            checkpoints.put(MatchSlots.key("match-checkpoint", slot),
                    ChessMatch.start().play(slot == MatchSlots.LIMIT ? Move.fromUci("a2a4")
                            : new Move(slot + 6, slot + 14, null)).checkpoint());
        }
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) {
            for (String prefix : List.of("match-checkpoint", "match-black-below", "match-manual-ai", "match-manual-used")) {
                keys.add(MatchSlots.key(prefix, slot));
            }
        }
        check.same("The original checkpoint remains readable in the first slot", legacy,
                ChessMatch.restore(checkpoints.get(MatchSlots.key("match-checkpoint", 1))).checkpoint());
        check.same("The four per-slot preference keys never collide", 40, keys.size());
        check.same("An eleventh slot cannot be allocated", 0,
                MatchSlots.firstFree(candidate -> checkpoints.containsKey(MatchSlots.key("match-checkpoint", candidate))));
        check.rejects("An out of bounds slot cannot write a checkpoint", IllegalArgumentException.class,
                () -> MatchSlots.key("match-checkpoint", 11));
    }

    @Test void changingOwnershipInvalidatesAQueuedBotReply(Checks check) {
        var pending = ChessMatch.restore("1|e2e4");
        var turn = new MatchSlots.BotTurn(3, 11, pending);
        check.same("The AI owns the opposing turn by default", false, MatchSlots.canMove(pending, false, false));
        check.same("Manual control enables the same legal opposing turn", true, MatchSlots.canMove(pending, true, false));
        check.same("Manual control does not unlock a historic position", false, MatchSlots.canMove(pending, true, true));
        check.same("The original queued reply belongs to the open slot and position", true, turn.isCurrent(3, 11, pending, false));
        check.same("A reply cannot commit after enabling manual control", false, turn.isCurrent(3, 11, pending, true));
        check.same("A reply cannot commit to another slot even with the same position", false, turn.isCurrent(4, 11, pending, false));
        check.same("Switching away and back rejects the old generation", false, turn.isCurrent(3, 12, pending, false));
        var manuallyPlayed = pending.play(Move.fromUci("c7c5"));
        check.same("Manual control records the chosen legal move", "c7c5", manuallyPlayed.lastMove().uci());
        check.same("An old reply cannot commit after a manual move", false, turn.isCurrent(3, 11, manuallyPlayed, false));
        check.same("Turning the option off restarts the bot when its turn is pending", true, MatchSlots.needsBot(pending, false));
    }

    @Test void aStudyContinuationOccupiesOnlyTheFreeSlotAndCannotDisplaceTenGames(Checks check) {
        var slots = new HashMap<Integer, String>();
        var source = ChessMatch.restore("3|WHITE|HARD|0|e2e4 e7e5 g1f3");
        for (int slot = 1; slot < MatchSlots.LIMIT; slot++) slots.put(slot, source.continueFrom(slot % 4).checkpoint());
        var existing = new HashMap<>(slots);
        var continuation = new AnalysisLine(source, 1).resumeMatch(true);
        int free = MatchSlots.firstFree(slots::containsKey);
        check.same("The continuation takes the tenth place", 10, free);
        slots.put(free, continuation.checkpoint());
        existing.forEach((slot, checkpoint) -> check.same("The previous checkpoint survives in slot " + slot, checkpoint, slots.get(slot)));
        var resumed = ChessMatch.restore(slots.get(free));
        check.same("The restored tenth game starts on the AI turn", true, MatchSlots.needsBot(resumed, false));
        check.same("Manual continuation gives the same turn to the player", true, MatchSlots.canMove(resumed, true, false));
        check.same("A full list has no replacement target for a study line", 0, MatchSlots.firstFree(slots::containsKey));
    }
}
