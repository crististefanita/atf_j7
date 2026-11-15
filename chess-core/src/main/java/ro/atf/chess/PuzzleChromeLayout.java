package ro.atf.chess;

/** Reserves the puzzle controls before measuring the square and its scrollable details. */
public record PuzzleChromeLayout(int headerHeight, int scoreHeight, int scoreLines, int navigationHeight) {
    public static final int TITLE_LINES = 2;

    /**
     * The score width is measured for the widest localized score row, including any badge
     * and padding, so awarding points or starting a streak cannot change the board position.
     */
    public static PuzzleChromeLayout fit(int scoreAvailableWidth, int touchTarget, int titleLineHeight,
                                         int scoreLineHeight, int reservedScoreWidth, int guidanceHeight) {
        int scoreLines = reservedScoreWidth <= scoreAvailableWidth ? 1 : 2;
        return new PuzzleChromeLayout(Math.max(touchTarget, TITLE_LINES * titleLineHeight),
                scoreLines * scoreLineHeight, scoreLines, touchTarget + guidanceHeight);
    }

    public BoardPanelLayout.WithDetails boardAndDetails(int width, int screenHeight, int systemBarsHeight,
                                                           int playerLabelsHeight, int detailsMinimum) {
        int available = Math.max(0, screenHeight - systemBarsHeight - headerHeight - scoreHeight - navigationHeight);
        return BoardPanelLayout.withDetails(width, available, playerLabelsHeight, detailsMinimum);
    }

    public static int detailsMinimum(int statusLineHeight, int statusVerticalPadding, int menuHeight, int footerHeight) {
        return Math.max(statusLineHeight + statusVerticalPadding, menuHeight - footerHeight);
    }
}
