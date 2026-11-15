package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.capture.ScreenCaptureGeometry;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ScreenCaptureGeometryTest {
    @Test void rejectsTheApi28CropAndAcceptsTheCompleteFramebuffer(Checks check) {
        var compact = new ScreenCaptureGeometry(720, 1280, 1080, 1920);
        check.same("A compact API 28 override selects the complete framebuffer", true, compact.useFullDisplay(28));
        check.same("The full 1080 x 1920 framebuffer covers the logical display", true, compact.accepts(28, 1080, 1920));
        check.same("A top-left 720 x 1280 crop fails", false, compact.accepts(28, 720, 1280));
        check.same("A partial framebuffer with one correct dimension fails", false, compact.accepts(28, 1080, 1280));

        var letterboxed = new ScreenCaptureGeometry(720, 1280, 1080, 2000);
        check.same("The exact physical dimensions cannot calibrate an unequal aspect ratio", false,
                letterboxed.accepts(28, 1080, 2000));
        check.same("An unequal aspect ratio cannot fall back to the cropped image", false,
                letterboxed.accepts(28, 720, 1280));
    }

    @Test void usesLogicalBoundsOutsideTheApi28Override(Checks check) {
        var normal = new ScreenCaptureGeometry(1080, 1920, 1080, 1920);
        check.same("API 28 without an override keeps the logical capture", false, normal.useFullDisplay(28));
        check.same("The normal API 28 capture has the logical dimensions", true, normal.accepts(28, 1080, 1920));

        var compact = new ScreenCaptureGeometry(720, 1280, 1080, 1920);
        check.same("API 29 keeps the logical capture under an override", false, compact.useFullDisplay(29));
        check.same("API 29 accepts its logical screenshot", true, compact.accepts(29, 720, 1280));
        check.same("API 29 rejects a differently sized screenshot", false, compact.accepts(29, 1080, 1920));
    }
}
