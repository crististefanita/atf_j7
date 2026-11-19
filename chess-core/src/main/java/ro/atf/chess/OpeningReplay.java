package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.MatchHint.Theory;

/** Read-only playback rooted at the exact board from which a theory path was offered. */
public final class OpeningReplay {
    private final Theory path;
    private final List<ChessPosition> positions;
    private int ply;

    public OpeningReplay(ChessPosition root, Theory path) {
        this.path = path;
        var boards = new ArrayList<ChessPosition>();
        var position = root;
        boards.add(position);
        for (var step : path.steps()) {
            position = position.play(step.move());
            boards.add(position);
        }
        positions = List.copyOf(boards);
    }

    public ChessPosition position() { return positions.get(ply); }
    public int ply() { return ply; }
    public int length() { return path.steps().size(); }
    public Move lastMove() { return ply == 0 ? null : path.steps().get(ply - 1).move(); }
    public OpeningCatalog.Step explainedStep() { return path.steps().get(Math.max(0, ply - 1)); }
    public boolean hasNext() { return ply < length(); }
    public boolean hasPrevious() { return ply > 0; }
    public void next() { if (hasNext()) ply++; }
    public void previous() { if (hasPrevious()) ply--; }
    public void seek(int value) {
        if (value < 0 || value > length()) throw new IllegalArgumentException("Invalid theory ply: " + value);
        ply = value;
    }
}
