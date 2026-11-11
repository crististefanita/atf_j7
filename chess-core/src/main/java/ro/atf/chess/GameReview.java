package ro.atf.chess;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Local analysis of the recorded line, with independent scores for both sides. */
public record GameReview(List<Turn> turns, Opening opening, int bookPlies, int initialWhitePoints,
                         Player white, Player black) {
    public enum Grade { BOOK, FORCED, BRILLIANT, GREAT, BEST, EXCELLENT, GOOD, INACCURACY, MISTAKE, MISS, BLUNDER }
    public record GradeCount(Grade grade, int human, int ai) { }

    public List<GradeCount> compareGrades(Side humanSide) {
        var human = humanSide == Side.WHITE ? white : black;
        var ai = humanSide == Side.WHITE ? black : white;
        var counts = new ArrayList<GradeCount>();
        for (var grade : List.of(Grade.BRILLIANT, Grade.GREAT, Grade.BEST, Grade.EXCELLENT, Grade.GOOD, Grade.BOOK,
                Grade.FORCED, Grade.INACCURACY, Grade.MISTAKE, Grade.MISS, Grade.BLUNDER)) {
            counts.add(new GradeCount(grade, human.grades().getOrDefault(grade, 0), ai.grades().getOrDefault(grade, 0)));
        }
        return List.copyOf(counts);
    }
    public record Rating(int elo, Integer low, int high) { }
    public record Opening(String eco, String name) { }
    public record Player(OptionalInt accuracy, Optional<Rating> rating, int ratedMoves, Map<Grade, Integer> grades) {
        public Player { grades = Map.copyOf(grades); }
    }
    public record Turn(int ply, Side side, Move played, Grade grade, int bestPoints, int playedPoints, int loss,
                       int whitePoints, int depth, List<Move> bestLine, List<Move> playedLine, int sacrificeCp) {
        public Turn {
            bestLine = List.copyOf(bestLine);
            playedLine = List.copyOf(playedLine);
        }
    }

    public GameReview { turns = List.copyOf(turns); }

    public static GameReview analyze(ChessMatch match, IntConsumer progress) {
        if (match.result() == ChessMatch.Result.IN_PROGRESS) throw new IllegalArgumentException("Finish the game before reviewing it");
        var policy = GameReviewPolicy.CURRENT;
        var book = OpeningIndex.CURRENT.recognize(match);
        var turns = new ArrayList<Turn>(match.ply());
        var white = new PlayerStats();
        var black = new PlayerStats();
        int initial = 500;
        var before = match.continueFrom(0);
        for (int ply = 1; ply <= match.ply(); ply++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            var board = before.position();
            var played = match.moveAt(ply);
            var comparison = TacticalAnalysis.review(board, played, before.automaticDrawMoves(), policy.search);
            int best = points(comparison.best().score(), policy.scoreScale);
            int actual = points(comparison.played().score(), policy.scoreScale);
            int loss = Math.max(0, best - actual);
            int alternative = points(comparison.alternativeScore(), policy.scoreScale);
            int sacrifice = Math.max(0, TacticalAnalysis.offeredMaterial(board, comparison.played()));
            boolean bookMove = ply <= book.bookPlies() && loss < 100;
            boolean forced = board.legalMoves().size() == 1;
            int previousLoss = turns.isEmpty() ? 0 : turns.get(turns.size() - 1).loss();
            Grade grade = classify(bookMove, forced, comparison.best().score() == comparison.played().score(),
                    comparison.depth(), best, actual, alternative, sacrifice, previousLoss, comparison.onlyProvedMate());
            var side = board.turn();
            if (ply == 1) initial = side == Side.WHITE ? best : 1000 - best;
            turns.add(new Turn(ply, side, played, grade, best, actual, loss, side == Side.WHITE ? actual : 1000 - actual,
                    comparison.depth(), comparison.best().line(), comparison.played().line(), sacrifice));
            (side == Side.WHITE ? white : black).add(grade, loss);
            before = before.play(played);
            progress.accept(ply);
        }
        var opponent = policy.opponentRatings.get(match.setup().level());
        var human = match.setup().humanSide();
        var humanStats = human == Side.WHITE ? white : black;
        var humanRating = estimate(match.result(), human, humanStats.choices, opponent, policy.minimumRatedMoves);
        return new GameReview(turns, book.opening(), book.bookPlies(), initial,
                white.finish(human == Side.WHITE ? humanRating : Optional.of(opponent)),
                black.finish(human == Side.BLACK ? humanRating : Optional.of(opponent)));
    }

    static int points(int score, int scale) {
        return (int) Math.round(1000 / (1 + Math.exp(-score / (double) scale)));
    }

    static Grade classify(boolean book, boolean forced, boolean bestMove, int depth, int best, int played,
                          int alternative, int sacrifice, int previousLoss, boolean onlyProvedMate) {
        if (book) return Grade.BOOK;
        if (forced) return Grade.FORCED;
        int loss = Math.max(0, best - played);
        boolean verified = depth >= 4 && bestMove;
        boolean distinct = best - alternative >= 100;
        if (verified && (distinct || onlyProvedMate) && played >= 550 && sacrifice >= 200) return Grade.BRILLIANT;
        if (verified && distinct && (best >= 750 && alternative < 700 || best >= 450 && alternative < 400)) return Grade.GREAT;
        if (bestMove) return Grade.BEST;
        if (previousLoss >= 100 && best >= 750 && played <= 550) return Grade.MISS;
        if (loss >= 200) return Grade.BLUNDER;
        if (loss >= 100) return Grade.MISTAKE;
        if (loss >= 50) return Grade.INACCURACY;
        return loss >= 20 ? Grade.GOOD : Grade.EXCELLENT;
    }

    static Optional<Rating> estimate(ChessMatch.Result result, Side human, int moves, Rating opponent, int minimum) {
        if (moves < minimum) return Optional.empty();
        double whiteScore = MatchEvaluation.terminalWhiteShare(result);
        double score = human == Side.WHITE ? whiteScore : 1 - whiteScore;
        // One observed game updates a uniform prior on expected score against the reference opponent.
        int center = opponent.elo() + eloDifference((1 + score) / 3);
        Integer low = opponent.low() == null ? null : opponent.low() + eloDifference(scoreQuantile(score, .05));
        int high = opponent.high() + eloDifference(scoreQuantile(score, .95));
        return Optional.of(new Rating(center, low, high));
    }

    private static int eloDifference(double score) {
        return (int) Math.round(400 * Math.log10(score / (1 - score)));
    }

    private static double scoreQuantile(double score, double quantile) {
        if (score == 1) return Math.sqrt(quantile);
        if (score == 0) return 1 - Math.sqrt(1 - quantile);
        // The half-point posterior is symmetric. Its integral can be inverted by bisection.
        double low = 0;
        double high = 1;
        for (int step = 0; step < 32; step++) {
            double mid = (low + high) / 2;
            double x = 2 * mid - 1;
            double cumulative = .5 + (Math.asin(x) + x * Math.sqrt(1 - x * x)) / Math.PI;
            if (cumulative < quantile) low = mid;
            else high = mid;
        }
        return (low + high) / 2;
    }

    private static final class PlayerStats {
        private final Map<Grade, Integer> grades = new EnumMap<>(Grade.class);
        private int choices;
        private long totalLoss;

        void add(Grade grade, int loss) {
            grades.merge(grade, 1, Integer::sum);
            if (grade == Grade.BOOK || grade == Grade.FORCED) return;
            choices++;
            totalLoss += loss;
        }

        Player finish(Optional<Rating> rating) {
            var accuracy = choices == 0 ? OptionalInt.empty() : OptionalInt.of((int) Math.round(100 - totalLoss / (10.0 * choices)));
            return new Player(accuracy, rating, choices, grades);
        }
    }
}
