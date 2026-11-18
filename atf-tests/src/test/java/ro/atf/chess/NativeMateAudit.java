package ro.atf.chess;

/** Exercises the ordinary opponent against every legal defense without supplying a puzzle key. */
final class NativeMateAudit {
    record Summary(ChessPosition.Move firstMove,int nativeDecisions,int defensiveBranches,int terminalBranches) { }
    private final Checks check;
    private final String label;
    private final ChessPosition.Side attacker;
    private ChessPosition.Move first;
    private int decisions,defenses,terminals;

    private NativeMateAudit(Checks check,String label,ChessPosition.Side attacker) {
        this.check=check;this.label=label;this.attacker=attacker;
    }

    static Summary verify(Checks check,ChessMatch game,int attackerMoves,String label) {
        if(attackerMoves<1||attackerMoves>3||game.result()!=ChessMatch.Result.IN_PROGRESS)
            throw new IllegalArgumentException("Select an active one-to-three-move exercise");
        var audit=new NativeMateAudit(check,label,game.position().turn());
        audit.walk(game,attackerMoves*2-1,"");
        return new Summary(audit.first,audit.decisions,audit.defenses,audit.terminals);
    }

    private void walk(ChessMatch game,int left,String path) {
        var position=game.position();
        if(game.result()!=ChessMatch.Result.IN_PROGRESS) {
            check.same(label+" terminal board "+path,true,position.isCheckmate()&&position.turn()!=attacker);terminals++;return;
        }
        check.same(label+" mate within the original horizon "+path,true,left>0);
        if(left==0)return;
        if(position.turn()==attacker) {
            var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(position,analysis);
            if(first==null)first=analysis.decision().move();decisions++;
            int maximum=HardQuestAudit.maximumNodes(position);
            check.same(label+" native step "+path+" H"+analysis.decision().depth()+" extra="+analysis.decision().extraNodes()+" max="+maximum+" PV="+analysis.search().result().ranked(),true,analysis.decision().extraNodes()<=maximum);
            walk(game.play(analysis.decision().move()),left-1,path+" "+analysis.decision().move().uci());
        } else {
            for(var reply:position.legalMoves()){defenses++;walk(game.play(reply),left-1,path+" "+reply.uci());}
        }
    }
}
