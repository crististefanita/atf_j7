package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseGate;
import ro.atf.chess.release.ReleaseRag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class DocumentationPairGovernanceTest {
    private static final String RELEASE = "docs/release/README.md";
    private static final String RECEIPT = "release/0.15.3/reports/public-download.json";
    private static final String GUIDE = "guide.md";

    @TempDir
    static Path repository;

    @BeforeAll
    static void createSnapshots() throws Exception {
        git("", "init", "--quiet");
        var en = snapshot("Play", "Published Release", "Qualification and Publication", "Overview", "Usage", "overview");
        var ro = snapshot("Jocul", "Ediția publicată", "Calificare și publicare", "Pe scurt", "Folosire", "pe-scurt");
        var importer = new StringBuilder();
        add(importer, "main", en);
        add(importer, "main_RO", ro);
        add(importer, "inventory-drift", with(ro, "unpaired.md", "# Unpaired guide\n"));
        add(importer, "shape-drift", with(ro, GUIDE, ro.get(GUIDE).replace("## Folosire", "### Folosire")));
        add(importer, "table-drift", with(ro, GUIDE, ro.get(GUIDE).replace("| Caz | Probă |", "| Caz | Probă | Owner |")));
        add(importer, "wrong-target", with(ro, RELEASE, ro.get(RELEASE).replace("#pe-scurt", "#folosire")));
        add(importer, "missing-target", with(ro, RELEASE, ro.get(RELEASE).replace("#pe-scurt", "#absent")));
        add(importer, "wrong-overview", with(ro, RELEASE, ro.get(RELEASE).replace("## Pe scurt", "## Overview")));
        add(importer, "receipt-drift", with(ro, RECEIPT, "{\"status\":404}\n"));
        git(importer.toString(), "fast-import", "--quiet");
    }

    @Test
    void pairedGateReadsImmutableMarkdownWithTranslatedAnchors(Checks check) throws Exception {
        Files.writeString(repository.resolve("README.md"), "# Untracked workspace copy\n");
        check.same("The English and Romanian commits have the same release routes, outline and heading destinations",
                "PASS", result("main", "main_RO"));
        check.same("The locale roles cannot be swapped", true,
                result("main_RO", "main").contains("main must use its localized"));
        check.same("A single snapshot cannot stand in for the pair", true,
                result("main", "main").contains("distinct commits"));
        check.same("An owner README must use its opening section in its own language", true,
                result("main", "wrong-overview").contains("localized ## Pe scurt section"));
    }

    @Test
    void pairedGateRejectsInventoryStructureLinksAndEvidenceDrift(Checks check) {
        check.same("An unpaired tracked Markdown file is rejected", true,
                result("main", "inventory-drift").contains("unpaired.md"));
        check.same("A changed heading level is rejected", true,
                result("main", "shape-drift").contains("Markdown structure differs"));
        check.same("A changed table shape is rejected", true,
                result("main", "table-drift").contains("table row"));
        check.same("An existing anchor at a different section is rejected", true,
                result("main", "wrong-target").contains("Markdown destination differs"));
        check.same("A broken local anchor is rejected with its target", true,
                result("main", "missing-target").contains("guide.md#absent"));
        check.same("A changed archived receipt is rejected", true,
                result("main", "receipt-drift").contains("Release evidence differs"));
    }

    @Test
    void ragRequestsPairVerificationForEveryMarkdownChange(Checks check) throws Exception {
        check.same("Unrouted Markdown still requires paired documentation review", true,
                ReleaseRag.inspect(repository, java.util.List.of("guide.md")).documentationPairRequired());
        check.same("An unrelated binary does not require Markdown pairing", false,
                ReleaseRag.inspect(repository, java.util.List.of("unrouted.bin")).documentationPairRequired());
    }

    private static String result(String en, String ro) {
        try {
            ReleaseGate.main(new String[]{"documentation-pair", repository.toString(), en, ro});
            return "PASS";
        } catch (IOException rejected) {
            return rejected.getMessage();
        }
    }

    private static Map<String, String> snapshot(String play, String edition, String release, String first, String second, String anchor) {
        String readme = """
                # Chess Lab

                ## %s
                - [APK](https://github.com/crististefanita/chess/releases/download/v0.15.3/ChessLab-0.15.3.apk)
                - [Web](https://crististefanita.github.io/chess/)

                ## %s
                - [Notes](https://github.com/crististefanita/chess/releases/tag/v0.15.3)
                """.formatted(play, edition);
        String guide = """
                # Guide

                ## %s
                - Case

                | Caz | Probă |
                |---|---|
                | A | B |

                ```text
                [Sample](not-a-repository-link.md)
                ```

                ## %s
                See the release notes.
                """.formatted(first, second);
        return Map.of(
                "README.md", readme,
                RELEASE, "# Release\n\n## " + first + "\n\n## " + release + "\n\n[Guide](../../" + GUIDE + "#" + anchor + ")\n",
                GUIDE, guide,
                RECEIPT, "{\"status\":200}\n");
    }

    private static Map<String, String> with(Map<String, String> snapshot, String path, String contents) {
        var result = new TreeMap<>(snapshot);
        result.put(path, contents);
        return result;
    }

    private static void add(StringBuilder importer, String branch, Map<String, String> snapshot) {
        importer.append("commit refs/heads/").append(branch)
                .append("\ncommitter Chess Docs <docs@example.test> 1234567890 +0000\ndata 7\nfixture\n");
        snapshot.forEach((path, content) -> importer.append("M 100644 inline ").append(path).append("\ndata ")
                .append(content.getBytes(StandardCharsets.UTF_8).length).append('\n').append(content).append('\n'));
        importer.append('\n');
    }

    private static void git(String input, String... arguments) throws Exception {
        var command = new ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(arguments));
        Process child = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start();
        try {
            try (var stream = child.getOutputStream()) {
                stream.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!child.waitFor(20, TimeUnit.SECONDS)) throw new IOException("Git fixture timed out");
            String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (child.exitValue() != 0) throw new IOException("Git fixture failed: " + output);
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }
}
