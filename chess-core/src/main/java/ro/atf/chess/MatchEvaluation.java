package ro.atf.chess;

import java.text.NumberFormat;
import java.util.Locale;
import ro.atf.chess.ChessPosition.Side;

/** Normalizes a side-to-move search score to the same perspective across a match. */
public record MatchEvaluation(TacticalAnalysis.Ending ending, int whiteCentipawns, int whiteMateMoves) {
    public static MatchEvaluation from(Side turn, TacticalAnalysis.Variation variation) {
        int perspective = turn == Side.WHITE ? 1 : -1;
        return switch (variation.ending()) {
            case HORIZON -> new MatchEvaluation(variation.ending(), perspective * variation.score(), 0);
            case STALEMATE -> new MatchEvaluation(variation.ending(), 0, 0);
            case CHECKMATE -> {
                int plies = variation.signedMatePlies();
                int moves = (Math.abs(plies) + 1) / 2;
                yield new MatchEvaluation(variation.ending(), 0, perspective * Integer.signum(plies) * moves);
            }
        };
    }

    public String pawnLabel(Locale locale) {
        var number = NumberFormat.getNumberInstance(locale);
        number.setGroupingUsed(false);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        String sign = whiteCentipawns > 0 ? "+" : whiteCentipawns < 0 ? "−" : "";
        return sign + number.format(Math.abs(whiteCentipawns) / 100.0);
    }

    /** Share of the track belonging to White. Eight pawns fill either side of the scale. */
    public double whiteShare() {
        return switch (ending) {
            case CHECKMATE -> whiteMateMoves > 0 ? 1 : 0;
            case STALEMATE -> .5;
            case HORIZON -> Math.max(0, Math.min(1, .5 + whiteCentipawns / 1600.0));
        };
    }

    public static double terminalWhiteShare(ChessMatch.Result result) {
        return switch (result) {
            case WHITE_WINS -> 1;
            case BLACK_WINS -> 0;
            case IN_PROGRESS -> throw new IllegalArgumentException("The game is still in progress");
            default -> .5;
        };
    }
}
