package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
class ChessWebOpeningStudyTest {
    @TempDir Path output;

    @Test void openingControllerPreservesLessonAndReplySemanticsWithoutABrowser(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path tapFile = output.resolve("web-opening-study.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/study-openings.test.mjs").toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(tapFile.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The opening controller checks complete", true, finished);
        String tap = Files.readString(tapFile, StandardCharsets.UTF_8);
        check.same("The mocked transport and clock checks pass", 0, process.exitValue());
        var pattern = Pattern.compile("^ok [0-9]+ - (.+)$");
        var passed = tap.lines().map(pattern::matcher).filter(java.util.regex.Matcher::matches)
                .map(match -> match.group(1)).toList();
        check.same("Lesson, caption focus and board contracts have evidence", List.of(
                "A learner move schedules exactly one native book reply with its saved position",
                "History review and route changes cancel delayed replies without moving the visible board",
                "An invalid opening checkpoint returns to the catalog without replaying a move",
                "Only the taught move can be played by clicking a revealed destination directly",
                "Lesson controls follow native boundaries, pause, review and completion",
                "A decisive comparison reaches the exact verified branch and ply",
                "EN long caption opens in full and returns focus without duplicate announcements",
                "RO long caption opens in full and returns focus without duplicate announcements",
                "The board and leading evaluation meter share one row before and after enabling analysis"), passed);
    }
}
