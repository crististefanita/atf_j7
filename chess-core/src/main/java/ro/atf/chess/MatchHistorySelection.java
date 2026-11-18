package ro.atf.chess;

import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** The one move that produced the displayed game position; independent of board orientation. */
public record MatchHistorySelection(int ply, int total, int number, Side side, Move move, Boundary boundary) {
    public enum Boundary { START, FIRST, MIDDLE, LAST, ONLY }

    public static MatchHistorySelection at(ChessMatch match, int ply) {
        if (ply < 0 || ply > match.ply()) throw new IllegalArgumentException("History position is outside the match");
        var boundary = ply == 0 ? Boundary.START : match.ply() == 1 ? Boundary.ONLY
                : ply == 1 ? Boundary.FIRST : ply == match.ply() ? Boundary.LAST : Boundary.MIDDLE;
        var side = ply == 0 ? null : match.positionAt(ply - 1).turn();
        int number = ply == 0 ? 0 : (ply + (match.positionAt(0).turn() == Side.WHITE ? 1 : 2)) / 2;
        return new MatchHistorySelection(ply, match.ply(), number, side, match.moveAt(ply), boundary);
    }

    public String compact() {
        if (move == null) return "";
        String uci = move.uci();
        return number + (side == Side.WHITE ? ". " : "… ") + uci.substring(0, 2) + "–" + uci.substring(2, 4)
                + (move.promotion() == null ? "" : "=" + Character.toUpperCase(uci.charAt(4)));
    }
}
