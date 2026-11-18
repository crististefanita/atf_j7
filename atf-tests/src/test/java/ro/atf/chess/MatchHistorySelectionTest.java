package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchHistorySelectionTest {
    @Test
    @EnabledIfSystemProperty(named = "chess.study.bundle", matches = ".+")
    void packagedWorkerMatchesTheJvmAndThePublishedWireFormat(Checks check) throws IOException, InterruptedException {
        var log = Files.createTempFile("match-history-worker-", ".log");
        try {
            var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                    ProjectRoot.locate().resolve("web/tests/match-history-worker.mjs").toString(), System.getProperty("chess.study.bundle"))
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean done = process.waitFor(30, TimeUnit.SECONDS);
            if (!done) process.destroyForcibly();
            check.same("The packaged cursor worker completes", true, done);
            check.same("The packaged worker preserves the selected move and boundary: " + log, 0, process.exitValue());
            check.same("All eleven ordinary game histories execute in the real worker", List.of("empty", "single-start", "single-move", "first",
                    "black-middle", "white-middle", "white-last", "branched-first", "black-last", "capture", "en-passant"),
                    Files.readAllLines(log).stream().filter(line -> line.startsWith("PASS ")).map(line -> line.substring(5)).toList());
        } finally { Files.deleteIfExists(log); }
    }

    @Test void sharedFixturesDescribeOnlyTheMoveProducingTheSelectedPosition(Checks check) throws IOException {
        var cases = new ObjectMapper().readTree(ProjectRoot.locate().resolve("atf-tests/src/test/resources/match-history-cases.json").toFile());
        check.same("The fixture covers both colors, boundaries and special moves", 14, cases.size());
        for (var example : cases) {
            var match = example.has("fen") ? ChessMatch.fromPosition(ChessPosition.fromFen(example.get("fen").asText())) : ChessMatch.start();
            for (var move : example.get("moves")) match = match.play(ChessPosition.Move.fromUci(move.asText()));
            var selection = MatchHistorySelection.at(match, example.get("ply").asInt());
            check.same(example.get("id").asText() + " · compact selected move", example.get("compact").asText(), selection.compact());
            check.same(example.get("id").asText() + " · boundary", example.get("boundary").asText(), selection.boundary().name());
            check.same(example.get("id").asText() + " · selected move", match.moveAt(selection.ply()), selection.move());
        }
    }

    @Test void rejectsPositionsOutsideTheLineAndRefreshesBoundariesOnGrowthAndBranching(Checks check) {
        var one = ChessMatch.restore("1|e2e4");
        var longer = one.play(ChessPosition.Move.fromUci("e7e5"));
        check.rejects("Cannot browse before the initial position", IllegalArgumentException.class, () -> MatchHistorySelection.at(one, -1));
        check.rejects("Cannot browse beyond the last saved move", IllegalArgumentException.class, () -> MatchHistorySelection.at(one, 2));
        check.same("A new reply changes the same selected index from ONLY to FIRST", List.of("ONLY", "FIRST"),
                List.of(MatchHistorySelection.at(one, 1).boundary().name(), MatchHistorySelection.at(longer, 1).boundary().name()));
        var other = ChessMatch.restore("1|d2d4");
        check.same("A new line at the same index replaces the selected move", "1. d2–d4", MatchHistorySelection.at(other, 1).compact());
    }

    @Test void webAdapterObeysTheSharedFixturesWithMockedControls(Checks check) throws IOException, InterruptedException {
        var log = Files.createTempFile("match-history-", ".tap");
        try {
            var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                    ProjectRoot.locate().resolve("web/tests/match-history.test.mjs").toString(),
                    ProjectRoot.locate().resolve("web/tests/line-navigation.test.mjs").toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean done = process.waitFor(30, TimeUnit.SECONDS);
            if (!done) process.destroyForcibly();
            check.same("Web history components finish", true, done);
            check.same("Web history components pass", "exit 0", process.exitValue() == 0 ? "exit 0" : Files.readString(log));
            var passed = Files.readAllLines(log).stream().filter(line -> line.startsWith("ok "))
                    .map(line -> line.replaceFirst("^ok \\d+ - ", "")).toList();
            check.same("Every navigation component executes", List.of("Shared game history cases keep the selected move and boundary in both wire formats",
                    "Only the current move is visible and boundary updates survive growth, stepping and branching",
                    "Invalid and stale cursors cannot render an unrelated move",
                    "Game navigation reserves space for one move and keeps continuation outside the arrow row",
                    "Line cursors distinguish start, first, intermediate, last and a single move",
                    "Shared position rendering preserves contextual labels, branch state and unchanged announcements").stream().sorted().toList(),
                    passed.stream().sorted().toList());
        } finally { Files.deleteIfExists(log); }
    }
}
