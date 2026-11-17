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
class ChessWebShellTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void homeAndArtworkFollowAndroidWithoutEmulator(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path log = output.resolve("web-shell.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/home-menu.test.mjs").toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The menu and artwork checks complete", true, finished);
        String tap = Files.readString(log);
        check.same("The offline menu and artwork checks succeed", 0, process.exitValue());
        var passed = tap.lines().map(PASS::matcher).filter(java.util.regex.Matcher::matches)
                .map(match -> match.group(1)).toList();
        check.same("All Android reference and Web adapter contracts execute", List.of(
                "Web home preserves Android groups, action order and RO and EN labels",
                "Only active saved games show the home action",
                "AI color choices preserve Android order and RO and EN labels",
                "Profile persistence respects the native validator and write failures",
                "Board piece renderer reuses the same Staunton SVG on a move and clears it on capture",
                "Home name, settings and AI profile share the existing dialog and stored profile",
                "Language reload retains Home with a saved review and preserves the AI profile route"), passed);
    }
}
