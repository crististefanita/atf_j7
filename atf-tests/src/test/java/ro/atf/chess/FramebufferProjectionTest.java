package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.capture.FramebufferProjection;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class FramebufferProjectionTest {
    private static final String WIDE = "Viewport: displayId=0, orientation=0, logicalFrame=[0, 0, 1480, 840], "
            + "physicalFrame=[0, 654, 1080, 1266], deviceSize=[1080, 1920]";

    @Test void mapsOnlyTheCompleteViewportReportedByAndroid(Checks check) {
        var projection = FramebufferProjection.fromInputDump(WIDE + "\n" + WIDE, 1480, 840, 1080, 1920);
        check.same("Repeated identical touch-device viewports describe one projection", true, projection.accepts(1080, 1920));
        check.same("A logical crop cannot stand in for the physical framebuffer", false, projection.accepts(1480, 840));
        check.same("Full logical display maps to the recorded letterbox", "[0, 654, 1080, 1266]",
                Arrays.toString(projection.project(0, 0, 1480, 840)));
        check.same("The center retains its compositor offset", "[540, 960, 540, 960]",
                Arrays.toString(projection.project(740, 420, 740, 420)));
        check.rejects("A rectangle outside the logical viewport fails", IllegalArgumentException.class,
                () -> projection.project(0, 0, 1481, 840));
    }

    @Test void rejectsAnUnverifiedOrForeignCompositorFrame(Checks check) {
        for (String input : new String[]{"", WIDE.replace("displayId=0", "displayId=1"), WIDE.replace("orientation=0", "orientation=1"),
                WIDE.replace("1480, 840", "1479, 840"), WIDE.replace("654", "653"), WIDE.replace("1080, 1920", "1080, 2000")}) {
            check.rejects("Unverified viewport fails: " + input, IllegalArgumentException.class,
                    () -> FramebufferProjection.fromInputDump(input, 1480, 840, 1080, 1920));
        }
        var portrait = new FramebufferProjection(720, 1280, 1080, 1920, 0, 0, 1080, 1920);
        check.same("The established portrait mapping is unchanged", "[0, 0, 1080, 1920]",
                Arrays.toString(portrait.project(0, 0, 720, 1280)));
    }

    @Test void quarterTurnRequiresTheObservedRotationAndUncroppedPhysicalFrame(Checks check) {
        String rotated = "Viewport: displayId=0, orientation=1, logicalFrame=[0, 0, 1480, 840], "
                + "physicalFrame=[234, 420, 846, 1500], deviceSize=[1080, 1920]";
        // A crop with the right aspect ratio must never become a complete display.
        check.rejects("Centered but incomplete rotated frame fails", IllegalArgumentException.class,
                () -> FramebufferProjection.fromInputDump(rotated, 1480, 840, 1920, 1080, 1));
        String full = "Viewport: displayId=0, orientation=1, logicalFrame=[0, 0, 1920, 1080], "
                + "physicalFrame=[0, 0, 1080, 1920], deviceSize=[1080, 1920]";
        var projection = FramebufferProjection.fromInputDump(full, 1920, 1080, 1920, 1080, 1);
        check.same("The physically rotated screenshot retains every pixel", "[0, 0, 1920, 1080]",
                Arrays.toString(projection.project(0, 0, 1920, 1080)));
        check.same("An unrotated raster cannot substitute for a quarter turn", false, projection.accepts(1080, 1920));
        check.rejects("Unobserved rotation cannot be inferred from dimensions", IllegalArgumentException.class,
                () -> FramebufferProjection.fromInputDump(full, 1920, 1080, 1920, 1080));
        check.rejects("A requested turn cannot accept a stale viewport", IllegalArgumentException.class,
                () -> FramebufferProjection.fromInputDump(full.replace("orientation=1", "orientation=0"), 1920, 1080, 1920, 1080, 1));
        for (int unsupported : new int[]{-1, 2, 3}) check.rejects("Unsupported rotation " + unsupported, IllegalArgumentException.class,
                () -> FramebufferProjection.fromInputDump(full, 1920, 1080, 1920, 1080, unsupported));
    }
}
