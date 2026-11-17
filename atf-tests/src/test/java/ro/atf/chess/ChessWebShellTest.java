package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebShellTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test
    @EnabledIfSystemProperty(named = "chess.study.bundle", matches = ".+")
    void packagedWorkerAppliesTheAndroidMatchActions(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path evidence = output.resolve("match-menu.log");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                root.resolve("web/tests/match-menu-worker.mjs").toString(), System.getProperty("chess.study.bundle"))
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly().waitFor();
        check.same("The real worker responds to the match menu", true, finished);
        String transcript = Files.readString(evidence);
        check.same("The worker completed the match cases", "ok", process.exitValue() == 0 ? "ok" : transcript);
        check.same("History, refusal and ending checkpoints were all checked", List.of(
                "PASS History and stale decisions are guarded and a resignation is archived once",
                "PASS Draw refusal keeps the saved game and agreement archives a draw"),
                transcript.lines().filter(line -> line.startsWith("PASS ")).toList());
    }

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
                "Inline analysis starts unchecked and shares the board frame geometry",
                "Home keeps imported review recovery below Study and outside the title header",
                "Inline analysis drops older frames and keeps hints on a board flip only",
                "Inline analysis projects the score, lamp and playable theory for the displayed move",
                "A hinted destination applies only the exact legal variation and respects selection",
                "Web home preserves Android groups, action order and RO and EN labels",
                "Only active saved games show the home action",
                "AI color choices preserve Android order and RO and EN labels",
                "The match options expose native board actions in RO and EN",
                "The automatic AI control has the native RO and EN labels",
                "A new board revision replaces the scheduled AI reply exactly once",
                "Disabling automation aborts a running calculation and discards its result",
                "Profile persistence respects the native validator and write failures",
                "Board piece renderer reuses the same Staunton SVG on a move and clears it on capture",
                "Home name, settings and AI profile share the existing dialog and stored profile",
                "Language reload retains Home with a saved review and preserves the AI profile route"), passed);
    }
}
