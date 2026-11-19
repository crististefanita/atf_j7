package ro.atf.chess;

/** Platform-neutral chrome policy, in logical dp/CSS px at the default text scale. */
public final class UiChrome {
    public static final int TARGET = 48;
    public static final int TITLE = 19;
    public static final int GAP = 6;
    public static final int INSET = 8;
    public static final int NAVIGATION_MAX = 384;
    public static final int COMPACT_WIDTH = 600;

    public enum Back { HOME, PUZZLES, OPENINGS, SOURCE }

    public enum Page {
        MATCH(Back.HOME), SAVED(Back.HOME), FINISHED(Back.HOME), SETUP(Back.HOME),
        PUZZLES(Back.HOME), PUZZLE(Back.PUZZLES), OPENINGS(Back.HOME), OPENING(Back.OPENINGS),
        ANALYSIS(Back.SOURCE), CALCULATED(Back.SOURCE), MATING(Back.SOURCE);

        private final Back back;
        Page(Back back) { this.back = back; }
        public Back back() { return back; }
    }

    public record Rail(int left, int width) { }
    public record Menu(int left, int top, int width, int height) { }

    private UiChrome() { }

    public static Rail navigation(int available) {
        return navigation(available, 1);
    }

    public static Rail navigation(int available, double density) {
        if (available < 0) throw new IllegalArgumentException("Negative navigation width");
        if (!Double.isFinite(density) || density <= 0) throw new IllegalArgumentException("Invalid navigation density");
        int width = Math.min(available, (int) Math.round(NAVIGATION_MAX * density));
        return new Rail((available - width) / 2, width);
    }

    public static Menu options(int width, int height, int anchorLeft, int anchorTop, int anchorRight, int anchorBottom,
                               int panelWidth, int panelHeight, int gap, int margin) {
        if (width < 0 || height < 0 || panelWidth < 0 || panelHeight < 0 || gap < 0 || margin < 0) throw new IllegalArgumentException("Invalid menu bounds");
        int availableWidth = Math.max(0, width - 2 * margin);
        int actualWidth = Math.min(panelWidth, availableWidth);
        int below = Math.max(0, height - margin - anchorBottom - gap);
        int above = Math.max(0, anchorTop - margin - gap);
        boolean before = panelHeight > below && above > below;
        int actualHeight = Math.min(panelHeight, before ? above : below);
        int aligned = anchorLeft + anchorRight > width ? anchorRight - actualWidth : anchorLeft;
        return new Menu(Math.max(margin, Math.min(aligned, width - margin - actualWidth)),
                before ? anchorTop - gap - actualHeight : anchorBottom + gap, actualWidth, actualHeight);
    }
}
