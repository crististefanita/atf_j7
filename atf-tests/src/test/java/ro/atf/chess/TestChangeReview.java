package ro.atf.chess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.release.GitTestChanges;

@Tag("test-change-review")
@ExtendWith(ExtentAudit.class)
class TestChangeReview {
    @Test void verifiesReviewedChanges(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        String base = System.getProperty("chess.testChanges.base");
        var snapshot = GitTestChanges.read(root, base);
        check.same("Explicit Git base resolved", base, snapshot.base());
        var review = TestChangePolicy.review(root, snapshot);
        Path proof = proofExtent();
        TestChangeProof.verify(review.methods(), review.componentOnly(), proof);
        for (String mapping : review.evidence()) {
            check.same("Executed coverage for " + mapping, true, Files.isRegularFile(proof));
        }
        check.same("All governed file changes have an accepted control",
                snapshot.changes().stream().filter(change -> change.kind() != GitTestChanges.Kind.ADDED)
                        .map(GitTestChanges.Change::path).distinct().count(), (long) review.evidence().size());
        if (!review.methods().isEmpty()) {
            check.same("Replacement tests wrote a dedicated Extent report", true, Files.isRegularFile(proof));
        }
        check.same("The Git base, candidate and changed-path inventory still match after the proof", snapshot, GitTestChanges.read(root, base));
        if (snapshot.clean()) {
            System.out.println("J7_TEST_CHANGES " + snapshot.base() + " " + snapshot.head());
        }
    }

    private static Path proofExtent() throws IOException {
        String configured = System.getProperty("chess.extent", "");
        if (configured.isBlank()) return Files.createTempDirectory("j7-change-proof-").resolve("components.html");
        Path current = Path.of(configured).toAbsolutePath();
        return current.resolveSibling(current.getFileName() + ".proof.html");
    }
}
