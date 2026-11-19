package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class ScotchContinuationBenchmarkTest {
    @Test void reviewCannotPreferThePawnGrabToTheTeacherWinningCheck(Checks check)throws Exception {
        var data=ScotchWitnessTest.fixture();var board=ScotchWitnessTest.position(data);var move=ChessPosition.Move.fromUci(data.path("rootPair").path("winning").asText());
        var review=TacticalAnalysis.review(board,move,java.util.Set.of(),GameReviewPolicy.CURRENT.search);
        check.same("Review must resolve the winning check before it can mark the actual Re8+ as a blunder",move,review.best().move());
    }
    @Test void theWinningCheckCannotBeOmittedFromTheRootComparison(Checks check)throws Exception {
        var data=ScotchWitnessTest.fixture();var hint=TacticalAnalysis.verifiedHints(ScotchWitnessTest.position(data));
        var move=ChessPosition.Move.fromUci(data.path("rootPair").path("winning").asText());
        check.same("The freely verified Re8+ must be considered before a pawn grab",true,
                hint.lines().stream().anyMatch(v->v.move().equals(move))||hint.comparison()!=null&&hint.comparison().result().ranked().stream().anyMatch(v->v.move().equals(move)));
        check.same("The winning root requires its actual scored continuation",move,hint.lines().getFirst().move());
    }
    @Test void theOpposingPreviewCannotReplaceAForcedMateWithAPawnGrab(Checks check)throws Exception {
        var data=ScotchWitnessTest.fixture();var rows=ScotchWitnessTest.rows(data);var hint=TacticalAnalysis.verifiedHints(ScotchWitnessTest.position(data));
        check.same("An unseen replacement requires renewed independent evidence",ScotchWitnessTest.moves(data.path("publicPath")),hint.lines().get(data.path("rank").asInt()-1).line());
        boolean large=true;for(int d:List.of(17,19))large&=StockfishStudyAudit.loss(rows.get(data.path("bad").path("queries").path("free").path(""+d).asText()),rows.get(data.path("bad").path("queries").path("forced").path(""+d).asText()))>=150;
        check.same("The scored opposing preview must retain the forcing mate rather than assume Bxb2",false,large);
    }
}
