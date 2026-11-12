package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import atf.testkit.scope.TestRunScope;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import ro.atf.chess.release.ReleaseEvidence;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ExtentGovernanceTest {
    @TempDir Path folder;

    @Test void publishingAComparisonDoesNotEvaluateItTwice(Checks check) {
        var comparisons = new AtomicInteger();
        Object observed = "observed";
        Object expected = new Object() {
            @Override public boolean equals(Object value) {
                comparisons.incrementAndGet();
                return value == observed;
            }

            @Override public int hashCode() { return observed.hashCode(); }
            @Override public String toString() { return "expected"; }
        };
        check.same("The observed value passes the comparison", expected, observed);
        check.same("JUnit evaluates equality once before Extent records it", 1, comparisons.get());
    }

    @Test void extentReflectsRealJUnitOutcomesAndKeepsTheAssertionRows(Checks check) throws IOException {
        var path = folder.resolve("audit-probe.html");
        var summary = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(AuditProbe.class))
                .configurationParameter("chess.extent", path.toString()).build(), summary);
        check.same("Two tests succeed, including a cleanup-only comparison", 2L, summary.getSummary().getTestsSucceededCount());
        check.same("Five tests fail, including a caught cleanup comparison", 5L, summary.getSummary().getTestsFailedCount());
        check.same("A runtime assumption is aborted", 1L, summary.getSummary().getTestsAbortedCount());

        var report = Jsoup.parse(path, "UTF-8");
        check.same("The successful scenario is marked Pass", "Pass", status(report, "recordsValues"));
        for (String method : new String[]{"setupFails", "teardownFails", "comparisonFails", "noComparisons", "swallowedInCleanup"}) {
            check.same(method + " is marked Fail in Extent", "Fail", status(report, method));
        }
        check.same("The aborted scenario is marked Skip", "Skip", status(report, "aborted"));
        var body = phase(report, "recordsValues", "TEST BODY");
        var evidence = body.selectFirst(".atf-ui-assertions");
        check.same("The rendered evidence is inside a collapsible node", true, evidence.closest(".collapse") != null);
        check.same("The report retains expected and actual values", "Probe comparison 7 7 ✅", evidence.selectFirst("tbody tr").text());
        check.same("A failed assertion publishes expected and observed exception types", true,
                scenario(report, "comparisonFails").text().contains("Probe rejection java.lang.IllegalStateException No exception"));
        check.same("Setup evidence is attached to setup", true, phase(report, "recordsValues", "SETUP").text().contains("Probe setup"));
        check.same("Cleanup evidence is attached to teardown", true, phase(report, "recordsValues", "TEARDOWN").text().contains("Probe cleanup"));
        check.same("Setup failure belongs to the setup phase", "Fail", phase(report, "setupFails", "SETUP").selectFirst(".card-header .log").text());
        check.same("Setup failure retains its diagnostic", true, scenario(report, "setupFails").text().contains("Probe setup error"));
        check.same("Teardown failure belongs to the cleanup phase", "Fail",
                phase(report, "teardownFails", "TEARDOWN").selectFirst(".card-header .log").text());
        check.same("Teardown failure preserves the diagnostic and earlier comparison", true,
                scenario(report, "teardownFails").text().contains("Probe teardown error")
                        && phase(report, "teardownFails", "TEST BODY").text().contains("Before teardown"));
        check.same("The failed cleanup comparison survives the caught exception", true,
                phase(report, "swallowedInCleanup", "TEARDOWN").text().contains("Probe caught comparison"));
        check.same("A comparison performed in cleanup is sufficient for an audited success", "Pass", status(report, "checksOnlyDuringCleanup"));
        check.same("Direct selection is identifiable in the report", true, report.text().contains("Direct JUnit selection"));
    }

    @Test void suiteSelectionUsesTheSameAuditAndPublishesTheEffectiveSettings(Checks check) throws IOException {
        Path path = folder.resolve("suite-probe.html");
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectMethod(AuditProbe.class, "recordsValues", Checks.class))
                .configurationParameter("chess.extent", path.toString())
                .configurationParameter(TestRunScope.CONFIGURATION_PARAMETER, "suite")
                .configurationParameter("chess.suite", "Chess audit fixture").build());
        var report = Jsoup.parse(path, "UTF-8");
        check.same("The suite name is visible", true, report.text().contains("Chess audit fixture"));
        var table = phase(report, "recordsValues", "TEST BODY").selectFirst(".atf-ui-assertions");
        check.same("Suite and direct entry use the same recorded comparison", "Probe comparison 7 7 ✅", table.selectFirst("tbody tr").text());
        String effectiveLimit = table.attr("data-atf-preview-limit");
        check.same("The rendered limit is present in report metadata", true,
                !effectiveLimit.isBlank() && report.select("tr").stream().anyMatch(row -> row.select("td").eachText()
                        .equals(java.util.List.of("Assertion preview", effectiveLimit + " characters"))));
    }

    @Test void nativeMenuEvidenceBindsEachAssertionAndCaptureToTheSameExecution(Checks check) throws Exception {
        Path receipt = menuReceipt(Files.createDirectory(folder.resolve("complete")));
        AndroidSanityPublication.publishMatchSlots(receipt);
        var report = Jsoup.parse(receipt.resolveSibling("android-match-slots.html"), "UTF-8");
        check.same("The Android scenario reports Pass", "Pass", menuScenario(report).selectFirst(".card-header .log").text());
        var tables = report.select(".atf-ui-assertions");
        check.same("Both checkpoint assertions have a collapsible table", 2L,
                tables.stream().filter(table -> table.closest(".collapse") != null).count());
        verifyCapture(check, report, "WHEN the player scrolls to the last saved game", receipt.resolveSibling("saved-games.png"),
                "The last game shows its title, moves and local time with Back visible");
        verifyCapture(check, report, "THEN selecting it restores the recorded position", receipt.resolveSibling("match-slots.png"),
                "Selecting a saved row restores the recorded position");
        check.same("The complete receipt hash is visible", true, report.text().contains(ReleaseEvidence.digest(receipt)));
    }

    @Test void nativeMenuWithSubstitutedCapturePublishesFailureAndPreservesOtherEvidence(Checks check) throws Exception {
        Path receipt = menuReceipt(Files.createDirectory(folder.resolve("substituted")));
        Path screenshot = receipt.resolveSibling("saved-games.png");
        Files.write(screenshot, Files.readAllBytes(receipt.resolveSibling("match-slots.png")));
        check.rejects("A valid PNG from another checkpoint cannot qualify", AssertionError.class,
                () -> AndroidSanityPublication.publishMatchSlots(receipt));
        var report = Jsoup.parse(receipt.resolveSibling("android-match-slots.html"), "UTF-8");
        check.same("The substituted capture fails the scenario", "Fail", menuScenario(report).selectFirst(".card-header .log").text());
        check.same("The report renders the failure reason and the surviving comparison", true,
                report.text().contains("Capture SHA-256 mismatch: saved-games.png")
                        && report.text().contains("Selecting a saved row restores the recorded position"));
        check.same("Only the capture matched to its checkpoint remains", 1, report.select("a[data-atf-screenshot=base64]").size());
    }

    @Test void twoDifferentScreensCannotQualifyWithAnIdenticalCapture(Checks check) throws Exception {
        Path receipt = menuReceipt(Files.createDirectory(folder.resolve("repeated")));
        Path list = receipt.resolveSibling("saved-games.png");
        Path match = receipt.resolveSibling("match-slots.png");
        String originalMatch = ReleaseEvidence.digest(match);
        Files.write(match, Files.readAllBytes(list));
        Path execution = receipt.resolveSibling("execution.json");
        Files.writeString(execution, Files.readString(execution).replace(originalMatch, ReleaseEvidence.digest(list)));
        check.rejects("The same rendered screen cannot satisfy both checkpoints", AssertionError.class,
                () -> AndroidSanityPublication.publishMatchSlots(receipt));
        var report = Jsoup.parse(receipt.resolveSibling("android-match-slots.html"), "UTF-8");
        check.same("Repeated evidence remains a visible failure", true,
                report.text().contains("Capture repeated across checkpoints: match-slots.png"));
    }

    private static void verifyCapture(Checks check, Document report, String action, Path png, String assertion) throws IOException {
        var step = namedNode(report, "ANDROID match-slots — " + action);
        var link = step.selectFirst("a[data-atf-screenshot=base64]");
        check.same("Embedded bytes for " + png.getFileName(), "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(png)),
                link == null ? "" : link.attr("href"));
        check.same("The checkpoint retains the capture hash and its own comparison", true,
                step.text().contains(ReleaseEvidence.digest(png)) && step.select(".atf-ui-assertions").text().contains(assertion));
    }

    private static Element menuScenario(Document report) {
        return namedNode(report, "Find and resume a saved game");
    }

    private static Element namedNode(Document report, String label) {
        return report.select("a.node").stream().filter(node -> node.text().equals(label)).findFirst().orElseThrow().closest(".card");
    }

    private static Path menuReceipt(Path directory) throws IOException {
        Path receipt = Files.writeString(directory.resolve("match-slots.txt"), """
                INSTRUMENTATION_RESULT: pid=1234
                INSTRUMENTATION_RESULT: phase=match-slots
                INSTRUMENTATION_RESULT: result=PASS
                INSTRUMENTATION_RESULT: check_01=The last game shows its title, moves and local time with Back visible\ttrue\ttrue\ttrue
                INSTRUMENTATION_RESULT: check_02=Selecting a saved row restores the recorded position\ttrue\ttrue\ttrue
                INSTRUMENTATION_CODE: -1
                """);
        Path list = directory.resolve("saved-games.png");
        Path match = directory.resolve("match-slots.png");
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", list.toFile());
        image.setRGB(0, 0, 0xffffff);
        ImageIO.write(image, "png", match.toFile());
        Files.writeString(directory.resolve("execution.json"), """
                {"status":"PASS","scope":"match-slots","variant":"release","api":"28","device":"fixture",
                 "sourceCommit":"%s","apkSha256":"%s","receiptSha256":"%s","assertions":2,
                 "captureSha256":{"saved-games.png":"%s","match-slots.png":"%s"}}
                """.formatted("a".repeat(40), "b".repeat(64), ReleaseEvidence.digest(receipt),
                ReleaseEvidence.digest(list), ReleaseEvidence.digest(match)));
        return receipt;
    }

    private static String status(Document report, String name) {
        return scenario(report, name).selectFirst(".card-header .log").text();
    }

    private static Element scenario(Document report, String name) {
        return report.select("a.node").stream().filter(node -> node.text().startsWith(name + "("))
                .findFirst().orElseThrow().closest(".card");
    }

    private static Element phase(Document report, String method, String label) {
        return scenario(report, method).select("a.node").stream().filter(node -> node.text().equals(label))
                .findFirst().orElseThrow().closest(".card");
    }

    @ExtendWith(ExtentAudit.class)
    static class AuditProbe {
        @BeforeEach void before(TestInfo test, Checks check) {
            if (test.getTestMethod().orElseThrow().getName().equals("setupFails")) throw new IllegalStateException("Probe setup error");
            if (test.getTestMethod().orElseThrow().getName().equals("recordsValues")) check.same("Probe setup", 3, 3);
        }

        @AfterEach void after(TestInfo test, Checks check) {
            switch (test.getTestMethod().orElseThrow().getName()) {
                case "teardownFails" -> throw new IllegalStateException("Probe teardown error");
                case "recordsValues", "checksOnlyDuringCleanup" -> check.same("Probe cleanup", 5, 5);
                case "swallowedInCleanup" -> {
                    try {
                        check.same("Probe caught comparison", 1, 2);
                    } catch (AssertionError expected) {
                        // This deliberate catch proves that the audit still fails the lifecycle.
                    }
                }
            }
        }

        @Test void recordsValues(Checks check) { check.same("Probe comparison", 7, 7); }
        @Test void setupFails(Checks check) { check.same("Unexpected execution", true, true); }
        @Test void teardownFails(Checks check) { check.same("Before teardown", 3, 3); }
        @Test void comparisonFails(Checks check) { check.rejects("Probe rejection", IllegalStateException.class, () -> { }); }
        @Test void noComparisons() { }
        @Test void checksOnlyDuringCleanup() { }
        @Test void swallowedInCleanup() { }
        @Test void aborted() { assumeTrue(false, "Probe unavailable"); }
    }
}
