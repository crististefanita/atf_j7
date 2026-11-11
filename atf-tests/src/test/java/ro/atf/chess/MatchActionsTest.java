package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchActionsTest {
    @Test void anAgreedDrawSurvivesRestoreAndRewindingStartsANewContinuation(Checks check) {
        var live = ChessMatch.restore("1|e2e4 e7e5");
        var ending = live.offerDraw();
        var restored = ChessMatch.restore(ending.checkpoint());
        check.same("A balanced position yields a saved draw by agreement", Result.AGREED_DRAW, restored.result());
        check.same("Agreement adds no move to the game", live.ply(), restored.ply());
        check.same("Previous historical positions remain playable", Result.IN_PROGRESS, restored.continueFrom(1).result());
        check.same("Manual analysis can explore the final position after an agreed draw", Result.IN_PROGRESS,
                new AnalysisLine(restored, restored.ply()).play(Move.fromUci("g1f3")).result());
        check.same("An agreed draw is neutral in the review", .5, MatchEvaluation.terminalWhiteShare(restored.result()));
        check.rejects("An ended match cannot accept another offer", IllegalStateException.class, restored::offerDraw);
    }

    @Test void theAiDeclinesWithAMaterialLeadAndLeavesTheLiveCheckpointIntact(Checks check) {
        var match = ChessMatch.restore("1|e2e4 d7d5 f1b5 c7c6 b5c6 b7c6");
        var declined = match.offerDraw();
        check.same("A material lead produces a refusal without a state transition", match, declined);
        check.same("The original match is still playable", Result.IN_PROGRESS, declined.result());
        check.rejects("A draw cannot be offered during the AI turn", IllegalStateException.class,
                () -> ChessMatch.restore("1|e2e4").offerDraw());
    }

    @Test void resigningAwardsTheOtherSideEvenIfTheAiIsThinking(Checks check) {
        var white = ChessMatch.restore("1|e2e4").resign();
        var black = ChessMatch.restore("3|BLACK|ADVANCED|0|").resign();
        check.same("Resigning as White awards Black", Result.BLACK_WINS, ChessMatch.restore(white.checkpoint()).result());
        var restored = ChessMatch.restore(black.checkpoint());
        check.same("Resigning as Black awards White and preserves the cause", Result.WHITE_WINS, restored.result());
        check.same("The checkpoint records resignation", true, restored.resigned());
        check.same("The result preserves the configured side", Side.BLACK, restored.setup().humanSide());
        check.rejects("Nested adjudications are rejected", IllegalArgumentException.class,
                () -> ChessMatch.restore("4|RESIGN|" + white.checkpoint()));
        check.rejects("A checkmate cannot be overwritten", IllegalArgumentException.class,
                () -> ChessMatch.restore("4|RESIGN|1|f2f3 e7e5 g2g4 d8h4"));
    }
}
