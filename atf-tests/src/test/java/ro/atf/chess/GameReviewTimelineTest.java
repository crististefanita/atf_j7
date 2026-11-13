package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewTimelineTest {
    @Test void navigatesTheReviewAndReplaysTheBestLineWithoutChangingTheArchivedGame(Checks check) {
        var source = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        String archived = source.checkpoint();
        var review = GameReview.analyze(source, ignored -> { });
        var timeline = new GameReviewTimeline(source, review);

        check.same("The review opens on the final recorded position", source.ply(), timeline.ply());
        check.same("The final frame has no following key moment", true, timeline.nextKeyMomentPly().isEmpty());
        timeline.seek(1);
        check.same("Next key moment skips ordinary moves and selects the missed defense", 3,
                timeline.nextKeyMomentPly().orElse(-1));
        check.same("Previous stops at the initial position", true, timeline.previous());
        check.same("Start is the exact initial position and cannot move backward", List.of(0, false, true),
                List.of(timeline.ply(), timeline.previous(), timeline.next()));
        check.same("The next control advances one move and clears any analysis branch", 1, timeline.ply());

        timeline.seek(3);
        var turn = timeline.turn().orElseThrow();
        var recommended = turn.bestLine().get(0);
        check.same("The selected review move is the source game move", source.moveAt(3), turn.played());
        check.same("The recommended line begins with a legal move from before the selected move", true,
                source.positionAt(2).legalMoves().contains(recommended));
        check.same("Show starts before the recommended move and marks its route", true, timeline.showBestLine());
        check.same("The board returns to the selected move's pre-position", true,
                source.positionAt(2).sameForRepetition(timeline.position()));
        check.same("The first recommended move is visible as the board cue", recommended,
                timeline.nextBestMove().orElseThrow());
        check.same("The first PV step advances the board and records the played branch move", true,
                timeline.nextBestLineStep());
        check.same("PV step one is the position after the recommended move", true,
                source.positionAt(2).play(recommended).sameForRepetition(timeline.position()));
        check.same("Previous PV returns to the pre-move position", true, timeline.previousBestLineStep());
        check.same("Selecting a different game ply exits PV mode", true, timeline.next());
        check.same("Selecting a different game ply restores the archived line", true,
                source.positionAt(4).sameForRepetition(timeline.position()));
        check.same("Review and PV exploration never modify the archived checkpoint or result",
                List.of(archived, ChessMatch.Result.BLACK_WINS), List.of(source.checkpoint(), source.result()));
        check.rejects("A cached review cannot be paired with a different game having the same length",
                IllegalArgumentException.class, () -> new GameReviewTimeline(
                        ChessMatch.restore("1|e2e4 e7e5 g1f3 b8c6"), review));
        check.rejects("Out-of-range review navigation is rejected", IllegalArgumentException.class,
                () -> timeline.seek(source.ply() + 1));
    }
}
