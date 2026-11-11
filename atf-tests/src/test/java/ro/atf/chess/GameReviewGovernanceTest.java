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
    @Test void classificationKeepsPublishedDefaultsAndRejectsInvalidOverrides(Checks check) {
        check.same("All published grade boundaries survive the migration to YAML",
                new GameReviewPolicy.Classification(100, 4, 100, 550, 200, 750, 700, 450, 400, 100, 750, 550, 20, 50, 100, 200),
                GameReviewPolicy.CURRENT.classification);
        var root = new HashMap<Object, Object>(ChessYaml.resource("game-review.yaml"));
        var thresholds = new HashMap<Object, Object>(ChessYaml.map(root, "classification"));
        root.put("classification", thresholds);
        thresholds.remove("good-loss-min");
        check.rejects("An incomplete policy does not fall back silently", IllegalArgumentException.class, () -> GameReviewPolicy.read(root));
        for (var invalid : List.of(50, 20.5, -1, 1001)) {
            thresholds.put("good-loss-min", invalid);
            check.rejects("Reject invalid good threshold " + invalid, IllegalArgumentException.class, () -> GameReviewPolicy.read(root));
        }
    }

    @Test void reviewBudgetAndReferenceRangesAreExplicitAndValidated(Checks check) {
        var policy = GameReviewPolicy.CURRENT;
        var hard = MatchAiPolicy.CURRENT.opponent(Level.HARD);
        check.same("Post-game review can search at least as deep as Hard within a separate bounded budget", true,
                policy.search.maxPlies() >= hard.maxPlies() && policy.search.extraNodes() > hard.extraNodes()
                        && policy.search.extraNodes() <= MatchAiPolicy.CURRENT.hint.search().extraNodes());
        check.same("The benchmarks cover all selectable profiles", Set.of(Level.values()), policy.opponentRatings.keySet());
        var protocol = ChessYaml.resource("ai-anchor.yaml");
        var league = AiLeague.load(ChessYaml.resource("ai-league.yaml"));
        check.same("Stockfish strength, nodes, game cap and bootstrap size are frozen", List.of("Stockfish 19", 1500, 6000, 160, 10000),
                List.of(ChessYaml.string(protocol, "stockfish"), ChessYaml.number(protocol, "uci-elo"),
                        ChessYaml.number(protocol, "nodes-per-move"), league.maxPlies(), league.samples()));
        var evidence = ChessYaml.resource("ai-anchor-observations.yaml");
        for (Level level : Level.values()) {
            check.same(level + " displayed Elo matches the opening-cluster calculation from all paired sessions",
                    AiAnchorEvidence.rating(evidence, level), policy.opponentRatings.get(level));
        }
        var incomplete = new HashMap<Object, Object>(evidence);
        incomplete.put("sessions", ChessYaml.list(evidence, "sessions").subList(0, 2));
        check.rejects("Two sessions cannot replace the published calibration", IllegalArgumentException.class,
                () -> AiAnchorEvidence.rating(incomplete, Level.HARD));
        var invalid = new HashMap<Object, Object>(ChessYaml.resource("game-review.yaml"));
        invalid.put("minimum-rated-moves", 1);
        check.rejects("The policy refuses Elo after a single decision", IllegalArgumentException.class, () -> GameReviewPolicy.read(invalid));
        invalid.put("minimum-rated-moves", 10);
        var ratings = new HashMap<Object, Object>(ChessYaml.map(invalid, "opponents"));
        ratings.put("ADVANCED", Map.of("elo", 1461, "low", 1450, "high", 1440));
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
