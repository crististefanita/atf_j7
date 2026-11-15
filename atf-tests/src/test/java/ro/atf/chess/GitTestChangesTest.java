package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.GitTestChanges;
import ro.atf.chess.release.GitTestChanges.Change;
import ro.atf.chess.release.GitTestChanges.Kind;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class GitTestChangesTest {
    private static final String JVM = "atf-tests/src/test/java/ro/atf/chess/";
    private static final String ANDROID = "android/app/src/androidTest/java/ro/atf/chess/android/";
    private static final String NATIVE_TEST = ANDROID + "BoardSanity.java";
    private static final String PLAYER_TEST = JVM + "PlayerTest.java";
    private static final String INITIAL = "// initial\n";

    @Test void requiresAnExplicitAncestorCommitAndTheCheckoutRoot(@TempDir Path root, Checks check) throws IOException {
        check.rejects("A source export without Git history cannot certify test changes", IOException.class,
                () -> GitTestChanges.read(root, "0".repeat(40)));
        String base = initialize(root, PLAYER_TEST);
        Path nested = Files.createDirectories(root.resolve("source-export"));
        String tree = git(root, "rev-parse", "HEAD^{tree}");
        String foreign = git(root, "commit-tree", tree, "-m", "independent root");
        check.same("A clean checkout reports the explicitly chosen commit and no changes",
                new GitTestChanges.Snapshot(base, base, true, List.of()), GitTestChanges.read(root, base));
        check.rejects("HEAD is a movable reference, not an explicit commit SHA", IOException.class,
                () -> GitTestChanges.read(root, "HEAD"));
        check.rejects("A shortened hash is never expanded", IOException.class,
                () -> GitTestChanges.read(root, base.substring(0, 10)));
        check.rejects("A checkout nested inside another repository cannot reuse its history", IOException.class,
                () -> GitTestChanges.read(nested, base));
        check.rejects("An existing commit outside the candidate's ancestry is rejected", IOException.class,
                () -> GitTestChanges.read(root, foreign));
    }

    @Test void separatesRenamedPathsAndIncludesIgnoredTestSources(@TempDir Path root, Checks check) throws IOException {
        initialize(root, NATIVE_TEST);
        String resource = "atf-tests/src/test/resources/local-fixture.txt";
        String buildOutput = "atf-tests/target/receipt.xml";
        write(root, ".gitignore", resource + "\n" + buildOutput + "\n");
        git(root, "add", "--", ".gitignore");
        String base = commit(root, "ignore sample inputs and build outputs");
        write(root, buildOutput, "<ignored/>");
        check.same("An ignored build output does not dirty the checkout", true, GitTestChanges.read(root, base).clean());
        write(root, resource, "Compiled test fixture");
        check.same("An ignored test input alone does dirty the checkout", false, GitTestChanges.read(root, base).clean());

        String moved = JVM + "Rejouer Étape.java";
        Files.createDirectories(root.resolve(moved).getParent());
        git(root, "mv", "--", NATIVE_TEST, moved);
        write(root, "README.md", "# Other change");
        var snapshot = GitTestChanges.read(root, base);

        check.same("A staged rename retains both exact paths even with spaces and non-ASCII characters",
                List.of(new Change(Kind.DELETED, NATIVE_TEST), new Change(Kind.ADDED, moved),
                        new Change(Kind.ADDED, resource)), snapshot.changes());
        check.same("Ignored test inputs make the release checkout dirty", false, snapshot.clean());
    }

    @Test void aLaterRestoreCannotHideACommittedOrAStagedDeletion(@TempDir Path root, Checks check) throws IOException {
        String base = initialize(root, NATIVE_TEST, PLAYER_TEST);
        git(root, "rm", "--", NATIVE_TEST);
        String candidate = commit(root, "delete Android assertion fixture");
        write(root, NATIVE_TEST, INITIAL);
        git(root, "add", "--", NATIVE_TEST);
        git(root, "rm", "--", PLAYER_TEST);
        write(root, PLAYER_TEST, INITIAL);
        var snapshot = GitTestChanges.read(root, base);

        check.same("The baseline comparison sees committed and staged deletions after restoration",
                List.of(new Change(Kind.DELETED, NATIVE_TEST), new Change(Kind.DELETED, PLAYER_TEST)),
                snapshot.changes().stream().filter(change -> change.kind() == Kind.DELETED).toList());
        check.same("The scan identifies the candidate commit and both dirty views",
                List.of(candidate, false), List.of(snapshot.head(), snapshot.clean()));
    }

    @Test void reportsProtectedBuildEditsAndNewUntrackedTests(@TempDir Path root, Checks check) throws IOException {
        var configurations = List.of("pom.xml", "atf-tests/pom.xml", "extra/module/pom.xml",
                "android/app/build.gradle", "android/settings.gradle", "scripts/run-android-sanity.ps1");
        String base = initialize(root, configurations.toArray(String[]::new));
        for (String file : configurations) write(root, file, "// changed test discovery");
        write(root, PLAYER_TEST, INITIAL);
        write(root, "docs/testing/README.md", "# Unprotected guide");
        var snapshot = GitTestChanges.read(root, base);
        check.same("Every executable test-discovery entry remains in the protected scope", true,
                configurations.stream().allMatch(GitTestChanges::protectedPath));
        var expected = new ArrayList<>(configurations.stream()
                .map(path -> new Change(Kind.MODIFIED, path)).toList());
        expected.add(new Change(Kind.ADDED, PLAYER_TEST));
        check.same("The scan reports selectors and fresh test files without unrelated docs",
                expected.stream().sorted(java.util.Comparator.comparing(Change::path)).toList(), snapshot.changes());
    }

    private static String initialize(Path root, String... files) throws IOException {
        git(root, "init", "--quiet");
        git(root, "config", "user.name", "J7 test fixture");
        git(root, "config", "user.email", "fixture@example.test");
        git(root, "config", "commit.gpgSign", "false");
        for (String file : files) write(root, file, INITIAL);
        git(root, "add", "--all");
        return commit(root, "baseline");
    }

    private static String commit(Path root, String message) throws IOException {
        git(root, "-c", "core.hooksPath=", "commit", "--quiet", "--no-gpg-sign", "-m", message);
        return git(root, "rev-parse", "HEAD");
    }

    private static void write(Path root, String file, String content) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String git(Path root, String... arguments) throws IOException {
        var invocation = new ArrayList<>(List.of("git", "-C", root.toString()));
        invocation.addAll(List.of(arguments));
        var child = new ProcessBuilder(invocation).redirectErrorStream(true).start();
        try (var stream = child.getInputStream()) {
            String output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (child.waitFor() != 0) throw new IOException("Git fixture command failed: " + output);
            return output;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Git fixture interrupted", interrupted);
        }
    }
}
