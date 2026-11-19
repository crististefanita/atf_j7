package ro.atf.chess;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Keeps the actual game decision, public hint and displayed estimate distinct at every played checkpoint. */
final class HardHintAuditedGame {
    record Entry(String checkpoint, String fen, HardMoveAudit.Entry hard,
                 HintAuditSession.Search hint, MatchEvaluation evaluation) { }

    private final HardAuditedGame game;
    private final Function<ChessPosition, HintAuditSession.Search> hints;
    private Entry pending;

    HardHintAuditedGame(ChessMatch match) {
        this(match, HardMoveAudit::capture, board -> {
            var search = TacticalAnalysis.verifiedHints(board, 200_000);
            return new HintAuditSession.Search(search.lines(), search.comparison() == null ? List.of() : search.comparison().result().ranked(),
                    search.extraNodes(), search.selectedDepth(), search.seedDepth(), search.comparison() == null ? 0 : search.comparison().depth(),search.proof());
        });
    }

    HardHintAuditedGame(ChessMatch match, Function<ChessMatch, HardMoveAudit.Entry> nativeSearch,
                        Function<ChessPosition, HintAuditSession.Search> hintSearch) {
        game = new HardAuditedGame(match, nativeSearch);
        hints = Objects.requireNonNull(hintSearch);
    }

    ChessMatch match() { return game.match(); }

    Entry analyze() {
        if (pending == null) {
            var match = game.match();
            var hard = game.analyze();
            var hint = Objects.requireNonNull(hints.apply(match.position()));
            validateHint(match.position(), hint);
            var estimate = hint.proof()==null?MatchEvaluation.forPosition(match.position(),hint.lines().getFirst()):MatchEvaluation.from(match.position().turn(),hint.lines().getFirst());
            pending = new Entry(match.checkpoint(), HintAuditSession.fen(match), hard, hint, estimate);
        }
        return pending;
    }

    void play(ChessPosition.Move move) {
        if (pending == null) throw new IllegalStateException("Native analysis and public hints are required before every played move");
        game.play(move);
        pending = null;
    }

    static void validate(Entry entry) {
        var match = ChessMatch.restore(entry.checkpoint());
        require(entry.fen().equals(HintAuditSession.fen(match)) && entry.checkpoint().equals(entry.hard().checkpoint()), "Foreign native/hint checkpoint");
        require(entry.evaluation() != null, "Missing displayed estimate");
        HardMoveAudit.validate(entry.hard());
        validateHint(match.position(), entry.hint());
    }

    static void validateHint(ChessPosition board, HintAuditSession.Search hint) {
        require(hint.extraNodes() >= 0 && hint.extraNodes() <= 200_000, "Hint exceeded its independent total allowance");
        HintAuditSession.validateProof(board,hint.proof(),hint.extraNodes(),hint.selectedDepth());
        require(hint.selectedDepth() > 0 && hint.selectedDepth() <= MatchAiPolicy.MAX_PLIES
                && hint.seedDepth() > 0 && hint.seedDepth() <= MatchAiPolicy.MAX_PLIES
                && hint.comparisonDepth() >= 0 && hint.comparisonDepth() <= MatchAiPolicy.MAX_PLIES, "Missing hint horizons");
        require(!hint.lines().isEmpty() && hint.lines().size() <= 3, "Missing or invented public suggestions");
        require((hint.comparisonDepth() == 0) == hint.compared().isEmpty(), "Foreign comparison phase");
        validateLines(board, hint.lines(), hint.selectedDepth());
        validateLines(board, hint.compared(), hint.comparisonDepth());
    }

    private static void validateLines(ChessPosition board, List<TacticalAnalysis.Variation> lines, int depth) {
        var roots = new HashSet<ChessPosition.Move>();
        for (var line : lines) {
            require(!line.line().isEmpty() && roots.add(line.move()), "Missing or repeated hint root");
            var end = board;
            for (var move : line.line()) end = end.play(move);
            require(line.materialChange() == HintAuditSession.materialChangeCp(board, end), "Substituted hint material ledger");
            require(line.ending() != TacticalAnalysis.Ending.CHECKMATE || end.isCheckmate(), "Unproved hint mate");
            require(line.ending() != TacticalAnalysis.Ending.STALEMATE || !end.hasLegalMove() && !end.isCheck(), "Unproved hint stalemate");
            require(line.ending() != TacticalAnalysis.Ending.HORIZON || line.line().size() >= depth, "Truncated public hint");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
