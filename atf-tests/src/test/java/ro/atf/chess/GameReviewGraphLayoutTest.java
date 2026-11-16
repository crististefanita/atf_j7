package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewGraphLayoutTest {
    private static final int LONG_GAME = 220;

    @Test void everyRenderedPointSelectsItsPlyAcrossWidthsAndDensities(Checks check) {
        verifyCenters(check, 328, 2, LONG_GAME);
        verifyCenters(check, 640, 4, LONG_GAME);
        verifyCenters(check, 1028, 6, LONG_GAME);
        verifyCenters(check, 21, 3, 600);
    }

    @Test void insetsAndNearestPointKeepTheExtremesSelectable(Checks check) {
        check.same("The first and last points are drawn at the inner edges", List.of(2.0, 326.0),
                List.of(GameReviewGraphLayout.xForPly(0, LONG_GAME, 328, 2),
                        GameReviewGraphLayout.xForPly(LONG_GAME, LONG_GAME, 328, 2)));
        check.same("The penultimate point at x=324.527px selects ply 219", 219,
                GameReviewGraphLayout.plyAtX(324.527, LONG_GAME, 328, 2));
        check.same("The second point at x=3.473px selects ply 1", 1,
                GameReviewGraphLayout.plyAtX(3.473, LONG_GAME, 328, 2));
        check.same("Touches in and beyond the margins stay at their respective ends", List.of(0, 0, 220, 220),
                List.of(GameReviewGraphLayout.plyAtX(-10, LONG_GAME, 328, 2),
                        GameReviewGraphLayout.plyAtX(1, LONG_GAME, 328, 2),
                        GameReviewGraphLayout.plyAtX(327, LONG_GAME, 328, 2),
                        GameReviewGraphLayout.plyAtX(340, LONG_GAME, 328, 2)));
        check.same("Touches either side of the midpoint pick the nearer of two plies", List.of(3, 4),
                List.of(GameReviewGraphLayout.plyAtX(36.99, 10, 104, 2),
                        GameReviewGraphLayout.plyAtX(37.01, 10, 104, 2)));
    }

    @Test void emptyAndCollapsedGraphsHaveAStableStart(Checks check) {
        check.same("The initial position of an empty graph is centered", 164.0,
                GameReviewGraphLayout.xForPly(0, 0, 328, 2));
        check.same("A touch in an empty graph resolves to the initial position", 0,
                GameReviewGraphLayout.plyAtX(327, 0, 328, 2));
        check.same("When insets consume the width, the graph collapses to its center", 1.5,
                GameReviewGraphLayout.xForPly(4, LONG_GAME, 3, 2));
        check.same("A collapsed graph resolves to the initial position", 0,
                GameReviewGraphLayout.plyAtX(1.5, LONG_GAME, 3, 2));
    }

    @Test void selectedPointKeepsThePositionAndWhiteScoreTogether(Checks check) {
        var player = new GameReview.Player(OptionalInt.empty(), Optional.empty(), 0, Map.of());
        var played = Move.fromUci("f2f3");
        var move = new GameReview.Turn(1, Side.WHITE, played, GameReview.Grade.MISTAKE,
                500, 483, 17, 483, 0, List.of(), List.of(), 0);
        var review = new GameReview(List.of(move), null, 0, 500, player, player);
        var initial = GameReviewGraphLayout.pointForPly(review, -1);
        var last = GameReviewGraphLayout.pointForPly(review, 4);
        check.same("An index before the game resolves to the initial position and its White score",
                new GameReviewGraphLayout.Point(0, 1, 500), initial);
        check.same("An index after the game resolves to the last position and its White score",
                new GameReviewGraphLayout.Point(1, 1, 483), last);
        check.same("The same selected point formats a decimal in the review language", List.of("48.3", "48,3"),
                List.of(last.whitePercent(Locale.US), last.whitePercent(Locale.forLanguageTag("ro-RO"))));
        check.same("A whole percentage has no decimal padding", List.of("50", "50"),
                List.of(initial.whitePercent(Locale.US), initial.whitePercent(Locale.forLanguageTag("ro-RO"))));
        var empty = new GameReview(List.of(), null, 0, 1000, player, player);
        check.same("An empty review retains its only score at position zero",
                new GameReviewGraphLayout.Point(0, 0, 1000), GameReviewGraphLayout.pointForPly(empty, 1));
    }

    private static void verifyCenters(Checks check, int width, int inset, int lastPly) {
        var mismatches = new ArrayList<String>();
        for (int ply = 0; ply <= lastPly; ply++) {
            float drawnX = (float) GameReviewGraphLayout.xForPly(ply, lastPly, width, inset);
            int observed = GameReviewGraphLayout.plyAtX(drawnX, lastPly, width, inset);
            if (observed != ply) mismatches.add(ply + " → " + observed);
        }
        check.same("Every point selects its ply at width " + width + "px and inset " + inset
                + "px for " + lastPly + " plies", List.of(), mismatches);
    }
}
