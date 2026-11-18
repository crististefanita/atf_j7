package ro.atf.chess;

/** Audits a position-only Hard benchmark without supplying the puzzle objective to the opponent. */
final class HardQuestAudit {
    private HardQuestAudit() { }

    static int maximumNodes(ChessPosition position) {
        return OpeningRepertoire.CURRENT.playReply(position, MatchSetup.Level.HARD) == null
                ? HardAdaptivePolicy.CURRENT.search().extraNodes()
                : MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes();
    }

    static void validate(ChessPosition position, ChessMatch.BotAnalysis analysis) {
        var decision = analysis.decision();
        var search = analysis.search();
        require(decision.extraNodes() >= 0 && decision.extraNodes() <= maximumNodes(position), "Native Hard exceeds its effective quest allowance");
        require(decision.extraNodes() == search.extraNodes() && decision.depth() == search.depth(), "Decision and search differ");
        require(position.legalMoves().contains(decision.move()) && !search.result().ranked().isEmpty(), "Missing or illegal native choice");
        require(search.result().best().stream().anyMatch(line -> line.move().equals(decision.move())), "The choice is not in the actual native ranking");
        for (var line : search.result().ranked()) {
            require(!line.line().isEmpty(), "Missing native PV");
            var end = position;
            for (var move : line.line()) end = end.play(move);
            require(line.materialChange() == HintAuditSession.materialChangeCp(position, end), "Substituted native material ledger");
            require(line.ending() != TacticalAnalysis.Ending.CHECKMATE || end.isCheckmate(), "Unproved native mate");
            require(line.ending() != TacticalAnalysis.Ending.STALEMATE || !end.hasLegalMove() && !end.isCheck(), "Unproved native stalemate");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
