package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Mocked unavailable advice tests the collector contract, not the engine's tactical strength. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AiCalibrationObservationTest {
    private static ChessMatch game(){return ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.BASIC,true));}
    private static LearningGameAudit.Identity identity(){return new LearningGameAudit.Identity("mocked-unavailable-advice","a".repeat(40),"b".repeat(64),"c".repeat(64));}
    private static CoachingProof.Work failure(ChessPosition p) {
        int limit=CoachingProof.CURRENT.maxNodes();
        return new CoachingProof.Work(CoachingProof.Status.NODE_LIMIT,limit,limit,limit,3,0,0,0,0,
                p.searchKey().toString(),List.of(p.legalMoves().getFirst()),200000,0,null,0);
    }
    @Test void anUnavailableHintDoesNotBorrowOrReplaceTheIndependentBasicChoice(Checks check) {
        var game=game();var actual=game.analyzeBot(MatchSetup.Level.BASIC);
        var observed=AiCalibrationObservation.capture(game,identity(),"mocked-basic",(position,nodes)->{throw new CoachingProof.Incomplete(failure(position));});
        check.same("The source of unavailable advice stays explicit",CoachingProof.Status.NODE_LIMIT,observed.failure().status());
        check.same("There is no final public coaching line",null,observed.before().hint());
        check.same("There is no final coaching score on incomplete proof",null,observed.before().estimate());
        check.same("The independent playing profile keeps its complete decision, depth, work and PV",actual,game.analyzeBot(MatchSetup.Level.BASIC));
        check.same("The actual game can advance without pretending the hint completed",game.ply()+1,game.play(actual.decision().move()).ply());
    }
    @Test void aComposedOriginCannotBeRelabeledAsTheInitialBoard(Checks check) {
        var composed=ChessMatch.fromPosition(ChessPosition.fromFen("8/8/8/4k3/8/1K6/Q7/8 w - - 0 1"),
                new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.BASIC,true));
        check.rejects("A composed origin is rejected before a search or an invented initial FEN",IllegalArgumentException.class,
                ()->AiCalibrationObservation.capture(composed,identity(),"mocked-composed",(p,n)->{throw new AssertionError("Do not search a rejected origin");}));
    }
    @Test void missingFailureEvidenceAndInventedFinalScoresAreRejected(Checks check) {
        var game=game();var observed=AiCalibrationObservation.capture(game,identity(),"mocked-basic",(position,nodes)->{throw new CoachingProof.Incomplete(failure(position));});
        check.rejects("Null advice without a typed failure is still incomplete evidence",IllegalArgumentException.class,
                ()->AiCalibrationObservation.validate(observed.before(),null,game));
        var f=observed.before();var forged=new LearningGameAudit.Frame(f.identity(),f.caseId(),f.initialFen(),f.ply(),f.history(),f.setup(),f.checkpoint(),f.fen(),f.side(),
                f.nativeLevel(),f.requestedHintNodes(),f.hard(),null,f.separateEstimateLimit(),MatchEvaluation.from(game.position().turn(),f.hard().search().result().ranked().getFirst()));
        check.rejects("An unavailable recommendation cannot publish the native player's score as its own",IllegalArgumentException.class,
                ()->AiCalibrationObservation.validate(forged,observed.failure(),game));
    }
}
