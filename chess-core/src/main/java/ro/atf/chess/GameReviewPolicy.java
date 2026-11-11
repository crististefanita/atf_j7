package ro.atf.chess;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

final class GameReviewPolicy {
    static final GameReviewPolicy CURRENT = read(ChessYaml.resource("game-review.yaml"));

    final MatchAiPolicy.Search search;
    final int scoreScale;
    final int minimumRatedMoves;
    final Map<MatchSetup.Level, GameReview.Rating> opponentRatings;

    private GameReviewPolicy(MatchAiPolicy.Search search, int scoreScale, int minimumRatedMoves,
                             Map<MatchSetup.Level, GameReview.Rating> opponentRatings) {
        this.search = search;
        this.scoreScale = scoreScale;
        this.minimumRatedMoves = minimumRatedMoves;
        this.opponentRatings = Map.copyOf(opponentRatings);
    }

    static GameReviewPolicy read(Map<?, ?> root) {
        if (!root.keySet().equals(Set.of("schema", "depth", "extra-nodes", "score-scale", "minimum-rated-moves", "opponents"))
                || ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid game review policy");
        var opponents = ChessYaml.map(root, "opponents");
        if (!opponents.keySet().equals(Set.of("BASIC", "ADVANCED", "HARD"))) throw new IllegalArgumentException("Missing game review profiles");
        var ratings = new EnumMap<MatchSetup.Level, GameReview.Rating>(MatchSetup.Level.class);
        for (var level : MatchSetup.Level.values()) {
            var row = ChessYaml.map(opponents, level.name());
            if (!row.keySet().equals(Set.of("elo", "low", "high"))) {
                throw new IllegalArgumentException("Invalid game review anchor");
            }
            int elo = bounded(row, "elo", 800, 3200);
            Integer low = row.get("low") == null ? null : bounded(row, "low", 0, elo);
            int high = bounded(row, "high", elo, 3200);
            ratings.put(level, new GameReview.Rating(elo, low, high));
        }
        return new GameReviewPolicy(new MatchAiPolicy.Search(bounded(root, "depth", 4, MatchAiPolicy.MAX_PLIES),
                bounded(root, "extra-nodes", 30_000, 250_000)), bounded(root, "score-scale", 100, 500),
                bounded(root, "minimum-rated-moves", 10, 30), ratings);
    }

    private static int bounded(Map<?, ?> row, String key, int min, int max) {
        int value = ChessYaml.number(row, key);
        if (value < min || value > max) throw new IllegalArgumentException("Invalid game review setting: " + key);
        return value;
    }
}
