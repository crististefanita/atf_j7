package ro.atf.chess;

import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;

/** A cursor over an immutable line. Playing from the cursor replaces only the analysis suffix. */
public record AnalysisLine(ChessMatch line, int ply) {
    public AnalysisLine {
        if (ply < 0 || ply > line.ply()) throw new IllegalArgumentException("Invalid analysis ply: " + ply);
        line = line.forAnalysis();
    }

    public ChessPosition position() { return line.positionAt(ply); }
    public Move lastMove() { return line.moveAt(ply); }
    public Result result() { return ply == line.ply() ? line.result() : Result.IN_PROGRESS; }

    public AnalysisLine seek(int selectedPly) {
        return selectedPly == ply ? this : new AnalysisLine(line, selectedPly);
    }

    public AnalysisLine play(Move move) {
        if (ply < line.ply() && move.equals(line.moveAt(ply + 1))) return seek(ply + 1);
        return new AnalysisLine(line.continueFrom(ply).play(move), ply + 1);
    }
}
