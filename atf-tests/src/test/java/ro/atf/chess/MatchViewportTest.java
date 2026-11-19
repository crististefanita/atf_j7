package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchViewportTest {
    @Test void shortWindowsUseCompactSeatsWithoutChangingTheWideReference(Checks check) {
        for (int[] size : new int[][]{{360, 640}, {480, 800}, {740, 420}, {900, 480}, {480, 480}}) {
            check.same("Compact chrome at " + size[0] + "x" + size[1], true, new MatchViewport(size[0], size[1]).compact());
        }
        for (int[] size : new int[][]{{1280, 900}, {481, 800}, {900, 481}, {0, 420}, {740, -1}}) {
            check.same("Keep the wide reference or reject missing geometry at " + size[0] + "x" + size[1], false,
                    new MatchViewport(size[0], size[1]).compact());
        }
    }
}
