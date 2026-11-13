package ro.atf.chess;

/** Horizontal projection shared by the evaluation curve and touch selection. */
public final class GameReviewGraphLayout {
    private GameReviewGraphLayout() { }

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
