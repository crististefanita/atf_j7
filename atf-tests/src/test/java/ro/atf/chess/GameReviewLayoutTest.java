package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewLayoutTest {
    @Test void usesTheAvailableWidthAndKeepsTheReportVisibleInPortrait(Checks check) {
        check.same("Tall portrait fills the usable width after insets", new GameReviewLayout(false, 360, 360),
                GameReviewLayout.fit(360, 688, 70, 240, 132, 248, 8));
        check.same("A short portrait reserves a third of the available height for the report", new GameReviewLayout(false, 260, 320),
                GameReviewLayout.fit(320, 460, 70, 240, 132, 248, 8));
        check.same("Larger text reserves its measured height without hiding the report", new GameReviewLayout(false, 247, 320),
                GameReviewLayout.fit(320, 460, 90, 240, 132, 248, 8));
        check.same("A very short window keeps a proportional report preview", new GameReviewLayout(false, 50, 270),
                GameReviewLayout.fit(270, 165, 90, 240, 132, 248, 8));
        check.same("An exhausted viewport never produces a negative board", new GameReviewLayout(false, 0, 320),
                GameReviewLayout.fit(320, 48, 48, 240, 132, 248, 8));
    }

    @Test void placesTheReportBesideTheBoardOnlyWhenBothPanelsFit(Checks check) {
        check.same("Wide landscape gives the board and report their full height", new GameReviewLayout(true, 290, 290),
                GameReviewLayout.fit(728, 360, 70, 240, 132, 248, 8));
        check.same("The short landscape board centers above a full-width toolbar", new GameReviewLayout(true, 150, 240),
                GameReviewLayout.fit(540, 220, 70, 240, 132, 248, 8));
        check.same("A narrow landscape window stacks to avoid clipping the controls", new GameReviewLayout(false, 214, 480),
                GameReviewLayout.fit(480, 390, 70, 240, 132, 248, 8));
        check.same("Stacking wins when it can fit a larger square", new GameReviewLayout(false, 314, 520),
                GameReviewLayout.fit(520, 516, 70, 240, 132, 248, 8));
    }
}
