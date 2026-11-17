package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebStudyShellTest {
    @TempDir Path output;

    @Test void sharedBoardWorksWithMockControls(Checks check) throws IOException, InterruptedException {
        verify(check, "study-board.test.mjs", List.of("--test", "--test-reporter=tap"), List.of(), "ok ", List.of(
                "Each study route has an enabled home entry and a distinct panel",
                "Study links, Back and Forward reopen the route represented by the address",
                "Home is the default route and direct game, study and review links remain distinct",
                "Browser Back and Forward switch between AI and Home once per changed route",
                "Study accepts native square order and placement without moving any piece",
                "Study draws the native origin and destination cue in both board orientations",
                "Study descriptions distinguish the hint, destination and legal move with native RO and EN labels",
                "Hint overlay survives board rotation and is removed when the solution is hidden",
                "Keyboard follows displayed squares without wrapping rows or activating a locked position",
                "Puzzle and match boards share the viewport budget and keep controls in view",
                "Compact AI board spends the available width only on the rail, gap and square"));
    }

    @Test
    @EnabledIfSystemProperty(named = "chess.study.bundle", matches = ".+")
    void packagedWorkerSharesTheContextualAnalysisCasesWithTheJvm(Checks check) throws IOException, InterruptedException {
        verify(check, "contextual-analysis-worker.mjs", List.of(), List.of(System.getProperty("chess.study.bundle")), "PASS ", List.of(
                "active-ai-history", "archived-resignation-history", "archived-terminal-position"));
    }

    @Test
    @EnabledIfSystemProperty(named = "chess.study.bundle", matches = ".+")
    void packagedWorkerKeepsTheThreeStudyFlowsIndependent(Checks check) throws IOException, InterruptedException {
        String site = System.getProperty("chess.study.bundle");
        verify(check, "study-worker.mjs", List.of(), List.of(site), "PASS ", List.of(
                "six puzzle chapters and portable progress snapshot",
                "opening catalog identity and translations",
                "analysis position, history and restoration",
                "study review retains the selected ply and leaves the ordinary review independent",
                "study continuation preserves orientation and a full game ledger"));
        verify(check, "study-quests-worker.test.mjs", List.of("--test", "--test-reporter=tap"), List.of(), "ok ", List.of(
                "Material progress from the published Web package retains solved boards and the pending exercise",
                "Built Web engine accepts APK puzzle stages, restores a pending defense and awards a solution once",
                "Built Web engine opens all material chapters with the authored replay and no solved credit",
                "Built Web engine solves one real puzzle in each of the six native chapters",
                "Mate chapters restore their own cursor and solved credit from one saved snapshot"));
        verify(check, "study-openings-worker.mjs", List.of(), List.of(site, "--analysis"), "Opening study worker: ", List.of(
                "bilingual catalog, legal/off-line outcomes, hints, pause, resume, review and both seats passed."));
        verify(check, "study-analysis-worker.mjs", List.of(), List.of(site), "Manual analysis worker: ", List.of(
                "legal play, branching, orientation, theory, tactical replay, resume, terminal and saved continuation passed."));
    }

    private void verify(Checks check, String script, List<String> nodeArgs, List<String> args, String prefix, List<String> expected)
            throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path log = output.resolve(script + ".log");
        var command = new ArrayList<String>();
        command.add(System.getProperty("chess.web.node", "node"));
        command.addAll(nodeArgs);
        command.add(root.resolve("web/tests").resolve(script).toString());
        command.addAll(args);
        var builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        String site = System.getProperty("chess.study.bundle");
        if (site != null) builder.environment().put("J7_STUDY_WEB_SITE", site);
        var process = builder.start();
        boolean finished = process.waitFor(50, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same(script + " completes", true, finished);
        String diagnostic = Files.readString(log);
        check.same(script + " exits successfully", "exit 0", process.exitValue() == 0 ? "exit 0" : diagnostic);
        List<String> actual = diagnostic.lines().filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).replaceFirst("^\\d+ - ", "")).toList();
        check.same(script + " records every required observation", expected, actual);
    }
}
