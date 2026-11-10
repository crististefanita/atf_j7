package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class BoardPanelLayoutTest {
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
}
