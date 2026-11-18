package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Bounded challengers for a defensive hint that currently omits a known theory move. */
final class HintCandidateCoverage {
    private HintCandidateCoverage() { }

    static ArrayList<Move> expand(ChessPosition position, List<Move> current, Set<Move> excluded) {
        var result = new ArrayList<>(current);
        if (current.size() != 2 || current.stream().noneMatch(move -> HintPinSupport.reinforces(position, move))) return result;
        var theory = OpeningRepertoire.CURRENT.playReply(position, MatchSetup.Level.HARD);
        if (theory == null || current.contains(theory) || excluded.contains(theory)) return result;
        result.add(theory);
        position.legalMoves().stream().filter(move -> !result.contains(move) && !excluded.contains(move))
                .map(move -> pressure(position, move)).filter(candidate -> candidate.pawns() > 0 && candidate.minors() > 0)
                .sorted(Comparator.comparingInt(Pressure::pawns).reversed()
                        .thenComparing(Comparator.comparingInt(Pressure::minors).reversed()).thenComparing(candidate -> candidate.move().uci()))
                .findFirst().ifPresent(candidate -> result.add(candidate.move()));
        return result;
    }

    record Pressure(Move move, int pawns, int minors) { }

    static Pressure pressure(ChessPosition position, Move move) {
        var mover = position.pieceAt(move.from());
        if (mover.kind() != Kind.QUEEN || MatchMaterial.victim(position, move) != null) return new Pressure(move, 0, 0);
        var after = position.playGenerated(move);
        if (after.legalCapturesAndPromotions().stream().anyMatch(reply -> reply.to() == move.to())) return new Pressure(move, 0, 0);
        int pawns = 0, minors = 0;
        for (int square = 0; square < 64; square++) {
            var target = after.pieceAt(square);
            if (target == null || target.side() == mover.side() || !after.attacksFrom(move.to(), square)
                    || position.attacksFrom(move.from(), square)) continue;
            if (target.kind() == Kind.KNIGHT || target.kind() == Kind.BISHOP) minors++;
            if (target.kind() == Kind.PAWN && !defended(after, square)) pawns++;
        }
        return new Pressure(move, pawns, minors);
    }

    private static boolean defended(ChessPosition position, int target) {
        for (int square = 0; square < 64; square++) {
            var defender = position.pieceAt(square);
            if (square != target && defender != null && defender.side() == position.pieceAt(target).side()
                    && position.attacksFrom(square, target)) return true;
        }
        return false;
    }
}
