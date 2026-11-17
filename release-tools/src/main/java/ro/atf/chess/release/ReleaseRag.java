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
import java.util.stream.Stream;

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

        public boolean documentationPairRequired() {
            return Stream.concat(covered.stream(), needsReview.stream()).anyMatch(DocumentationPair::isMarkdown);
        }
    }

    private static final Route RELEASE = new Route("release-tools/", "docs/release/README.md", "## Qualification and Publication",
            "DocumentationGovernanceTest, ReleaseGovernanceTest and ExtentGovernanceTest");
    private static final List<Route> ROUTES = List.of(
            new Route("chess-core/", "docs/testing/README.md", "## Engine and projection", "ChessComponentTest and QuestCatalogTest"),
            ai("chess-core/src/main/resources/ai-repertoire.yaml"),
            ai("chess-core/src/main/resources/match-ai.yaml"),
            ai("chess-core/src/main/resources/hard-adaptive.yaml"),
            adaptive("chess-core/src/main/resources/hard-adaptive.yaml"),
            ai("chess-core/src/main/java/ro/atf/chess/HardAdaptivePolicy.java"),
            adaptive("chess-core/src/main/java/ro/atf/chess/HardAdaptivePolicy.java"),
            ai("chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java"),
            matchHints("chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java"),
            new Route("chess-core/src/main/java/ro/atf/chess/ForcedMateSearch.java", "docs/board/ai-quality.md", "## Solving puzzles with a declared objective",
                    "TacticalAnalysisTest and HardQuestTransferBenchmarkTest"),
            new Route("chess-core/src/main/java/ro/atf/chess/HardQuestSolver.java", "docs/board/ai-quality.md", "## Solving puzzles with a declared objective",
                    "TacticalAnalysisTest and HardQuestTransferBenchmarkTest"),
            tacticalHoldout("atf-tests/src/test/resources/tactical-transfer-holdout.yaml"),
            tacticalHoldout("atf-tests/src/test/resources/tactical-transfer-holdout.sha256"),
            ai("chess-core/src/main/java/ro/atf/chess/ChessMatch.java"),
            new Route("chess-core/src/main/java/ro/atf/chess/ChessMatch.java", "docs/testing/README.md", "## Game and History", "ChessMatchTest and BoardSanity"),
            new Route("chess-core/src/main/resources/mate-in-three.yaml", "docs/board/quest-curation.md", "## Mate in three moves", "MateInThreeCatalogTest and verify-mate-in-three.cjs"),
            new Route("chess-core/src/main/resources/mate-in-two.yaml", "docs/board/quest-curation.md", "## Mate in two moves", "MateInTwoCatalogTest and verify-mate-in-two.cjs"),
            new Route("chess-core/src/main/resources/quests.yaml", "docs/board/quest-curation.md", "## Configuring the chapters",
                    "QuestCatalogTest and update-mate-one-order.ps1"),
            new Route("chess-core/src/main/resources/training.yaml", "docs/board/quest-curation.md", "## Tactical Training and verification set",
                    "QuestCatalogTest and verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/training.sha256", "docs/board/quest-curation.md", "## Tactical Training and verification set",
                    "QuestCatalogTest and verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/intermezzo.yaml", "docs/board/quest-curation.md", "## Intermediate Move",
                    "QuestCatalogTest and verify-tactics.cjs"),
            new Route("chess-core/src/main/resources/intermezzo.sha256", "docs/board/quest-curation.md", "## Intermediate Move",
                    "QuestCatalogTest and verify-tactics.cjs"),
            new Route("chess-core/src/main/java/ro/atf/chess/QuestCatalog.java", "docs/board/quest-curation.md", "## Tactical Training and verification set",
                    "QuestCatalogTest"),
            new Route("chess-core/src/main/resources/tactics.yaml", "docs/testing/README.md", "## Tactics and scoring", "TacticsCatalogTest and verify-tactics.cjs"),
            new Route("chess-core/src/main/java/ro/atf/chess/QuestRewards.java", "docs/testing/README.md", "## Tactics and scoring", "QuestRewardsTest and MainMenuLayoutTest"),
            questProgress("chess-core/src/main/java/ro/atf/chess/QuestProgress.java"),
            questProgress("chess-core/src/main/java/ro/atf/chess/QuestStore.java"),
            questProgress("android/app/src/main/java/ro/atf/chess/android/ChessProgressStore.java"),
            questProgress("android/app/src/main/java/ro/atf/chess/android/AndroidGamePreferences.java"),
            questProgress("atf-tests/src/test/java/ro/atf/chess/QuestStoreTest.java"),
            questProgress("atf-tests/src/test/java/ro/atf/chess/AndroidReceiptTest.java"),
            new Route("android/", "docs/testing/README.md", "## Android Sanity", "BoardSanity"),
            new Route("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java", "docs/testing/README.md", "## Game and History", "ChessMatchTest and BoardSanity"),
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
            review("android/app/src/main/java/ro/atf/chess/android/AnalysisReplayDialog.java"),
            new Route("android/app/src/main/java/ro/atf/chess/android/AnalysisReplayDialog.java",
                    "docs/testing/README.md", "## Analysis Board",
                    "OpeningTheoryTest, GameReviewLayoutTest and BoardSanity -Distribution -AnalysisOnly -IncludeManualAnalysis"),
            new Route("android/app/src/main/java/ro/atf/chess/android/AnalysisReplayDialog.java",
                    "docs/testing/README.md", "## Shared replay on a compact screen",
                    "GameReviewLayoutTest and BoardSanity -Distribution -ReplayCompactOnly"),
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
            new Route("android/app/src/main/java/ro/atf/chess/android/BoardView.java", "docs/testing/README.md", "## TalkBack probe", "QuestHintTest, BoardSanity and the TalkBack probe"),
            openings("chess-core/src/main/java/ro/atf/chess/OpeningLesson.java"),
            openings("android/app/src/main/java/ro/atf/chess/android/OpeningActivity.java"),
            openings("docs/board/openings.md"),
            testChanges("docs/testing/test-changes.yaml"),
            testChanges("atf-tests/src/test/java/ro/atf/chess/TestChangeReview.java"),
            testChanges("atf-tests/src/test/java/ro/atf/chess/TestChangePolicy.java"),
            testChanges("atf-tests/src/test/java/ro/atf/chess/TestChangeProof.java"),
            testChanges("atf-tests/src/test/java/ro/atf/chess/SuiteGovernanceTest.java"),
            testChanges("release-tools/src/main/java/ro/atf/chess/release/GitTestChanges.java"),
            testChanges("release-tools/src/main/java/ro/atf/chess/release/TestChangeReceipt.java"),
            testChanges("release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java"),
            new Route("atf-tests/", "docs/testing/README.md", "## JVM suites", "ChessJvmSuite and ChessGovernanceSuite"),
            new Route("atf-tests/src/test/java/ro/atf/chess/DocumentationGovernanceTest.java", "docs/release/README.md",
                    "## Strict Exception for Installing the Exact APK", "DocumentationGovernanceTest and ReleaseGovernanceTest"),
            new Route("atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java", "docs/release/README.md",
                    "## Strict Exception for Installing the Exact APK", "ReleaseGovernanceTest with negative cases for commit, hash, authorization, and Extent"),
            new Route("release-tools/src/main/java/ro/atf/chess/release/ReleaseEvidence.java", "docs/release/README.md",
                    "## Strict Exception for Installing the Exact APK", "ReleaseGovernanceTest and DocumentationGovernanceTest"),
            new Route("docs/release/README.md", "docs/release/README.md",
                    "## Strict Exception for Installing the Exact APK", "DocumentationGovernanceTest and ReleaseGovernanceTest"),
            audit("atf-tests/src/test/java/ro/atf/chess/ExtentAudit.java"),
            audit("atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java"),
            audit("atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java"),
            matchHints("atf-tests/src/test/java/ro/atf/chess/ArchivedReleaseEvidence.java"),
            audit("scripts/run-android-sanity.ps1"),
            audit("docs/testing/README.md"),
            new Route("atf-tests/src/test/java/ro/atf/chess/DocumentationGovernanceTest.java", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("docs/testing/README.md", "docs/testing/README.md", "## JVM suites", "ChessJvmSuite and ChessGovernanceSuite"),
            new Route("docs/README.md", "docs/README.md", "## Documentation Checks", "DocumentationGovernanceTest and ReleaseGovernanceTest"),
            new Route("docs/board/", "docs/testing/README.md", "## Engine and projection", "BoardPanelLayoutTest and QuestHintTest"),
            new Route("docs/feedback/", "docs/feedback/README.md", "# Feedback · Chess Lab",
                    "DocumentationGovernanceTest and review of open criteria"),
            new Route("lib/", "lib/README.md", "## Provenance", "ReleaseGovernanceTest"),
            new Route(".gitattributes", "lib/README.md", "## Provenance", "PlatformLibraryProvenanceTest"),
            new Route("library-provenance.json", "lib/README.md", "## Provenance", "PlatformLibraryProvenanceTest"),
            new Route("docs/release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("release/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("scripts/Initialize-J7Signing.ps1", "docs/release/README.md", "## Signing Identity and Clean Installation",
                    "ReleaseGovernanceTest and ReleaseGate signing-continuity"),
            new Route("scripts/verify-signing-continuity.ps1", "docs/release/README.md", "## Signing Identity and Clean Installation",
                    "ReleaseGovernanceTest and ReleaseGate signing-continuity"),
            new Route("scripts/verify-tactics.cjs", "docs/board/quest-curation.md", "## Tactical Training and verification set",
                    "verify-tactics.cjs and chess.js 1.4.0"),
            new Route("pom.xml", RELEASE.document(), RELEASE.heading(), RELEASE.check()),
            new Route("README.md", RELEASE.document(), RELEASE.heading(), RELEASE.check()), RELEASE);

    private ReleaseRag() { }

    private static Route testChanges(String path) {
        return new Route(path, "docs/testing/README.md", "## Test changes against a Git baseline",
                "TestChangeReview with the explicit Git baseline and ReleaseGate preflight");
    }

    private static Route questProgress(String path) {
        return new Route(path, "docs/testing/README.md", "## Puzzle progress",
                "QuestStoreTest, AndroidReceiptTest and BoardSanity after restart");
    }

    private static Route audit(String path) {
        return new Route(path, "docs/testing/README.md", "## Extent Reporting", "ExtentGovernanceTest");
    }

    private static Route ai(String path) {
        return new Route(path, "docs/board/ai-quality.md", "## Checks for every change",
                "HardQuestTransferBenchmarkTest, RookEndgameHistoryTest and ChessBenchmarkSuite: Stockfish and the offline league");
    }

    private static Route adaptive(String path) {
        return new Route(path, "docs/testing/README.md", "### Adaptive Hard budget",
                "HardAdaptivePolicyTest, AiOpeningRegressionTest, MatchSetupTest and RookEndgameHistoryTest");
    }

    private static Route openings(String path) {
        return new Route(path, "docs/testing/README.md", "## Openings", "OpeningLessonTest and BoardSanity -IncludeOpenings");
    }

    private static Route tacticalHoldout(String path) {
        return new Route(path, "docs/board/quest-curation.md", "## Tactical Training and verification set",
                "QuestCatalogTest, TacticsCatalogTest and HardQuestTransferBenchmarkTest");
    }

    private static Route review(String path) {
        return new Route(path, "docs/testing/README.md", "## Game and History",
                "GameReviewTest, GameReviewTimelineTest, GameReviewLayoutTest, GameReviewGraphLayoutTest, GameReviewGovernanceTest, MatchStoreTest and BoardSanity -Distribution -GameReviewOnly");
    }

    private static Route manualAnalysis(String path) {
        return new Route(path, "docs/testing/README.md", "## Analysis Board",
                "AnalysisLineTest, MainMenuLayoutTest and BoardSanity -ManualAnalysisOnly");
    }

    private static Route matchHints(String path) {
        return new Route(path, "docs/testing/README.md", "## Game and History",
                "MatchHintTest, HintPreviewTest, HintComparisonTest, AndroidReceiptTest and BoardSanity -AnalysisOnly -IncludeManualAnalysis");
    }

    private static Route anchor(String path) {
        return new Route(path, "docs/board/ai-quality.md", "### External anchor results",
                "AiAnchorBenchmarkTest and GameReviewGovernanceTest");
    }

    private static Route games(String path) {
        return new Route(path, "docs/testing/README.md", "## Game and History",
                "MatchListEntryTest, MatchStoreTest, MatchArchiveTest, MatchSlotsTest, MainMenuLayoutTest and BoardSanity -MatchMenusOnly");
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
        if (inventory.documentationPairRequired()) {
            System.out.println("DOC_PAIR | main EN / main_RO RO. See docs/release/README.md (Bilingual Documentation / Documentație bilingvă).");
            System.out.println("  Run ReleaseGate documentation-pair <Git root> <EN ref> <RO ref> on the paired candidates, then review translated meaning.");
        }
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
