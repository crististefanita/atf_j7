package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/** Stable local slots and move ownership shared by the controller and persistence. */
public final class MatchSlots {
    public static final int LIMIT = 10;

    private MatchSlots() { }

    public static String key(String legacyKey, int slot) {
        if (slot < 1 || slot > LIMIT) throw new IllegalArgumentException("Invalid match slot: " + slot);
        return slot == 1 ? legacyKey : legacyKey + "-" + slot;
    }

    public static int firstFree(IntPredicate occupied) {
        for (int slot = 1; slot <= LIMIT; slot++) if (!occupied.test(slot)) return slot;
        return 0;
    }

    public static List<Integer> newestFirst(IntPredicate occupied, IntToLongFunction creationOrder) {
        var slots = new ArrayList<Integer>();
        for (int slot = 1; slot <= LIMIT; slot++) if (occupied.test(slot)) slots.add(slot);
        slots.sort(Comparator.comparingLong((Integer slot) -> creationOrder.applyAsLong(slot)).thenComparingInt(Integer::intValue).reversed());
        return List.copyOf(slots);
    }

    public static int forNewGame(IntPredicate occupied, IntToLongFunction creationOrder) {
        int free = firstFree(occupied);
        return free > 0 ? free : newestFirst(occupied, creationOrder).get(LIMIT - 1);
    }

    public static boolean needsBot(ChessMatch match, boolean manualAi) {
        return !manualAi && match.awaitingBot();
    }

    public static boolean canMove(ChessMatch match, boolean manualAi, boolean reviewing) {
        return !reviewing && match.result() == ChessMatch.Result.IN_PROGRESS && !needsBot(match, manualAi);
    }

    public record BotTurn(int slot, int generation, ChessMatch match) {
        public boolean isCurrent(int activeSlot, int activeGeneration, ChessMatch activeMatch, boolean manualAi) {
            return slot == activeSlot && generation == activeGeneration && match == activeMatch && needsBot(match, manualAi);
        }
    }
}
