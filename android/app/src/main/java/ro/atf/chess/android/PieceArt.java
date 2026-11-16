package ro.atf.chess.android;

import android.graphics.Path;
import ro.atf.chess.ChessPieceArtwork;
import ro.atf.chess.ChessPosition.Kind;

/** Original Staunton silhouettes on a 48-unit grid. */
final class PieceArt {
    private static final Path[] BODIES = new Path[Kind.values().length];
    private static final Path[] LINES = new Path[BODIES.length];

    static {
        for (Kind kind : Kind.values()) {
            var artwork = ChessPieceArtwork.forKind(kind);
            BODIES[kind.ordinal()] = path(artwork.body());
            LINES[kind.ordinal()] = path(artwork.details());
        }
    }

    private PieceArt() { }

    static Path shape(Kind kind) { return BODIES[kind.ordinal()]; }
    static Path details(Kind kind) { return LINES[kind.ordinal()]; }

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
