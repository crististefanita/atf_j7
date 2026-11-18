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
class ChessWebQuestStudyTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void puzzleUiUsesNativeChaptersAndPersistsScriptedReplies(Checks check) throws IOException, InterruptedException {
        Path root = ProjectRoot.locate();
        Path log = output.resolve("web-quests.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/study-quests.test.mjs").toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("Puzzle adapter tests finish without an emulator", true, finished);
        String transcript = Files.readString(log);
        check.same("Puzzle adapter tests succeed: " + log, 0, process.exitValue());
        var passed = transcript.lines().map(PASS::matcher).filter(java.util.regex.Matcher::matches)
                .map(match -> match.group(1)).toList();
        check.same("Puzzle adapter verifies all native contracts with mocks", List.of(
                "Web puzzle menu has the six APK chapters in native order in RO and EN",
                "Only legal destinations select a move and promotion choices are preserved",
                "Paired scripted reply commits once from the persisted quest and ply",
                "Leaving a pending defense cancels stale callbacks and reopening resumes the saved reply",
                "A failed write keeps the prior board and an externally changed puzzle blocks stale commands",
                "Home summary reads saved totals without mounting the quest board",
                "Puzzle headers retain localized contextual Back before the title in both languages",
                "Wrong material check stays in the current puzzle and keeps the Web controls usable",
                "Puzzle hints announce the APK turn, piece, destination and promotion once in RO and EN",
                "Puzzle options match the APK and Escape returns focus to the chapters",
                "Puzzle points, streak and guidance match the native states and announce a newly reached goal once",
                "Puzzle replay keeps its actions outside the scrollable text and retains eight square rows on compact screens",
                "A puzzle lesson captures the source when replay opens inside the same dialog"), passed);
    }
}
