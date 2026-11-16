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
class ChessWebReviewAccessibilityTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void focusPolicyRunsWithoutABrowserOrEmulator(Checks check) throws IOException, InterruptedException {
        runNode(check, "review-focus.test.mjs", List.of(
                "History navigation stays within recorded moves", "Graph and history preserve their own entry targets",
                "Explanation enters from Back and returns to its opener", "A toolbar command keeps priority before neighboring controls",
                "Revealing focus scrolls only the necessary distance"));
    }

    @Test void announcementPolicyRunsWithoutABrowserOrEmulator(Checks check) throws IOException, InterruptedException {
        runNode(check, "review-announcement.test.mjs", List.of(
                "Move text speaks the recorded move and position in both languages",
                "Graph text adds the White score without losing the move or cursor",
                "A committed slider change publishes one textual value and keeps unchanged renders silent",
                "Why text keeps the recorded move with or without a detailed reason",
                "Focus, value and live status are mutually exclusive channels", "Slider commands clamp at the archived endpoints"));
    }

    private void runNode(Checks check, String script, List<String> expected) throws IOException, InterruptedException {
        var log = output.resolve(script + ".tap");
        var root = ProjectRoot.locate();
        String node = System.getProperty("chess.web.node", "node");
        var execution = new ProcessBuilder(node, "--test", "--test-reporter=tap",
                root.resolve("web/tests").resolve(script).toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = execution.waitFor(30, TimeUnit.SECONDS);
        if (!completed) execution.destroyForcibly();
        check.same("The offline Node suite completes successfully", 0, completed ? execution.exitValue() : -1);
        try (var lines = Files.lines(log)) {
            check.same("Every expected contract passes", expected,
                    lines.map(PASS::matcher).filter(matcher -> matcher.matches()).map(matcher -> matcher.group(1)).toList());
        }
    }
}
