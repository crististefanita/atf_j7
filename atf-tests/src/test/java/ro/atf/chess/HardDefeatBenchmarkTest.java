package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Separate quality goals: valid historical evidence is never a PASS for the current opponent. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardDefeatBenchmarkTest {
    @Test void alekhineMustAvoidTheConfirmedBishopSacrificeLoss(Checks check)throws Exception {verify("alekhine-hard-black",check);}
    @Test void kingsGambitMustAvoidTheConfirmedBishopCheckLoss(Checks check)throws Exception {verify("kings-gambit-hard-black",check);}
    @Test void nimzoMustAvoidTheConfirmedQueenMoveLoss(Checks check)throws Exception {verify("nimzo-rubinstein-hard-white",check);}
    @Test void petroffMustAvoidTheConfirmedPawnMoveLoss(Checks check)throws Exception {verify("petroff-hard-white",check);}
    @Test void slavMustAvoidTheConfirmedKnightExchangeLoss(Checks check)throws Exception {verify("slav-hard-black",check);}
    private static void verify(String id,Checks check)throws Exception {
        var w=HardDefeatWitnessTest.load().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();
        var game=w.game();var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,analysis);String move=analysis.decision().move().uci();
        for(int d:java.util.List.of(17,19))check.same(id+" current="+move+" H"+analysis.decision().depth()+" nodes="+analysis.decision().extraNodes()+" independent H"+d,true,w.loss(move,d)<150);
    }
}
