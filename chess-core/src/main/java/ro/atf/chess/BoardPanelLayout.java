package ro.atf.chess;

/** Fits the board and its adjacent labels into the space left by the screen controls. */
public record BoardPanelLayout(int left, int top, int side) {
    public static BoardPanelLayout fit(int width, int height, int topLabel, int bottomLabel) {
        int available = Math.max(0, height - topLabel - bottomLabel);
        int side = Math.min(width, available);
        return new BoardPanelLayout((width - side) / 2, (available - side) / 2, side);
    }
}
