package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A reveal cursor for the best continuations of one analyzed position. */
public final class MatchHint {
    private final List<TacticalAnalysis.Variation> lines;
    private final ChessPosition position;
    private final TacticalAnalysis.SearchReport comparison;
    private int index = -1;

    public MatchHint(List<TacticalAnalysis.Variation> lines) {
        this(null, lines, null);
    }

    private MatchHint(ChessPosition position, List<TacticalAnalysis.Variation> lines, TacticalAnalysis.SearchReport comparison) {
        this.position = position;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.comparison = comparison;
    }

    public static MatchHint analyze(ChessPosition position) {
        var search = TacticalAnalysis.verifiedHints(position);
        return new MatchHint(position, search.lines(), search.comparison());
    }

    List<TacticalAnalysis.Variation> lines() { return lines; }
    public int count() { return lines.size(); }
    public int number() { return index + 1; }
    public TacticalAnalysis.Variation first() { return lines.get(0); }
    public TacticalAnalysis.Variation revealed() { return index < 0 ? null : lines.get(index); }

    public Optional<HintPreview> preview(ChessPosition position) {
        var line = revealed();
        return line == null || !matches(position) ? Optional.empty() : HintPreview.open(position, line);
    }

    public Optional<HintComparison> compare(ChessPosition position) {
        var current = revealed();
        if (current == null || comparison == null || !matches(position)) return Optional.empty();
        var other = index == 0 ? comparison.result().ranked().get(1) : current;
        return Optional.of(HintComparison.of(position, first(), other, comparison.depth()));
    }

    public ChessPosition.Move moveForTap(ChessPosition position, int selected, int square) {
        var line = revealed();
        if (selected >= 0 || line == null || line.move().to() != square || !matches(position)) return null;
        var move = line.move();
        return position.legalMovesFrom(move.from()).contains(move) ? move : null;
    }

    public void hide() { index = -1; }

    public void revealNext() {
        if (!lines.isEmpty()) index = (index + 1) % lines.size();
    }

    private boolean matches(ChessPosition board) { return position == null || position == board; }
}
