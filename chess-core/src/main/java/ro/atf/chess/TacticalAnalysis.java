package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Bounded tactical search from the point of view of the side to move. */
public final class TacticalAnalysis {
    private static final int MATE = 100_000;
    private static final int LIMIT = MATE + 1;
    private static final Policy POLICY = Policy.load();
    private static final Comparator<Variation> RANK = Comparator.comparingInt(Variation::score).reversed()
            .thenComparing(entry -> entry.move().uci());

    public enum Ending { HORIZON, CHECKMATE, STALEMATE }
    private enum Objective { MATERIAL, POSITION }

    public record Variation(List<Move> line, int score, int materialChange, Ending ending) {
        public Variation { line = Collections.unmodifiableList(new ArrayList<>(line)); }

        public Move move() { return line.get(0); }

        /** Positive for a winning mate, negative for a losing mate, zero if none is proved. */
        public int signedMatePlies() {
            return Math.abs(score) >= MATE - MatchAiPolicy.MAX_PLIES - 2
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
        return analyze(position, plies, position.legalMoves(), Objective.MATERIAL);
    }

    static Result checkingReplies(ChessPosition position, List<Move> replies) {
        return analyze(position, POLICY.questReplyPlies, replies, Objective.MATERIAL);
    }

    record SearchReport(Result result, int depth, int extraNodes) { }

    record ReviewSearch(Variation best, Variation played, int alternativeScore, int depth, boolean onlyProvedMate) { }

