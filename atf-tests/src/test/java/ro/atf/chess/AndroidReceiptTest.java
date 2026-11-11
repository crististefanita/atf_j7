package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidReceiptTest {
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

    private AndroidSanityPublication.PhaseEvidence read(String file, String contents) throws IOException {
        return AndroidSanityPublication.readPhase(Files.writeString(folder.resolve(file + ".txt"), contents), "openings", 1);
    }
}
