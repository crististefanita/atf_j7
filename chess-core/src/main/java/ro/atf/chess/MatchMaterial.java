package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

/** Captures and material balance at the same point in the move history. */
public record MatchMaterial(Seat white, Seat black) {
    public record Captured(Kind kind, int count) { }

    public record Seat(List<Captured> captured, int advantage) {
        public Seat { captured = List.copyOf(captured); }
    }

    public Seat forSide(Side side) { return side == Side.WHITE ? white : black; }

    static MatchMaterial at(ChessMatch match, int ply) {
        var board = match.positionAt(ply);
        var counts = new int[2][Kind.values().length];
        for (int index = 1; index <= ply; index++) {
            var before = match.positionAt(index - 1);
            Piece victim = victim(before, match.moveAt(index));
            if (victim != null) counts[before.turn().ordinal()][victim.kind().ordinal()]++;
        }
        int balance = balance(board, Side.WHITE);
        return new MatchMaterial(seat(counts[Side.WHITE.ordinal()], balance), seat(counts[Side.BLACK.ordinal()], -balance));
    }

    /** Identifies the captured piece, including en passant, for a legal move. */
    static Piece victim(ChessPosition before, Move move) {
        Piece target = before.pieceAt(move.to());
        if (target == null && before.pieceAt(move.from()).kind() == Kind.PAWN && move.from() % 8 != move.to() % 8) {
            return before.pieceAt(BoardSquare.of(move.to() % 8, move.from() / 8));
        }
        return target;
    }

    static int balance(ChessPosition board, Side side) {
        int balance = 0;
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece != null) balance += (piece.side() == side ? 1 : -1) * points(piece.kind());
        }
        return balance;
    }

    private static Seat seat(int[] counts, int balance) {
        var captured = new ArrayList<Captured>();
        for (Kind kind : Kind.values()) {
            if (counts[kind.ordinal()] > 0) captured.add(new Captured(kind, counts[kind.ordinal()]));
        }
        return new Seat(captured, Math.max(0, balance));
    }

    private static int points(Kind kind) {
        return switch (kind) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> 0;
        };
    }
}
