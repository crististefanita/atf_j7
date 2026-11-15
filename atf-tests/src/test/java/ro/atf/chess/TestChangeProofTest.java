package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import ro.atf.chess.suites.ChessGovernanceSuite;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class TestChangeProofTest {
    @TempDir Path folder;

    @Test void runsOnlyTheSelectedComponentAndPublishesItsAssertions(Checks check) throws IOException {
        String proof = QuestStepTest.class.getName() + "#restoresTheBoardBeforeAndAfterEachDefense";
        Path report = folder.resolve("replacement.html");
        TestChangeProof.verify(Set.of(proof), Set.of(proof), report);
        var html = Files.readString(report);
        check.same("Extent records the selected replay contract", true, html.contains("The completed defense supplies the new highlight"));
        check.same("Other methods in the same class do not run", false, html.contains("A solve in another chapter cannot unlock Next"));
        check.rejects("Existing Extent evidence cannot be reused for a new proof", IOException.class,
                () -> TestChangeProof.verify(Set.of(proof), Set.of(proof), report));
    }

    @Test void rejectsMissingWrongLayerAndRecursiveEvidence(Checks check) {
        Path report = folder.resolve("invalid.html");
        String absent = QuestStepTest.class.getName() + "#doesNotExist";
        String governance = SuiteGovernanceTest.class.getName() + "#everyJvmTestHasExactlyOnePrimarySuiteAndAnExtentAudit";
        String review = TestChangeReview.class.getName() + "#verifiesReviewedChanges";
        check.rejects("A method absent from both test suites cannot prove a change", IOException.class,
                () -> TestChangeProof.verify(Set.of(absent), Set.of(absent), report));
        check.rejects("An Android replacement must actually be in the component suite", IOException.class,
                () -> TestChangeProof.verify(Set.of(governance), Set.of(governance), report));
        check.rejects("The review cannot prove itself recursively", IOException.class,
                () -> TestChangeProof.verify(Set.of(review), Set.of(), report));
        check.same("Invalid selections produce no Extent evidence", false, Files.exists(report));
    }

    @Test void rejectsAmbiguousSkippedAbortedAndFailedMethods(Checks check) {
        var expected = Map.of("ambiguous", "more than one suite test", "skipped", "skipped 1",
                "aborted", "aborted 1", "failed", "failed 1");
        for (String method : expected.keySet().stream().sorted().toList()) {
            Path report = folder.resolve(method + ".html");
            String proof = Fixture.class.getName() + "#" + method;
            var failure = assertThrows(IOException.class, () -> TestChangeProof.verify(Set.of(proof), Set.of(proof),
                    report, FixtureSuite.class, ChessGovernanceSuite.class));
            check.same("Proof gives the diagnostic for the " + method + " method", true,
                    failure.getMessage().contains(expected.get(method)));
            if (Set.of("aborted", "failed").contains(method)) {
                check.same("Extent records the executed " + method + " method", true, Files.isRegularFile(report));
            }
        }
    }

    @Suite
    @SelectClasses(Fixture.class)
    @IncludeEngines("junit-jupiter")
    static class FixtureSuite { }

    @ExtendWith(ExtentAudit.class)
    static class Fixture {
        @Test void ambiguous() { }

        @Test void ambiguous(Checks check) { check.same("A separate overload", true, true); }

        @Disabled("Exercise the skipped proof path")
        @Test void skipped(Checks check) { check.same("A disabled test does not run", true, true); }

        @Test void aborted() { Assumptions.assumeTrue(false, "Replacement environment unavailable"); }

        @Test void failed(Checks check) { check.same("A replacement regression is detected", true, false); }
    }
}
