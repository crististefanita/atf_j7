package ro.atf.chess.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small local retriever. It cites tracked guidance and leaves execution to the evidence gates. */
public final class ReleaseRag {
    private record Route(String prefix, String document, String heading, String check) { }

    public record Citation(String path, int line, String heading, String sha256, String check) { }

    private static final Route RELEASE = new Route("release-tools/", "docs/release/README.md", "## Calificare și publicare", "ReleaseGovernanceTest");
    private static final List<Route> ROUTES = List.of(
            new Route("chess-core/", "docs/testing/README.md", "## Motor și proiecție", "ChessComponentTest și QuestCatalogTest"),
            ai("chess-core/src/main/resources/ai-repertoire.yaml"),
            ai("chess-core/src/main/resources/match-ai.yaml"),
            ai("chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java"),
            ai("chess-core/src/main/java/ro/atf/chess/ChessMatch.java"),
            new Route("chess-core/src/main/java/ro/atf/chess/ChessMatch.java", "docs/testing/README.md", "## Partidă și istoric", "ChessMatchTest și BoardSanity"),
            new Route("chess-core/src/main/resources/mate-in-three.yaml", "docs/board/quest-curation.md", "## Mat în trei mutări", "MateInThreeCatalogTest și verify-mate-in-three.cjs"),
            new Route("chess-core/src/main/resources/mate-in-two.yaml", "docs/board/quest-curation.md", "## Mat în două mutări", "MateInTwoCatalogTest și verify-mate-in-two.cjs"),
            new Route("chess-core/src/main/resources/quests.yaml", "docs/board/quest-curation.md", "## Configurarea capitolelor",
                    "QuestCatalogTest și update-mate-one-order.ps1"),
            new Route("chess-core/src/main/resources/tactics.yaml", "docs/testing/README.md", "## Tactică și punctaj", "TacticsCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/java/ro/atf/chess/QuestRewards.java", "docs/testing/README.md", "## Tactică și punctaj", "QuestRewardsTest și MainMenuLayoutTest"),
            new Route("android/", "docs/testing/README.md", "## Sanity Android", "BoardSanity"),
            new Route("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java", "docs/testing/README.md", "## Partidă și istoric", "ChessMatchTest și BoardSanity"),
            review("chess-core/src/main/java/ro/atf/chess/GameReview.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewPolicy.java"),
            review("chess-core/src/main/java/ro/atf/chess/OpeningIndex.java"),
            review("chess-core/src/main/resources/game-review.yaml"),
            review("chess-core/src/main/resources/opening-book.tsv"),
            review("android/app/src/main/java/ro/atf/chess/android/GameReviewDialog.java"),
            manualAnalysis("chess-core/src/main/java/ro/atf/chess/AnalysisLine.java"),
            manualAnalysis("android/app/src/main/java/ro/atf/chess/android/AnalysisActivity.java"),
            manualAnalysis("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java"),
            anchor("atf-tests/src/test/resources/ai-anchor.yaml"),
            anchor("atf-tests/src/test/resources/ai-anchor-observations.yaml"),
            anchor("atf-tests/src/test/java/ro/atf/chess/PairedElo.java"),
            new Route("android/app/src/main/java/ro/atf/chess/android/BoardView.java", "docs/testing/README.md", "## Proba TalkBack", "QuestHintTest, BoardSanity și proba TalkBack"),
            openings("chess-core/src/main/java/ro/atf/chess/OpeningLesson.java"),
            openings("android/app/src/main/java/ro/atf/chess/android/OpeningActivity.java"),
            openings("docs/board/openings.md"),
            new Route("atf-tests/", "docs/testing/README.md", "## Suite JVM", "ChessJvmSuite și ChessGovernanceSuite"),
            new Route("docs/testing/README.md", "docs/testing/README.md", "## Suite JVM", "ChessJvmSuite și ChessGovernanceSuite"),
            new Route("docs/board/", "docs/testing/README.md", "## Motor și proiecție", "BoardPanelLayoutTest și QuestHintTest"),
            new Route("docs/feedback/", "docs/feedback/README.md", "# Feedback · Chess Lab", "Revizuiți criteriile deschise înaintea promovării"),
            new Route("lib/", "lib/README.md", "## Proveniență", "ReleaseGovernanceTest"),
            new Route("docs/release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("README.md", RELEASE.document(), RELEASE.heading(), RELEASE.check()), RELEASE);

    private ReleaseRag() { }

    private static Route ai(String path) {
        return new Route(path, "docs/board/ai-quality.md", "## Control la fiecare schimbare",
                "ChessBenchmarkSuite: Stockfish și liga offline");
    }

    private static Route openings(String path) {
        return new Route(path, "docs/testing/README.md", "## Deschideri", "OpeningLessonTest și BoardSanity -IncludeOpenings");
    }

    private static Route review(String path) {
        return new Route(path, "docs/testing/README.md", "## Partidă și istoric",
                "GameReviewTest, GameReviewGovernanceTest și BoardSanity -IncludeGameReview");
    }

    private static Route manualAnalysis(String path) {
        return new Route(path, "docs/testing/README.md", "## Tablă de analiză",
                "AnalysisLineTest, MainMenuLayoutTest și BoardSanity -ManualAnalysisOnly");
    }

    private static Route anchor(String path) {
        return new Route(path, "docs/board/ai-quality.md", "### Rezultatele ancorei externe",
                "AiAnchorBenchmarkTest și GameReviewGovernanceTest");
    }

    public static List<Citation> retrieve(Path root, List<String> changedPaths) throws IOException {
        var result = new ArrayList<Citation>();
        for (var route : ROUTES) {
            if (changedPaths.stream().map(path -> path.replace('\\', '/')).anyMatch(route.prefix().endsWith("/")
                    ? path -> path.startsWith(route.prefix()) : path -> path.equals(route.prefix()))) {
                Path document = root.resolve(route.document());
                var lines = Files.readAllLines(document, StandardCharsets.UTF_8);
                int line = lines.indexOf(route.heading()) + 1;
                if (line == 0) throw new IOException("Missing citation anchor: " + route.document() + " " + route.heading());
                var citation = new Citation(route.document(), line, route.heading(), ReleaseEvidence.digest(document), route.check());
                if (!result.contains(citation)) result.add(citation);
            }
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) throw new IllegalArgumentException("Usage: ReleaseRag <source-root> <changed-path>...");
        var citations = retrieve(Path.of(args[0]), List.of(args).subList(1, args.length));
        System.out.println("ADVISORY_ONLY | Qualification and publication are NOT_RUN by this command.");
        if (citations.isEmpty()) System.out.println("NO_MATCH | Review the changed paths with the release owner.");
        for (var item : citations) {
            System.out.printf("%s:%d %s%n  SHA-256 %s%n  Verify %s%n", item.path(), item.line(), item.heading(), item.sha256(), item.check());
            if (item.path().equals(RELEASE.document()) && item.heading().equals(RELEASE.heading())) {
                for (String step : workflow(Path.of(args[0]))) System.out.println(step);
            }
        }
    }

    public static List<String> workflow(Path root) throws IOException {
        var lines = Files.readAllLines(root.resolve(RELEASE.document()), StandardCharsets.UTF_8);
        int start = lines.indexOf(RELEASE.heading());
        if (start < 0) throw new IOException("Missing release workflow anchor");
        var steps = new ArrayList<String>();
        for (int index = start + 1; index < lines.size() && !lines.get(index).startsWith("## "); index++) {
            String line = lines.get(index);
            if (line.matches("[0-9]+\\. .+")) steps.add(line);
        }
        return List.copyOf(steps);
    }
}
