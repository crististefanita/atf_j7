package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ExtentGovernanceTest {
    @TempDir Path folder;

    @Test void extentReflectsRealJUnitOutcomesAndKeepsTheAssertionRows(Checks check) throws IOException {
        var path = folder.resolve("audit-probe.html");
        var summary = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(AuditProbe.class))
                .configurationParameter("chess.extent", path.toString()).build(), summary);
        check.same("One test succeeds", 1L, summary.getSummary().getTestsSucceededCount());
        check.same("Four tests fail, including setup and teardown", 4L, summary.getSummary().getTestsFailedCount());
        check.same("A runtime assumption is aborted", 1L, summary.getSummary().getTestsAbortedCount());

        var report = Jsoup.parse(path, "UTF-8");
        check.same("The successful scenario is marked Pass", "Pass", status(report, "recordsValues"));
        for (String method : new String[]{"setupFails", "teardownFails", "comparisonFails", "noComparisons"}) {
            check.same(method + " is marked Fail in Extent", "Fail", status(report, method));
        }
        check.same("The aborted scenario is marked Skip", "Skip", status(report, "aborted"));
        var body = scenario(report, "recordsValues");
        var evidence = body.selectFirst(".atf-ui-assertions");
        check.same("The rendered evidence is inside a collapsible node", true, evidence.closest(".collapse") != null);
        check.same("The report retains expected and actual values", "Probe comparison 7 7 ✅", evidence.selectFirst("tbody tr").text());
        check.same("A failed assertion publishes expected and observed exception types", true,
                scenario(report, "comparisonFails").text().contains("Probe rejection java.lang.IllegalStateException No exception"));
        check.same("Setup failure retains its diagnostic", true, scenario(report, "setupFails").text().contains("Probe setup error"));
        check.same("Teardown failure retains earlier comparisons and the later diagnostic", true,
                scenario(report, "teardownFails").text().contains("Probe teardown error")
                        && scenario(report, "teardownFails").text().contains("Before teardown"));
    }

    private static String status(Document report, String name) {
        return scenario(report, name).selectFirst(".card-header .log").text();
    }

    private static Element scenario(Document report, String name) {
        return report.select("a.node").stream().filter(node -> node.text().startsWith(name + "("))
                .findFirst().orElseThrow().closest(".card");
    }

    @ExtendWith(ExtentAudit.class)
    static class AuditProbe {
        @BeforeEach void before(TestInfo test) {
            if (test.getTestMethod().orElseThrow().getName().equals("setupFails")) throw new IllegalStateException("Probe setup error");
        }

        @AfterEach void after(TestInfo test) {
            if (test.getTestMethod().orElseThrow().getName().equals("teardownFails")) throw new IllegalStateException("Probe teardown error");
        }

        @Test void recordsValues(Checks check) { check.same("Probe comparison", 7, 7); }
        @Test void setupFails(Checks check) { check.same("Unexpected execution", true, true); }
        @Test void teardownFails(Checks check) { check.same("Before teardown", 3, 3); }
        @Test void comparisonFails(Checks check) { check.rejects("Probe rejection", IllegalStateException.class, () -> { }); }
        @Test void noComparisons() { }
        @Test void aborted() { assumeTrue(false, "Probe unavailable"); }
    }
}
