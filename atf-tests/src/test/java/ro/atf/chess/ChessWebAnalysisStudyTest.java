package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebAnalysisStudyTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void analysisStudyUsesMockWorkersWithoutABrowser(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path log = output.resolve("study-analysis.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/analysis-study.test.mjs").toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The manual study mock-worker scenarios complete", true, finished);
        check.same("All mocked responses and localized scores pass", 0, process.exitValue());
        var passed = Files.readString(log).lines().map(PASS::matcher).filter(Matcher::matches).map(result -> result.group(1)).toList();
        check.same("The script covers session recovery, localized feedback and Analysis board controls", List.of(
                "Mounted analysis restores its bookmark and requests one evaluation for each position",
                "Late evaluation after a seek or route exit cannot replace the chosen board",
                "Analysis retains a playable session when storage is blocked",
                "Match analysis restores its own line after review without touching the standalone bookmark",
                "Contextual analysis returns once to the exact active or archived game and restores focus",
                "Leaving contextual analysis through another route discards the old game origin",
                "Reported analysis score uses the same side, mate and decimal policy as Android",
                "A revealed analysis hint names its moves and opening for screen readers",
                "Analysis places evaluation to the left and all history controls in APK order",
                "Selecting an offscreen analysis move scrolls only the strip in either direction",
                "Analysis options use the shared anchored menu and preserve the board on dismissal in RO and EN"), passed);
    }
}
