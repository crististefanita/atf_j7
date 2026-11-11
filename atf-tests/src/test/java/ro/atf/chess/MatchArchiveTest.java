package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchArchiveTest {
    @Test @Tag("governance") void keepsOnlyTheTenMostRecentResultsWithoutRefreshingReopenedGames(Checks check) {
        List<String> archive = List.of();
        for (int id = 1; id <= 12; id++) archive = MatchArchive.retain(archive, "game-" + id).kept();
        var expected = IntStream.rangeClosed(0, 9).mapToObj(index -> "game-" + (12 - index)).toList();
        check.same("The archive keeps exactly the newest ten results in descending order", expected, archive);
        check.same("Reopening an older result does not refresh its position", expected, MatchArchive.retain(archive, "game-5").kept());
        check.same("A different identity can archive a new ending even with the same move sequence", "new-finish",
                MatchArchive.retain(archive, "new-finish").kept().getFirst());
        var next = MatchArchive.retain(archive, "game-13");
        check.same("Only the oldest game's checkpoint and cached review are eligible for removal", List.of("game-3"), next.evicted());
        check.same("Opening an existing game never schedules a deletion", List.of(), MatchArchive.retain(archive, "game-5").evicted());
    }

    @Test void roundTripsEveryPartOfACompletedAnalysis(Checks check) {
        var match = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var review = GameReview.analyze(match, ignored -> { });
        var restored = GameReviewCodec.decode(ChessMatch.restore(match.checkpoint()), GameReviewCodec.encode(match, review));
        check.same("Opening, graph, ratings, counts, move details and variations survive reloading", review, restored);
        check.same("The archived review still has the exact move list", 4, restored.turns().size());
    }

    @Test @Tag("governance") void rejectsObsoleteIncompleteAndWrongCheckpointCaches(Checks check) {
        var match = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var review = GameReview.analyze(match, ignored -> { });
        String encoded = GameReviewCodec.encode(match, review);
        var bytes = Base64.getDecoder().decode(encoded);
        var truncated = Base64.getEncoder().encodeToString(Arrays.copyOf(bytes, bytes.length - 1));
        check.rejects("A truncated cache cannot replace a new calculation", IllegalArgumentException.class,
                () -> GameReviewCodec.decode(match, truncated));
        bytes[3]++;
        var newerVersion = Base64.getEncoder().encodeToString(bytes);
        check.rejects("An incompatible codec version is rejected", IllegalArgumentException.class, () -> GameReviewCodec.decode(match, newerVersion));
        check.rejects("Even identical moves with a different setup have a different identity", IllegalArgumentException.class,
                () -> GameReviewCodec.decode(ChessMatch.restore("3|WHITE|ADVANCED|0|f2f3 e7e5 g2g4 d8h4"), encoded));
        check.rejects("An unfinished line cannot reuse an archived analysis", IllegalArgumentException.class,
                () -> GameReviewCodec.decode(match.continueFrom(3), encoded));
    }
}
