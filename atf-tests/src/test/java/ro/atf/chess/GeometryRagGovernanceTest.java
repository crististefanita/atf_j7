package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class GeometryRagGovernanceTest {
    private static final String GUIDE = "docs/testing/README.md";
    private static final List<String> PATHS = List.of(
            "android/app/src/main/java/ro/atf/chess/android/BoardPanel.java",
            "chess-core/src/main/java/ro/atf/chess/HintArrow.java",
            "web/src/main/web/hint-arrow.mjs", "web/src/main/web/study-board.mjs",
            "web/src/main/web/review-workspace.mjs", "web/src/main/web/match-inline-analysis.mjs");
    private static final List<String> CHECKS = List.of("HintArrowTest", "BoardPanelLayoutTest", "MatchAnalysisParityTest", "ChessWebBoardSizingTest");

    @Test void everyGeometryAdapterCitesCommonComponentsAndBothRenderedBoundaries(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        for (String path : PATHS) {
            var inventory = ReleaseRag.inspect(root, List.of(path));
            check.same(path + " exists in the current source", true, Files.isRegularFile(root.resolve(path)));
            check.same(path + " has a reviewed advisory route", List.of(), inventory.needsReview());
            var focused = inventory.citations().stream().filter(c -> CHECKS.stream().allMatch(c.check()::contains)).toList();
            check.same(path + " has one focused geometry contract", 1, focused.size());
            var citation = focused.getFirst();
            check.same("Citation owns the shared testing guide", GUIDE, citation.path());
            check.same("Citation names its actual heading and line", citation.heading(), Files.readAllLines(root.resolve(GUIDE)).get(citation.line() - 1));
            check.same("Rendering is explicit on the Android and MCP boundaries", true,
                    citation.check().contains("Android") && citation.check().contains("MCP") && citation.check().contains("130%"));
        }
        for (String test : CHECKS) check.same(test + " has executable component coverage", true,
                Files.isRegularFile(root.resolve("atf-tests/src/test/java/ro/atf/chess/" + test + ".java")));
    }

    @Test void sharedRoutesDeduplicateWithoutClaimingACompletedTestRun(Checks check) throws IOException {
        var inventory = ReleaseRag.inspect(ProjectRoot.locate(), List.of(PATHS.get(0), PATHS.get(0).replace('/', '\\'), PATHS.get(3)));
        check.same("Duplicate Windows and slash paths are one changed file", List.of(PATHS.get(0), PATHS.get(3)), inventory.covered());
        check.same("One shared geometry contract is cited once", 1L,
                inventory.citations().stream().filter(c -> CHECKS.stream().allMatch(c.check()::contains)).count());
        check.same("A geometry change without Markdown does not request a documentation-pair check", false, inventory.documentationPairRequired());
    }

    @Test void missingGuidanceCannotBorrowAnUnrelatedSection(@TempDir Path root, Checks check) throws IOException {
        Files.createDirectories(root.resolve("docs/testing")); Files.writeString(root.resolve(GUIDE), "# Tests\n\n## Unrelated section\n");
        check.rejects("An absent geometry contract blocks its citation instead of choosing another paragraph", IOException.class,
                () -> ReleaseRag.inspect(root, List.of(PATHS.get(3))));
    }

    @Test void aSimilarUnregisteredFilenameKeepsItsManualReview(Checks check) throws IOException {
        String path = PATHS.get(3) + ".backup"; var inventory = ReleaseRag.inspect(ProjectRoot.locate(), List.of(path));
        check.same("A filename suffix cannot inherit the known adapter's checks", List.of(path), inventory.needsReview());
        check.same("Unknown content does not invent executed evidence", List.of(), inventory.citations());
    }
}
