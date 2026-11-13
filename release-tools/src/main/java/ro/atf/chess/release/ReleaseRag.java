package ro.atf.chess.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

/** Small local retriever. It cites tracked guidance and leaves execution to the evidence gates. */
public final class ReleaseRag {
    private record Route(String path, String document, String heading, String check) {
        boolean matches(String changedPath) {
            return path.endsWith("/") ? changedPath.startsWith(path) : changedPath.equals(path);
        }
    }

    public record Citation(String path, int line, String heading, String sha256, String check) { }
    public record Inventory(List<Citation> citations, List<String> covered, List<String> needsReview) {
        public Inventory {
            citations = List.copyOf(citations);
            covered = List.copyOf(covered);
            needsReview = List.copyOf(needsReview);
        }
    }

    private static final Route RELEASE = new Route("release-tools/", "docs/release/README.md", "## Calificare și publicare",
            "DocumentationGovernanceTest, ReleaseGovernanceTest și ExtentGovernanceTest");
    private static final List<Route> ROUTES = List.of(
            new Route("chess-core/", "docs/testing/README.md", "## Motor și proiecție", "ChessComponentTest și QuestCatalogTest"),
            ai("chess-core/src/main/resources/ai-repertoire.yaml"),
            ai("chess-core/src/main/resources/match-ai.yaml"),
            ai("chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java"),
            matchHints("chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java"),
            new Route("chess-core/src/main/java/ro/atf/chess/ForcedMateSearch.java", "docs/board/ai-quality.md", "## Rezolvarea quest-urilor cu obiectiv declarat",
                    "TacticalAnalysisTest și HardQuestTransferBenchmarkTest"),
            new Route("chess-core/src/main/java/ro/atf/chess/HardQuestSolver.java", "docs/board/ai-quality.md", "## Rezolvarea quest-urilor cu obiectiv declarat",
                    "TacticalAnalysisTest și HardQuestTransferBenchmarkTest"),
            tacticalHoldout("atf-tests/src/test/resources/tactical-transfer-holdout.yaml"),
            tacticalHoldout("atf-tests/src/test/resources/tactical-transfer-holdout.sha256"),
            ai("chess-core/src/main/java/ro/atf/chess/ChessMatch.java"),
            new Route("chess-core/src/main/java/ro/atf/chess/ChessMatch.java", "docs/testing/README.md", "## Partidă și istoric", "ChessMatchTest și BoardSanity"),
            new Route("chess-core/src/main/resources/mate-in-three.yaml", "docs/board/quest-curation.md", "## Mat în trei mutări", "MateInThreeCatalogTest și verify-mate-in-three.cjs"),
            new Route("chess-core/src/main/resources/mate-in-two.yaml", "docs/board/quest-curation.md", "## Mat în două mutări", "MateInTwoCatalogTest și verify-mate-in-two.cjs"),
            new Route("chess-core/src/main/resources/quests.yaml", "docs/board/quest-curation.md", "## Configurarea capitolelor",
                    "QuestCatalogTest și update-mate-one-order.ps1"),
            new Route("chess-core/src/main/resources/training.yaml", "docs/board/quest-curation.md", "## Antrenament tactic și lot de verificare",
                    "QuestCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/training.sha256", "docs/board/quest-curation.md", "## Antrenament tactic și lot de verificare",
                    "QuestCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/intermezzo.yaml", "docs/board/quest-curation.md", "## Mutarea intermediară",
                    "QuestCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/intermezzo.sha256", "docs/board/quest-curation.md", "## Mutarea intermediară",
                    "QuestCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/java/ro/atf/chess/QuestCatalog.java", "docs/board/quest-curation.md", "## Antrenament tactic și lot de verificare",
                    "QuestCatalogTest"),
            new Route("chess-core/src/main/resources/tactics.yaml", "docs/testing/README.md", "## Tactică și punctaj", "TacticsCatalogTest și verify-tactics.cjs"),
            new Route("chess-core/src/main/java/ro/atf/chess/QuestRewards.java", "docs/testing/README.md", "## Tactică și punctaj", "QuestRewardsTest și MainMenuLayoutTest"),
            questProgress("chess-core/src/main/java/ro/atf/chess/QuestProgress.java"),
            questProgress("chess-core/src/main/java/ro/atf/chess/QuestStore.java"),
            questProgress("android/app/src/main/java/ro/atf/chess/android/ChessProgressStore.java"),
            questProgress("android/app/src/main/java/ro/atf/chess/android/AndroidGamePreferences.java"),
            questProgress("atf-tests/src/test/java/ro/atf/chess/QuestStoreTest.java"),
            questProgress("atf-tests/src/test/java/ro/atf/chess/AndroidReceiptTest.java"),
            new Route("android/", "docs/testing/README.md", "## Sanity Android", "BoardSanity"),
            new Route("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java", "docs/testing/README.md", "## Partidă și istoric", "ChessMatchTest și BoardSanity"),
            review("chess-core/src/main/java/ro/atf/chess/GameReview.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewTimeline.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewExplanation.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewCodec.java"),
            review("docs/board/game-review.md"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewLayout.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewGraphLayout.java"),
            review("atf-tests/src/test/java/ro/atf/chess/GameReviewTest.java"),
            review("atf-tests/src/test/java/ro/atf/chess/GameReviewTimelineTest.java"),
            review("atf-tests/src/test/java/ro/atf/chess/GameReviewLayoutTest.java"),
            review("atf-tests/src/test/java/ro/atf/chess/GameReviewGraphLayoutTest.java"),
            review("atf-tests/src/test/java/ro/atf/chess/GameReviewGovernanceTest.java"),
            review("chess-core/src/main/java/ro/atf/chess/GameReviewPolicy.java"),
            review("chess-core/src/main/java/ro/atf/chess/OpeningIndex.java"),
            review("chess-core/src/main/resources/game-review.yaml"),
            review("chess-core/src/main/resources/opening-book.tsv"),
            review("android/app/src/main/java/ro/atf/chess/android/GameReviewDialog.java"),
            review("android/app/src/main/java/ro/atf/chess/android/GameReviewWorkspace.java"),
            review("android/app/src/androidTest/java/ro/atf/chess/android/BoardSanity.java"),
            games("chess-core/src/main/java/ro/atf/chess/MatchArchive.java"),
            games("chess-core/src/main/java/ro/atf/chess/GameReviewCodec.java"),
            games("chess-core/src/main/java/ro/atf/chess/MatchSlots.java"),
            games("chess-core/src/main/java/ro/atf/chess/MatchListEntry.java"),
            games("chess-core/src/main/java/ro/atf/chess/MatchStore.java"),
            games("android/app/src/main/java/ro/atf/chess/android/AndroidMatchPreferences.java"),
            games("android/app/src/main/java/ro/atf/chess/android/ChessProgressStore.java"),
            games("android/app/src/main/java/ro/atf/chess/android/MatchListActivity.java"),
            games("android/app/src/main/java/ro/atf/chess/android/MatchLabels.java"),
            games("android/app/src/main/res/layout/main_menu.xml"),
            games("android/app/src/main/res/layout/match_list.xml"),
            games("android/app/src/main/res/layout/match_list_row.xml"),
            games("android/app/src/main/res/layout/match_setup.xml"),
            games("android/app/src/main/res/drawable/match_date_badge.xml"),
            games("android/app/src/main/res/drawable/match_list_divider.xml"),
            manualAnalysis("chess-core/src/main/java/ro/atf/chess/AnalysisLine.java"),
            manualAnalysis("android/app/src/main/java/ro/atf/chess/android/AnalysisActivity.java"),
            manualAnalysis("android/app/src/main/java/ro/atf/chess/android/ChessActivity.java"),
            manualAnalysis("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java"),
            matchHints("chess-core/src/main/java/ro/atf/chess/MatchHint.java"),
            matchHints("chess-core/src/main/java/ro/atf/chess/HintPreview.java"),
            matchHints("chess-core/src/main/java/ro/atf/chess/HintComparison.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/HintPreviewTest.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/HintComparisonTest.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/AndroidReceiptTest.java"),
            ai("atf-tests/src/test/java/ro/atf/chess/MatchAiBenchmarkTest.java"),
            matchHints("android/app/src/main/java/ro/atf/chess/android/MatchAnalysisView.java"),
            matchHints("android/app/src/main/java/ro/atf/chess/android/HintPreviewDialog.java"),
            matchHints("android/app/src/main/res/values/hint_preview.xml"),
            matchHints("android/app/src/main/res/values-ro/hint_preview.xml"),
            matchHints("android/app/src/main/res/values/hint_preview_ids.xml"),
            anchor("atf-tests/src/test/resources/ai-anchor.yaml"),
            anchor("atf-tests/src/test/resources/ai-anchor-observations.yaml"),
            anchor("atf-tests/src/test/java/ro/atf/chess/PairedElo.java"),
            new Route("android/app/src/main/java/ro/atf/chess/android/BoardView.java", "docs/testing/README.md", "## Proba TalkBack", "QuestHintTest, BoardSanity și proba TalkBack"),
            openings("chess-core/src/main/java/ro/atf/chess/OpeningLesson.java"),
            openings("android/app/src/main/java/ro/atf/chess/android/OpeningActivity.java"),
            openings("docs/board/openings.md"),
            new Route("atf-tests/", "docs/testing/README.md", "## Suite JVM", "ChessJvmSuite și ChessGovernanceSuite"),
            new Route("atf-tests/src/test/java/ro/atf/chess/DocumentationGovernanceTest.java", "docs/release/README.md",
                    "## Excepție strictă pentru instalarea APK-ului exact", "DocumentationGovernanceTest și ReleaseGovernanceTest"),
            new Route("atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java", "docs/release/README.md",
                    "## Excepție strictă pentru instalarea APK-ului exact", "ReleaseGovernanceTest cu negative pentru commit, hash, autorizare și Extent"),
            new Route("release-tools/src/main/java/ro/atf/chess/release/ReleaseEvidence.java", "docs/release/README.md",
                    "## Excepție strictă pentru instalarea APK-ului exact", "ReleaseGovernanceTest și DocumentationGovernanceTest"),
            new Route("docs/release/README.md", "docs/release/README.md",
                    "## Excepție strictă pentru instalarea APK-ului exact", "DocumentationGovernanceTest și ReleaseGovernanceTest"),
            audit("atf-tests/src/test/java/ro/atf/chess/ExtentAudit.java"),
            audit("atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java"),
            audit("atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/ArchivedReleaseEvidence.java"),
            audit("scripts/run-android-sanity.ps1"),
            audit("docs/testing/README.md"),
            new Route("atf-tests/src/test/java/ro/atf/chess/DocumentationGovernanceTest.java", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("docs/testing/README.md", "docs/testing/README.md", "## Suite JVM", "ChessJvmSuite și ChessGovernanceSuite"),
            new Route("docs/README.md", "docs/README.md", "## Verificarea documentației", "DocumentationGovernanceTest și ReleaseGovernanceTest"),
            new Route("docs/board/", "docs/testing/README.md", "## Motor și proiecție", "BoardPanelLayoutTest și QuestHintTest"),
            new Route("docs/feedback/", "docs/feedback/README.md", "# Feedback · Chess Lab",
                    "DocumentationGovernanceTest și revizia criteriilor deschise"),
            new Route("lib/", "lib/README.md", "## Proveniență", "ReleaseGovernanceTest"),
            new Route(".gitattributes", "lib/README.md", "## Proveniență", "PlatformLibraryProvenanceTest"),
            new Route("library-provenance.json", "lib/README.md", "## Proveniență", "PlatformLibraryProvenanceTest"),
            new Route("docs/release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/Initialize-J7Signing.ps1", "docs/release/README.md", "## Identitatea de semnare și instalare curată",
                    "ReleaseGovernanceTest și ReleaseGate signing-continuity"),
            new Route("scripts/verify-signing-continuity.ps1", "docs/release/README.md", "## Identitatea de semnare și instalare curată",
                    "ReleaseGovernanceTest și ReleaseGate signing-continuity"),
            new Route("scripts/verify-tactics.cjs", "docs/board/quest-curation.md", "## Antrenament tactic și lot de verificare",
                    "verify-tactics.cjs și chess.js 1.4.0"),
            new Route("pom.xml", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("README.md", RELEASE.document(), RELEASE.heading(), RELEASE.check()), RELEASE);

    private ReleaseRag() { }

    private static Route questProgress(String path) {
        return new Route(path, "docs/testing/README.md", "## Progresul problemelor",
                "QuestStoreTest, AndroidReceiptTest și BoardSanity la restart");
    }

    private static Route audit(String path) {
        return new Route(path, "docs/testing/README.md", "## Raportare Extent", "ExtentGovernanceTest");
    }

    private static Route ai(String path) {
        return new Route(path, "docs/board/ai-quality.md", "## Control la fiecare schimbare",
                "HardQuestTransferBenchmarkTest, RookEndgameHistoryTest și ChessBenchmarkSuite: Stockfish și liga offline");
    }

    private static Route openings(String path) {
        return new Route(path, "docs/testing/README.md", "## Deschideri", "OpeningLessonTest și BoardSanity -IncludeOpenings");
    }

    private static Route tacticalHoldout(String path) {
        return new Route(path, "docs/board/quest-curation.md", "## Antrenament tactic și lot de verificare",
                "QuestCatalogTest, TacticsCatalogTest și HardQuestTransferBenchmarkTest");
    }

    private static Route review(String path) {
        return new Route(path, "docs/testing/README.md", "## Partidă și istoric",
                "GameReviewTest, GameReviewTimelineTest, GameReviewLayoutTest, GameReviewGraphLayoutTest, GameReviewGovernanceTest, MatchStoreTest și BoardSanity -Distribution -GameReviewOnly");
    }

    private static Route manualAnalysis(String path) {
        return new Route(path, "docs/testing/README.md", "## Tablă de analiză",
                "AnalysisLineTest, MainMenuLayoutTest și BoardSanity -ManualAnalysisOnly");
    }

    private static Route matchHints(String path) {
        return new Route(path, "docs/testing/README.md", "## Partidă și istoric",
                "MatchHintTest, HintPreviewTest, HintComparisonTest, AndroidReceiptTest și BoardSanity -AnalysisOnly -IncludeManualAnalysis");
    }

    private static Route anchor(String path) {
        return new Route(path, "docs/board/ai-quality.md", "### Rezultatele ancorei externe",
                "AiAnchorBenchmarkTest și GameReviewGovernanceTest");
    }

    private static Route games(String path) {
        return new Route(path, "docs/testing/README.md", "## Partidă și istoric",
                "MatchListEntryTest, MatchStoreTest, MatchArchiveTest, MatchSlotsTest, MainMenuLayoutTest și BoardSanity -MatchMenusOnly");
    }

    public static List<Citation> retrieve(Path root, List<String> changedPaths) throws IOException {
        return inspect(root, changedPaths).citations();
    }

    public static Inventory inspect(Path root, List<String> changedPaths) throws IOException {
        var paths = changedPaths.stream().map(path -> path.replace('\\', '/')).distinct().toList();
        var covered = new java.util.HashSet<String>();
        var result = new ArrayList<Citation>();
        var documents = new HashMap<String, Guidance>();
        for (var route : ROUTES) {
            var matches = paths.stream().filter(route::matches).toList();
            if (matches.isEmpty()) continue;
            covered.addAll(matches);
            var document = documents.get(route.document());
            if (document == null) {
                document = Guidance.read(root.resolve(route.document()));
                documents.put(route.document(), document);
            }
            int line = document.lines().indexOf(route.heading()) + 1;
            if (line == 0) throw new IOException("Missing citation anchor: " + route.document() + " " + route.heading());
            var citation = new Citation(route.document(), line, route.heading(), document.sha256(), route.check());
            if (!result.contains(citation)) result.add(citation);
        }
        return new Inventory(result, paths.stream().filter(covered::contains).toList(),
                paths.stream().filter(path -> !covered.contains(path)).toList());
    }

    private record Guidance(List<String> lines, String sha256) {
        static Guidance read(Path path) throws IOException {
            String source = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
            try {
                byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new Guidance(source.lines().toList(), hash);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) throw new IllegalArgumentException("Usage: ReleaseRag <source-root> <changed-path>...");
        var inventory = inspect(Path.of(args[0]), List.of(args).subList(1, args.length));
        var citations = inventory.citations();
        System.out.println("ADVISORY_ONLY | Qualification and publication are NOT_RUN by this command.");
        if (citations.isEmpty()) System.out.println("NO_MATCH | Review the changed paths with the release owner.");
        inventory.covered().forEach(path -> System.out.println("COVERED | " + path));
        inventory.needsReview().forEach(path -> System.out.println("NEEDS_REVIEW | " + path));
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
