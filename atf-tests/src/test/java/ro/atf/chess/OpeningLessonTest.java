package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.OpeningPractice.Phase;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningLessonTest {
    @Test void stepsThroughEveryAuthoredLineAndBackFromBothSeats(Checks check) {
        for (var opening : OpeningCatalog.load()) {
            for (Side learner : Side.values()) {
                var lesson = new OpeningLesson(new OpeningPractice(opening, learner, 0));
                var replay = ChessPosition.initial();
                for (int index = 0; index < opening.steps().size(); index++) {
                    var move = opening.steps().get(index).move();
                    lesson = lesson.forward();
                    replay = replay.play(move);
                    String step = opening.id() + " " + learner + " ply " + (index + 1);
                    check.same(step + " applies only the authored move", move, lesson.practice().previous());
                    check.same(step + " preserves the board reached by legal replay", true,
                            replay.sameForRepetition(lesson.practice().position()));
                    check.same(step + " leaves the next reply under the player's control", false, lesson.canAutoReply());
                }
                check.same(opening.id() + " stops on the last move", lesson, lesson.forward());
                var start = lesson.show(0);
                check.same(opening.id() + " keeps the completed line while reviewing", Phase.COMPLETE, start.practice().phase());
                check.same(opening.id() + " opens the first move again", 1, start.forward().shownPly());
                check.same(opening.id() + " lets the learner replay from the start", 0, start.replay().practice().ply());
            }
        }
    }

    @Test void reviewingPausesPendingRepliesAndReplayingStartsAtTheSelectedPosition(Checks check) {
        var start = new OpeningLesson(new OpeningPractice(OpeningCatalog.load().getFirst(), Side.WHITE, 0));
        var e4 = Move.fromUci("e2e4");
        var pending = start.play(e4);
        check.same("A player move queues the scripted reply", true, pending.canAutoReply());
        var review = pending.show(0);
        check.same("A pending reply cannot move a reviewed board", false, review.canAutoReply());
        check.rejects("A reviewed position requires explicit replay", IllegalStateException.class, () -> review.play(e4));
        check.rejects("A queued reply cannot fire while reviewing", IllegalStateException.class, review::reply);
        var viewedReply = review.forward();
        check.same("Forward returns to the pending position with the reply paused", true, viewedReply.replyPaused());
        check.same("Returning from review preserves an explicit pause", viewedReply, viewedReply.show(0).show(1));
        check.same("The next explicit forward applies e5 once", "e7e5", viewedReply.forward().practice().previous().uci());
        var resumed = viewedReply.resume().reply();
        check.same("Resuming applies e5 once and returns control to White", true, resumed.canPlay() && resumed.shownPly() == 2);
        var replay = resumed.show(0).replay();
        check.same("Replaying clears the later moves from active practice", start, replay);
        check.same("The learner can play e4 again from the restored position", pending, replay.play(e4));
    }
}
