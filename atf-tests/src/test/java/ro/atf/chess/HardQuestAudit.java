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
        validate(position,analysis,java.util.List.of(),0);
    }

    static void validate(ChessMatch game,ChessMatch.BotAnalysis analysis) {
        validate(game.position(),analysis,game.searchHistory(),game.searchQuietPlies());
    }

    private static void validate(ChessPosition position,ChessMatch.BotAnalysis analysis,java.util.List<ChessPosition> history,int quietPlies) {
        var decision = analysis.decision();
        var search = analysis.search();
        require(decision.extraNodes() >= 0 && decision.extraNodes() <= maximumNodes(position), "Native Hard exceeds its effective quest allowance");
        require(decision.extraNodes() == search.extraNodes() && decision.depth() == search.depth(), "Decision and search differ");
        require(position.legalMoves().contains(decision.move()) && !search.result().ranked().isEmpty(), "Missing or illegal native choice");
        require(search.result().best().stream().anyMatch(line -> line.move().equals(decision.move())), "The choice is not in the actual native ranking");
        for (var line : search.result().ranked()) {
            require(!line.line().isEmpty(), "Missing native PV");
            require(search.pawnProof()!=null||line.line().size()<=search.depth(),"A longer native PV needs its separate proof certificate");
            var end = position;
            for (var move : line.line()) end = end.play(move);
            require(line.materialChange() == HintAuditSession.materialChangeCp(position, end), "Substituted native material ledger");
            require(line.ending() != TacticalAnalysis.Ending.CHECKMATE || end.isCheckmate(), "Unproved native mate");
            require(line.ending() != TacticalAnalysis.Ending.STALEMATE || !end.hasLegalMove() && !end.isCheck(), "Unproved native stalemate");
        }
        var certificate=search.pawnProof();
        if(certificate!=null) {
            var policy=PawnPushPolicy.CURRENT;
            require(policy.enabled()&&PawnPushProof.eligible(position,policy.maxPawns())&&certificate.maxPlies()==policy.maxPlies(),"Foreign pawn proof policy or scope");
            require(certificate.historySha256().equals(PawnPushProof.historyHash(history))&&certificate.quietPlies()==quietPlies,"Foreign pawn proof history or draw clock");
            require(certificate.extraNodes()>0&&certificate.extraNodes()<=search.extraNodes()&&certificate.cacheLimit()>=0&&certificate.cacheLimit()<=MatchAiPolicy.CURRENT.hardTranspositions.entryLimit(maximumNodes(position)),"Unaccounted pawn proof allowance");
            int[] nodes={0};var verifier=new PawnPushProof(position.turn(),()->{if(++nodes[0]>certificate.extraNodes())throw new IllegalArgumentException("Unfinished pawn proof");},certificate.cacheLimit(),history);
            var proof=verifier.prove(position,policy.maxPlies(),quietPlies);
            require(proof!=null&&nodes[0]==certificate.extraNodes()&&verifier.defenderReplies()==certificate.defenderEdgesVisited(),"Invented pawn proof work or opposing coverage");
            require(proof.strategySha256().equals(certificate.strategySha256())&&proof.line().size()==certificate.linePlies(),"Changed pawn proof strategy");
            require(search.result().ranked().size()==1&&proof.line().equals(search.result().ranked().getFirst().line()),"Native PV differs from its complete winning proof");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
