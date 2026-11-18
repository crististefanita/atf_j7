package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class RookRootReentryTest {
    @Test void nativeKingEntryRetainsMateAgainstEveryLegalDefense(Checks check) {
        verify(check,"4r3/2p2R2/2p5/2Pk4/KP1P4/P4P2/8/8 b - - 2 54","d5c4");
        verify(check,"8/8/p4p2/kp1p4/2pK4/2P5/2P2r2/4R3 w - - 2 54","d4c5");
    }

    @Test void nativeRookEntryRetainsMateAgainstEveryLegalDefense(Checks check) {
        verify(check,"1r6/6R1/3p4/KP1Rp3/2r1P3/2k2P2/8/8 b - - 2 45","c4a4");
        verify(check,"8/8/2K2p2/2R1p3/kp1rP3/3P4/6r1/1R6 w - - 2 45","c5a5");
    }

    private static void verify(Checks check,String fen,String expected) {
        var position=ChessPosition.fromFen(fen);var game=ChessMatch.fromPosition(position,new MatchSetup(position.turn().other(),MatchSetup.Level.HARD,false));
        var actual=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(position,actual);
        check.same("Ordinary Hard receives no declared mate objective or curriculum key",expected,actual.decision().move().uci());
        int[] visited={0,0};walk(check,game.play(actual.decision().move()),position.turn(),4,expected,visited);
        check.same("Every legal opposing reply was explored, not only the canonical defense",true,visited[1]>1);
        check.same("The continuation uses actual bounded native choices",true,visited[0]>0);
    }

    private static void walk(Checks check,ChessMatch game,ChessPosition.Side attacker,int remaining,String path,int[] visited) {
        var position=game.position();
        if(game.result()!=ChessMatch.Result.IN_PROGRESS) {
            check.same("Actual terminal board is mating, not a drawn shortcut: "+path,true,position.isCheckmate()&&position.turn()!=attacker);return;
        }
        check.same("The mate finishes within the original three moves: "+path,true,remaining>0);
        if(remaining==0)return;
        if(position.turn()==attacker) {
            var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(position,analysis);visited[0]++;
            check.same("Native step "+path+" H"+analysis.decision().depth()+" extra="+analysis.decision().extraNodes()+" PV="+analysis.search().result().ranked(),true,analysis.decision().extraNodes()<=200000);
            walk(check,game.play(analysis.decision().move()),attacker,remaining-1,path+" "+analysis.decision().move().uci(),visited);
        } else {
            for(var reply:position.legalMoves()){visited[1]++;walk(check,game.play(reply),attacker,remaining-1,path+" "+reply.uci(),visited);}
        }
    }
}
