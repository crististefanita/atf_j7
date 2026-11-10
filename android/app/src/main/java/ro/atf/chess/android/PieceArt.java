package ro.atf.chess.android;

import android.graphics.Path;
import ro.atf.chess.ChessPosition.Kind;

/** Original Staunton silhouettes on a 48-unit grid. */
final class PieceArt {
    private static final Path[] BODIES = new Path[Kind.values().length];
    private static final Path[] LINES = new Path[BODIES.length];

    static {
        piece(Kind.PAWN,
                "M 12 39 C 12 34 17 31 19 28 C 21 25 22 23 21 21 C 15 19 15 10 20 8 C 24 5 30 7 31 12 C 33 16 30 20 27 21 C 26 24 29 29 32 32 C 35 34 36 37 36 39",
                "M 18 22 C 21 23 27 23 30 22");
        piece(Kind.KNIGHT,
                "M 12 39 C 12 34 15 31 21 28 C 27 24 29 21 27 18 C 24 21 22 22 19 22 L 13 26 L 8 24 C 6 23 7 20 9 17 L 16 11 L 17 5 L 23 9 C 33 8 39 15 39 24 C 39 31 36 34 36 39",
                "M 16 16 Q 18 13 21 14 M 29 13 C 36 19 34 29 29 33 M 10 22 L 13 21");
        piece(Kind.BISHOP,
                "M 12 39 L 14 35 L 19 33 C 13 29 12 23 17 16 Q 20 12 23 9 C 20 7 22 3 25 3 C 29 3 30 7 27 9 Q 23 16 22 24 L 26 25 Q 27 18 31 13 C 37 20 38 28 30 33 L 34 35 L 36 39",
                "M 19 33 Q 24 35 30 33");
        piece(Kind.ROOK,
                "M 10 39 Q 11 34 16 32 Q 18 26 18 20 L 13 16 Q 11 15 11 12 L 11 6 L 17 6 L 17 11 L 21 11 L 21 6 L 27 6 L 27 11 L 31 11 L 31 6 L 37 6 L 37 12 Q 37 15 35 16 L 30 20 Q 30 27 32 32 Q 37 35 38 39",
                "M 15 17 Q 24 20 33 17 M 16 32 Q 24 34 32 32");
        piece(Kind.QUEEN,
                "M 10 39 Q 12 34 16 32 L 11 20 L 7 14 C 2 13 4 7 8 8 C 12 8 13 11 11 14 L 18 23 L 16 10 C 11 7 14 2 18 4 C 21 4 22 8 19 10 L 24 22 L 23 8 C 19 5 21 1 24 1 C 28 1 30 6 26 8 L 25 22 L 30 10 C 27 7 28 4 31 4 C 35 3 37 8 33 10 L 30 23 L 37 14 C 35 10 38 7 41 8 C 45 9 45 13 41 14 L 37 20 L 32 32 Q 37 35 38 39",
                "M 16 32 Q 24 30 32 32 M 13 35 Q 24 33 35 35");
        piece(Kind.KING,
                "M 9 39 C 10 35 13 33 15 31 C 9 27 6 21 10 17 C 13 13 19 15 22 20 L 22 12 L 17 12 L 17 8 L 22 8 L 22 3 L 26 3 L 26 8 L 31 8 L 31 12 L 26 12 L 26 20 C 29 15 35 13 38 17 C 42 21 39 27 33 31 C 35 33 38 35 39 39",
                "M 15 31 Q 24 27 33 31 M 24 20 L 24 29 M 12 35 Q 24 32 36 35");
    }

    private PieceArt() { }

    static Path shape(Kind kind) { return BODIES[kind.ordinal()]; }
    static Path details(Kind kind) { return LINES[kind.ordinal()]; }

    private static void piece(Kind kind, String body, String details) {
        String left = body.split(" ", 4)[1];
        BODIES[kind.ordinal()] = path(body + " Q 42 41 42 44 L 6 44 Q 6 41 " + left + " 39 Z");
        LINES[kind.ordinal()] = path(details + " M 10 39 Q 24 37 38 39");
    }

    private static Path path(String description) {
        Path path = new Path();
        String[] tokens = description.split(" ");
        for (int i = 0; i < tokens.length;) {
            switch (tokens[i++]) {
                case "M" -> { path.moveTo(number(tokens, i), number(tokens, i + 1)); i += 2; }
                case "L" -> { path.lineTo(number(tokens, i), number(tokens, i + 1)); i += 2; }
                case "Q" -> { path.quadTo(number(tokens, i), number(tokens, i + 1), number(tokens, i + 2), number(tokens, i + 3)); i += 4; }
                case "C" -> {
                    path.cubicTo(number(tokens, i), number(tokens, i + 1), number(tokens, i + 2), number(tokens, i + 3),
                            number(tokens, i + 4), number(tokens, i + 5));
                    i += 6;
                }
                case "Z" -> path.close();
                default -> throw new IllegalStateException("Unknown piece path command");
            }
        }
        return path;
    }

    private static float number(String[] tokens, int index) { return Float.parseFloat(tokens[index]); }
}
