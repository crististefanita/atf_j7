package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebBoardSizingTest {
    @TempDir Path output;

    @Test void sharedCssMatchesIndependentAnalysisFixtures(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var log = output.resolve("board-layout.json");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                root.resolve("web/tests/board-layout-probe.mjs").toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The CSS model probe completes", true, finished);
        check.same("The strict CSS arithmetic probe succeeds", 0, process.exitValue());
        var json = new ObjectMapper();
        var cases = json.readTree(root.resolve("web/tests/fixtures/analysis-board-layout.json").toFile());
        var actual = json.readTree(log.toFile());
        check.same("All ten independent size fixtures execute", 10, actual.size());
        check.same("Fixture inventory is unchanged", cases.size(), actual.size());
        for (int i = 0; i < cases.size(); i++) {
            var expected = cases.get(i);
            var row = actual.get(i);
            String id = expected.path("id").asText();
            check.same("Fixture identity", id, row.path("id").asText());
            for (String field : List.of("limit", "square")) {
                check.same(id + " · " + field + " in nominal CSS pixels",
                        String.format(Locale.ROOT, "%.3f", expected.path(field).asDouble()), row.path(field).asText());
            }
        }
    }

    @Test void arithmeticProbeRejectsUnknownInputs(Checks check) throws Exception {
        var log = output.resolve("board-layout.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                ProjectRoot.locate().resolve("web/tests/board-layout.test.mjs").toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("Positive and negative arithmetic components complete", true, finished);
        check.same("Positive and negative arithmetic components pass", "exit 0", process.exitValue() == 0 ? "exit 0" : Files.readString(log));
        check.same("Both required components execute", List.of(
                "Analysis reference sizing matches independent screen, rail and font fixtures",
                "The arithmetic harness rejects unresolved, cyclic and unsupported CSS instead of guessing"),
                Files.readAllLines(log).stream().filter(line -> line.startsWith("ok ")).map(line -> line.replaceFirst("^ok \\d+ - ", "")).toList());
    }
}