    static ReviewSearch review(ChessPosition position, Move played, Set<Move> draws, MatchAiPolicy.Search policy) {
        var root = new RootSearch(position, draws);
        var ranked = analyze(root, MatchAiPolicy.BASE_PLIES, position.legalMoves(), Objective.POSITION).ranked();
        var result = reviewAt(ranked, played, MatchAiPolicy.BASE_PLIES, null, -LIMIT);
        var moves = new ArrayList<Move>();
        for (var line : ranked) moves.add(line.move());
        var budget = new SearchBudget(policy.extraNodes(), 1);
        final int window = 180;
        for (int depth = MatchAiPolicy.BASE_PLIES + 1; depth <= policy.maxPlies(); depth++) {
            try {
                var next = deepen(root, moves, depth, budget, window, Map.of()).ranked();
                Variation missing = null;
                if (next.stream().noneMatch(line -> line.move().equals(played))) {
                    int balance = material(position);
                    var line = root.line(played, depth, LIMIT, budget, Objective.POSITION, balance);
                    missing = new Variation(line.moves(), line.score(), line.balance() - balance, line.ending());
                }
                var leader = next.get(0);
                result = reviewAt(next, played, depth, missing, leader.score() - window);
                moves.remove(leader.move());
                moves.add(0, leader.move());
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return result;
    }

    private static ReviewSearch reviewAt(List<Variation> ranked, Move played, int depth, Variation fallback, int alternativeFloor) {
        var chosen = ranked.stream().filter(line -> line.move().equals(played)).findFirst().orElse(fallback);
        int alternative = ranked.stream().filter(line -> !line.move().equals(played)).findFirst()
                .map(Variation::score).orElse(alternativeFloor);
        // Any other proved mate at this depth is within the root score window.
        boolean onlyMate = chosen.signedMatePlies() > 0 && ranked.stream()
                .noneMatch(line -> !line.move().equals(played) && line.signedMatePlies() > 0);
        return new ReviewSearch(ranked.get(0), chosen, alternative, depth, onlyMate);
    }

    static int offeredMaterial(ChessPosition position, Variation variation) {
        if (variation.line().size() < 2) return 0;
        var reply = position.playGenerated(variation.move()).playGenerated(variation.line().get(1));
        return material(position) - material(reply);
    }

    static SearchReport botAnalysis(ChessPosition position, MatchSetup.Level level) {
        return botAnalysis(position, level, 0);
    }

    static SearchReport botAnalysis(ChessPosition position, MatchSetup.Level level, int gap) {
        return botAnalysis(position, level, gap, Set.of());
    }

    static SearchReport botAnalysis(ChessPosition position, MatchSetup.Level level, int gap, Set<Move> repetitionOffers) {
        boolean hard = level == MatchSetup.Level.HARD;
        var repertoire = hard && gap == 0 ? OpeningRepertoire.CURRENT.reply(position) : null;
        var root = new RootSearch(position, repetitionOffers);
        var moves = repertoire == null ? position.legalMoves() : List.of(repertoire);
        var base = analyze(root, MatchAiPolicy.BASE_PLIES, moves, Objective.POSITION);
        return matchSearch(root, MatchAiPolicy.CURRENT.opponent(level), gap, 0, hard && repertoire == null, base);
    }

    static SearchReport hintAnalysis(ChessPosition position) {
        var policy = MatchAiPolicy.CURRENT.hint;
        var repertoire = OpeningRepertoire.CURRENT.reply(position);
        var root = new RootSearch(position, Set.of());
        if (repertoire != null) {
            var base = analyze(position, MatchAiPolicy.BASE_PLIES, List.of(repertoire), Objective.POSITION);
            return matchSearch(root, policy.search(), 0, policy.tacticalExtensionPlies(), false, base);
        }
        var hardPolicy = MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD);
        var base = openingSearch(position);
        var hard = matchSearch(root, hardPolicy, 0, 0, true, base);
        int available = policy.search().extraNodes() - hard.extraNodes();
        var tacticalPolicy = new MatchAiPolicy.Search(policy.search().maxPlies(), available - policy.verificationNodes());
        var tactical = matchSearch(root, tacticalPolicy, policy.maxLossCp(), policy.tacticalExtensionPlies(), false, base);
        var verified = chooseCoachingSearch(position, base, hard, tactical, policy);
        return new SearchReport(new Result(hintLines(new Result(coachingOrder(position, verified.result())), policy)),
                verified.depth(), hard.extraNodes() + tactical.extraNodes() + verified.extraNodes());
    }

    private static List<Variation> coachingOrder(ChessPosition position, Result result) {
        var ranked = new ArrayList<>(result.ranked());
        ranked.sort(Comparator.comparingInt(Variation::score).reversed()
                .thenComparing(Comparator.comparingInt((Variation line) -> ChessMatch.activity(position, line.move())).reversed())
                .thenComparing(line -> line.move().uci()));
        return ranked;
    }

    private static SearchReport chooseCoachingSearch(ChessPosition position, Result base, SearchReport hard, SearchReport tactical,
                                                      MatchAiPolicy.Hint policy) {
        if (base.ranked().isEmpty()) return new SearchReport(base, hard.depth(), 0);
        var hardBest = coachingOrder(position, hard.result()).get(0);
        var tacticalBest = coachingOrder(position, tactical.result()).get(0);
        int hardMate = hardBest.signedMatePlies();
        int tacticalMate = tacticalBest.signedMatePlies();
        if (hardMate != 0 || tacticalMate != 0) {
            var proof = hardMate == 0 || tacticalMate != 0 && tacticalBest.score() > hardBest.score() ? tactical : hard;
            return new SearchReport(proof.result(), proof.depth(), 0);
        }
        if (hardBest.move().equals(tacticalBest.move())) return new SearchReport(tactical.result(), tactical.depth(), 0);
        if (hard.depth() == MatchAiPolicy.BASE_PLIES) return new SearchReport(base, hard.depth(), 0);

        // Compare alternatives with the completed Hard horizon before displaying their score differences.
        var candidates = new LinkedHashSet<Move>();
        hard.result().ranked().forEach(line -> candidates.add(line.move()));
        tactical.result().ranked().forEach(line -> candidates.add(line.move()));
        var budget = new SearchBudget(policy.verificationNodes(), 0);
        var confirmation = hard.depth() == MatchAiPolicy.BASE_PLIES + 1 && material(position) == 0
                ? boundedShallowScores(position, base) : Map.<Move, Integer>of();
        Result result = hard.result();
        try {
            result = deepen(new RootSearch(position, Set.of()), List.copyOf(candidates), hard.depth(), budget, policy.maxLossCp(), confirmation);
        } catch (BudgetExhausted exhausted) {
            // The completed Hard result remains available if the alternatives cannot all be compared.
        }
        return new SearchReport(result, hard.depth(), policy.verificationNodes() - budget.remaining);
    }

    private static Result openingSearch(ChessPosition position) {
        return analyze(position, MatchAiPolicy.BASE_PLIES, position.legalMoves(), Objective.POSITION);
    }

    public static List<Variation> recommendations(ChessPosition position) {
        return hintAnalysis(position).result().ranked();
    }

    static int positionEstimate(ChessPosition position, int fallback) {
        var policy = MatchAiPolicy.CURRENT.evaluation;
        var budget = new SearchBudget(policy.nodes(), 2);
        int estimate = fallback;
        try {
            // Start with equal turns for both sides, then admit bounded evidence from the next ply.
            var even = search(position, 2, 0, -LIMIT, LIMIT, budget, Objective.POSITION);
            if (even.ending() != Ending.HORIZON) return estimate;
            estimate = even.score();
            var odd = search(position, 3, 0, -LIMIT, LIMIT, budget, Objective.POSITION);
            if (odd.ending() == Ending.HORIZON) {
                int correction = (odd.score() - estimate) / 2;
                int limit = policy.maxHorizonCorrectionCp();
                estimate += Math.max(-limit, Math.min(limit, correction));
            }
        } catch (BudgetExhausted exhausted) {
            // Keep the last complete estimate when the display budget is used up.
        }
        return estimate;
    }

    static List<Variation> hintLines(Result result, MatchAiPolicy.Hint policy) {
        if (result.ranked().isEmpty()) return List.of();
        Variation best = result.ranked().get(0);
        var selected = new ArrayList<Variation>();
        for (Variation line : result.ranked()) {
            if (selected.size() == policy.maxVariants()) break;
            if (best.signedMatePlies() != 0 || line.signedMatePlies() != 0) {
                if (line.score() != best.score()) break;
            } else if (best.score() - line.score() > policy.maxLossCp()) break;
            selected.add(line);
        }
        return List.copyOf(selected);
    }

    private static SearchReport matchSearch(RootSearch root, MatchAiPolicy.Search policy, int gap, int tacticalExtension,
                                            boolean confirmAcrossHorizons, Result base) {
        int completed = MatchAiPolicy.BASE_PLIES;
        Result best = base;
        if (policy.maxPlies() == completed || best.ranked().isEmpty() || best.ranked().get(0).signedMatePlies() == 1) {
            return new SearchReport(best, completed, 0);
        }
        var moves = new ArrayList<Move>();
        for (var line : best.ranked()) moves.add(line.move());
        // In an unequal position, let the deeper search resolve immediate material recovery on its own.
        var shallowScores = confirmAcrossHorizons && material(root.position()) == 0
                ? boundedShallowScores(root.position(), best) : Map.<Move, Integer>of();
        var budget = new SearchBudget(policy.extraNodes(), tacticalExtension);
        for (int depth = completed + 1; depth <= policy.maxPlies(); depth++) {
            try {
                // Give Hard a two-horizon ranking if the next full iteration cannot finish.
                var confirmation = depth == MatchAiPolicy.BASE_PLIES + 1 ? shallowScores : Map.<Move, Integer>of();
                best = deepen(root, moves, depth, budget, gap, confirmation);
                completed = depth;
                var leader = best.ranked().get(0).move();
                moves.remove(leader);
                moves.add(0, leader);
            }
            catch (BudgetExhausted exhausted) { break; }
        }
        return new SearchReport(best, completed, policy.extraNodes() - budget.remaining);
    }

    private static Map<Move, Integer> boundedShallowScores(ChessPosition position, Result shallow) {
        int pawn = POLICY.values[Kind.PAWN.ordinal()];
        var scores = new HashMap<Move, Integer>();
        for (var line : shallow.ranked()) {
            if (line.ending() != Ending.HORIZON) continue;
            int gain = line.materialChange();
            // A quiet move gets less credit for a large gain several moves later than a forcing capture.
            int credit = Math.min(gain, pawn);
            if (gain > pawn && captureOrder(position, line.move()) == 0) credit = Math.max(0, 2 * pawn - gain);
            int adjusted = line.score() - gain + credit;
            scores.put(line.move(), Math.max(-2 * pawn, Math.min(2 * pawn, adjusted)));
        }
        return scores;
    }

    private static Result deepen(RootSearch root, List<Move> moves, int plies, SearchBudget budget, int gap,
                                 Map<Move, Integer> shallowScores) {
        var best = new ArrayList<Variation>();
        int top = -LIMIT;
        int startingBalance = material(root.position());
        for (Move move : moves) {
            int cutoff = top - gap;
            int offset = root.repetitionOffers().contains(move) ? 0 : shallowScores.getOrDefault(move, 0);
            // Allow for the bonus before pruning and keep the boundary inclusive. Endings use the unadjusted score.
            int beta = Math.min(LIMIT, 1 - cutoff + Math.max(0, offset));
            var line = root.line(move, plies, beta, budget, Objective.POSITION, startingBalance);
            int score = line.ending() == Ending.HORIZON ? line.score() + offset : line.score();
            if (score < cutoff) continue;
            top = Math.max(top, score);
            best.add(new Variation(line.moves(), score, line.balance() - startingBalance, line.ending()));
        }
        int floor = top - gap;
        best.removeIf(line -> line.score() < floor);
        best.sort(RANK);
        return new Result(best);
    }

    private static Result analyze(ChessPosition position, int plies, List<Move> moves, Objective objective) {
        return analyze(new RootSearch(position, Set.of()), plies, moves, objective);
    }

    private static Result analyze(RootSearch root, int plies, List<Move> moves, Objective objective) {
        int startingBalance = material(root.position());
        var ranked = new ArrayList<Variation>(moves.size());
        for (Move move : moves) {
            checkInterrupted();
            // A fresh window gives every root move an exact score at this horizon, including ties.
            var reply = root.line(move, plies, LIMIT, null, objective, startingBalance);
            ranked.add(new Variation(reply.moves(), reply.score(), reply.balance() - startingBalance, reply.ending()));
        }
        ranked.sort(RANK);
        return new Result(ranked);
    }

    private record RootSearch(ChessPosition position, Set<Move> repetitionOffers) {
        Line line(Move move, int plies, int beta, SearchBudget budget, Objective objective, int startingBalance) {
            // Discount the projected score when a repeat invites a draw. The game still decides its own result.
            if (repetitionOffers.contains(move)) return new Line(0, startingBalance, List.of(move), Ending.HORIZON);
            return search(position.playGenerated(move), plies - 1, 1, -LIMIT, beta, budget, objective).prepend(move);
        }
    }

    private static Line search(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget, Objective objective) {
        if (budget != null) budget.visit();
        if (left == 0) {
            return budget == null || budget.tacticalExtension == 0 ? leaf(position, distance, position.hasLegalMove(), objective)
                    : settle(position, budget.tacticalExtension, distance, alpha, beta, budget);
        }
        var moves = position.legalMoves();
        if (moves.isEmpty()) return leaf(position, distance, false, objective);
        if (objective == Objective.POSITION) {
            moves = new ArrayList<>(moves);
            moves.sort(Comparator.comparingInt((Move move) -> moveOrder(position, move)).reversed());
        }

        Line best = null;
        for (Move move : moves) {
            var child = search(position.playGenerated(move), left - 1, distance + 1, -beta, -alpha, budget, objective);
            int score = -child.score();
            if (best == null || score > best.score()) best = child.prepend(move);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        return best;
    }

    /** Continue tactical exchanges at the search boundary before scoring material. */
    private static Line settle(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget) {
        if (left == 0) return leaf(position, distance, position.hasLegalMove(), Objective.POSITION);
        boolean inCheck = position.isCheck();
        var moves = new ArrayList<Move>();
        Line best = null;
        if (inCheck) {
            moves.addAll(position.legalMoves());
            if (moves.isEmpty()) return leaf(position, distance, false, Objective.POSITION);
        } else {
            if (!position.hasLegalMove()) return leaf(position, distance, false, Objective.POSITION);
            best = leaf(position, distance, true, Objective.POSITION);
            if (best.score() >= beta) return best;
            alpha = Math.max(alpha, best.score());
            moves.addAll(position.legalCapturesAndPromotions());
        }
        moves.sort(Comparator.comparingInt((Move move) -> captureOrder(position, move)).reversed());
        for (Move move : moves) {
            budget.visit();
            var child = settle(position.playGenerated(move), left - 1, distance + 1, -beta, -alpha, budget).prepend(move);
            if (best == null || child.score() > best.score()) best = child;
            alpha = Math.max(alpha, child.score());
            if (alpha >= beta) break;
        }
        return best;
    }

    private static int captureOrder(ChessPosition position, Move move) {
        var victim = position.pieceAt(move.to());
        var attacker = position.pieceAt(move.from());
        int capture = victim == null ? (attacker.kind() == Kind.PAWN && move.from() % 8 != move.to() % 8 ? 100 : 0)
                : POLICY.values[victim.kind().ordinal()];
        return (capture == 0 ? 0 : 1_000 + 10 * capture - POLICY.values[attacker.kind().ordinal()])
                + (move.promotion() == null ? 0 : 10_000 + POLICY.values[move.promotion().ordinal()]);
    }

    private static int moveOrder(ChessPosition position, Move move) {
        return captureOrder(position, move) + ChessMatch.activity(position, move);
    }

    private static final class SearchBudget {
        private int remaining;
        private final int tacticalExtension;

        SearchBudget(int remaining, int tacticalExtension) {
            this.remaining = remaining;
            this.tacticalExtension = tacticalExtension;
        }

        void visit() {
            checkInterrupted();
            if (remaining == 0) throw new BudgetExhausted();
            remaining--;
        }
    }

    private static final class BudgetExhausted extends RuntimeException { }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static Line leaf(ChessPosition position, int distance, boolean hasMove, Objective objective) {
        int balance = material(position);
        if (hasMove) {
            int score = objective == Objective.POSITION ? balance + MatchPositionEvaluation.bonus(position) : balance;
            return new Line(score, balance, Collections.emptyList(), Ending.HORIZON);
        }
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
