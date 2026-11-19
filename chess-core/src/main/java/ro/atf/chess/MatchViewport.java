package ro.atf.chess;

/** Selects compact match chrome before allocating the remaining area to the square board. */
public record MatchViewport(int widthDp, int heightDp) {
    public boolean compact() {
        return widthDp > 0 && heightDp > 0 && (heightDp <= 480 || widthDp <= 480 && heightDp >= widthDp);
    }
}
