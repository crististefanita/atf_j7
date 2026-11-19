package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidSourceReplayReceiptTest {
    @TempDir Path folder;

    @Test void nativeFixtureHasIndependentLegalCalculatedBranches(Checks check) {
        var match = ChessMatch.restore(ro.atf.chess.capture.ReplayViewportFixture.CALCULATED);
        var hint = match.hintAt(match.ply());
        hint.revealNext();
        var lines = hint.playback(match.position()).orElseThrow().lines();
        check.same("The shared fixture exposes a real alternative without injecting a suggestion", true, lines.size() >= 2);
        check.same("The alternatives start with distinct moves", (long) lines.size(), lines.stream().map(line -> line.move().uci()).distinct().count());
        for (var line : lines) {
            var preview = HintPreview.openDefended(match.position(), line).orElseThrow();
            check.same("The branch has a legal reply to navigate", true, preview.length() >= 2);
        }
    }

    @Test void requiresEachNativeGeometryAssertionAndTheActualProfile(Checks check) throws Exception {
        String valid = receipt();
        check.same("All twenty ordered native checks pass on the requested display", true, read("valid", valid));
        check.same("A missing branch assertion fails even with a passing terminal status", false,
                read("missing", valid.replaceAll("(?m)^INSTRUMENTATION_RESULT: check_07=.*\\R", "")));
        check.same("An unrelated replacement cannot satisfy the geometry contract", false,
                read("foreign", valid.replace("calculated branch keeps the board and control rectangles", "The app launched")));
        check.same("Duplicating a check cannot replace coverage", false,
                read("duplicate", valid.replace("check_07=", "check_06=")));
        check.same("An unequal source/replay side fails despite a claimed PASS", false,
                read("side", valid.replace("\t316\t316\ttrue", "\t316\t300\ttrue")));
        check.same("A different density cannot borrow the requested dimensions", false,
                read("density", valid.replace("@160", "@320")));
        check.same("A different language cannot borrow this profile", false,
                read("locale", valid.replace("language=en", "language=ro")));
        check.same("No profile marker is not evidence of the requested viewport", false,
                read("profile", valid.replaceAll("(?m)^INSTRUMENTATION_RESULT: replayProfile=.*\\R", "")));
        check.same("Duplicate display markers are ambiguous", false,
                read("profiles", valid + "INSTRUMENTATION_RESULT: replayProfile=360x640@160, font=1.0, language=en\n"));
    }

    private boolean read(String name, String content) throws Exception {
        Path file = folder.resolve(name + ".txt");
        Files.writeString(file, content);
        return AndroidSanityPublication.readSourceReplayPhase(file, "360x640", "en").passed();
    }

    private String receipt() {
        var text = new StringBuilder("""
                INSTRUMENTATION_RESULT: phase=source-sized-replay
                INSTRUMENTATION_RESULT: pid=123
                INSTRUMENTATION_RESULT: result=PASS
                INSTRUMENTATION_RESULT: replayProfile=360x640@160, font=1.0, language=en
                INSTRUMENTATION_CODE: -1
                """);
        for (int id = 0; id < AndroidSanityPublication.SOURCE_REPLAY_CHECKS.size(); id++) {
            text.append(String.format(Locale.ROOT, "INSTRUMENTATION_RESULT: check_%02d=%s\t316\t316\ttrue%n",
                    id + 1, AndroidSanityPublication.SOURCE_REPLAY_CHECKS.get(id)));
        }
        return text.toString();
    }
}
