package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebOptionsMenuTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void optionsMenusStayAnchoredAndKeyboardReachable(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path log = output.resolve("options-menu.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/options-menu.test.mjs").toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("Anchored menu components finish without a browser or emulator", true, finished);
        String transcript = Files.readString(log);
        check.same("Menu placement, accessible focus and listener cleanup pass", "exit 0",
                process.exitValue() == 0 ? "exit 0" : transcript);
        var passed = transcript.lines().map(PASS::matcher).filter(java.util.regex.Matcher::matches)
                .map(result -> result.group(1)).toList();
        check.same("Every required menu contract appears in the component transcript", List.of(
                "Menu geometry stays beside its trigger on compact and desktop at 100 and 130 percent",
                "Menus clamp sideways, flip above and retain scroll space for every option",
                "Zoomed visual viewport offsets constrain the menu in the actually visible region",
                "A menu outside the trigger parent follows the trigger instead of the document bottom",
                "Options focus and Escape preserve the page position and return to their own trigger",
                "Outside interaction and blocked actions close or refuse the options without a focus trap",
                "Viewport changes update menu limits and leaving the visible trigger releases listeners"), passed);
    }
}
