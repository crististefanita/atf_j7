package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PlayerProfileTest {
    @Test void acceptsCompactNamesAndRejectsInvisibleOrUnsafeLabels(Checks check) {
        check.same("Trim and normalize Romanian names", "Ștefan", PlayerProfile.normalize("  S\u0326tefan  "));
        check.same("Spaces, apostrophes and hyphens stay visible", "D'Arc-2", PlayerProfile.normalize("D'Arc-2"));
        check.same("Ten characters fit the seat", "1234567890", PlayerProfile.normalize("1234567890"));
        for (String name : new String[]{"", " -- ", "LongerThan10", "A\nB", "A\u202EB"}) {
            assertThrows(IllegalArgumentException.class, () -> PlayerProfile.normalize(name));
        }
    }
}
