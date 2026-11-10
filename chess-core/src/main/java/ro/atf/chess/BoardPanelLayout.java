package ro.atf.chess;

/** Fits the board and its adjacent labels into the space left by the screen controls. */
public record BoardPanelLayout(int left, int top, int side) {
    public static BoardPanelLayout fit(int width, int height, int topLabel, int bottomLabel) {
        return fit(width, height, topLabel, bottomLabel, 0);
    }

    public static BoardPanelLayout fit(int width, int height, int topLabel, int bottomLabel, int leadingGutter) {
        int available = Math.max(0, height - topLabel - bottomLabel);
        int gutter = Math.min(width, leadingGutter);
        int side = Math.min(width - gutter, available);
        return new BoardPanelLayout((width - gutter - side) / 2 + gutter, (available - side) / 2, side);
    }
}
