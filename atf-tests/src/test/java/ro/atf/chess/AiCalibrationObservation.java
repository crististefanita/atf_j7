package ro.atf.chess;

import java.util.*;
import java.util.function.BiFunction;

/** In a rating study, unavailable advice is evidence, never a replacement move or final score. */
final class AiCalibrationObservation {
    static final String INITIAL="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    record Observed(LearningGameAudit.Frame before,CoachingProof.Work failure) { }
    static Observed capture(ChessMatch game,LearningGameAudit.Identity identity,String id) {
        return capture(game,identity,id,TacticalAnalysis::verifiedHints);
    }
    static Observed capture(ChessMatch game,LearningGameAudit.Identity identity,String id,
                            BiFunction<ChessPosition,Integer,TacticalAnalysis.HintSearch> hints) {
        require(game.positionAt(0).sameForRepetition(ChessPosition.initial()),"Calibration requires the standard initial origin; use a composed-position-aware collector otherwise");
        var hard=game.analyzeBot(MatchSetup.Level.HARD);TacticalAnalysis.HintSearch hint=null;CoachingProof.Work failure=null;
        try {
            hint=hints.apply(game.position(),200000);
            if(hint.proof()!=null&&!hint.proof().complete())throw new CoachingProof.Incomplete(hint.proof());
        } catch(CoachingProof.Incomplete incomplete){failure=incomplete.work();hint=null;}
        var estimate=hint==null?null:hint.proof()==null?MatchEvaluation.forPosition(game.position(),hint.lines().getFirst()):MatchEvaluation.from(game.position().turn(),hint.lines().getFirst());
        var history=LearningGameAudit.history(game);
        var frame=new LearningGameAudit.Frame(identity,id,INITIAL,game.ply(),history,game.setup(),game.checkpoint(),
                StudyFen.replay(INITIAL,history),game.position().turn().name(),"HARD",200000,hard,hint,MatchAiPolicy.CURRENT.evaluation.nodes(),estimate);
        validate(frame,failure,game);return new Observed(frame,failure);
    }
    static void validate(LearningGameAudit.Frame frame,CoachingProof.Work failure,ChessMatch game) {
        require(game.positionAt(0).sameForRepetition(ChessPosition.initial())&&frame.initialFen().equals(INITIAL)
                &&ChessPosition.fromFen(frame.fen()).sameForRepetition(game.position()),"Foreign calibration origin or displayed board");
        if(failure==null){LearningGameAudit.validateFrame(frame,game);return;}
        require(!failure.complete()&&frame.hint()==null&&frame.estimate()==null,"An incomplete hint must not carry lines or a final coaching score");
        require(frame.ply()==game.ply()&&frame.setup().equals(game.setup())&&frame.history().equals(LearningGameAudit.history(game))
                &&frame.checkpoint().equals(game.checkpoint())&&frame.initialFen().equals(INITIAL)
                &&frame.fen().equals(StudyFen.replay(INITIAL,frame.history()))&&frame.side().equals(game.position().turn().name()),"Foreign incomplete-hint context");
        require(frame.nativeLevel().equals("HARD")&&frame.requestedHintNodes()==200000&&frame.hard().search().scoreCredits().isEmpty(),"Wrong native or requested hint policy");
        require(failure.ceiling()==CoachingProof.CURRENT.maxNodes()&&failure.ordinaryNodes()<=200000,"The failed attempt cannot widen its declared proof reserve");
        failure.validate(game.position(),failure.ordinaryPlies());HardQuestAudit.validate(game,frame.hard());
    }
    private static void require(boolean okay,String why){if(!okay)throw new IllegalArgumentException(why);}
}
