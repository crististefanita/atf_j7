package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class BoardPanelLayoutTest {
    @Test void reservesTheBarWithoutMovingAHeightConstrainedBoardBetweenScores(Checks check) {
        check.same("The bar and square fit a narrow screen", new BoardPanelLayout(13, 26, 307),
                BoardPanelLayout.fit(320, 399, 20, 20, 13));
        check.same("On a short screen the board stays square and the group is centered", new BoardPanelLayout(56, 0, 261),
                BoardPanelLayout.fit(360, 301, 20, 20, 13));
    }

    @Test void centersTheWholeGroupAndReservesBothLabelsBeforeSizingTheBoard(Checks check) {
        check.same("A tall window centers the labels with the full-width board", new BoardPanelLayout(0, 288, 1080),
                BoardPanelLayout.fit(1080, 1800, 72, 72));
        check.same("A short window shrinks the square after reserving unequal label heights", new BoardPanelLayout(43, 0, 274),
                BoardPanelLayout.fit(360, 350, 32, 44));
        check.same("Enlarged labels reduce the square in a short window", new BoardPanelLayout(76, 0, 208),
                BoardPanelLayout.fit(360, 350, 70, 72));
        check.same("An exhausted viewport has no negative board dimensions", new BoardPanelLayout(180, 0, 0),
                BoardPanelLayout.fit(360, 55, 32, 44));
    }

    @Test void puzzleAndReplayTakeTheFullContentWidthBeforeExpandingDetails(Checks check) {
        for (int width : new int[]{280, 320, 393, 480}) {
            int height = width + 128;
            var match = BoardPanelLayout.fit(width, height, 45, 45);
            var puzzle = BoardPanelLayout.withDetails(width, height, 62, 56);
            var replay = BoardPanelLayout.withDetails(width, height, 0, 56);
            check.same("The puzzle reaches the same full width as a match at " + width, match.side(),
                    BoardPanelLayout.fit(width, puzzle.boardHeight(), 31, 31).side());
            check.same("Replay uses every available column at " + width, width, replay.boardHeight());
            check.same("The details use only the remaining height at " + width, 66, puzzle.detailsHeight());
        }
    }

    @Test void shortAndEnlargedLayoutsKeepAVisibleDetailsViewportAndAStableSquare(Checks check) {
        var shortLayout = BoardPanelLayout.withDetails(360, 328, 74, 64);
        check.same("A short window keeps the measured text allowance", new BoardPanelLayout.WithDetails(264, 64), shortLayout);
        check.same("The height-constrained square is centered across the whole window",
                new BoardPanelLayout(85, 0, 190), BoardPanelLayout.fit(360, shortLayout.boardHeight(), 37, 37));
        check.same("A large-font preview cannot take over a shallow window",
                new BoardPanelLayout.WithDetails(180, 40), BoardPanelLayout.withDetails(360, 220, 100, 96));
        check.same("A short replay still takes the greatest square after the immediate caption",
                new BoardPanelLayout.WithDetails(264, 64), BoardPanelLayout.withDetails(360, 328, 0, 64));
        check.same("The layout stays nonnegative when only the labels fit",
                new BoardPanelLayout.WithDetails(30, 0), BoardPanelLayout.withDetails(360, 30, 62, 56));
    }
}
