package ro.atf.chess;

/** A board square is numbered from a1 (0) to h8 (63), independent of the view. */
public final class BoardSquare {
    private BoardSquare() { }

    public static int of(int file, int rank) {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Square outside board");
        }
        return rank * 8 + file;
    }

    public static int parse(String square) {
        if (square.length() != 2) throw new IllegalArgumentException("Expected square such as e4");
        return of(square.charAt(0) - 'a', square.charAt(1) - '1');
    }

    public static String name(int square) {
        if (square < 0 || square >= 64) throw new IllegalArgumentException("Square outside board");
        return "" + (char) ('a' + square % 8) + (char) ('1' + square / 8);
    }

    public static int atDisplay(int column, int row, boolean blackBelow) {
        return blackBelow ? of(7 - column, row) : of(column, 7 - row);
    }

    public static boolean isLight(int square) {
        return (square % 8 + square / 8) % 2 == 1;
    }
}
