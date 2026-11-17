package ro.atf.chess;

/** Logical sizing for an opening lesson. Adapters measure their own labels and system insets. */
public final class OpeningLessonLayout {
    private static final int SIDE_PANEL_BREAKPOINT = 704;
    private static final int SIDE_PANEL_BOARD_INSET = 48;

    private OpeningLessonLayout() { }

    public static boolean sideBySide(int width, int height) {
        return width >= SIDE_PANEL_BREAKPOINT && width > height;
    }

    public static int boardLimit(int panelWidth, boolean sideBySide) {
        return sideBySide ? Math.max(0, panelWidth - SIDE_PANEL_BOARD_INSET) : (int) (panelWidth * 4L / 5);
    }

    public static int panelHeight(int width, int leadingGutter, int labelsHeight, int maximumSide, int availableHeight) {
        int side = Math.min(Math.max(0, width - leadingGutter), maximumSide);
        return Math.min(availableHeight, side + labelsHeight);
    }
}
