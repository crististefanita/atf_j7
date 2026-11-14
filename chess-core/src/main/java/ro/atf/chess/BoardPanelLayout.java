package ro.atf.chess;

/** Fits the board and its adjacent labels into the space left by the screen controls. */
public record BoardPanelLayout(int left, int top, int side) {
    public record WithDetails(int boardHeight, int detailsHeight) { }

    public static BoardPanelLayout fit(int width, int height, int topLabel, int bottomLabel) {
        return fit(width, height, topLabel, bottomLabel, 0);
    }

    public static BoardPanelLayout fit(int width, int height, int topLabel, int bottomLabel, int leadingGutter) {
        int available = Math.max(0, height - topLabel - bottomLabel);
        int gutter = Math.min(width, leadingGutter);
        int side = Math.min(width - gutter, available);
        return new BoardPanelLayout((width - gutter - side) / 2 + gutter, (available - side) / 2, side);
    }

    /** Extra height goes to the scrolling details once the board reaches the full width. */
    public static WithDetails withDetails(int width, int height, int labelsHeight, int detailsMinimum) {
        int reserve = Math.min(detailsMinimum, Math.max(0, height - labelsHeight) / 3);
        int boardHeight = Math.min(width + labelsHeight, height - reserve);
        return new WithDetails(boardHeight, height - boardHeight);
    }
}
