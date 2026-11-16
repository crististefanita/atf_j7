package ro.atf.chess;

import java.text.NumberFormat;
import java.util.Locale;

/** Evaluation points shared by the graph, its touch targets and their descriptions. */
public final class GameReviewGraphLayout {
    private GameReviewGraphLayout() { }

    public record Point(int ply, int lastPly, int whitePoints) {
        public String whitePercent(Locale locale) {
            var percent = NumberFormat.getNumberInstance(locale);
            percent.setMaximumFractionDigits(1);
            return percent.format(whitePoints / 10.0);
        }
    }

    public static Point pointForPly(GameReview review, int ply) {
        int lastPly = review.turns().size();
        int selected = clampPly(ply, lastPly);
        int whitePoints = selected == 0 ? review.initialWhitePoints() : review.turns().get(selected - 1).whitePoints();
        return new Point(selected, lastPly, whitePoints);
    }

    public static double xForPly(int ply, int lastPly, double width, double inset) {
        double span = width - 2 * inset;
        if (lastPly <= 0 || span <= 0) return width / 2;
        return inset + span * clampPly(ply, lastPly) / lastPly;
    }

    public static int plyAtX(double x, int lastPly, double width, double inset) {
        double span = width - 2 * inset;
        if (lastPly <= 0 || span <= 0) return 0;
        double offset = Math.max(0, Math.min(span, x - inset));
        return (int) Math.round(lastPly * offset / span);
    }

    private static int clampPly(int ply, int lastPly) {
        return Math.max(0, Math.min(lastPly, ply));
    }
}
