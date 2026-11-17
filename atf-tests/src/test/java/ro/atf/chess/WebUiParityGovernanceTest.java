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
class WebUiParityGovernanceTest {
    private static final String GUIDE = "docs/testing/README.md";
    private static final List<String> MENUS = List.of(
            "web/src/main/web/index.html", "web/src/main/web/home-menu.mjs",
            "web/src/main/web/game-review.mjs", "web/src/main/web/game-review.css",
            "web/src/main/web/review-import.mjs", "web/src/main/web/options-menu.mjs",
            "web/src/main/web/study-analysis.mjs", "web/src/main/web/study-analysis.css");
    private static final List<String> HINTS = List.of(
            "android/app/src/main/java/ro/atf/chess/android/HintPreviewDialog.java",
            "android/app/src/main/java/ro/atf/chess/android/AnalysisReplayDialog.java");

    @Test void changedMenuAndHintAdaptersCiteTheirExecutableContracts(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        for (String path : MENUS) verify(root, path, List.of("ChessWebShellTest", "ChessWebOptionsMenuTest"), check);
        for (String path : HINTS) verify(root, path, List.of("HintReplayPresentationTest", "HintPreviewTest", "GameReviewLayoutTest"), check);
    }

    @Test void anUnknownSurfaceKeepsItsReviewRequirement(Checks check) throws IOException {
        String unknown = "web/src/main/web/new-unregistered-panel.mjs";
        var result = ReleaseRag.inspect(ProjectRoot.locate(), List.of(MENUS.get(0), unknown));
        check.same("A registered adapter is covered", List.of(MENUS.get(0)), result.covered());
        check.same("A new surface requires its own reviewed route", List.of(unknown), result.needsReview());
    }

    @Test void missingOwnerGuidanceCannotProduceAUsefulCitation(@TempDir Path root, Checks check) throws IOException {
        Files.createDirectories(root.resolve("docs/testing"));
        Files.writeString(root.resolve(GUIDE), "# Testing\n\n## Another section\n");
        check.rejects("Missing menu guidance fails with an explicit citation error", IOException.class,
                () -> ReleaseRag.inspect(root, List.of("web/src/main/web/options-menu.mjs")));
    }

    private static void verify(Path root, String path, List<String> tests, Checks check) throws IOException {
        var result = ReleaseRag.inspect(root, List.of(path));
        check.same(path + " has a concrete route", List.of(), result.needsReview());
        var citations = result.citations().stream()
                .filter(citation -> citation.path().equals(GUIDE)
                        && tests.stream().allMatch(citation.check()::contains)).toList();
        check.same(path + " cites all focused component contracts", 1, citations.size());
        var citation = citations.get(0);
        check.same(path + " points to the actual owner section", citation.heading(),
                Files.readAllLines(root.resolve(GUIDE)).get(citation.line() - 1));
        for (String test : tests) {
            check.same(test + " has an executable test source", true,
                    Files.isRegularFile(root.resolve("atf-tests/src/test/java/ro/atf/chess/" + test + ".java")));
        }
    }
}
