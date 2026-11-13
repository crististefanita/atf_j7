package ro.atf.chess;

/** Allocates the largest square that leaves room for controls and the report. */
public record GameReviewLayout(boolean sideBySide, int boardSide, int panelWidth) {
    public static GameReviewLayout fit(int width, int height, int controlsHeight, int controlsWidth,
                                       int reportPreview, int reportMinWidth, int gutter) {
        int boardHeight = Math.max(0, height - controlsHeight);
        int preview = Math.min(reportPreview, boardHeight / 3);
        int stackedSide = Math.min(width, boardHeight - preview);

        int panelLimit = width - reportMinWidth - gutter;
        int splitSide = panelLimit >= controlsWidth ? Math.min(panelLimit, boardHeight) : 0;
        if (width > height && splitSide > stackedSide) {
            return new GameReviewLayout(true, splitSide, Math.max(controlsWidth, splitSide));
        }
        return new GameReviewLayout(false, stackedSide, width);
    }
}
