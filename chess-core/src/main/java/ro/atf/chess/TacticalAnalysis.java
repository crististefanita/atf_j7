package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Bounded tactical search from the point of view of the side to move. */
public final class TacticalAnalysis {
    private static final int MATE = 100_000;
    private static final int LIMIT = MATE + 1;
    private static final Policy POLICY = Policy.load();

    public enum Ending { HORIZON, CHECKMATE, STALEMATE }

    public record Variation(List<Move> line, int score, int materialChange, Ending ending) {
        public Variation { line = Collections.unmodifiableList(new ArrayList<>(line)); }

        public Move move() { return line.get(0); }

        /** Positive for a winning mate, negative for a losing mate, zero if none is proved. */
        public int signedMatePlies() {
            return Math.abs(score) >= MATE - MatchSetup.Level.HARD.maxPlies
                    ? Integer.signum(score) * (MATE - Math.abs(score)) : 0;
        }
    }

    public record Result(List<Variation> ranked) {
        public Result { ranked = Collections.unmodifiableList(new ArrayList<>(ranked)); }

        public List<Variation> best() {
            if (ranked.isEmpty()) return Collections.emptyList();
            int score = ranked.get(0).score();
            int count = 1;
            while (count < ranked.size() && ranked.get(count).score() == score) count++;
            return ranked.subList(0, count);
        }
    }

    private TacticalAnalysis() { }

    public static Result analyze(ChessPosition position, int plies) {
        if (plies < 1 || plies > POLICY.maxPlies) throw new IllegalArgumentException("Unsupported analysis depth: " + plies);
        return analyze(position, plies, position.legalMoves());
    }

    static Result checkingReplies(ChessPosition position, List<Move> replies) {
        return analyze(position, POLICY.questReplyPlies, replies);
    }

    static Result botAnalysis(ChessPosition position, MatchSetup.Level level) {
        Result best = analyze(position, MatchSetup.Level.BASIC.maxPlies);
        if (level == MatchSetup.Level.BASIC) return best;
        var moves = new ArrayList<Move>();
        for (var line : best.ranked()) moves.add(line.move());
        var budget = new SearchBudget(level.extraNodes);
        for (int depth = MatchSetup.Level.BASIC.maxPlies + 1; depth <= level.maxPlies; depth++) {
            try { best = deepen(position, moves, depth, budget); }
            catch (BudgetExhausted exhausted) { break; }
        }
        return best;
    }

    private static Result deepen(ChessPosition position, List<Move> moves, int plies, SearchBudget budget) {
        var best = new ArrayList<Variation>();
        int top = -LIMIT;
        int startingBalance = material(position);
        for (Move move : moves) {
            // A one-point margin keeps exact ties while allowing inferior root moves to cut off.
            int beta = Math.min(LIMIT, 1 - top);
            var line = search(position.play(move), plies - 1, 1, -LIMIT, beta, budget).prepend(move);
            if (line.score() < top) continue;
            if (line.score() > top) {
                best.clear();
                top = line.score();
            }
            best.add(new Variation(line.moves(), top, line.balance() - startingBalance, line.ending()));
        }
        best.sort(Comparator.comparing(entry -> entry.move().uci()));
        return new Result(best);
    }

    private static Result analyze(ChessPosition position, int plies, List<Move> moves) {
        int startingBalance = material(position);
        var ranked = new ArrayList<Variation>(moves.size());
        for (Move move : moves) {
            // A fresh window gives every root move an exact score at this horizon, including ties.
            var reply = search(position.play(move), plies - 1, 1, -LIMIT, LIMIT, null).prepend(move);
            ranked.add(new Variation(reply.moves(), reply.score(), reply.balance() - startingBalance, reply.ending()));
        }
        ranked.sort(Comparator.comparingInt(Variation::score).reversed().thenComparing(entry -> entry.move().uci()));
        return new Result(ranked);
    }

    private static Line search(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget) {
        if (budget != null) budget.visit();
        if (left == 0) return leaf(position, distance, position.hasLegalMove());
        var moves = position.legalMoves();
        if (moves.isEmpty()) return leaf(position, distance, false);
        if (budget != null) {
            moves = new ArrayList<>(moves);
            moves.sort(Comparator.comparingInt((Move move) -> captureOrder(position, move)).reversed());
        }

        Line best = null;
        for (Move move : moves) {
            var child = search(position.play(move), left - 1, distance + 1, -beta, -alpha, budget);
            int score = -child.score();
            if (best == null || score > best.score()) best = child.prepend(move);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        return best;
    }

    private static int captureOrder(ChessPosition position, Move move) {
        var victim = position.pieceAt(move.to());
        return (victim == null ? 0 : POLICY.values[victim.kind().ordinal()]) + (move.promotion() == null ? 0 : 1000);
    }

    private static final class SearchBudget {
        private int remaining;

        SearchBudget(int remaining) { this.remaining = remaining; }

        void visit() { if (remaining-- == 0) throw new BudgetExhausted(); }
    }

    private static final class BudgetExhausted extends RuntimeException { }

    private static Line leaf(ChessPosition position, int distance, boolean hasMove) {
        int balance = material(position);
        if (hasMove) return new Line(balance, balance, Collections.emptyList(), Ending.HORIZON);
        boolean mate = position.isCheck();
        return new Line(mate ? distance - MATE : 0, balance, Collections.emptyList(),
                mate ? Ending.CHECKMATE : Ending.STALEMATE);
    }

    private static int material(ChessPosition position) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece != null) score += (piece.side() == position.turn() ? 1 : -1) * POLICY.values[piece.kind().ordinal()];
        }
        return score;
    }

    private record Line(int score, int balance, List<Move> moves, Ending ending) {
        Line prepend(Move move) {
            var variation = new ArrayList<Move>(moves.size() + 1);
            variation.add(move);
            variation.addAll(moves);
            return new Line(-score, -balance, variation, ending);
        }
    }

    private record Policy(int maxPlies, int questReplyPlies, int[] values) {
        static Policy load() {
            var root = ChessYaml.resource("tactical-analysis.yaml");
            int max = ChessYaml.number(root, "max-plies");
            int quest = ChessYaml.number(root, "quest-reply-plies");
            if (ChessYaml.number(root, "schema") != 1 || max < 1 || max > 3 || quest < 1 || quest > max) {
                throw new IllegalArgumentException("Invalid tactical analysis depth");
            }
            var values = new int[Kind.values().length];
            var weights = ChessYaml.map(root, "material");
            for (Kind kind : Kind.values()) {
                int value = ChessYaml.number(weights, kind.name());
                if (kind == Kind.KING ? value != 0 : value < 1 || value > 1000) {
                    throw new IllegalArgumentException("Invalid material weight for " + kind);
                }
                values[kind.ordinal()] = value;
            }
            return new Policy(max, quest, values);
        }
    }
}
