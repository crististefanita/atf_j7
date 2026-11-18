package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintCandidateCoverageTest {
    static final String HISTORY="e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1g5 e7e6 f2f4";

    @Test void comparesAllFourSicilianRootsAtOneCompletedHorizonAndOneCeiling(Checks check) {
        var game=ChessMatch.restore("2|analysis|"+HISTORY);var before=game.checkpoint();var board=game.position();
        var hint=TacticalAnalysis.verifiedHints(board,200_000);var comparison=hint.comparison();
        check.same("The whole calculation retains its executed seed/selection/comparison/budget",List.of(3,5,5,200_000),List.of(hint.seedDepth(),hint.selectedDepth(),comparison.depth(),hint.extraNodes()));
        check.same("All four roots are retained as scored diagnostic evidence",Set.of("b8c6","b8d7","d8b6","f8e7"),comparison.result().ranked().stream().map(v->v.move().uci()).collect(Collectors.toSet()));
        check.same("Coverage adds no oracle score or positional bonus",Map.of("b8d7",-21,"b8c6",-21,"f8e7",-25,"d8b6",-29),comparison.result().ranked().stream().collect(Collectors.toMap(v->v.move().uci(),TacticalAnalysis.Variation::score)));
        check.same("A theory challenger is compared rather than copied as the answer",move("f8e7"),OpeningRepertoire.CURRENT.playReply(board,MatchSetup.Level.HARD));
        check.same("The accepted best public line remains the qualified knight defense",List.of("b8d7"),hint.lines().stream().map(v->v.move().uci()).toList());
        for(var line:comparison.result().ranked()) {
            var end=board;for(var action:line.line())end=end.play(action);
            check.same("The scored challenger PV is legal: "+line.move().uci(),line.materialChange(),HintAuditSession.materialChangeCp(board,end));
            check.same("Each challenger reaches the actual shared horizon",true,line.line().size()>=comparison.depth());
        }
        var publicHint=game.hintAt(game.ply());publicHint.revealNext();
        check.same("Bulb and destination still use the same scored leader",List.of(hint.lines().getFirst().move(),hint.lines().getFirst().move()),List.of(publicHint.revealed().move(),publicHint.moveForTap(board,-1,hint.lines().getFirst().move().to())));
        check.same("The original analysis history is retained",before,game.checkpoint());
    }

    @Test void allocatesKnownTheoryAndSafeDoublePressureWithoutAnOpeningException(Checks check) {
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();var roots=List.of(move("b8c6"),move("b8d7"));
        check.same("The ordinary hint has no position override",null,OpeningRepertoire.CURRENT.reply(board));
        check.same("The general allocator adds theory and queen pressure",List.of(move("b8c6"),move("b8d7"),move("f8e7"),move("d8b6")),HintCandidateCoverage.expand(board,roots,Set.of()));
        check.same("An explicitly excluded root cannot reappear",List.of(move("b8c6"),move("b8d7"),move("f8e7")),HintCandidateCoverage.expand(board,roots,Set.of(move("d8b6"))));
        check.same("Excluding the theory reference keeps the original scope",roots,HintCandidateCoverage.expand(board,roots,Set.of(move("f8e7"))));
        for(var unchanged:List.of(List.<Move>of(),List.of(move("b8d7")),List.of(move("b8c6"),move("f8e7")),List.of(move("b8c6"),move("b8d7"),move("f8e7"))))
            check.same("Scope does not grow without a two-root defensive disagreement",unchanged,HintCandidateCoverage.expand(board,unchanged,Set.of()));
    }

    @Test void queenPressureIsOnlyAnAllocationSignalAndRejectsTheHangingQueen(Checks check) {
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();
        var candidate=HintCandidateCoverage.pressure(board,move("d8b6"));
        check.same("Qb6 newly attacks one undefended pawn and one minor",List.of(1,1),List.of(candidate.pawns(),candidate.minors()));
        var protectedPawn=HintCandidateCoverage.pressure(board,move("d8a5"));
        check.same("The rook-defended a2 pawn does not impersonate the b2 target",0,protectedPawn.pawns());
        var counter=ChessPosition.fromFen("rnbqkb1r/1p3ppp/p2ppn2/P5B1/3NPP2/2N5/1PP3PP/R2QKB1R b KQkq - 0 7");
        var unsafe=HintCandidateCoverage.pressure(counter,move("d8b6"));
        check.same("The queen move is legal but is captured by the a5 pawn",true,counter.legalMoves().contains(move("d8b6")));
        check.same("An immediately capturable queen is not a quiet pressure challenger",List.of(0,0),List.of(unsafe.pawns(),unsafe.minors()));
    }

    @Test void smallBudgetsKeepAllSearchAndComparisonWorkInsideTheOriginalAllowance(Checks check) {
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();
        for(int nodes:List.of(1_000,5_000,50_000,100_000,200_000)) {
            var hint=TacticalAnalysis.verifiedHints(board,nodes);
            check.same("The wider challenger set cannot spend beyond "+nodes,true,hint.extraNodes()<=nodes);
            for(var line:hint.lines()) {var end=board;for(var move:line.line())end=end.play(move);check.same("The fallback remains a scored legal PV",line.materialChange(),HintAuditSession.materialChangeCp(board,end));}
            if(hint.comparison()!=null)check.same("An unfinished deeper horizon is not promoted",hint.selectedDepth(),hint.comparison().depth());
        }
    }
    private static Move move(String text){return Move.fromUci(text);}
}
