package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ReplayBoardLayoutTest {
    private record Profile(String name, int width, int height, int source, int controls, ReplayBoardLayout expected) { }

    @Test void calculatedAndMateReplaysKeepTheSourceSideAndTheirFixedControls(Checks check) {
        var profiles = List.of(
                new Profile("compact", 344, 576, 316, 100, new ReplayBoardLayout(316, 160)),
                new Profile("short landscape", 560, 364, 193, 100, new ReplayBoardLayout(193, 71)),
                new Profile("desktop", 560, 812, 547, 100, new ReplayBoardLayout(547, 165)),
                new Profile("130 percent text", 328, 544, 289, 120, new ReplayBoardLayout(289, 135)));
        for (var profile : profiles) for (int ply = 0; ply < 12; ply++) {
            var actual = ReplayBoardLayout.fit(profile.width(), profile.height(), profile.source(), profile.controls(), 132);
            check.same(profile.name() + " keeps the source at ply " + ply, profile.expected(), actual);
            check.same(profile.name() + " keeps controls and details within the window at ply " + ply,
                    profile.height(), actual.side() + profile.controls() + actual.detailsHeight());
        }
    }

    @Test void longExplanationsCannotReallocateTheMeasuredSquare(Checks check) {
        var expected = new ReplayBoardLayout(316, 160);
        for (int text : new int[]{0, 56, 132, 2000}) {
            check.same("Text demand " + text + " scrolls in the remaining details viewport", expected,
                    ReplayBoardLayout.fit(344, 576, 316, 100, text));
        }
        check.same("A new opening measures the resized source rather than retaining the old cap", new ReplayBoardLayout(193, 71),
                ReplayBoardLayout.fit(560, 364, 193, 100, 132));
    }

    @Test void rotationAndMissingGeometryStayBoundedWithoutInventingAReplaySize(Checks check) {
        check.same("Rotation clamps a source that no longer fits the usable height", new ReplayBoardLayout(220, 0),
                ReplayBoardLayout.fit(740, 320, 547, 100, 132));
        check.same("A narrower window clamps the captured side to its content width", new ReplayBoardLayout(280, 196),
                ReplayBoardLayout.fit(280, 576, 316, 100, 132));
        check.same("An unmeasured source uses a bounded fallback", new ReplayBoardLayout(344, 132),
                ReplayBoardLayout.fit(344, 576, 0, 100, 132));
        check.same("No available space yields no negative square or details", new ReplayBoardLayout(0, 0),
                ReplayBoardLayout.fit(-1, 80, 316, 100, 132));
    }

    @Test void nativeOwnersPassTheMeasuredBoardAtTheTimeOfOpening(Checks check) throws Exception {
        var root = ProjectRoot.locate().resolve("android/app/src/main/java/ro/atf/chess/android");
        for (String file : List.of("ChessActivity.java", "AnalysisActivity.java", "OpeningActivity.java")) {
            check.same(file + " supplies current geometry rather than a display fraction", true,
                    Files.readString(root.resolve(file)).contains("() -> board.getWidth()"));
        }
        String hints = Files.readString(root.resolve("MatchAnalysisView.java"));
        check.same("Calculated and theory hints capture their source at opening", 2L,
                java.util.regex.Pattern.compile("sourceBoardSide.getAsInt\\(\\)").matcher(hints).results().count());
        check.same("Mating line forwards its source to the measured details adapter", true,
                Files.readString(root.resolve("QuestPayoffDialog.java")).contains("details, movePreview, sourceBoardSide)"));
        check.same("The common replay adapter receives the source independently of its frame content", true,
                Files.readString(root.resolve("AnalysisReplayDialog.java")).contains("board, controls, scroll, sourceBoardSide)"));
    }
}
