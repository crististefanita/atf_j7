package ro.atf.chess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@Tag("web-parity")
@ExtendWith(ExtentAudit.class)
class ChessWebReviewFocusComponents {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void focusPolicyRunsWithoutABrowserOrEmulator(Checks check) throws IOException, InterruptedException {
        var log = output.resolve("review-focus.tap");
        var root = ProjectRoot.locate();
        String node = System.getProperty("chess.web.node", "node");
        var execution = new ProcessBuilder(node, "--test", "--test-reporter=tap",
                root.resolve("web/tests/review-focus.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = execution.waitFor(30, TimeUnit.SECONDS);
        if (!completed) execution.destroyForcibly();
        check.same("The offline Node focus suite completes successfully", 0, completed ? execution.exitValue() : -1);
        try (var lines = Files.lines(log)) {
            check.same("Every expected focus contract passes", List.of(
                    "History navigation stays within recorded moves", "Graph and history preserve their own entry targets",
                    "Explanation enters from Back and returns to its opener", "A toolbar command keeps priority before neighboring controls",
                    "Revealing focus scrolls only the necessary distance"),
                    lines.map(PASS::matcher).filter(matcher -> matcher.matches()).map(matcher -> matcher.group(1)).toList());
        }
    }
}
