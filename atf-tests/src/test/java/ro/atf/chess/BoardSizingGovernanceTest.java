package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class BoardSizingGovernanceTest {
    @Test void oneVersionedModuleOwnsTheAnalysisReference(Checks check) throws Exception {
        check.same("The entry loads the reference sizing module", true, source("index.html").contains("href=\"board-layout.css\""));
        try (var files = Files.list(ProjectRoot.locate().resolve("web/src/main/web"))) {
            long owners = 0;
            for (var file : files.filter(path -> path.toString().endsWith(".css")).toList()) {
                owners += Pattern.compile("--play-board-limit\\s*:").matcher(Files.readString(file)).results().count();
            }
            check.same("There is one board limit owner", 1L, owners);
        }
        check.same("The reference remains bounded by the common column", true, source("board-layout.css").contains("--play-board-limit: min(var(--play-column)"));
        check.same("Game sizing has no separate puzzle chrome allowance", false, source("game-review.css").contains("--play-puzzle-text"));
    }

    @Test void consumersShareTheReferenceAndRails(Checks check) throws Exception {
        for (String owner : List.of("study-analysis.css", "study-openings.css", "game-review.css")) {
            check.same(owner + " uses the common rail", true, source(owner).contains("var(--board-meter-size)"));
        }
        for (String owner : List.of("study-analysis.css", "study-openings.css", "study-quests.css", "game-review.css")) {
            check.same(owner + " uses the common square limit", true, source(owner).contains("var(--play-board-limit)"));
            check.same(owner + " has no second height-based square reduction", false,
                    Pattern.compile("(?:inline-size|width|--opening-board-side):[^;]*(?:100cqb|100cqh)").matcher(source(owner)).find());
        }
        check.same("Analysis captions use the reference metric", true, source("study-analysis.css").contains("--analysis-caption-height: var(--board-seat-size)"));
        check.same("Opening lessons retain the centered reference column on wide windows", false,
                source("study-openings.css").contains("2 * var(--play-room)"));
        check.same("All eight ranks remain square grid rows", true, source("game-review.css").contains("grid-template-rows: repeat(8, minmax(0, 1fr))"));
    }

    @Test void explanationsScrollInsideTheViewportWithoutSqueezingTheSquare(Checks check) throws Exception {
        String puzzles = source("study-quests.css");
        check.same("The standard puzzle window owns one viewport", true, puzzles.contains("block-size: calc(100dvh - var(--quest-insets))"));
        check.same("Puzzle details yield remaining space without a second square reduction", true,
                puzzles.contains("grid-template-rows: auto minmax(0, 1fr)"));
        for (String owner : List.of("study-analysis.css", "study-openings.css", "study-quests.css", "game-review.css")) {
            check.same(owner + " preserves scrollable explanations", true, source(owner).contains("overflow: auto"));
        }
        check.same("Controls retain their shared 48px target", true, source("chess-controls.css").contains("--chess-control-size: 48px"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(ProjectRoot.locate().resolve("web/src/main/web/" + file));
    }
}
