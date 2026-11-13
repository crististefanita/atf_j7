package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidReceiptTest {
    private static final String TACTICS_RESTART = "A real process restart keeps the tactics chapter position and its two awards";
    private static final String TRAINING_RESTART = "Training reopens its second unsolved puzzle after process restart";
    private static final String PASSING = """
            INSTRUMENTATION_RESULT: check_01=The board is visible	true	true	true
            INSTRUMENTATION_RESULT: phase=openings
            INSTRUMENTATION_RESULT: pid=123
            INSTRUMENTATION_RESULT: result=PASS
            INSTRUMENTATION_CODE: -1
            """;

    @TempDir Path folder;

    @Test void reportsOnlyACompleteAndUnambiguousNativePass(Checks check) throws IOException {
        var passing = read("passing", PASSING);
        check.same("A complete native receipt passes", true, passing.passed());
        check.same("The recorded assertion survives import", "The board is visible", passing.rows().getFirst().name());
        var mixed = read("mixed", PASSING + "INSTRUMENTATION_RESULT: result=FAIL\n");
        check.same("Contradictory native verdicts fail", false, mixed.passed());
        check.same("The diagnostic shows both observed verdicts", true, mixed.rows().getLast().actual().contains("[PASS, FAIL]"));
        check.same("An opening receipt without a valid PID fails", false,
                read("pid", PASSING.replace("pid=123", "pid=0")).passed());
        check.same("An extra instrumentation code fails", false,
                read("code", PASSING + "INSTRUMENTATION_CODE: 0\n").passed());
        check.same("A claimed passing check with unequal values fails", false,
                read("wrong-value", PASSING.replace("\ttrue\ttrue\ttrue", "\ttrue\tfalse\ttrue")).passed());
        var invalid = read("bad-flag", PASSING.replace("\ttrue\ttrue\ttrue", "\ttrue\ttrue\tunknown"));
        check.same("A malformed assertion cannot count towards coverage", false, invalid.passed());
        check.same("The diagnostic separates valid assertions from raw lines", true,
                invalid.rows().getLast().actual().contains("valid checks 0/1 from 1 native rows"));
    }

    @Test void rejectsOpeningPassWhenAnUnrelatedCheckReplacesTheBoardGeometry(Checks check) throws IOException {
        String reply = "Next preserves the board and its corner squares when Resume lesson appears";
        String learner = "Next preserves the board and its corner squares when Lesson hint returns";
        var lines = new StringBuilder(PASSING.substring(PASSING.indexOf("INSTRUMENTATION_RESULT: phase=")));
        for (int id = 1; id <= 24; id++) {
            lines.append("INSTRUMENTATION_RESULT: check_%02d=Lesson step %d\ttrue\ttrue\ttrue%n".formatted(id, id));
        }
        lines.append("INSTRUMENTATION_RESULT: check_25=").append(reply).append("\ttrue\ttrue\ttrue\n");
        lines.append("INSTRUMENTATION_RESULT: check_26=").append(learner).append("\ttrue\ttrue\ttrue\n");
        var valid = AndroidSanityPublication.readOpeningPhase(Files.writeString(folder.resolve("opening-geometry.txt"), lines));
        check.same("Both Next transitions qualify the native opening receipt", true, valid.passed());
        var invalid = AndroidSanityPublication.readOpeningPhase(Files.writeString(folder.resolve("opening-missing-geometry.txt"),
                lines.toString().replace(learner, "An unrelated passing comparison")));
        check.same("The total alone cannot qualify the opening receipt", false, invalid.passed());
        check.same("The diagnostic names the missing Next transition", true, invalid.rows().getLast().actual().contains(learner));
    }

    @Test void requiresTheExactLongGameGraphComparisonAtTheExpectedCount(Checks check) throws IOException {
        String selection = "A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row";
        writePhase(folder, "game-review", 123, 23, List.of(selection));
        Path receipt = folder.resolve("game-review.txt");
        check.same("The long-game graph comparison qualifies a complete review receipt", true,
                AndroidSanityPublication.readGameReviewPhase(receipt).passed());
        writePhase(folder, "game-review", 123, 23, List.of("An unrelated passing comparison"));
        var unrelated = AndroidSanityPublication.readGameReviewPhase(receipt);
        check.same("The original assertion count cannot substitute for long-game selection", false, unrelated.passed());
        check.same("The rejection names the missing long-game graph comparison", true,
                unrelated.rows().getLast().actual().contains(selection));
        writePhase(folder, "game-review", 123, 23, List.of(selection, selection));
        check.same("A repeated long-game comparison also fails", false, AndroidSanityPublication.readGameReviewPhase(receipt).passed());
    }

    @Test void rendersChapterRestartEvidenceAndRejectsAnUnrelatedComparisonAtTheSameCount(Checks check) throws Exception {
        Path valid = boardReceipt(Files.createDirectory(folder.resolve("complete")), TRAINING_RESTART);
        AndroidSanityPublication.publishBoardSanity(valid);
        var resume = restoreStep(valid);
        check.same("The chapter restore step passes with both native checkpoints", "Pass", resume.selectFirst(".card-header .log").text());
        var table = resume.selectFirst(".atf-ui-assertions");
        check.same("Both comparisons are available in the collapsible Extent assertion table", true,
                table.closest(".collapse") != null && table.text().contains(TACTICS_RESTART) && table.text().contains(TRAINING_RESTART));

        Path incomplete = boardReceipt(Files.createDirectory(folder.resolve("wrong-check")), "Unrelated passing comparison");
        check.rejects("A passing line from an unrelated check cannot qualify the restart", AssertionError.class,
                () -> AndroidSanityPublication.publishBoardSanity(incomplete));
        resume = restoreStep(incomplete);
        check.same("The chapter restore step renders Fail despite the correct count", "Fail", resume.selectFirst(".card-header .log").text());
        check.same("The missing Training restart is named and the Tactics comparison is preserved", true,
                resume.text().contains("Missing or duplicate chapter restart checks: [" + TRAINING_RESTART + "]")
                        && resume.text().contains(TACTICS_RESTART));
    }

    private static Path boardReceipt(Path directory, String training) throws IOException {
        writePhase(directory, "prepare", 111, 46, List.of());
        writePhase(directory, "restore", 222, 26, List.of(TACTICS_RESTART, training));
        writePhase(directory, "match-prepare", 333, 3, List.of());
        writePhase(directory, "match-restore", 444, 15, List.of());
        writePhase(directory, "match-settled", 555, 4, List.of());
        var pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        for (String frame : List.of("hint-piece", "hint-solution", "board", "milestone", "mate-two", "menu",
                "mate-three", "tactics-streak", "match-resumed", "match-history")) {
            ImageIO.write(pixel, "png", directory.resolve(frame + ".png").toFile());
        }
        return directory.resolve("prepare.txt");
    }

    private static void writePhase(Path directory, String phase, int pid, int checks, List<String> required) throws IOException {
        var text = new StringBuilder();
        text.append("INSTRUMENTATION_RESULT: phase=").append(phase).append('\n');
        text.append("INSTRUMENTATION_RESULT: pid=").append(pid).append('\n');
        for (int index = 0; index < checks; index++) {
            String name = index < required.size() ? required.get(index) : "Fixture comparison " + (index + 1);
            text.append("INSTRUMENTATION_RESULT: check_%02d=%s\ttrue\ttrue\ttrue%n".formatted(index + 1, name));
        }
        text.append("INSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n");
        Files.writeString(directory.resolve(phase + ".txt"), text);
    }

    private static Element restoreStep(Path receipt) throws IOException {
        var report = Jsoup.parse(receipt.resolveSibling("android-board-sanity.html"), "UTF-8");
        return report.select("a.node").stream().filter(node -> node.text().equals("Resume pending quests and restore tactics, training and mate progress"))
                .findFirst().orElseThrow().closest(".card");
    }

    private AndroidSanityPublication.PhaseEvidence read(String file, String contents) throws IOException {
        return AndroidSanityPublication.readPhase(Files.writeString(folder.resolve(file + ".txt"), contents), "openings", 1);
    }
}
