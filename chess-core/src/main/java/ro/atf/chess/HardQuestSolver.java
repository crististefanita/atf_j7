package ro.atf.chess;

import java.util.List;
import ro.atf.chess.ChessPosition.Move;

/** Solves an exercise from its position and objective without reading the catalog. */
public final class HardQuestSolver {
    private static final int MAX_MATE_NODES = 100_000;
    private static final int MATERIAL_PLIES = 3;
    private static final int MIN_MATERIAL_GAIN = 200;

    public enum Status { SOLVED, UNPROVEN, BUDGET_EXHAUSTED }

    public record Solution(Status status, List<Move> line) {
        public Solution { line = List.copyOf(line); }
    }

    private HardQuestSolver() { }

    /** The current curriculum supports up to three moves by the attacking side. */
    public static Solution mate(ChessPosition position, int moves) {
        if (moves < 1 || moves > 3) throw new IllegalArgumentException("Unsupported mate length: " + moves);
        var proof = ForcedMateSearch.prove(position, 2 * moves - 1, MAX_MATE_NODES);
        return switch (proof.status()) {
            case PROVED -> new Solution(Status.SOLVED, proof.line());
            case NO_MATE -> new Solution(Status.UNPROVEN, List.of());
            case BUDGET_EXHAUSTED -> new Solution(Status.BUDGET_EXHAUSTED, List.of());
        };
    }

    public static Solution material(ChessPosition position) {
        var winners = TacticalAnalysis.analyze(position, MATERIAL_PLIES).best();
        if (winners.size() != 1) return new Solution(Status.UNPROVEN, List.of());
        var best = winners.get(0);
        return best.ending() == TacticalAnalysis.Ending.HORIZON && best.materialChange() >= MIN_MATERIAL_GAIN
                ? new Solution(Status.SOLVED, best.line()) : new Solution(Status.UNPROVEN, List.of());
    }
}
