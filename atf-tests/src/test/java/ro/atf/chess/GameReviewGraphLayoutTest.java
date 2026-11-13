package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
