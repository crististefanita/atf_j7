package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PlayerProfileTest {
    @Test void acceptsCompactNamesAndRejectsInvisibleOrUnsafeLabels(Checks check) {
        check.same("Trim and normalize Romanian names", "Ștefan", PlayerProfile.normalize("  S\u0326tefan  "));
        check.same("Spaces, apostrophes and hyphens stay visible", "D'Arc-2", PlayerProfile.normalize("D'Arc-2"));
        check.same("Ten characters fit the seat", "1234567890", PlayerProfile.normalize("1234567890"));
        check.rejects("Empty name", IllegalArgumentException.class, () -> PlayerProfile.normalize(""));
        check.rejects("Name with punctuation only", IllegalArgumentException.class, () -> PlayerProfile.normalize(" -- "));
        check.rejects("Name longer than ten characters", IllegalArgumentException.class, () -> PlayerProfile.normalize("LongerThan10"));
        check.rejects("Name with a line break", IllegalArgumentException.class, () -> PlayerProfile.normalize("A\nB"));
        check.rejects("Name with a direction override", IllegalArgumentException.class, () -> PlayerProfile.normalize("A\u202EB"));
    }
}
