package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class ScotchReviewClassificationBenchmarkTest {
    @Test void aSoundRe8CannotBecomeABlunderInTheActualGameReview(Checks check)throws Exception {
        var data=ScotchWitnessTest.fixture();var board=ScotchWitnessTest.position(data);
        var move=ChessPosition.Move.fromUci(data.path("rootPair").path("winning").asText());var teacher=ScotchWitnessTest.rows(data);
        for(int depth:List.of(17,19))check.same("Re8+ has small independent H"+depth+" loss",true,
                StockfishStudyAudit.loss(teacher.get(data.path("rootPair").path("free").path(""+depth).asText()),teacher.get(data.path("rootPair").path("played").path(""+depth).asText()))<50);
        var game=ChessMatch.fromPosition(board,new MatchSetup(board.turn(),MatchSetup.Level.HARD,true)).play(move);
        var review=GameReview.analyzeLine(game,ignored->{});var turn=review.turns().getFirst();
        check.same("The real review classifies the actually played move",move,turn.played());
        check.same("A source-bound sound move must not be called BLUNDER (native loss="+turn.loss()+")",false,turn.grade()==GameReview.Grade.BLUNDER);
        check.same("The actual review must retain a small loss, independently of the bulb",true,turn.loss()<50);
    }
}
