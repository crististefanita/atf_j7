package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ProjectDocumentationIdentityTest {
    private static final Pattern LEGACY_NAME = Pattern.compile("atf[-_]j7", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIFACT = Pattern.compile(
            "https://github\\.com/crististefanita/chess/blob/([\\w.-]+)/([^\\s)#?]+)");

    @Test void activeGuidesUseTheCurrentRepositoryIdentity(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        List<String> guides = activeGuides(root);
        check.same("The inventory includes the project and documentation entry points", true,
                guides.containsAll(List.of("README.md", "docs/README.md")));
        var failures = new ArrayList<String>();
        for (String file : guides) {
            var lines = Files.readAllLines(root.resolve(file));
            for (int line = 0; line < lines.size(); line++) {
                if (LEGACY_NAME.matcher(lines.get(line)).find()) failures.add(file + ":" + (line + 1));
            }
        }
        check.same("Current guides contain no former repository or checkout identity", List.of(), failures);
    }

    @Test void legacyRoutesAreRejectedWhileTechnicalIdentifiersAndEvidenceKeepTheirMeaning(Checks check) {
        for (String text : List.of("https://github.com/crististefanita/atf_j7/blob/main/README.md",
                "C:/work/1/atf-j7/docs", "C:\\work\\1\\ATF_J7\\README.md")) {
            check.same("A former route is rejected: " + text, true, LEGACY_NAME.matcher(text).find());
        }
        check.same("Current routes and the existing signing protocol remain valid", false,
                LEGACY_NAME.matcher("chess J7_SIGNING_STORE Initialize-J7Signing.ps1").find());
        check.same("Historical release evidence is excluded from editorial rewriting", false,
                activeGuide("release/0.16.0/reports/README.md"));
    }

    @Test void historicalLinksPointAtCommitsThatActuallyContainTheEvidence(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        var missing = new ArrayList<String>();
        for (String file : activeGuides(root)) {
            var links = ARTIFACT.matcher(Files.readString(root.resolve(file)));
            while (links.find()) {
                String ref = links.group(1);
                if (ref.matches("[a-f0-9]{40}|v[0-9]+\\.[0-9]+\\.[0-9]+")) {
                    String object = ref + ":" + links.group(2);
                    if (!hasObject(root, object)) missing.add(file + " -> " + object);
                }
            }
        }
        check.same("Pinned historical artifacts exist in the named Git objects", List.of(), missing);
        check.same("A known revision supplies its tracked README", true, hasObject(root, "HEAD:README.md"));
        check.same("A valid revision cannot supply a nonexistent evidence file", false,
                hasObject(root, "HEAD:release/nonexistent-evidence/never-published.tsv"));
    }

    private static List<String> activeGuides(Path root) throws Exception {
        var git = new ProcessBuilder("git", "ls-files", "--", "*.md")
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (git.waitFor() != 0) throw new IOException("Cannot enumerate tracked documentation: " + output);
        return output.lines().filter(ProjectDocumentationIdentityTest::activeGuide).toList();
    }

    private static boolean activeGuide(String path) {
        return path.equals("README.md") || path.equals(".atf-signing/README.md")
                || path.startsWith("docs/") || path.startsWith("web/") || path.startsWith("lib/");
    }

    private static boolean hasObject(Path root, String object) throws Exception {
        var git = new ProcessBuilder("git", "cat-file", "-e", object).directory(root.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        return git.waitFor() == 0;
    }
}
