package ro.atf.chess;

/** The exact source match, independent study cursor and board orientation at entry. */
public final class AnalysisEntry {
    private final ChessMatch source;
    private final AnalysisLine line;
    private final boolean blackBelow;

    public AnalysisEntry(ChessMatch source, int ply, boolean blackBelow) {
        this.source = source;
        this.line = new AnalysisLine(source, ply);
        this.blackBelow = blackBelow;
    }

    public static AnalysisEntry restore(String checkpoint, int ply, boolean blackBelow) {
        return new AnalysisEntry(ChessMatch.restore(checkpoint), ply, blackBelow);
    }

    public ChessMatch source() { return source; }
    public AnalysisLine line() { return line; }
    public boolean blackBelow() { return blackBelow; }
}
