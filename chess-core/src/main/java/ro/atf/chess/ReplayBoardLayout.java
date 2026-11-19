package ro.atf.chess;

/** Keeps a measured source square through replay; only the explanation absorbs text growth. */
public record ReplayBoardLayout(int side, int detailsHeight) {
    public static ReplayBoardLayout fit(int width, int height, int sourceSide, int controlsHeight, int detailsMinimum) {
        int available = Math.max(0, height - Math.max(0, controlsHeight));
        int reserve = sourceSide > 0 ? 0 : Math.min(Math.max(0, detailsMinimum), available / 3);
        int ceiling = sourceSide > 0 ? sourceSide : Math.max(0, width);
        int side = Math.min(Math.max(0, width), Math.min(ceiling, available - reserve));
        return new ReplayBoardLayout(side, available - side);
    }
}
