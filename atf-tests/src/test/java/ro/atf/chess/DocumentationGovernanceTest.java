package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import ro.atf.chess.release.ReleaseEvidence;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class DocumentationGovernanceTest {
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern GRADLE_INCLUDE = Pattern.compile("(?m)^\\h*include\\h*\\(?([^\\r\\n]*)");
    private static final Pattern GRADLE_SOURCE = Pattern.compile("(?m)^\\h*sourceSets\\.main\\.(java|resources)\\.srcDir\\h*(['\"])([^'\"]+)\\2\\h*$");
    private static final Pattern GRADLE_LITERAL = Pattern.compile("(['\"])([^'\"]+)\\1");

    @TempDir Path folder;

    @Test void currentDocumentationResolvesEveryLocalTargetAndHeading(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        Path readme = root.resolve("README.md");
        var documents = new ArrayList<>(List.of(readme, root.resolve("release/README.md")));
        try (var libraries = Files.newDirectoryStream(root.resolve("lib"), "README*.md")) {
            for (Path library : libraries) if (Files.isRegularFile(library)) documents.add(library);
        }
        String tag = ReleaseEvidence.gameLinks(Files.readString(readme)).getFirst();
        String current = tag.substring(tag.lastIndexOf('/') + 2);
        String previous = jsonValue(Files.readString(root.resolve("release/index.json")), "previousVersion");
        for (String version : List.of(current, previous)) {
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) continue;
            Path release = root.resolve("release").resolve(version);
            documents.add(release.resolve("README.md"));
            documents.add(release.resolve("reports/README.md"));
        }
        try (var paths = Files.walk(root.resolve("docs"))) {
            paths.filter(path -> path.toString().endsWith(".md")).sorted().forEach(documents::add);
        }
        var failures = new ArrayList<String>();
        for (Path document : documents) {
            if (!Files.isRegularFile(document)) {
                failures.add(root.relativize(document) + " is missing");
                continue;
            }
            for (String broken : brokenLinks(root, document)) failures.add(root.relativize(document) + " → " + broken);
        }
        check.same("Local links resolve in the guides, both retained editions and feedback", List.of(), failures);
    }

    @Test void authoredGuidesKeepPortableMarkdownAndExplainedDiagrams(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        var documents = new ArrayList<>(List.of(root.resolve("README.md")));
        for (String directory : List.of("docs", "lib")) {
            try (var paths = Files.walk(root.resolve(directory))) {
                paths.filter(path -> path.toString().endsWith(".md")).sorted().forEach(documents::add);
            }
        }
        var failures = new ArrayList<String>();
        for (Path document : documents) {
            for (String violation : MarkdownVisualContract.inspect(Files.readAllLines(document))) {
                failures.add(root.relativize(document) + ":" + violation);
            }
        }
        check.same("Authored guides use portable Markdown and label and explain their diagrams", List.of(), failures);
    }

    @Test void rejectsUnlabelledDiagramAndUntypedExample(Checks check) {
        check.same("A diagram without a relationship label or adjacent legend fails and an untyped block fails",
                List.of("2 diagram edge needs a label", "5 diagram needs an adjacent collapsed legend", "7 fence needs a language"),
                MarkdownVisualContract.inspect(List.of("# Guide", "```mermaid", "flowchart LR", "    A --> B", "```", "",
                        "```", "ignored --> example", "```")));
    }

    @Test void mainReadmeLinksOnlyToAReleaseWithCompletePublicationEvidence(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        List<String> links = ReleaseEvidence.gameLinks(Files.readString(root.resolve("README.md")));
        String releaseUrl = links.getFirst();
        String assetUrl = links.getLast();
        String tag = releaseUrl.substring(releaseUrl.lastIndexOf("/tag/") + 5);
        Path reports = root.resolve("release").resolve(tag.substring(1)).resolve("reports");
        Path githubRelease = reports.resolve("github-release.json");
        Path publicDownload = reports.resolve("public-download.json");
        Path authenticatedDownload = reports.resolve("distribution-verification.json");
        Path qualification = reports.resolve("qualification.json");
        Path buildArtifact = reports.resolve("build-artifact.json");
        Path installationException = reports.resolve("publication-exception.json");
        Path supplementalSanity = reports.resolve("android-signed-sanity.json");
        Path supplementalExecution = reports.resolve("android-signed-sanity-execution.json");
        Path signingTransition = reports.resolve("signing-transition.json");
        Path supplementalExtent = reports.resolve("android-signed-sanity.html");
        boolean evidenceExists = List.of(githubRelease, publicDownload, authenticatedDownload)
                .stream().allMatch(Files::isRegularFile);
        boolean complete = false;
        if (evidenceExists) {
            String release = Files.readString(githubRelease);
            String direct = Files.readString(publicDownload);
            String authenticated = Files.readString(authenticatedDownload);
            String sourceCommit = "";
            String apkSha = "";
            String apkBytes = "";
            boolean qualifiedOrException = false;
            if (Files.isRegularFile(qualification)) {
                var candidate = ArchivedReleaseEvidence.verify(reports, tag.substring(1));
                sourceCommit = candidate.commit();
                apkSha = candidate.apkSha256();
                apkBytes = Long.toString(candidate.apkBytes());
                qualifiedOrException = true;
            } else if (List.of(buildArtifact, installationException, supplementalSanity, supplementalExecution,
                    signingTransition, supplementalExtent).stream().allMatch(Files::isRegularFile)) {
                String build = Files.readString(buildArtifact);
                sourceCommit = jsonValue(build, "sourceCommit");
                apkSha = jsonValue(build, "apkSha256");
                apkBytes = jsonValue(build, "apkBytes");
                String sanity = Files.readString(supplementalSanity);
                String execution = Files.readString(supplementalExecution);
                String signing = Files.readString(signingTransition);
                String exception = Files.readString(installationException);
                try {
                    var candidate = new ReleaseEvidence.Candidate(jsonValue(build, "version"), sourceCommit,
                            Long.parseLong(apkBytes), apkSha);
                    var receipt = new ReleaseEvidence.InstallationException(jsonValue(exception, "status"),
                            jsonValue(exception, "scope"), jsonValue(exception, "authorization"),
                            jsonValue(exception, "reason"), jsonValue(exception, "candidateCommit"),
                            jsonValue(exception, "candidateApkSha256"), jsonValue(exception, "supplementalApkSha256"),
                            Integer.parseInt(jsonValue(exception, "supplementalAssertions")),
                            jsonValue(exception, "distributionInstalled").equals("true"),
                            jsonValue(exception, "signingStatus"), jsonValue(exception, "supplementalExtentSha256"),
                            supplementalExtent);
                    ReleaseEvidence.installationException(candidate, receipt);
                    String extentSha = ReleaseEvidence.digest(supplementalExtent);
                    qualifiedOrException = candidate.version().equals(tag.substring(1))
                            && jsonValue(build, "signatureV2Verified").equals("true")
                            && jsonValue(build, "zipalignVerified").equals("true")
                            && jsonValue(build, "status").equals("PASS")
                            && jsonValue(sanity, "sourceCommit").equals(sourceCommit)
                            && jsonValue(sanity, "status").equals("PASS_SUPPLEMENTAL_ONLY")
                            && jsonValue(sanity, "distributionPackageInstalled").equals("false")
                            && jsonValue(exception, "supplementalApkSha256").equals(jsonValue(sanity, "apkSha256"))
                            && jsonValue(exception, "supplementalAssertions").equals(jsonValue(sanity, "assertions"))
                            && jsonValue(sanity, "extentSha256").equals(extentSha)
                            && jsonValue(execution, "sourceCommit").equals(sourceCommit)
                            && jsonValue(execution, "status").equals("PASS")
                            && jsonValue(execution, "apkSha256").equals(jsonValue(sanity, "apkSha256"))
                            && jsonValue(execution, "assertions").equals(jsonValue(sanity, "assertions"))
                            && jsonValue(execution, "extentSha256").equals(extentSha)
                            && jsonValue(signing, "result").equals("CLEAN_INSTALL_REQUIRED")
                            && jsonValue(signing, "apkSha256").equals(apkSha)
                            && jsonValue(signing, "candidateCertificateSha256").equals(jsonValue(build, "certificateSha256"))
                            && jsonValue(exception, "status").equals("PUBLISHED_WITH_INSTALL_EXCEPTION");
                } catch (IllegalArgumentException | IOException invalidReceipt) {
                    qualifiedOrException = false;
                }
            }
            String releaseCommit = jsonValue(release, "targetCommit");
            if (releaseCommit.isBlank()) releaseCommit = jsonValue(release, "candidateCommit");
            complete = qualifiedOrException
                    && jsonValue(release, "tag").equals(tag)
                    && jsonValue(release, "releaseUrl").equals(releaseUrl)
                    && jsonValue(release, "assetUrl").equals(assetUrl)
                    && releaseCommit.equals(sourceCommit)
                    && jsonValue(direct, "status").equals("PASS")
                    && jsonValue(direct, "assetUrl").equals(assetUrl)
                    && jsonValue(direct, "httpStatus").equals("200")
                    && jsonValue(direct, "zipCrc").equals("PASS")
                    && jsonValue(direct, "sha256").equals(apkSha)
                    && jsonValue(direct, "bytes").equals(apkBytes)
                    && jsonValue(authenticated, "status").equals("PASS")
                    && jsonValue(authenticated, "tag").equals(tag)
                    && jsonValue(authenticated, "targetCommit").equals(sourceCommit)
                    && jsonValue(authenticated, "releaseUrl").equals(releaseUrl)
                    && jsonValue(authenticated, "assetUrl").equals(assetUrl)
                    && jsonValue(authenticated, "channel").toLowerCase(Locale.ROOT).contains("authenticated")
                    && jsonValue(authenticated, "httpStatus").equals("200")
                    && jsonValue(authenticated, "zipCrc").equals("PASS")
                    && jsonValue(authenticated, "sha256").equals(apkSha)
                    && jsonValue(authenticated, "bytes").equals(apkBytes);
        }
        check.same("The main README links only to a release with strict qualification or a scoped, evidenced install exception and complete downloads",
                true, complete);
    }

    @Test void strictReleaseRejectsChangedReportsAndForeignAndroidReceipts(Checks check) throws IOException {
        Path reports = Files.createDirectory(folder.resolve("reports"));
        Path components = reports.resolve("components.html");
        Path android = reports.resolve("android-game-review.html");
        Path execution = reports.resolve("game-review-execution.json");
        Files.writeString(components, "<html><body>Component evidence</body></html>");
        String nativeReport = "<html><body>Android evidence</body></html>";
        Files.writeString(android, nativeReport);
        String commit = "a".repeat(40);
        String apk = "b".repeat(64);
        var json = new ObjectMapper();
        var qualified = json.createObjectNode().put("version", "0.13.3").put("qualificationGate", "PASS")
                .put("sourceCommit", commit).put("apkSha256", apk).put("apkBytes", 1042)
                .put("componentTests", 7).put("componentExtentSha256", ReleaseEvidence.digest(components));
        var nativeQualification = qualified.putObject("android").putObject("game-review")
                .put("sourceCommit", commit).put("nativeAssertions", 2).put("extentSha256", ReleaseEvidence.digest(android))
                .put("report", android.getFileName().toString()).put("execution", execution.getFileName().toString());
        Path qualification = reports.resolve("qualification.json");
        json.writeValue(qualification.toFile(), qualified);
        ObjectNode receipt = json.createObjectNode().put("status", "PASS").put("scope", "game-review")
                .put("sourceCommit", commit).put("apkSha256", apk).put("variant", "release").put("assertions", 2);
        json.writeValue(execution.toFile(), receipt);

        check.same("The original local reports and receipt identify the qualified candidate",
                new ReleaseEvidence.Candidate("0.13.3", commit, 1042, apk), ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        Files.writeString(components, "<html><body>Unrelated component report</body></html>");
        check.rejects("A substituted component report is rejected", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        Files.writeString(components, "<html><body>Component evidence</body></html>");
        Files.writeString(android, "<html><body>Unrelated Android report</body></html>");
        check.rejects("A different valid HTML report is rejected", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        Files.delete(android);
        check.rejects("A missing Android report is rejected", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        Files.writeString(android, nativeReport);
        receipt.put("sourceCommit", "c".repeat(40));
        json.writeValue(execution.toFile(), receipt);
        check.rejects("A foreign receipt with the same assertion count is rejected", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        receipt.put("sourceCommit", commit).put("variant", "sanity");
        json.writeValue(execution.toFile(), receipt);
        check.rejects("A supplemental sanity receipt cannot stand in for release", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
        receipt.put("variant", "release");
        json.writeValue(execution.toFile(), receipt);
        Files.writeString(folder.resolve("external.html"), nativeReport);
        nativeQualification.put("report", "../external.html");
        json.writeValue(qualification.toFile(), qualified);
        check.rejects("Even matching bytes outside the edition are rejected", IOException.class,
                () -> ArchivedReleaseEvidence.verify(reports, "0.13.3"));
    }

    @Test void theComparisonEditionKeepsReadableArchivedQualification(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String version = jsonValue(Files.readString(root.resolve("release/index.json")), "previousVersion");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            check.same("The comparison edition must have a valid version", true, false);
            return;
        }
        Path reports = root.resolve("release").resolve(version).resolve("reports");
        if (Files.isRegularFile(reports.resolve("qualification.json"))) {
            check.same("The retained comparison edition passes offline archive verification", version,
                    ArchivedReleaseEvidence.verify(reports, version).version());
        } else {
            check.same("A comparison edition without strict qualification has an installation exception and build evidence", true,
                    List.of("publication-exception.json", "build-artifact.json", "github-release.json").stream()
                            .map(reports::resolve).allMatch(Files::isRegularFile));
        }
    }

    @Test void releaseCatalogKeepsTheCurrentAndOneComparisonVersion(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String catalog = Files.readString(root.resolve("release/index.json"));
        List<String> links = ReleaseEvidence.gameLinks(Files.readString(root.resolve("README.md")));
        String current = jsonValue(catalog, "currentVersion");
        String linkedTag = links.getFirst().substring(links.getFirst().lastIndexOf("/tag/") + 5);
        var versionBlock = Pattern.compile("\\\"versions\\\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(catalog);
        List<String> versions = new ArrayList<>();
        if (versionBlock.find()) {
            var entries = Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(versionBlock.group(1));
            while (entries.find()) versions.add(entries.group(1));
        }
        String android = Files.readString(root.resolve("android/app/build.gradle"));
        var androidVersion = Pattern.compile("(?m)^\\s*versionName '([0-9]+\\.[0-9]+\\.[0-9]+)'$").matcher(android);
        var androidCode = Pattern.compile("(?m)^\\s*versionCode ([0-9]+)$").matcher(android);
        String catalogVersion = jsonValue(catalog, "version");
        String catalogCode = jsonValue(catalog, "versionCode");
        String previous = jsonValue(catalog, "previousVersion");
        boolean candidate = !catalogVersion.isBlank() && !catalogVersion.equals(current);
        List<String> expectedVersions = List.of(current, previous);
        var releaseIndex = prose(root.resolve("release/README.md"));
        boolean releaseReadmeMatches = releaseIndex.stream().anyMatch(line -> line.startsWith("| Curentă | " + current + " |")
                && line.contains("(" + current + "/README.md)"))
                && releaseIndex.stream().anyMatch(line -> line.startsWith("| Precedentă | " + previous + " |")
                        && line.contains("(" + previous + "/README.md)"));
        boolean aligned = androidVersion.find() && androidCode.find() && current.equals(linkedTag.substring(1))
                && versions.equals(expectedVersions)
                && releaseReadmeMatches
                && (candidate ? catalogVersion.equals(androidVersion.group(1)) && !previous.isBlank()
                        : catalogVersion.equals(current) && !previous.isBlank() && !previous.equals(current))
                && catalogCode.equals(androidCode.group(1))
                && jsonValue(catalog, "repository").equals("crististefanita/atf_j7");
        check.same("The root points only to the current APK while the catalog retains at most one immediate comparison release", true, aligned);
        if (aligned) {
            try (var directories = Files.list(root.resolve("release"))) {
                List<String> local = directories.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                        .filter(name -> name.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
                        .toList();
                List<String> expectedLocal = candidate
                        ? java.util.stream.Stream.concat(versions.stream(), java.util.stream.Stream.of(catalogVersion)).toList()
                        : versions;
                check.same("Local release evidence keeps only current, immediate predecessor, and active candidate", true,
                        local.size() == expectedLocal.size() && local.containsAll(expectedLocal));
            }
        }
    }

    @Test void releaseAndTestingGuidesLeadToTheirEvidenceOwners(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        var release = prose(root.resolve("docs/release/README.md"));
        var testing = prose(root.resolve("docs/testing/README.md"));
        check.same("Release guidance links to suite commands and audit criteria", true,
                release.stream().anyMatch(line -> line.contains("../testing/README.md#suite-jvm"))
                        && release.stream().anyMatch(line -> line.contains("../testing/README.md#raportare-extent")));
        check.same("Audit guidance links to the qualification owner", true,
                testing.stream().anyMatch(line -> line.contains("../release/README.md#calificare-și-publicare")));
        check.same("The test strategy defines the level before the test inventory", true,
                testing.indexOf("## Alegerea nivelului") >= 0 && testing.indexOf("## Alegerea nivelului") < testing.indexOf("## Suite JVM"));
        check.same("The catalog leads to the owner of the progress verification", true,
                prose(root.resolve("docs/board/quest-curation.md")).stream().anyMatch(line -> line.contains("../testing/README.md#progresul-problemelor")));
    }

    @Test void androidVisualAssertionsMapScreenCoordinatesToCapturePixels(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String sanity = Files.readString(root.resolve("android/app/src/androidTest/java/ro/atf/chess/android/BoardSanity.java"));
        var testing = prose(root.resolve("docs/testing/README.md"));
        boolean scalesBothAxes = sanity.contains("getDefaultDisplay().getRealMetrics(display)")
                && sanity.contains("(double) image.getWidth() / display.widthPixels")
                && sanity.contains("(double) image.getHeight() / display.heightPixels");
        check.same("Android pixel checks map real screen bounds to both captured bitmap axes", true, scalesBothAxes);
        check.same("The test strategy records why raster and display dimensions can differ", true,
                testing.stream().anyMatch(line -> line.contains("Captura UI Automation")
                        && line.contains("dimensiuni raster") && line.contains("bitmapului")));
    }

    @Test void documentationStartsFromTheLocalOwnersAndTheirVerification(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        check.same("The project README links to its documentation index", true,
                prose(root.resolve("README.md")).stream().anyMatch(line -> line.contains("](docs/README.md)")));
        var index = prose(root.resolve("docs/README.md"));
        var targets = List.of("board/README.md", "board/quest-curation.md", "testing/README.md#alegerea-nivelului",
                "testing/README.md#raportare-extent", "release/README.md#calificare-și-publicare", "feedback/README.md");
        check.same("The index routes readers to each local owner", List.of(), targets.stream()
                .filter(target -> index.stream().noneMatch(line -> line.contains("](" + target + ")"))).toList());
        check.same("The document verification has a stable section", true, anchors(root.resolve("docs/README.md")).contains("verificarea-documentației"));
    }

    @Test void projectReadmeKeepsAnArchitectureMapAndDirectRoutes(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        check.same("README.md: architecture owners, release, testing and feedback are directly navigable",
                List.of(), missingReadmeRoutes(prose(root.resolve("README.md"))));
    }

    @Test void projectReadmeArchitectureMatchesTheExecutableBuild(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        var declarations = new BuildDeclarations(Files.readString(root.resolve("pom.xml")),
                Files.readString(root.resolve("atf-tests/pom.xml")), Files.readString(root.resolve("android/settings.gradle")),
                Files.readString(root.resolve("android/app/build.gradle")));
        var readme = prose(root.resolve("README.md"));
        check.same("README build topology agrees with Maven and Gradle declarations", List.of(), architectureDrift(readme, declarations));
    }

    @Test void documentedArchitectureRejectsMissingSourcesExtraModulesAndScopeDrift(Checks check) throws Exception {
        var readme = List.of("## Arhitectura J7",
                "- **Maven** compilează `atf-tests`, `chess-core/src/main/java`, `chess-core/src/main/resources` și `release-tools/src/main/java`.",
                "- **Gradle** construiește `:app` din `chess-core/src/main/java` și",
                "  `chess-core/src/main/resources`.",
                "- **Biblioteci ATF** folosește `atf-reporting`, `atf-test-kit`, cu scope `test`.",
                "## Puncte de intrare");
        String reactor = "<project><modules><module>atf-tests</module></modules></project>";
        String tests = """
                <project><dependencies>
                  <dependency><groupId>atf.api</groupId><artifactId>atf-reporting</artifactId><scope>test</scope></dependency>
                  <dependency><groupId>atf.api</groupId><artifactId>atf-test-kit</artifactId><scope>test</scope></dependency>
                </dependencies><build>
                  <sourceDirectory>../chess-core/src/main/java</sourceDirectory>
                  <resources><resource><directory>../chess-core/src/main/resources</directory></resource></resources>
                  <plugins><plugin><groupId>org.codehaus.mojo</groupId><artifactId>build-helper-maven-plugin</artifactId>
                    <executions><execution><goals><goal>add-source</goal></goals><configuration><sources>
                      <source>../release-tools/src/main/java</source>
                    </sources></configuration></execution></executions>
                  </plugin></plugins>
                </build></project>
                """;
        String settings = "include ':app'";
        String gradle = """
                sourceSets.main.java.srcDir '../../chess-core/src/main/java'
                sourceSets.main.resources.srcDir '../../chess-core/src/main/resources'
                """;
        var intact = new BuildDeclarations(reactor, tests, settings, gradle);
        check.same("The complete fixture agrees with its build", List.of(), architectureDrift(readme, intact));

        var staleReadme = new ArrayList<>(readme);
        staleReadme.set(3, "  `chess-core/src/main/catalog`.");
        check.same("A renamed Gradle resource path in prose is detected", List.of("Gradle"), architectureDrift(staleReadme, intact));
        check.same("An added Maven module invalidates the documented module set", List.of("Maven"), architectureDrift(readme,
                new BuildDeclarations(reactor.replace("</modules>", "<module>chess-core</module></modules>"), tests, settings, gradle)));
        check.same("A release-tools path under the wrong Maven goal is ignored", List.of("Maven"), architectureDrift(readme,
                new BuildDeclarations(reactor, tests.replace("<goal>add-source</goal>", "<goal>add-test-source</goal>"), settings, gradle)));
        check.same("A commented Gradle resource path does not count", List.of("Gradle"), architectureDrift(readme,
                new BuildDeclarations(reactor, tests, settings, gradle.replace("sourceSets.main.resources", "// sourceSets.main.resources"))));
        String wrongSourceSets = gradle.replace("main.java", "main.tmp").replace("main.resources", "main.java").replace("main.tmp", "main.resources");
        check.same("Swapping Gradle source sets is drift even when the paths are unchanged", List.of("Gradle"),
                architectureDrift(readme, new BuildDeclarations(reactor, tests, settings, wrongSourceSets)));
        check.same("A compile-scoped ATF dependency changes the documented boundary", List.of("Biblioteci ATF"), architectureDrift(readme,
                new BuildDeclarations(reactor, tests.replace("atf-test-kit</artifactId><scope>test", "atf-test-kit</artifactId><scope>compile"), settings, gradle)));
    }

    private record BuildDeclarations(String reactorPom, String testsPom, String gradleSettings, String gradleApp) { }

    private static List<String> architectureDrift(List<String> readme, BuildDeclarations declarations) throws Exception {
        var architecture = readme.stream().dropWhile(line -> !line.equals("## Arhitectura J7")).skip(1)
                .takeWhile(line -> !line.startsWith("## ")).toList();
        var parser = DocumentBuilderFactory.newDefaultInstance();
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = parser.newDocumentBuilder();
        var reactor = builder.parse(new InputSource(new StringReader(declarations.reactorPom())));
        var tests = builder.parse(new InputSource(new StringReader(declarations.testsPom())));
        var xpath = XPathFactory.newInstance().newXPath();
        var maven = new HashSet<String>();
        for (var module : xpath.evaluateExpression("/project/modules/module", reactor, XPathNodes.class)) {
            maven.add(module.getTextContent().strip());
        }
        String addedSources = "/project/build/plugins/plugin[groupId='org.codehaus.mojo' and artifactId='build-helper-maven-plugin']"
                + "/executions/execution[goals/goal='add-source']/configuration/sources/source";
        for (var source : xpath.evaluateExpression("/project/build/sourceDirectory | /project/build/resources/resource/directory | "
                + addedSources, tests, XPathNodes.class)) {
            maven.add(repositoryPath("atf-tests", source.getTextContent()));
        }

        var gradle = new HashSet<String>();
        for (var include : GRADLE_INCLUDE.matcher(declarations.gradleSettings()).results().toList()) {
            GRADLE_LITERAL.matcher(include.group(1)).results().map(literal -> literal.group(2)).forEach(gradle::add);
        }
        var sourceKinds = new HashSet<String>();
        for (var source : GRADLE_SOURCE.matcher(declarations.gradleApp()).results().toList()) {
            String path = repositoryPath("android/app", source.group(3));
            if (path.endsWith("/" + source.group(1))) sourceKinds.add(source.group(1));
            gradle.add(path);
        }

        var libraries = new HashSet<String>();
        var scopes = new ArrayList<String>();
        for (String library : List.of("atf-reporting", "atf-test-kit")) {
            String selector = "/project/dependencies/dependency[groupId='atf.api' and artifactId='" + library + "']";
            if (xpath.evaluateExpression(selector, tests, XPathNodes.class).size() == 1) {
                libraries.add(library);
                scopes.add(xpath.evaluate(selector + "/scope", tests).strip());
            }
        }
        libraries.addAll(scopes);
        var drift = new ArrayList<String>();
        if (!maven.equals(documentedTokens(architecture, "Maven"))) drift.add("Maven");
        if (!sourceKinds.equals(Set.of("java", "resources")) || !gradle.equals(documentedTokens(architecture, "Gradle"))) drift.add("Gradle");
        if (!scopes.equals(List.of("test", "test")) || !libraries.equals(documentedTokens(architecture, "Biblioteci ATF"))) drift.add("Biblioteci ATF");
        return drift;
    }

    private static String repositoryPath(String owner, String path) {
        return Path.of(owner).resolve(path.strip()).normalize().toString().replace('\\', '/');
    }

    private static Set<String> documentedTokens(List<String> architecture, String label) {
        var claim = Pattern.compile("(?m)^- \\*\\*" + Pattern.quote(label) + "\\*\\*[^\\r\\n]*(?:\\R {2,}[^\\r\\n]+)*")
                .matcher(String.join("\n", architecture));
        if (!claim.find()) return Set.of();
        var tokens = new HashSet<String>();
        CODE_SPAN.matcher(claim.group()).results().map(token -> token.group(1)).forEach(tokens::add);
        return claim.find() ? Set.of() : tokens;
    }

    @Test void movingTheMotorLinkOutOfTheArchitectureMapDoesNotSatisfyTheContract(Checks check) {
        var readme = List.of("## Arhitectura J7",
                "| [Android](android/app/src/main) | APK |",
                "| [Teste](atf-tests) | JVM |",
                "| [Release](release-tools/src/main) | RAG |",
                "| [Biblioteci](lib/README.md) | JAR |",
                "## Puncte de intrare",
                "- [Motor](chess-core/src/main)",
                "- [Teste](docs/testing/README.md#alegerea-nivelului)",
                "- [Release](docs/release/README.md#calificare-și-publicare)",
                "- [Feedback](docs/feedback/README.md)");
        check.same("A generic link cannot replace the motor row in the architecture section",
                List.of("Arhitectura J7 → chess-core/src/main"), missingReadmeRoutes(readme));
    }

    private static List<String> missingReadmeRoutes(List<String> lines) {
        var missing = new ArrayList<String>();
        var architecture = lines.stream().dropWhile(line -> !line.equals("## Arhitectura J7")).skip(1)
                .takeWhile(line -> !line.startsWith("## ")).filter(line -> line.startsWith("|")).toList();
        for (String owner : List.of("chess-core/src/main", "android/app/src/main", "atf-tests", "release-tools/src/main", "lib/README.md")) {
            if (!linksTo(architecture, owner)) missing.add("Arhitectura J7 → " + owner);
        }
        for (String guide : List.of("docs/testing/README.md", "docs/release/README.md", "docs/feedback/README.md")) {
            if (!linksTo(lines, guide)) missing.add("README.md → " + guide);
        }
        return missing;
    }

    private static boolean linksTo(List<String> lines, String destination) {
        return lines.stream().flatMap(line -> LINK.matcher(line).results())
                .map(link -> link.group(1).split("#", 2)[0]).anyMatch(destination::equals);
    }

    @Test void feedbackKeepsOpenCriteriaAndReportsStayWithTheirEdition(Checks check) throws IOException {
        Path feedback = ProjectRoot.locate().resolve("docs/feedback");
        var backlog = prose(feedback.resolve("README.md"));
        check.same("Feedback exposes the owner area, observation and closure criterion", true,
                backlog.contains("| Prioritate | Zonă | Observație | Criteriu de închidere |"));
        var incomplete = backlog.stream().filter(line -> line.startsWith("| P")).filter(line -> !line.startsWith("| Prioritate"))
                .filter(line -> !line.matches("\\| P[0-9] \\| [^|]+ \\| [^|]+ \\| [^|]+ \\|")).toList();
        check.same("Every open item has an owner area and a concrete closure criterion", List.of(), incomplete);
        try (var files = Files.walk(feedback)) {
            check.same("Generated Extent HTML belongs with release evidence", List.of(),
                    files.filter(path -> path.toString().endsWith(".html")).map(feedback::relativize).toList());
        }
    }

    @Test void catchesMissingFilesAndRenamedAnchorsWithoutReadingCodeExamples(Checks check) throws IOException {
        Path guide = Files.writeString(folder.resolve("guide.md"), """
                ## Mat în două
                ## Mat în două
                ```md
                ## Exemplu
                ```
                """);
        Path source = Files.writeString(folder.resolve("README.md"), """
                [Lecție](guide.md#mat-în-două)
                [Secțiune repetată](guide.md#mat-în-două-1)
                [Referință publică](https://example.test/not-checked)
                [Lipsește](absent.md)
                [Titlu mutat](guide.md#exemplu)
                [În afara checkoutului](../guide.md)
                ```md
                [Exemplu](example.md)
                ```
                """);
        check.same("Both headings in the guide are indexed", Set.of("mat-în-două", "mat-în-două-1"), anchors(guide));
        check.same("Only broken local targets are reported", List.of("absent.md", "guide.md#exemplu", "../guide.md"), brokenLinks(folder, source));
    }

    private static List<String> brokenLinks(Path root, Path source) throws IOException {
        var broken = new ArrayList<String>();
        for (String line : prose(source)) {
            var links = LINK.matcher(line);
            while (links.find()) {
                String link = links.group(1);
                if (link.startsWith("https://") || link.startsWith("http://")) continue;
                var parts = link.split("#", 2);
                Path target = parts[0].isEmpty() ? source : source.getParent().resolve(decode(parts[0])).normalize();
                if (!target.startsWith(root) || !(Files.isRegularFile(target) || parts.length == 1 && Files.isDirectory(target))
                        || parts.length == 2 && target.toString().endsWith(".md") && !anchors(target).contains(decode(parts[1]))) {
                    broken.add(link);
                }
            }
        }
        return broken;
    }

    private static Set<String> anchors(Path document) throws IOException {
        var result = new HashSet<String>();
        for (String line : prose(document)) {
            var heading = HEADING.matcher(line);
            if (!heading.matches()) continue;
            String slug = heading.group(1).strip().toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{M}\\p{N} _-]", "").replace(' ', '-');
            String candidate = slug;
            for (int occurrence = 1; !result.add(candidate); occurrence++) candidate = slug + "-" + occurrence;
        }
        return result;
    }

    private static List<String> prose(Path document) throws IOException {
        var lines = new ArrayList<String>();
        String open = "";
        for (String line : Files.readAllLines(document)) {
            var fence = FENCE.matcher(line.stripLeading());
            if (fence.matches()) {
                if (open.isEmpty()) open = fence.group(1);
                else if (fence.group(1).startsWith(open) && fence.group(2).isBlank()) open = "";
            } else if (open.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String jsonValue(String source, String name) {
        var matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9]+)|(true|false))")
                .matcher(source);
        return matcher.find() ? matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3) : "";
    }
}
