package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A reveal cursor for the best continuations of one analyzed position. */
public final class MatchHint {
    private final List<TacticalAnalysis.Variation> lines;
    private int index = -1;

    public MatchHint(List<TacticalAnalysis.Variation> lines) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public int count() { return lines.size(); }
    public int number() { return index + 1; }
    public TacticalAnalysis.Variation first() { return lines.get(0); }
    public TacticalAnalysis.Variation revealed() { return index < 0 ? null : lines.get(index); }

    public ChessPosition.Move moveForTap(ChessPosition position, int selected, int square) {
        var line = revealed();
        if (selected >= 0 || line == null || line.move().to() != square) return null;
        var move = line.move();
        return position.legalMovesFrom(move.from()).contains(move) ? move : null;
    }

    public void hide() { index = -1; }

    public void revealNext() {
        if (!lines.isEmpty()) index = (index + 1) % lines.size();
    }
}
