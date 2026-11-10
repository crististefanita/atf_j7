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
            new Route("chess-core/src/main/resources/mate-in-three.yaml", "docs/board/quest-curation.md", "## Mat în trei mutări", "MateInThreeCatalogTest și verify-mate-in-three.cjs"),
            new Route("android/", "docs/testing/README.md", "## Sanity Android", "BoardSanity"),
            new Route("android/app/src/main/java/ro/atf/chess/android/BoardView.java", "docs/testing/README.md", "## Proba TalkBack", "QuestHintTest, BoardSanity și proba TalkBack"),
            new Route("atf-tests/", "docs/testing/README.md", "## Raportare Extent", "ChessComponentTest și ReleaseGovernanceTest"),
            new Route("docs/board/", "docs/testing/README.md", "## Motor și proiecție", "BoardPanelLayoutTest și QuestHintTest"),
            new Route("docs/feedback/", "docs/feedback/README.md", "# Feedback · Chess Lab", "Revizuiți criteriile deschise înaintea promovării"),
            new Route("lib/", "lib/README.md", "## Proveniență", "ReleaseGovernanceTest"),
            new Route("docs/release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("README.md", RELEASE.document(), RELEASE.heading(), RELEASE.check()), RELEASE);

    private ReleaseRag() { }

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
        }
    }
}
