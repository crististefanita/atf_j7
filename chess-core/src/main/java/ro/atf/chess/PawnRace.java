package ro.atf.chess;

import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

/** Conservative empty-file pawn-race geometry; it does not certify a game result. */
final class PawnRace {
    private PawnRace() { }

    static boolean kingBlocksPassedPawn(ChessPosition position, int square, Piece pawn) {
        int front = square + direction(pawn.side());
        if (front < 0 || front >= 64) return false;
        var blocker = position.pieceAt(front);
        return blocker != null && blocker.side() != pawn.side() && blocker.kind() == Kind.KING
                && passedPawn(position, square, pawn);
    }

    static boolean outsidePawnAdvance(ChessPosition position, Move move) {
        var pawn = position.pieceAt(move.from());
        if (pawn == null || pawn.kind() != Kind.PAWN || pawn.side() != position.turn()
                || MatchPositionEvaluation.phase(position) != 0 || position.pieceAt(move.to()) != null
                || move.to() - move.from() != direction(pawn.side())) return false;
        for (int square = 0; square < 64; square++) {
            var blocked = position.pieceAt(square);
            if (blocked != null && blocked.kind() == Kind.PAWN && blocked.side() != pawn.side()
                    && kingBlocksPassedPawn(position, square, blocked)
                    && uncatchableOutsidePasser(position, square, blocked, move.from())) return true;
        }
        return false;
    }

    static boolean uncatchableOutsidePasser(ChessPosition position, int blockedSquare, Piece blockedPawn) {
        return uncatchableOutsidePasser(position, blockedSquare, blockedPawn, -1);
    }

    private static boolean uncatchableOutsidePasser(ChessPosition position, int blockedSquare,
                                                   Piece blockedPawn, int requiredPawn) {
        int defendingKing = -1;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece != null && piece.side() == blockedPawn.side() && piece.kind() == Kind.KING) {
                defendingKing = square;
                break;
            }
        }
        for (int square = 0; square < 64; square++) {
            if (requiredPawn >= 0 && square != requiredPawn) continue;
            var pawn = position.pieceAt(square);
            if (pawn == null || pawn.kind() != Kind.PAWN || pawn.side() == blockedPawn.side()
                    || Math.abs(square % 8 - blockedSquare % 8) < 2
                    || !passedPawn(position, square, pawn) || !clearAdvance(position, square, pawn.side())) continue;
            int promotion = (pawn.side() == Side.WHITE ? 56 : 0) + square % 8;
            int pawnMoves = Math.abs(promotion / 8 - square / 8);
            int kingMoves = pawnMoves + (position.turn() == blockedPawn.side() ? 1 : 0);
            int distance = Math.max(Math.abs(defendingKing % 8 - promotion % 8),
                    Math.abs(defendingKing / 8 - promotion / 8));
            // Include capture on the promotion square after the defender's initial tempo.
            // Ignoring an initial double push makes this bound conservative.
            if (distance > kingMoves) return true;
        }
        return false;
    }

    private static boolean passedPawn(ChessPosition position, int square, Piece pawn) {
        int step = direction(pawn.side());
        for (int ahead = square + step; ahead >= 0 && ahead < 64; ahead += step) {
            for (int file = Math.max(0, square % 8 - 1); file <= Math.min(7, square % 8 + 1); file++) {
                var other = position.pieceAt(ahead / 8 * 8 + file);
                if (other != null && other.side() != pawn.side() && other.kind() == Kind.PAWN) return false;
            }
        }
        return true;
    }

    private static boolean clearAdvance(ChessPosition position, int square, Side side) {
        int step = direction(side);
        for (int ahead = square + step; ahead >= 0 && ahead < 64; ahead += step)
            if (position.pieceAt(ahead) != null) return false;
        return true;
    }

    private static int direction(Side side) { return side == Side.WHITE ? 8 : -8; }
}
