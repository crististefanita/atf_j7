package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseEvidence;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class DocumentationGovernanceTest {
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");

    @TempDir Path folder;

    @Test void currentDocumentationResolvesEveryLocalTargetAndHeading(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        Path readme = root.resolve("README.md");
        var documents = new ArrayList<>(List.of(readme, root.resolve("lib/README.md"), root.resolve("release/README.md")));
        String tag = ReleaseEvidence.gameLinks(Files.readString(readme)).getFirst();
        Path release = root.resolve("release").resolve(tag.substring(tag.lastIndexOf('/') + 2));
        documents.add(release.resolve("README.md"));
        Path reports = release.resolve("reports/README.md");
        if (Files.isRegularFile(reports)) documents.add(reports);
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
        check.same("All local links in current guides, release process and feedback resolve", List.of(), failures);
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
        boolean evidenceExists = List.of(githubRelease, publicDownload, authenticatedDownload, qualification)
                .stream().allMatch(Files::isRegularFile);
        boolean complete = false;
        if (evidenceExists) {
            String release = Files.readString(githubRelease);
            String direct = Files.readString(publicDownload);
            String authenticated = Files.readString(authenticatedDownload);
            String qualified = Files.readString(qualification);
            String apkSha = jsonValue(qualified, "apkSha256");
            String apkBytes = jsonValue(qualified, "apkBytes");
            complete = jsonValue(release, "tag").equals(tag)
                    && jsonValue(release, "releaseUrl").equals(releaseUrl)
                    && jsonValue(release, "assetUrl").equals(assetUrl)
                    && jsonValue(direct, "status").equals("PASS")
                    && jsonValue(direct, "assetUrl").equals(assetUrl)
                    && jsonValue(direct, "httpStatus").equals("200")
                    && jsonValue(direct, "zipCrc").equals("PASS")
                    && jsonValue(direct, "sha256").equals(apkSha)
                    && jsonValue(direct, "bytes").equals(apkBytes)
                    && jsonValue(authenticated, "status").equals("PASS")
                    && jsonValue(authenticated, "tag").equals(tag)
                    && jsonValue(authenticated, "releaseUrl").equals(releaseUrl)
                    && jsonValue(authenticated, "assetUrl").equals(assetUrl)
                    && jsonValue(authenticated, "channel").toLowerCase(Locale.ROOT).contains("authenticated")
                    && jsonValue(authenticated, "httpStatus").equals("200")
                    && jsonValue(authenticated, "zipCrc").equals("PASS")
                    && jsonValue(authenticated, "sha256").equals(apkSha)
                    && jsonValue(authenticated, "bytes").equals(apkBytes);
        }
        check.same("The main README links only to a release with matching asset, qualification and complete public/authenticated download receipts",
                true, complete);
    }

    @Test void releaseCatalogKeepsOnlyTheCurrentDownloadAndSeparatesItsCandidate(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String catalog = Files.readString(root.resolve("release/index.json"));
        List<String> links = ReleaseEvidence.gameLinks(Files.readString(root.resolve("README.md")));
        String current = jsonValue(catalog, "currentVersion");
        String linkedTag = links.getFirst().substring(links.getFirst().lastIndexOf("/tag/") + 5);
        var versions = Pattern.compile("\\\"versions\\\"\\s*:\\s*\\[\\s*\\\"([^\\\"]+)\\\"\\s*\\]").matcher(catalog);
        String android = Files.readString(root.resolve("android/app/build.gradle"));
        var androidVersion = Pattern.compile("(?m)^\\s*versionName '([0-9]+\\.[0-9]+\\.[0-9]+)'$").matcher(android);
        var androidCode = Pattern.compile("(?m)^\\s*versionCode ([0-9]+)$").matcher(android);
        boolean aligned = versions.find() && androidVersion.find() && androidCode.find()
                && current.equals(linkedTag.substring(1))
                && versions.group(1).equals(current)
                && jsonValue(catalog, "version").equals(androidVersion.group(1))
                && jsonValue(catalog, "versionCode").equals(androidCode.group(1))
                && !jsonValue(catalog, "version").equals(current)
                && jsonValue(catalog, "repository").equals("crististefanita/atf_j7");
        check.same("The root download, single active edition and separate candidate agree with release metadata and Android versioning", true, aligned);
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
        var matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9]+))")
                .matcher(source);
        return matcher.find() ? matcher.group(1) == null ? matcher.group(2) : matcher.group(1) : "";
    }
}
