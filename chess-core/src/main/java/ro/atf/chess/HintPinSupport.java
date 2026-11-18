package ro.atf.chess;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Variation;

/** A conservative preference among exactly tied hint roots, never an evaluation bonus. */
final class HintPinSupport {
    private HintPinSupport() { }

    static void sort(ChessPosition position, List<Variation> lines) {
        var support = new HashMap<Move, Boolean>();
        for (var line : lines) support.put(line.move(), line.signedMatePlies() == 0 && reinforces(position, line.move()));
        lines.sort(Comparator.comparingInt(Variation::score).reversed()
                .thenComparing(Comparator.comparing((Variation line) -> support.get(line.move())).reversed()));
    }

    static boolean reinforces(ChessPosition before, Move move) {
        var defender = before.pieceAt(move.from());
        if (defender == null || (defender.kind() != Kind.KNIGHT && defender.kind() != Kind.BISHOP)
                || MatchMaterial.victim(before, move) != null) return false;
        var after = before.playGenerated(move);
        if (pinnedTo(after, move.to(), Kind.KING)) return false;
        for (var reply : after.legalCapturesAndPromotions()) if (reply.to() == move.to()) return false;
        for (int square = 0; square < 64; square++) {
            var piece = before.pieceAt(square);
            if (square == move.from() || piece == null || piece.side() != defender.side()
                    || (piece.kind() != Kind.KNIGHT && piece.kind() != Kind.BISHOP)) continue;
            if (after.attacksFrom(move.to(), square) && !before.attacksFrom(move.from(), square)
                    && (pinnedTo(before, square, Kind.QUEEN) || pinnedTo(before, square, Kind.KING))) return true;
        }
        return false;
    }

    private static boolean pinnedTo(ChessPosition board, int square, Kind protectedKind) {
        var piece = board.pieceAt(square);
        for (int origin = 0; origin < 64; origin++) {
            var attacker = board.pieceAt(origin);
            if (attacker == null || attacker.side() == piece.side()
                    || (attacker.kind() != Kind.BISHOP && attacker.kind() != Kind.ROOK && attacker.kind() != Kind.QUEEN)
                    || !board.attacksFrom(origin, square)) continue;
            if (protectedKind != Kind.KING && pinnedTo(board, origin, Kind.KING)) continue;
            int dx = Integer.signum(square % 8 - origin % 8);
            int dy = Integer.signum(square / 8 - origin / 8);
            for (int x = square % 8 + dx, y = square / 8 + dy; x >= 0 && x < 8 && y >= 0 && y < 8; x += dx, y += dy) {
                var behind = board.pieceAt(y * 8 + x);
                if (behind == null) continue;
                if (behind.side() == piece.side() && behind.kind() == protectedKind) return true;
                break;
            }
        }
        return false;
    }
}
