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
        var documents = new ArrayList<>(List.of(root.resolve("README.md"), root.resolve("lib/README.md")));
        try (var paths = Files.walk(root.resolve("docs"))) {
            paths.filter(path -> path.toString().endsWith(".md")).sorted().forEach(documents::add);
        }
        var failures = new ArrayList<String>();
        for (Path document : documents) {
            for (String broken : brokenLinks(root, document)) failures.add(root.relativize(document) + " → " + broken);
        }
        check.same("All local links in current guides, release process and feedback resolve", List.of(), failures);
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
        check.same("Feedback retains a distinct list of open acceptance criteria", true,
                prose(root.resolve("docs/feedback/README.md")).contains("| Prioritate | Pas | Criteriu de închidere |"));
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
}
