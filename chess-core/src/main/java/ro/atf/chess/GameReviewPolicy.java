package ro.atf.chess;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

final class GameReviewPolicy {
    static final GameReviewPolicy CURRENT = read(ChessYaml.resource("game-review.yaml"));

    final MatchAiPolicy.Search search;
    final int scoreScale;
    final int minimumRatedMoves;
    final Classification classification;
    final Map<MatchSetup.Level, GameReview.Rating> opponentRatings;

    private GameReviewPolicy(MatchAiPolicy.Search search, int scoreScale, int minimumRatedMoves,
                             Map<MatchSetup.Level, GameReview.Rating> opponentRatings, Classification classification) {
        this.search = search;
        this.scoreScale = scoreScale;
        this.minimumRatedMoves = minimumRatedMoves;
        this.opponentRatings = Map.copyOf(opponentRatings);
        this.classification = classification;
    }

    static GameReviewPolicy read(Map<?, ?> root) {
        if (!root.keySet().equals(Set.of("schema", "depth", "extra-nodes", "score-scale", "minimum-rated-moves", "opponents", "classification"))
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
                bounded(root, "minimum-rated-moves", 10, 30), ratings, Classification.read(ChessYaml.map(root, "classification")));
    }

    record Classification(int bookLoss, int verifiedDepth, int distinctGap, int brilliantPlayed, int brilliantSacrifice,
                          int winningBest, int winningAlternative, int recoveryBest, int recoveryAlternative,
                          int missPreviousLoss, int missBest, int missPlayed, int good, int inaccuracy, int mistake, int blunder) {
        static Classification read(Map<?, ?> row) {
            if (!row.keySet().equals(Set.of("book-loss-exclusive", "verified-depth", "distinct-gap", "brilliant-played-min",
                    "brilliant-sacrifice-cp", "great-winning-best-min", "great-winning-alternative-exclusive",
                    "great-recovery-best-min", "great-recovery-alternative-exclusive", "miss-previous-loss-min",
                    "miss-best-min", "miss-played-max", "good-loss-min", "inaccuracy-loss-min", "mistake-loss-min", "blunder-loss-min"))) {
                throw new IllegalArgumentException("Invalid classification keys");
            }
            var policy = new Classification(points(row, "book-loss-exclusive"), bounded(row, "verified-depth", 1, MatchAiPolicy.MAX_PLIES),
                    points(row, "distinct-gap"), points(row, "brilliant-played-min"), bounded(row, "brilliant-sacrifice-cp", 0, 4_000),
                    points(row, "great-winning-best-min"), points(row, "great-winning-alternative-exclusive"),
                    points(row, "great-recovery-best-min"), points(row, "great-recovery-alternative-exclusive"),
                    points(row, "miss-previous-loss-min"), points(row, "miss-best-min"), points(row, "miss-played-max"),
                    points(row, "good-loss-min"), points(row, "inaccuracy-loss-min"), points(row, "mistake-loss-min"), points(row, "blunder-loss-min"));
            if (!(policy.good > 0 && policy.good < policy.inaccuracy && policy.inaccuracy < policy.mistake && policy.mistake < policy.blunder
                    && policy.winningAlternative < policy.winningBest && policy.recoveryAlternative < policy.recoveryBest
                    && policy.recoveryBest < policy.winningBest && policy.missPlayed < policy.missBest)) {
                throw new IllegalArgumentException("Invalid classification threshold order");
            }
            return policy;
        }

        private static int points(Map<?, ?> row, String key) { return bounded(row, key, 0, 1_000); }
    }

    private static int bounded(Map<?, ?> row, String key, int min, int max) {
        int value = ChessYaml.number(row, key);
        if (value < min || value > max) throw new IllegalArgumentException("Invalid game review setting: " + key);
        return value;
    }
}
