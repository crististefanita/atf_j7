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
    @Test void lessonBoardUsesLogicalProfilesAndFitsAfterLabelsAtEnlargedText(Checks check) {
        boolean compact = OpeningLessonLayout.sideBySide(360, 568);
        boolean landscape = OpeningLessonLayout.sideBySide(1280, 820);
        check.same("The compact lesson stacks the controls", false, compact);
        check.same("The wide lesson places controls beside the board", true, landscape);
        check.same("A narrow landscape window still stacks the lesson", false, OpeningLessonLayout.sideBySide(703, 440));
        check.same("A wide portrait window still stacks the lesson", false, OpeningLessonLayout.sideBySide(820, 1280));

        int compactLimit = OpeningLessonLayout.boardLimit(360, compact);
        int landscapeLimit = OpeningLessonLayout.boardLimit(640, landscape);
        int compactHeight = OpeningLessonLayout.panelHeight(360, 13, 44, compactLimit, 338);
        int landscapeHeight = OpeningLessonLayout.panelHeight(640, 13, 44, landscapeLimit, 716);
        check.same("The compact board keeps a 288dp square after reserving both seats", new BoardPanelLayout(42, 0, 288),
                BoardPanelLayout.fit(360, compactHeight, 22, 22, 13));
        check.same("The large board keeps a 592dp square in one of two equal columns", new BoardPanelLayout(30, 0, 592),
                BoardPanelLayout.fit(640, landscapeHeight, 22, 22, 13));
        int shortHeight = OpeningLessonLayout.panelHeight(360, 13, 52, compactLimit, 250);
        check.same("Less height shrinks the square while preserving both seats and the evaluation gutter",
                new BoardPanelLayout(87, 0, 198), BoardPanelLayout.fit(360, shortHeight, 26, 26, 13));
    }

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
