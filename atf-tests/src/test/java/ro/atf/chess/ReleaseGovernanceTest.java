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
import ro.atf.chess.release.ReleaseEvidence.InstallationException;
import ro.atf.chess.release.ReleaseEvidence.Suite;
import ro.atf.chess.release.ReleaseGate;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ReleaseGovernanceTest {
    @Test void ragReportsCoverageForEveryPathInAMixedDiff(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var pom = ReleaseRag.inspect(root, List.of("pom.xml"));
        check.same("The root Maven configuration has release guidance", List.of("pom.xml"), pom.covered());
        check.same("The POM cites the qualification workflow", List.of("## Calificare și publicare"),
                pom.citations().stream().map(ReleaseRag.Citation::heading).toList());
        String board = "android/app/src/main/java/ro/atf/chess/android/BoardView.java";
        var mixed = ReleaseRag.inspect(root, List.of("pom.xml", "misc/new-tool", board.replace('/', '\\'), board));
        check.same("Covered paths retain input order and normalize Windows separators", List.of("pom.xml", board), mixed.covered());
        check.same("The unmapped path remains visible when other paths have citations", List.of("misc/new-tool"), mixed.needsReview());
        var archive = ReleaseRag.inspect(root, List.of("chess-core/src/main/java/ro/atf/chess/GameReviewCodec.java"));
        check.same("Review cache changes point to archive, slot and menu components", true,
                archive.citations().stream().anyMatch(citation -> citation.check().contains("MatchArchiveTest")
                        && citation.check().contains("-MatchMenusOnly")));
        var menus = ReleaseRag.inspect(root, List.of("android/app/src/main/res/layout/match_list.xml"));
        check.same("Changing game list layout selects component and both focused Android checks", true,
                menus.citations().stream().anyMatch(citation -> citation.check().contains("MainMenuLayoutTest")
                        && citation.check().contains("-MatchMenusOnly")));
    }

    @Test void libraryPackagingMetadataRoutesToItsLocalAuditOwner(Checks check) throws IOException {
        var paths = List.of(".gitattributes", "library-provenance.json");
        var result = ReleaseRag.inspect(ProjectRoot.locate(), paths);
        check.same("Both packaging metadata files are covered", paths, result.covered());
        check.same("Byte and provenance rules share one deduplicated audit route", List.of("PlatformLibraryProvenanceTest"),
                result.citations().stream().map(ReleaseRag.Citation::check).toList());
    }
    private static final String SOURCE = "1".repeat(40);
    private static final String OTHER = "2".repeat(40);
    private static final String REPOSITORY = "example/atf_j7";
    private static final String BASE = "https://github.com/" + REPOSITORY + "/releases/";
    private static final String URL = BASE + "download/v0.1.0/ChessLab-0.1.0.apk";
    private static final String README = "# Chess Lab\n\nȘah și studiu.\n\n## Jocul\n\n"
            + "- [Release](" + BASE + "tag/v0.1.0)\n- [APK](" + URL + ")\n\n## În joc\n\n[Studiu](docs/board/README.md)\n";

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

    @Test void exactInstallExceptionIsNarrowExplicitAndBackedBySupplementalExtent(Checks check) throws IOException {
        String supplementalApk = "3".repeat(64);
        InstallationException exception = exception("PUBLISHED_WITH_INSTALL_EXCEPTION", "exact-distribution-install",
                "USER_DIRECTED_MAIN_README_PUBLICATION", "The available emulator retains the prior signer and user data.",
                SOURCE, candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                android.htmlSha256(), android.html());
        check.same("Explicit publication exception accepts supplemental evidence without claiming exact install",
                "accepted", decision(() -> ReleaseEvidence.installationException(candidate, exception)));
        check.same("Unscoped exception cannot bypass exact APK install", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception("PASS", exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("outside its narrow scope"));
        check.same("Publication needs the user's explicit direction", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), "UNAUTHORIZED", exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("explicit publication direction"));
        check.same("Publication exception must explain the blocked installation", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), " ", SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("no concrete reason"));
        check.same("A different candidate commit blocks the exception", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), OTHER,
                                candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("another source commit"));
        check.same("A different distribution APK blocks the exception", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                "4".repeat(64), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("another distribution APK"));
        check.same("Supplemental sanity cannot masquerade as the distribution APK", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), candidate.apkSha256(), 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("different APK"));
        check.same("Zero supplemental assertions cannot justify the exception", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 0, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("only supplemental assertions"));
        check.same("Exception must never claim the distribution APK was installed", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, true, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), android.html()))).contains("only supplemental assertions"));
        check.same("An inconsistent signing transition blocks the exception", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, false, "UPGRADE_COMPATIBLE",
                                android.htmlSha256(), android.html()))).contains("inconsistent signing transition"));
        Path changedExtent = Files.writeString(folder.resolve("changed-android.html"), "not the supplied Extent");
        check.same("Changed supplemental Extent bytes block the exception", true,
                decision(() -> ReleaseEvidence.installationException(candidate,
                        exception(exception.status(), exception.scope(), exception.authorization(), exception.reason(), SOURCE,
                                candidate.apkSha256(), supplementalApk, 94, false, "CLEAN_INSTALL_REQUIRED",
                                android.htmlSha256(), changedExtent))).contains("missing or changed"));
    }

    @Test void signingPreflightRejectsMissingAndNonPassingGovernanceReceipts(Checks check) throws IOException {
        String xml = "<testsuite name=\"ro.atf.chess.%s\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>";
        String documentation = "DocumentationGovernanceTest";
        check.rejects("Missing documentation execution blocks signing", IllegalStateException.class, () -> ReleaseGate.preflight(folder));
        for (String name : List.of(documentation, "ReleaseGovernanceTest", "ExtentGovernanceTest")) {
            Files.writeString(folder.resolve("TEST-ro.atf.chess." + name + ".xml"), xml.formatted(name));
        }
        check.same("Three passing governance classes allow signing", "accepted", decision(() -> ReleaseGate.preflight(folder)));
        String extent = "ExtentGovernanceTest";
        Path receipt = folder.resolve("TEST-ro.atf.chess." + extent + ".xml");
        for (String field : List.of("failures", "errors", "skipped")) {
            Files.writeString(receipt, xml.formatted(extent).replace(field + "=\"0\"", field + "=\"1\""));
            check.rejects("Signing rejects " + field + " in the Extent checks", IllegalStateException.class, () -> ReleaseGate.preflight(folder));
        }
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

    @Test void aNewSigningCertificateCannotMasqueradeAsAnInPlaceUpgrade(Checks check) throws IOException {
        String previous = "4f15d961364d331531f78348b4aa167ad87ceceac442126ef802a8202a50344f";
        String candidate = "8a572946eeb3d73b517efcf053e2066616dd121c9f742dd4cb3b764aaa725416";
        check.same("The existing release signer keeps the in-place upgrade path", "UPGRADE_COMPATIBLE",
                ReleaseEvidence.signingContinuity(previous, previous, "UPGRADE"));
        check.same("A different certificate is accepted only as an explicit clean install", "CLEAN_INSTALL_REQUIRED",
                ReleaseEvidence.signingContinuity(previous, candidate, "CLEAN_INSTALL"));
        check.same("The old app cannot be silently updated with a new signer", true,
                decision(() -> ReleaseEvidence.signingContinuity(previous, candidate, "UPGRADE"))
                        .contains("in-place upgrade is blocked"));
        check.same("Unknown installation modes cannot bypass signer continuity", true,
                decision(() -> ReleaseEvidence.signingContinuity(previous, candidate, "AUTO"))
                        .contains("Unknown signing transition mode"));
        check.same("Malformed certificate fingerprints fail closed", true,
                decision(() -> ReleaseEvidence.signingContinuity("not-a-certificate", candidate, "CLEAN_INSTALL"))
                        .contains("Invalid signing certificate fingerprint"));
    }

    @Test void signingRecoveryUsesTheProtectedIdentityAndExplicitlyGatesRotation(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String initializer = Files.readString(root.resolve("scripts/Initialize-J7Signing.ps1"));
        check.same("Signing secrets use a protected external DPAPI record", true,
                initializer.contains("password.dpapi") && initializer.contains("ConvertFrom-SecureString")
                        && initializer.contains("SetAccessRuleProtection"));
        check.same("A missing published key cannot rotate silently", true,
                initializer.contains("release/index.json")
                        && initializer.contains("qualification.json") && initializer.contains("build-artifact.json")
                        && initializer.contains("Published signing evidence is missing")
                        && initializer.contains("-StartNewIdentity")
                        && initializer.contains("accept a clean-install transition"));
        check.same("Incomplete key records are preserved for recovery", true,
                initializer.contains("Incomplete J7 signing identity") && initializer.contains("do not rotate automatically"));
        check.same("The actual keystore alias is checked against the protected certificate manifest", true,
                initializer.contains("identity-verification.log") && initializer.contains("stored keystore certificate differs")
                        && initializer.contains("(Get-Content -Raw -LiteralPath $credential).Trim()"));
        var rag = ReleaseRag.inspect(root, List.of("scripts/Initialize-J7Signing.ps1", "scripts/verify-signing-continuity.ps1"));
        check.same("Both signing operations cite the same owner guidance", List.of("docs/release/README.md"),
                rag.citations().stream().map(ReleaseRag.Citation::path).distinct().toList());
        check.same("The signer path is fully governed", List.of(), rag.needsReview());
    }

    @Test void readmeKeepsTheDownloadSectionFirstAndRejectsOtherApkRoutes(Checks check) throws IOException {
        check.same("A brief introduction, two canonical bullets and a guide qualify", "accepted",
                decision(() -> publish(SOURCE, README.replace("\n", "\r\n"), 200, apk)));
        check.same("An introductory section cannot precede the download", "Jocul must be the first section",
                decision(() -> ReleaseEvidence.gameLinks(README.replace("Șah și studiu.", "## Introducere\n\nȘah și studiu."))));
        check.same("Prose appended to a link is rejected", "Jocul must contain exactly two link bullets",
                decision(() -> ReleaseEvidence.gameLinks(README.replace(URL + ")", URL + ") — APK Android semnat."))));
        check.same("A label cannot carry integrity metadata", "Integrity metadata belongs in the release evidence",
                decision(() -> ReleaseEvidence.gameLinks(README.replace("[APK]", "[APK SHA-256]"))));
        check.same("A paragraph between download bullets is rejected", "Jocul must contain exactly two link bullets",
                decision(() -> ReleaseEvidence.gameLinks(README.replace("- [APK]", "Descarcă aici.\n\n- [APK]"))));
        check.same("A duplicate download section is rejected", "Duplicate Jocul section",
                decision(() -> ReleaseEvidence.gameLinks(README + "\n## Jocul\n")));
        check.same("A repeated canonical APK link in the introduction is rejected", "APK route outside Jocul",
                decision(() -> ReleaseEvidence.gameLinks(README.replace("Șah și studiu.", "[Alt traseu](" + URL + ")"))));
        for (String route : List.of("[copie](dist/ChessLab.apk)", "[copie]: https://example.org/ChessLab%2EAPK?dl=1",
                "<https://example.org/ChessLab.apk#download>", "<a href=\"https://example.org/ChessLab.apk\">Copie</a>")) {
            check.same("An APK route outside Jocul is rejected · " + route, "APK route outside Jocul",
                    decision(() -> ReleaseEvidence.gameLinks(README + "\n" + route + "\n")));
        }
    }

    @Test void rootReadmePublishesTheCandidateOnlyAfterDistributionEvidenceExists(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String gradle = Files.readString(root.resolve("android/app/build.gradle"));
        var version = java.util.regex.Pattern.compile("(?m)^\\s*versionName '([0-9]+\\.[0-9]+\\.[0-9]+)'$").matcher(gradle);
        if (!version.find()) throw new IOException("Missing Android versionName");
        String name = version.group(1);
        String base = "https://github.com/crististefanita/atf_j7/releases/";
        List<String> links = ReleaseEvidence.gameLinks(Files.readString(root.resolve("README.md")));
        Path publication = root.resolve("release").resolve(name).resolve("reports/distribution-verification.json");
        boolean candidatePublished = Files.isRegularFile(publication)
                && Files.readString(publication).contains("\"status\": \"PASS\"");
        if (candidatePublished) {
            check.same("Jocul links to the published candidate and exact APK", List.of(base + "tag/v" + name, base + "download/v" + name + "/ChessLab-" + name + ".apk"), links);
        } else {
            check.same("Jocul withholds an unqualified candidate from the download links", false,
                    links.contains(base + "tag/v" + name) || links.contains(base + "download/v" + name + "/ChessLab-" + name + ".apk"));
        }
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
        check.same("History changes cite both consumers and the release workflow",
                List.of("## Sanity Android", "## Partidă și istoric", "## Tablă de analiză", "## Calificare și publicare"),
                history.stream().map(ReleaseRag.Citation::heading).toList());
        var hints = ReleaseRag.retrieve(root, List.of("chess-core/src/main/java/ro/atf/chess/MatchHint.java",
                "android/app/src/main/java/ro/atf/chess/android/MatchAnalysisView.java"));
        check.same("Shared hints cite both signed Android journeys once",
                List.of("MatchHintTest și BoardSanity -Distribution -AnalysisOnly -IncludeManualAnalysis"),
                hints.stream().filter(item -> item.heading().equals("## Partidă și istoric")).map(ReleaseRag.Citation::check).toList());
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

    @Test void ragSelectsDocumentationAndAuditChecksBeforeDistribution(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        var citations = ReleaseRag.inspect(root, List.of("docs/testing/README.md", "docs/release/README.md",
                "docs/feedback/README.md", "scripts/run-android-sanity.ps1"));
        check.same("All affected documents and Android runner are routed", List.of(), citations.needsReview());
        check.same("Native evidence cites the audit contract", List.of("ExtentGovernanceTest"),
                citations.citations().stream().filter(item -> item.heading().equals("## Raportare Extent"))
                        .map(ReleaseRag.Citation::check).toList());
        var steps = ReleaseRag.workflow(root);
        check.same("The pre-build step requires all three governance checks", true,
                steps.get(3).contains("DocumentationGovernanceTest") && steps.get(3).contains("ReleaseGovernanceTest")
                        && steps.get(3).contains("ExtentGovernanceTest") && steps.get(4).contains("Gradle"));
    }

    @Test void ragCitesTheAiProtocolForEachEngineInputAndKeepsReadmeFocused(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        var aiPaths = List.of("chess-core/src/main/resources/ai-repertoire.yaml", "chess-core/src/main/resources/match-ai.yaml",
                "chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java", "chess-core/src/main/java/ro/atf/chess/ChessMatch.java");
        for (String source : aiPaths) {
            var citations = ReleaseRag.retrieve(root, List.of(source));
            var ai = citations.stream().filter(citation -> citation.path().equals("docs/board/ai-quality.md")).toList();
            check.same(source + " cites Hard-transfer, history and external AI controls",
                    List.of("HardQuestTransferBenchmarkTest, RookEndgameHistoryTest și ChessBenchmarkSuite: Stockfish și liga offline"),
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

    @Test void gameReviewRoutesToItsComponentGovernanceAndSignedAndroidChecks(Checks check) throws IOException {
        var paths = List.of("chess-core/src/main/java/ro/atf/chess/GameReview.java",
                "chess-core/src/main/java/ro/atf/chess/GameReviewTimeline.java", "chess-core/src/main/resources/opening-book.tsv",
                "chess-core/src/main/java/ro/atf/chess/GameReviewLayout.java",
                "android/app/src/main/java/ro/atf/chess/android/GameReviewDialog.java",
                "android/app/src/main/java/ro/atf/chess/android/GameReviewWorkspace.java");
        var citations = ReleaseRag.retrieve(ProjectRoot.locate(), paths).stream()
                .filter(citation -> citation.heading().equals("## Partidă și istoric")).toList();
        check.same("Game review changes cite the signed scenario and both JVM layers once",
                List.of("GameReviewTest, GameReviewTimelineTest, GameReviewLayoutTest, GameReviewGovernanceTest și BoardSanity -Distribution -GameReviewOnly"),
                citations.stream().map(ReleaseRag.Citation::check).toList());
    }

    @Test void questStorageRoutesToOfflineContractsAndTheNativeRestart(Checks check) throws IOException {
        var paths = List.of("chess-core/src/main/java/ro/atf/chess/QuestStore.java",
                "android/app/src/main/java/ro/atf/chess/android/AndroidGamePreferences.java");
        var result = ReleaseRag.inspect(ProjectRoot.locate(), paths);
        check.same("Both storage layers have release guidance", paths, result.covered());
        check.same("The progress contract selects the component, receipt and restart probes",
                List.of("QuestStoreTest, AndroidReceiptTest și BoardSanity la restart"),
                result.citations().stream().filter(citation -> citation.heading().equals("## Progresul problemelor"))
                        .map(ReleaseRag.Citation::check).toList());
    }

    @Test void manualAnalysisAndAnchorChangesHaveDistinctVerification(Checks check) throws IOException {
        var paths = List.of("chess-core/src/main/java/ro/atf/chess/AnalysisLine.java",
                "android/app/src/main/java/ro/atf/chess/android/AnalysisActivity.java",
                "android/app/src/main/java/ro/atf/chess/android/ChessActivity.java",
                "chess-core/src/main/java/ro/atf/chess/QuestProgress.java",
                "atf-tests/src/test/resources/ai-anchor.yaml", "atf-tests/src/test/resources/ai-anchor-observations.yaml");
        var citations = ReleaseRag.retrieve(ProjectRoot.locate(), paths);
        check.same("Manual board changes require the native and component checks once",
                List.of("AnalysisLineTest, MainMenuLayoutTest și BoardSanity -ManualAnalysisOnly"),
                citations.stream().filter(item -> item.heading().equals("## Tablă de analiză")).map(ReleaseRag.Citation::check).toList());
        check.same("Anchor observations and protocol changes cite the same independent calibration",
                List.of("AiAnchorBenchmarkTest și GameReviewGovernanceTest"),
                citations.stream().filter(item -> item.heading().equals("### Rezultatele ancorei externe")).map(ReleaseRag.Citation::check).toList());
        check.same("Progress changes cite their component and native restart evidence",
                List.of("QuestStoreTest, AndroidReceiptTest și BoardSanity la restart"),
                citations.stream().filter(item -> item.heading().equals("## Progresul problemelor")).map(ReleaseRag.Citation::check).toList());
    }

    private Suite suite(String name, String installedApk) throws IOException {
        Path html = Files.writeString(folder.resolve(name + ".html"), "<html>" + name + " fixture</html>");
        return new Suite(SOURCE, installedApk, 3, true, ReleaseEvidence.digest(html), html);
    }

    private InstallationException exception(String status, String scope, String authorization, String reason,
                                            String commit, String distributionApk, String supplementalApk,
                                            int assertions, boolean installed, String signingStatus,
                                            String extentSha, Path extent) {
        return new InstallationException(status, scope, authorization, reason, commit, distributionApk,
                supplementalApk, assertions, installed, signingStatus, extentSha, extent);
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
