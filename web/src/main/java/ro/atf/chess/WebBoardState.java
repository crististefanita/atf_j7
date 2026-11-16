package ro.atf.chess;

import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Values shared by the board views exposed across the worker boundary. */
final class WebBoardState {
    private WebBoardState() { }

    static String placement(ChessPosition position) {
        var result = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            if (rank < 7) result.append('/');
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                var piece = position.pieceAt(BoardSquare.of(file, rank));
                if (piece == null) {
                    empty++;
                    continue;
                }
                if (empty > 0) result.append(empty);
                empty = 0;
                char symbol = "pnbrqk".charAt(piece.kind().ordinal());
                result.append(piece.side() == Side.WHITE ? Character.toUpperCase(symbol) : symbol);
            }
            if (empty > 0) result.append(empty);
        }
        return result.toString();
    }

    static String move(Move move) { return move == null ? "null" : quote(move.uci()); }

    static String quote(String token) {
        if (token == null) return "null";
        var json = new StringBuilder().append('"');
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (value == '"' || value == '\\') json.append('\\');
            if (value < ' ') {
                json.append("\\u00").append(Character.forDigit(value >> 4, 16)).append(Character.forDigit(value & 15, 16));
            } else {
                json.append(value);
            }
        }
        return json.append('"').toString();
    }
}
