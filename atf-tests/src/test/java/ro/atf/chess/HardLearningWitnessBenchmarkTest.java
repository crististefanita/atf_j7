package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardLearningWitnessBenchmarkTest {
    @Test void queensGambitAvoidsTheRetainedLargeLoss(Checks check)throws Exception { verify("queens-gambit-21",check); }
    @Test void frenchAvoidsTheRetainedLargeLoss(Checks check)throws Exception { verify("french-20",check); }
    private static void verify(String id,Checks check)throws Exception {
        var witness=HardLearningWitnesses.load().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow();
        var game=witness.game();String checkpoint=game.checkpoint();var analysis=game.analyzeBot(MatchSetup.Level.HARD);String move=analysis.decision().move().uci();
        double h17=witness.loss(move,17),h19=witness.loss(move,19);
        check.same(id+" current move="+move+" depth="+analysis.decision().depth()+" nodes="+analysis.decision().extraNodes(),checkpoint,game.checkpoint());
        check.same("The native decision no longer repeats a stable large loss; H17="+h17+" H19="+h19,true,h17<150||h19<150);
    }
}
