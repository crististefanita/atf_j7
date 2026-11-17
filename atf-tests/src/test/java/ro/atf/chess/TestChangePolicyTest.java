package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.GitTestChanges;
import ro.atf.chess.release.ReleaseGate;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class TestChangePolicyTest {
    private static final String OLD = "android/app/src/androidTest/java/ro/atf/chess/android/OldPuzzleTest.java";
    private static final String NATIVE = "android/app/src/androidTest/java/ro/atf/chess/android/BoardSanity.java";
    private static final String NEW = "atf-tests/src/test/java/ro/atf/chess/QuestStepTest.java";
    private static final String METHOD = "ro.atf.chess.QuestStepTest#restoresTheBoardBeforeAndAfterEachDefense";
    private static final String SUITE = "ro.atf.chess.TestChangeReview";

    @TempDir Path temporary;

    @Test void demandsAnExactReplacementAndRunsTheMigratedContract(Checks check) throws Exception {
        Path repo = initialize();
        write(repo, OLD, "compare(\"restores the board across a defense\", expected, actual);");
        write(repo, NATIVE, "capture(\"actual dialog after device rotation\");");
        String base = commit(repo, "test baseline");
        Files.delete(repo.resolve(OLD));
        write(repo, NEW, Files.readString(ProjectRoot.locate().resolve(NEW)));
        commit(repo, "move replay-state assertion to JVM");
        var snapshot = GitTestChanges.read(repo, base);
        String approved = control(base);
        write(repo, TestChangePolicy.REGISTER, "testChanges: []");
        check.rejects("Moving the old assertion without a reviewed control fails", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved.replace("allowDelete: true", "allowDelete: false"));
        check.rejects("A source removal needs explicit deletion authorization", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved.replace(base, snapshot.head()));
        check.rejects("A control from another valid Git base cannot authorize this change", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved.replace(NEW, NEW.replace("QuestStep", "Unrelated")));
        check.rejects("A different path cannot qualify a passing method from another class", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved.replace(OLD, OLD.replace("OldPuzzleTest", "*")));
        check.rejects("A wildcard cannot cover the old Android test", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved + approved.substring(approved.indexOf("  - id:")).replace("MOVE-REPLAY", "DUPLICATE"));
        check.rejects("Two controls cannot approve the same source and base", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, approved);
        write(repo, NATIVE, "capture(\"unrelated native checkpoint\");");
        check.rejects("The original native checkpoint must still exist", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, NATIVE, "capture(\"actual dialog after device rotation\");");
        Files.delete(repo.resolve(NEW));
        check.rejects("A named test without its destination source cannot cover the move", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, NEW, Files.readString(ProjectRoot.locate().resolve(NEW)));
        var review = TestChangePolicy.review(repo, snapshot);
        check.same("The exact replacement belongs to the offline component proof", Set.of(METHOD), review.componentOnly());
        Path proof = temporary.resolve("migration-proof.html");
        TestChangeProof.verify(review.methods(), review.componentOnly(), proof);
        String extent = Files.readString(proof);
        check.same("The replacement really runs and publishes the equivalent assertion",
                true, extent.contains("restoresTheBoardBeforeAndAfterEachDefense") && extent.contains("Assertions"));
    }

    @Test void anInFileDeletionAndMalformedApprovalsCannotEscapeReview(Checks check) throws Exception {
        Path repo = initialize();
        write(repo, NATIVE, "capture(\"device checkpoint\");\ncompare(\"cursor after defense\", expected, actual);");
        String base = commit(repo, "test baseline");
        write(repo, NATIVE, "capture(\"device checkpoint\");");
        var snapshot = GitTestChanges.read(repo, base);
        write(repo, TestChangePolicy.REGISTER, "testChanges: []");
        check.rejects("Editing an existing harness with the same file and method names still needs review", IOException.class,
                () -> TestChangePolicy.review(repo, snapshot));
        write(repo, TestChangePolicy.REGISTER, "testChanges: []\ntestChanges: []\n");
        check.rejects("Duplicate YAML keys fail closed", IOException.class, () -> TestChangePolicy.review(repo, snapshot));
        check.same("The test-only edit did not delete or rename a file", List.of(new GitTestChanges.Change(GitTestChanges.Kind.MODIFIED, NATIVE)),
                snapshot.changes());
    }

    @Test void releasePreflightRequiresACleanCandidateAndTheMatchingReviewReceipt(Checks check) throws Exception {
        Path repo = initialize();
        write(repo, "notes.txt", "base");
        String base = commit(repo, "review base");
        write(repo, "notes.txt", "candidate");
        String head = commit(repo, "release candidate");
        Path receipts = Files.createDirectory(temporary.resolve("receipts"));
        for (String name : List.of("DocumentationGovernanceTest", "ReleaseGovernanceTest", "ExtentGovernanceTest")) {
            String suite = "ro.atf.chess." + name;
            write(receipts, "TEST-" + suite + ".xml", suiteReceipt(suite, ""));
        }
        check.rejects("Passing ordinary governance cannot stand in for the explicit Git review", IOException.class,
                () -> ReleaseGate.preflight(receipts, repo, base));
        write(receipts, "TEST-" + SUITE + ".xml", reviewReceipt("J7_TEST_CHANGES " + base + " " + base));
        check.rejects("A receipt with the right base but another candidate is rejected", IOException.class,
                () -> ReleaseGate.preflight(receipts, repo, base));
        write(receipts, "TEST-" + SUITE + ".xml", reviewReceipt("J7_TEST_CHANGES " + head + " " + head));
        check.rejects("A passing receipt for another base is rejected", IOException.class,
                () -> ReleaseGate.preflight(receipts, repo, base));
        write(receipts, "TEST-" + SUITE + ".xml", reviewReceipt("J7_TEST_CHANGES " + base + " " + head)
                .replace("<system-out>", "<skipped/><system-out>"));
        check.rejects("A skipped testcase cannot acquire PASS from its suite counters", IOException.class,
                () -> ReleaseGate.preflight(receipts, repo, base));
        write(receipts, "TEST-" + SUITE + ".xml", reviewReceipt("J7_TEST_CHANGES " + base + " " + head));
        ReleaseGate.preflight(receipts, repo, base);
        check.same("The accepted clean candidate and distinct reviewed base agree with Git", head, GitTestChanges.read(repo, base).head());
        write(repo, "notes.txt", "uncommitted");
        check.rejects("The receipt cannot qualify a dirty source tree", IOException.class, () -> ReleaseGate.preflight(receipts, repo, base));
        check.rejects("The release CLI stops when root and base are omitted", IllegalArgumentException.class,
                () -> ReleaseGate.main(new String[]{"preflight", receipts.toString()}));
    }

    @Test void ragCitesTheReviewProcessForCoverageChanges(Checks check) throws IOException {
        var citations = ReleaseRag.retrieve(ProjectRoot.locate(), List.of(TestChangePolicy.REGISTER,
                "release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java"));
        check.same("RAG identifies the explicit Git review and the release preflight",
                List.of("TestChangeReview with the explicit Git baseline and ReleaseGate preflight"),
                citations.stream().filter(item -> item.heading().equals("## Test changes against a Git baseline"))
                        .map(ReleaseRag.Citation::check).toList());
    }

    private static String control(String base) {
        return """
                testChanges:
                  - id: MOVE-REPLAY
                    base: "%s"
                    from: %s
                    replacement: %s
                    test: %s
                    contract: "Restore the saved board before and after each defense"
                    allowDelete: true
                    nativePath: %s
                    nativeProbe: "actual dialog after device rotation"
                """.formatted(base, OLD, NEW, METHOD, NATIVE);
    }

    private static String reviewReceipt(String marker) {
        return suiteReceipt(SUITE, "<testcase classname=\"" + SUITE + "\" name=\"verifiesReviewedChanges(Checks)\"><system-out>"
                + marker + "</system-out></testcase>");
    }

    private static String suiteReceipt(String suite, String body) {
        return "<testsuite name=\"" + suite + "\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\">" + body + "</testsuite>";
    }

    private Path initialize() throws Exception {
        Path repo = Files.createDirectory(temporary.resolve("repo"));
        git(repo, "init", "-q");
        return repo;
    }

    private static String commit(Path repo, String message) throws Exception {
        git(repo, "add", "-A");
        git(repo, "commit", "-qm", message);
        return git(repo, "rev-parse", "HEAD");
    }

    private static String git(Path repo, String... arguments) throws Exception {
        var invocation = new ArrayList<>(List.of("git", "-C", repo.toString(), "-c", "user.name=J7 Test",
                "-c", "user.email=j7-test@example.invalid", "-c", "commit.gpgSign=false"));
        invocation.addAll(List.of(arguments));
        var process = new ProcessBuilder(invocation).redirectErrorStream(true).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Fixture Git command timed out");
        }
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0) throw new IOException("Fixture Git failed: " + output);
        return output;
    }

    private static void write(Path root, String path, String content) throws IOException {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
