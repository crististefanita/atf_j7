package ro.atf.chess;

import java.util.Objects;
import java.util.function.Function;

/** A native offline game cannot advance without the cached analysis of its current full checkpoint. */
final class HardAuditedGame {
    private final Function<ChessMatch, HardMoveAudit.Entry> analyzer;
    private ChessMatch match;
    private HardMoveAudit.Entry pending;

    HardAuditedGame(ChessMatch match) { this(match, HardMoveAudit::capture); }

    HardAuditedGame(ChessMatch match, Function<ChessMatch, HardMoveAudit.Entry> analyzer) {
        this.match = Objects.requireNonNull(match);
        this.analyzer = Objects.requireNonNull(analyzer);
    }

    ChessMatch match() { return match; }

    HardMoveAudit.Entry analyze() {
        if (pending == null) {
            var result = analyzer.apply(match);
            if (!result.checkpoint().equals(match.checkpoint())) throw new IllegalArgumentException("Foreign analysis checkpoint");
            HardMoveAudit.validate(result);
            pending = result;
        }
        return pending;
    }

    void play(ChessPosition.Move move) {
        if (pending == null || !pending.checkpoint().equals(match.checkpoint()))
            throw new IllegalStateException("Analyze the current checkpoint before every played move");
        var next = match.play(move);
        match = next;
        pending = null;
    }
}
