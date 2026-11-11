package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseEvidence;
import ro.atf.chess.release.ReleaseEvidence.Candidate;
import ro.atf.chess.release.ReleaseEvidence.Download;
import ro.atf.chess.release.ReleaseEvidence.Suite;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ReleaseGovernanceTest {
    private static final String SOURCE = "1".repeat(40);
    private static final String OTHER = "2".repeat(40);
    private static final String REPOSITORY = "example/atf_j7";
    private static final String BASE = "https://github.com/" + REPOSITORY + "/releases/";
    private static final String URL = BASE + "download/v0.1.0/ChessLab-0.1.0.apk";
    private static final String README = "## Jocul\n\n[Release](" + BASE + "tag/v0.1.0)\n[APK](" + URL + ")\n";

    @TempDir Path folder;
    private Candidate candidate;
    private Suite components;
    private Suite android;
    private Path apk;

    @BeforeEach void fixture() throws IOException {
        apk = folder.resolve("ChessLab-0.1.0.apk");
        try (var zip = new ZipOutputStream(Files.newOutputStream(apk))) {
            for (String name : List.of("AndroidManifest.xml", "classes.dex")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        candidate = new Candidate("0.1.0", SOURCE, Files.size(apk), ReleaseEvidence.digest(apk));
        components = suite("components", "");
        android = suite("android", candidate.apkSha256());
    }

    @Test void candidateRequiresEvidenceFromTheSameCommitAndBinary(Checks check) throws IOException {
        check.same("Consistent fixture qualifies", "accepted", decision(() -> ReleaseEvidence.qualification(candidate, apk, components, android)));
        check.same("Foreign component commit blocks", "Wrong candidate in components", decision(() -> ReleaseEvidence.qualification(candidate, apk,
                new Suite(OTHER, "", 3, true, components.htmlSha256(), components.html()), android)));
        check.same("Foreign installed APK blocks", "Wrong candidate in Android E2E", decision(() -> ReleaseEvidence.qualification(candidate, apk,
                components, new Suite(SOURCE, "0".repeat(64), 3, true, android.htmlSha256(), android.html()))));
        check.same("Missing native receipt blocks", "Missing Android E2E receipt", decision(() -> ReleaseEvidence.qualification(candidate, apk, components, null)));
        check.same("Empty native assertions block even with PASS flag", "Missing passing assertions in Android E2E", decision(() -> ReleaseEvidence.qualification(
                candidate, apk, components, new Suite(SOURCE, candidate.apkSha256(), 0, true, android.htmlSha256(), android.html()))));
        Files.writeString(components.html(), "Substituted report");
        check.same("Changed Extent bytes block", "Extent hash mismatch in components", decision(() -> ReleaseEvidence.qualification(candidate, apk, components, android)));
    }

    @Test void downloadMustBeginAtTheCanonicalReadmeRouteAndFinishWithAllBytes(Checks check) throws IOException {
        check.same("Consistent publication fixture is accepted", "accepted", decision(() -> publish(SOURCE, README, 200, apk)));
        check.same("Tag on a different commit blocks", "Published tag points to another commit", decision(() -> publish(OTHER, README, 200, apk)));
        check.same("Stale README tag blocks", "README does not lead to the qualified release and APK",
                decision(() -> publish(SOURCE, README.replace("v0.1.0", "v0.0.9"), 200, apk)));
        check.same("A 404 response blocks before file acceptance", "Wrong download route or HTTP status", decision(() -> publish(SOURCE, README, 404, apk)));
        Path truncated = Files.writeString(folder.resolve("truncated.apk"), "404: Not Found");
        check.same("A text error disguised as APK blocks even with status 200", "APK bytes or SHA-256 differ from qualification",
                decision(() -> publish(SOURCE, README, 200, truncated)));
    }

    @Test void authenticatedDownloadRejectsTransportAndContentSubstitutions(Checks check) throws IOException {
        check.same("Authenticated account and canonical asset qualify", "accepted",
                decision(() -> ReleaseEvidence.publication(candidate, new Download(SOURCE, SOURCE, README, REPOSITORY, URL, "AUTHENTICATED", 200, apk))));
        check.same("An unverified session mode cannot qualify", "Unspecified access mode",
                decision(() -> ReleaseEvidence.publication(candidate, new Download(SOURCE, SOURCE, README, REPOSITORY, URL, "UNKNOWN", 200, apk))));
        check.same("A raw URL fails even if it returns the right bytes", "Wrong download route or HTTP status", decision(() -> ReleaseEvidence.publication(
                candidate, new Download(SOURCE, SOURCE, README, REPOSITORY, "https://raw.githubusercontent.com/" + REPOSITORY + "/main/app.apk", "AUTHENTICATED", 200, apk))));
        check.same("A partial HTTP response fails even if its file matches", "Wrong download route or HTTP status",
                decision(() -> ReleaseEvidence.publication(candidate, new Download(SOURCE, SOURCE, README, REPOSITORY, URL, "AUTHENTICATED", 206, apk))));
        byte[] altered = Files.readAllBytes(apk);
        altered[altered.length / 2] ^= 1;
        Path sameLength = Files.write(folder.resolve("same-length.apk"), altered);
        check.same("A substituted APK of the same size fails integrity", "APK bytes or SHA-256 differ from qualification",
                decision(() -> ReleaseEvidence.publication(candidate, new Download(SOURCE, SOURCE, README, REPOSITORY, URL, "AUTHENTICATED", 200, sameLength))));
    }

    @Test void rootReadmeLeadsToTheVersionDeclaredByAndroid(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String gradle = Files.readString(root.resolve("android/app/build.gradle"));
        var version = java.util.regex.Pattern.compile("(?m)^\\s*versionName '([0-9]+\\.[0-9]+\\.[0-9]+)'$").matcher(gradle);
        if (!version.find()) throw new IOException("Missing Android versionName");
        String name = version.group(1);
        String base = "https://github.com/crististefanita/atf_j7/releases/";
        String readme = Files.readString(root.resolve("README.md"));
        check.same("Jocul links to the matching release and exact APK", List.of(base + "tag/v" + name, base + "download/v" + name + "/ChessLab-" + name + ".apk"),
                ReleaseEvidence.gameLinks(readme));
        String game = readme.substring(readme.indexOf("## Jocul"), readme.indexOf("## Proiectul"));
        check.same("Integrity metadata stays out of the download section", false,
                java.util.regex.Pattern.compile("(?i)sha[- ]?256|checksum|\\bbytes?\\b|[a-f0-9]{64}").matcher(game).find());
    }

    @Test void ragRetrievesOnlyAffectedGuidanceAndVerifiesTheAnchor(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        var citations = ReleaseRag.retrieve(root, List.of("android/app/src/main/AndroidManifest.xml", "README.md"));
        check.same("Two affected contracts cited", List.of("## Sanity Android", "## Calificare și publicare"), citations.stream().map(ReleaseRag.Citation::heading).toList());
        check.same("The cited document bytes match their hash", ReleaseEvidence.digest(root.resolve(citations.getFirst().path())), citations.getFirst().sha256());
        var chapter = ReleaseRag.retrieve(root, List.of("chess-core/src/main/resources/mate-in-three.yaml", "docs/feedback/README.md"));
        check.same("A chapter release cites its independent oracle and open feedback", List.of("## Motor și proiecție", "## Mat în trei mutări", "# Feedback · Chess Lab"),
                chapter.stream().map(ReleaseRag.Citation::heading).toList());
        var board = ReleaseRag.retrieve(root, List.of("android/app/src/main/java/ro/atf/chess/android/BoardView.java"));
        check.same("Board changes cite native sanity and TalkBack evidence", List.of("## Sanity Android", "## Proba TalkBack"),
                board.stream().map(ReleaseRag.Citation::heading).toList());
        var history = ReleaseRag.retrieve(root, List.of("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java", "README.md"));
        check.same("History changes cite the match probe and release workflow", List.of("## Sanity Android", "## Partidă și istoric", "## Calificare și publicare"),
                history.stream().map(ReleaseRag.Citation::heading).toList());
        var tactics = ReleaseRag.retrieve(root, List.of("chess-core/src/main/resources/tactics.yaml",
                "chess-core/src/main/java/ro/atf/chess/QuestRewards.java"));
        check.same("The new catalog and reward policy cite their specific verification",
                List.of("TacticsCatalogTest și verify-tactics.cjs", "QuestRewardsTest și MainMenuLayoutTest"),
                tactics.stream().filter(item -> item.heading().equals("## Tactică și punctaj")).map(ReleaseRag.Citation::check).toList());
        var steps = ReleaseRag.workflow(root);
        check.same("The release workflow follows the eight required steps", List.of(1, 2, 3, 4, 5, 6, 7, 8),
                steps.stream().map(line -> Integer.parseInt(line.substring(0, line.indexOf('.')))).toList());
        check.same("The last step verifies the download again after pushing the evidence", true,
                steps.getLast().contains("repetați descărcarea"));
        check.same("A path outside the route table has no citation", List.of(), ReleaseRag.retrieve(root, List.of("unknown/foo")));
        Path missing = Files.createDirectories(folder.resolve("docs/testing")).resolve("README.md");
        Files.writeString(missing, "# No known anchor");
        check.same("A missing anchor fails with the source", "Missing citation anchor: docs/testing/README.md ## Sanity Android",
                decision(() -> ReleaseRag.retrieve(folder, List.of("android/app/build.gradle"))));
    }

    @Test void ragCitesTheAiProtocolForEachEngineInputAndKeepsReadmeFocused(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        var aiPaths = List.of("chess-core/src/main/resources/ai-repertoire.yaml", "chess-core/src/main/resources/match-ai.yaml",
                "chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java", "chess-core/src/main/java/ro/atf/chess/ChessMatch.java");
        for (String source : aiPaths) {
            var citations = ReleaseRag.retrieve(root, List.of(source));
            var ai = citations.stream().filter(citation -> citation.path().equals("docs/board/ai-quality.md")).toList();
            check.same(source + " cites the Stockfish and league protocol", List.of("ChessBenchmarkSuite: Stockfish și liga offline"),
                    ai.stream().map(ReleaseRag.Citation::check).toList());
        }
        check.same("A multi-file AI diff cites the protocol once", 1L,
                ReleaseRag.retrieve(root, aiPaths).stream().filter(citation -> citation.path().equals("docs/board/ai-quality.md")).count());
        check.same("A root README-only change cites just the release workflow", List.of("docs/release/README.md"),
                ReleaseRag.retrieve(root, List.of("README.md")).stream().map(ReleaseRag.Citation::path).toList());
    }

    @Test void openingNavigationRoutesToItsSignedSanityAndComponentTest(Checks check) throws IOException {
        var paths = List.of("chess-core/src/main/java/ro/atf/chess/OpeningLesson.java",
                "android/app/src/main/java/ro/atf/chess/android/OpeningActivity.java", "docs/board/openings.md");
        var citations = ReleaseRag.retrieve(ProjectRoot.locate(), paths).stream()
                .filter(citation -> citation.heading().equals("## Deschideri")).toList();
        check.same("Opening changes cite the component and Android release tests once",
                List.of("OpeningLessonTest și BoardSanity -IncludeOpenings"),
                citations.stream().map(ReleaseRag.Citation::check).toList());
    }

    private Suite suite(String name, String installedApk) throws IOException {
        Path html = Files.writeString(folder.resolve(name + ".html"), "<html>" + name + " fixture</html>");
        return new Suite(SOURCE, installedApk, 3, true, ReleaseEvidence.digest(html), html);
    }

    private void publish(String tag, String readme, int status, Path downloaded) throws IOException {
        ReleaseEvidence.publication(candidate, new Download(tag, OTHER, readme, REPOSITORY, URL, "ANONYMOUS", status, downloaded));
    }

    private static String decision(Action action) {
        try {
            action.run();
            return "accepted";
        } catch (IOException blocked) {
            return blocked.getMessage();
        }
    }

    @FunctionalInterface private interface Action { void run() throws IOException; }
}
