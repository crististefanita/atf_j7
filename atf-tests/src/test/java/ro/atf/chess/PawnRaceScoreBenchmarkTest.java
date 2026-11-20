package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** An explicit open quality goal, separate from the passing recommendation and receipt tests. */
@AtfComponentTest @Tag("benchmark") @ExtendWith(ExtentAudit.class)
class PawnRaceScoreBenchmarkTest {
    @Test void aConfirmedWinToDrawMustNotBeReviewedAsAGoodMove(Checks check)throws Exception {
        try(var stream=getClass().getResourceAsStream("/pawn-race-witnesses.yaml")) {
            var cases=LearningGameAudit.mapper().readTree(stream).path("cases");
            for(int i=0;i<2;i++) {
                var witness=HardLearningWitnesses.read(cases.get(i));var game=witness.game();String before=game.checkpoint();
                for(int depth:List.of(17,19))check.same(witness.id()+" independent win-to-draw witness at H"+depth,true,witness.loss(witness.originalMove(),depth)>=150);
                var review=GameReview.analyzeLine(game.play(ChessPosition.Move.fromUci(witness.originalMove())),n->{});var turn=review.turns().getFirst();
                check.same("Reviewing an alternative keeps the actual history",before,game.checkpoint());
                check.same(witness.id()+" played="+turn.played().uci()+" best="+turn.bestLine().getFirst().uci()+" grade="+turn.grade()+" nativeLoss="+turn.loss(),true,
                        Set.of(GameReview.Grade.INACCURACY,GameReview.Grade.MISTAKE,GameReview.Grade.MISS,GameReview.Grade.BLUNDER).contains(turn.grade()));
            }
        }
    }
}
