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
            return Math.abs(score) >= MATE - POLICY.maxPlies
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

    private static Result analyze(ChessPosition position, int plies, List<Move> moves) {
        int startingBalance = material(position);
        var ranked = new ArrayList<Variation>(moves.size());
        for (Move move : moves) {
            // A fresh window gives every root move an exact score at this horizon, including ties.
            var reply = search(position.play(move), plies - 1, 1, -LIMIT, LIMIT).prepend(move);
            ranked.add(new Variation(reply.moves(), reply.score(), reply.balance() - startingBalance, reply.ending()));
        }
        ranked.sort(Comparator.comparingInt(Variation::score).reversed().thenComparing(entry -> entry.move().uci()));
        return new Result(ranked);
    }

    private static Line search(ChessPosition position, int left, int distance, int alpha, int beta) {
        if (left == 0) return leaf(position, distance, position.hasLegalMove());
        var moves = position.legalMoves();
        if (moves.isEmpty()) return leaf(position, distance, false);

        Line best = null;
        for (Move move : moves) {
            var child = search(position.play(move), left - 1, distance + 1, -beta, -alpha);
            int score = -child.score();
            if (best == null || score > best.score()) best = child.prepend(move);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        return best;
    }

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
