package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PuzzleChromeLayoutTest {
    @Test void usesTheScoreCeilingAndTwoTitleLinesToReserveStableRows(Checks check) {
        var standard = PuzzleChromeLayout.fit(300, 48, 18, 16, 252, 0);
        check.same("Menu and navigation both reserve at least a 48 dp touch target", 48, standard.headerHeight());
        check.same("The navigation stays a single row of touch targets", 48, standard.navigationHeight());
        check.same("The longest score fits the standard toolbar in one line", 1, standard.scoreLines());
        check.same("The standard score takes one measured line", 16, standard.scoreHeight());

        var exact = PuzzleChromeLayout.fit(252, 48, 27, 22, 252, 0);
        var narrow = PuzzleChromeLayout.fit(251, 48, 27, 22, 252, 0);
        check.same("Two enlarged title lines can extend beyond the tap target", 54, narrow.headerHeight());
        check.same("An enlarged score at the exact available width uses one line", 1, exact.scoreLines());
        check.same("One less column reserves two score lines for the whole chapter", 2, narrow.scoreLines());
        check.same("Both enlarged score lines fit before the board starts", 44, narrow.scoreHeight());
    }

    @Test void maximizesTheSquareOnBothPortraitProfilesWithRomanianText(Checks check) {
        // Rounded dp budgets for Romanian text at 1.3x, with one turn label and two guidance lines.
        var compactLayout = PuzzleChromeLayout.fit(328, 48, 22, 21, 298, 36);
        var compact = compactLayout.boardAndDetails(360, 640, 72, 22, 42);
        check.same("Compact 720 x 1280 at 320 dpi reserves the system bars, controls and details",
                new BoardPanelLayout.WithDetails(373, 42), compact);
        check.same("The compact profile centers the largest square that fits after the text",
                new BoardPanelLayout(4, 0, 351), BoardPanelLayout.fit(360, compact.boardHeight(), 22, 0));

        var tallLayout = PuzzleChromeLayout.fit(361, 48, 22, 21, 298, 36);
        var tall = tallLayout.boardAndDetails(393, 698, 72, 22, 42);
        check.same("The 1080 x 1920 profile at 440 dpi gives extra height to scrolling details",
                new BoardPanelLayout.WithDetails(415, 58), tall);
        check.same("The tall profile keeps its full-width square",
                new BoardPanelLayout(0, 0, 393), BoardPanelLayout.fit(393, tall.boardHeight(), 22, 0));
    }

    @Test void keepsControlsAndMaximizesTheSquareOnAShortWindowWithLargeText(Checks check) {
        var layout = PuzzleChromeLayout.fit(248, 48, 27, 28, 264, 36);
        check.same("The two guidance lines retain the full navigation target", 84, layout.navigationHeight());
        var shortWindow = layout.boardAndDetails(280, 480, 48, 27, 62);
        check.same("System bars, enlarged title, score, navigation and details all retain space",
                new BoardPanelLayout.WithDetails(176, 62), shortWindow);
        check.same("The height-constrained board uses all remaining height and stays centered",
                new BoardPanelLayout(65, 0, 149), BoardPanelLayout.fit(280, shortWindow.boardHeight(), 27, 0));
        check.same("A window with no remaining body space has no negative allocations",
                new BoardPanelLayout.WithDetails(0, 0), layout.boardAndDetails(280, 200, 48, 27, 62));
    }

    @Test void fitsTheFullWidthSquareWithoutOverlappingTheOpenMenu(Checks check) {
        int details = PuzzleChromeLayout.detailsMinimum(54, 38, 319, 210);
        check.same("The menu and fixed footer can share the space below the square", 109, details);
        var body = BoardPanelLayout.withDetails(1080, 1254, 58, details);
        check.same("On the physical portrait profile the board can use all 1080 pixels",
                new BoardPanelLayout(0, 0, 1080), BoardPanelLayout.fit(1080, body.boardHeight(), 58, 0));
        check.same("A single enlarged status line remains visible when it needs more room than the open menu",
                106, PuzzleChromeLayout.detailsMinimum(70, 36, 290, 210));
    }
}
