package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class GameReviewGovernanceTest {
    @Test void reviewBudgetAndReferenceRangesAreExplicitAndValidated(Checks check) {
        var policy = GameReviewPolicy.CURRENT;
        var hard = MatchAiPolicy.CURRENT.opponent(Level.HARD);
        check.same("Post-game review can search at least as deep as Hard within a separate bounded budget", true,
                policy.search.maxPlies() >= hard.maxPlies() && policy.search.extraNodes() > hard.extraNodes()
                        && policy.search.extraNodes() <= MatchAiPolicy.CURRENT.hint.search().extraNodes());
        check.same("The benchmarks cover all selectable profiles", Set.of(Level.values()), policy.opponentRatings.keySet());
        check.same("The configured estimates match the paired Stockfish reference evidence", List.of(
                new GameReview.Rating(1245, null, 1442), new GameReview.Rating(1544, 1346, 1755), new GameReview.Rating(1380, 1083, 1558)),
                List.of(policy.opponentRatings.get(Level.BASIC), policy.opponentRatings.get(Level.ADVANCED), policy.opponentRatings.get(Level.HARD)));
        check.same("The Basic reference keeps its measured unbounded endpoint", null, policy.opponentRatings.get(Level.BASIC).low());
        var invalid = new HashMap<Object, Object>(ChessYaml.resource("game-review.yaml"));
        invalid.put("minimum-rated-moves", 1);
        check.rejects("The policy refuses Elo after a single decision", IllegalArgumentException.class, () -> GameReviewPolicy.read(invalid));
        invalid.put("minimum-rated-moves", 10);
        var ratings = new HashMap<Object, Object>(ChessYaml.map(invalid, "opponents"));
        ratings.put("ADVANCED", Map.of("elo", 1544, "low", 1540, "high", 1530));
        invalid.put("opponents", ratings);
        check.rejects("A range that excludes its reference estimate is rejected", IllegalArgumentException.class, () -> GameReviewPolicy.read(invalid));
        check.same("The expected-score transform preserves color symmetry", 1000,
                GameReview.points(250, policy.scoreScale) + GameReview.points(-250, policy.scoreScale));
    }

    @Test void everyImportedOpeningReplaysLegallyToItsPublishedPosition(Checks check) throws Exception {
        var failures = new ArrayList<String>();
        var volumes = new TreeSet<Character>();
        int rows = 0;
        var stream = OpeningIndex.class.getResourceAsStream("/" + OpeningIndex.RESOURCE);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            check.same("The data names the pinned CC0 upstream revision", true,
                    reader.readLine().equals("# lichess-org/chess-openings 3ba6b20d31b2e6125cb0e6a2e792f400717d9d39 CC0-1.0"));
            check.same("The opening schema carries notation and an independent position", "eco\tname\tepd\tmoves", reader.readLine());
            for (String row; (row = reader.readLine()) != null;) {
                var fields = row.split("\t", -1);
                rows++;
                try {
                    var position = ChessPosition.initial();
                    for (String move : fields[3].split(" ")) position = position.play(Move.fromUci(move));
                    var expected = ChessPosition.fromFen(fields[2] + " 0 1");
                    if (!position.sameForRepetition(expected)) failures.add(fields[0] + " " + fields[1]);
                    volumes.add(fields[0].charAt(0));
                } catch (IllegalArgumentException invalid) {
                    failures.add(fields[0] + " " + invalid.getMessage());
                }
            }
        }
        check.same("The imported catalog covers every ECO volume", Set.of('A', 'B', 'C', 'D', 'E'), volumes);
        check.same("The full pinned source is present", 3865, rows);
        check.same("Every legal UCI line reaches the independently generated EPD", List.of(), failures);
    }
}
