package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Bounded tactical search from the point of view of the side to move. */
public final class TacticalAnalysis {
    private static final int MATE = 100_000;
    private static final int LIMIT = MATE + 1;
    private static final int HINT_ROOT_LIMIT = 2;
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
            return Math.abs(score) >= MATE - MatchAiPolicy.MAX_PLIES - 4
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

    record SearchReport(Result result, int depth, int extraNodes, List<Move> horizonLeaders,
                        Map<Move, Integer> scoreCredits, Set<Move> excludedRoots, Set<Move> nonEquivalentRoots) {
        SearchReport(Result result, int depth, int extraNodes) { this(result, depth, extraNodes, List.of(), Map.of(), Set.of(), Set.of()); }
        SearchReport(Result result, int depth, int extraNodes, List<Move> leaders) { this(result, depth, extraNodes, leaders, Map.of(), Set.of(), Set.of()); }
        SearchReport { horizonLeaders = List.copyOf(horizonLeaders); scoreCredits = Map.copyOf(scoreCredits); excludedRoots = Set.copyOf(excludedRoots); nonEquivalentRoots = Set.copyOf(nonEquivalentRoots); }
        Variation rawLine(Variation line) {
            int credit = line.ending() == Ending.HORIZON ? scoreCredits.getOrDefault(line.move(), 0) : 0;
            return credit == 0 ? line : new Variation(line.line(), line.score() - credit, line.materialChange(), line.ending());
        }
    }

    record ReviewSearch(Variation best, Variation played, int alternativeScore, int depth, boolean onlyProvedMate) { }

    static ReviewSearch review(ChessPosition position, Move played, Set<Move> draws, MatchAiPolicy.Search policy) {
        var root = new RootSearch(position, draws);
        var reference = OpeningRepertoire.CURRENT.reviewReply(position);
        if (reference != null && draws.contains(reference)) reference = null;
        var ranked = analyze(root, MatchAiPolicy.BASE_PLIES, position.legalMoves(), Objective.POSITION).ranked();
        var result = reviewAt(root, ranked, played, reference, MatchAiPolicy.BASE_PLIES, -LIMIT);
        var moves = new ArrayList<Move>();
        for (var line : ranked) moves.add(line.move());
        var budget = new SearchBudget(policy.extraNodes(), 1);
        final int window = 180;
        for (int depth = MatchAiPolicy.BASE_PLIES + 1; depth <= policy.maxPlies(); depth++) {
            try {
                var next = new ArrayList<>(deepen(root, moves, depth, budget, window, Map.of()).ranked());
                var leader = next.get(0);
                completeReviewLine(root, next, played, depth, budget);
                if (reference != null) completeReviewLine(root, next, reference, depth, budget);
                result = reviewAt(root, next, played, reference, depth, leader.score() - window);
                moves.remove(leader.move());
                moves.add(0, leader.move());
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return result;
    }

    private static void completeReviewLine(RootSearch root, List<Variation> lines, Move move, int depth, SearchBudget budget) {
        if (lines.stream().anyMatch(line -> line.move().equals(move))) return;
        int balance = material(root.position());
        var line = root.line(move, depth, LIMIT, budget, Objective.POSITION, balance);
        lines.add(new Variation(line.moves(), line.score(), line.balance() - balance, line.ending()));
    }

    private static ReviewSearch reviewAt(RootSearch root, List<Variation> ranked, Move played, Move reference, int depth, int alternativeFloor) {
        var chosen = ranked.stream().filter(line -> line.move().equals(played)).findFirst().orElseThrow();
        var leader = ranked.get(0);
        var book = ranked.stream().filter(line -> line.move().equals(reference)).findFirst().orElse(null);
        boolean useReference = book != null && leader.ending() == Ending.HORIZON && book.ending() == Ending.HORIZON
                && !root.repetitionOffers().contains(leader.move());
        var recommendation = useReference ? book : leader;
        int alternative = ranked.stream().filter(line -> !line.move().equals(played)).mapToInt(Variation::score)
                .reduce(alternativeFloor, Math::max);
        // Any other proved mate at this depth is within the root score window.
        boolean onlyMate = chosen.signedMatePlies() > 0 && ranked.stream()
                .noneMatch(line -> !line.move().equals(played) && line.signedMatePlies() > 0);
        return new ReviewSearch(recommendation, chosen, alternative, depth, onlyMate);
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
        return botAnalysis(position, level, gap, repetitionOffers, List.of());
    }

    static SearchReport botAnalysis(ChessPosition position, MatchSetup.Level level, int gap, Set<Move> repetitionOffers,
                                    List<ChessPosition> repetitionHistory) {
        boolean hard = level == MatchSetup.Level.HARD;
        var repertoire = gap == 0 ? OpeningRepertoire.CURRENT.playReply(position, level) : null;
        var root = new RootSearch(position, repetitionOffers);
        var moves = repertoire == null ? position.legalMoves() : List.of(repertoire);
        int depth = level == MatchSetup.Level.BASIC && !bareKingMateEndgame(position)
                ? MatchAiPolicy.CURRENT.basicPlies : MatchAiPolicy.BASE_PLIES;
        var base = analyze(root, depth, moves, Objective.POSITION);
        if (depth < MatchAiPolicy.BASE_PLIES) return new SearchReport(base, depth, 0);
        var adaptive = hard && repertoire == null ? HardAdaptivePolicy.CURRENT : null;
        var searchPolicy = adaptive == null ? MatchAiPolicy.CURRENT.opponent(level) : adaptive.search();
        if (hard && repertoire == null && rookEndgame(position)) {
            var policy = MatchAiPolicy.CURRENT.rookEndgame;
            return rookEndgameSearch(root, searchPolicy,
                    Math.max(gap, policy.candidateGapCp()), base, repetitionHistory, policy, adaptive);
        }
        return matchSearch(root, searchPolicy, gap, 0, hard && repertoire == null,
                hard && repertoire == null, base, adaptive);
    }

    private static boolean bareKingMateEndgame(ChessPosition position) {
        boolean hasMajor = false;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece == null || piece.kind() == Kind.KING) continue;
            if (hasMajor || (piece.kind() != Kind.ROOK && piece.kind() != Kind.QUEEN)) return false;
            hasMajor = true;
        }
        return hasMajor;
    }

    static SearchReport hintAnalysis(ChessPosition position) {
        var policy = MatchAiPolicy.CURRENT.hint;
        var repertoire = OpeningRepertoire.CURRENT.reply(position);
        return hintAnalysis(position, repertoire, policy, policy.search().extraNodes());
    }

    private static SearchReport hintAnalysis(ChessPosition position, Move repertoire, MatchAiPolicy.Hint policy, int nodes) {
        return hintAnalysis(position, repertoire, policy, hintBudget(nodes));
    }

    private static SearchBudget hintBudget(int nodes) {
        var budget = new SearchBudget(nodes, MatchAiPolicy.CURRENT.hint.tacticalExtensionPlies(),
                MatchAiPolicy.CURRENT.hardTranspositions.entryLimit(nodes));
        budget.checkOrderBonus = MatchAiPolicy.CURRENT.hardOrdering.checkBonus();
        budget.quietMoveOrdering = new QuietMoveOrdering();
        return budget;
    }

    private static SearchReport hintAnalysis(ChessPosition position, Move repertoire, MatchAiPolicy.Hint policy, SearchBudget budget) {
        var root = new RootSearch(position, Set.of());
        var moves = repertoire == null ? position.legalMoves() : List.of(repertoire);
        if (moves.isEmpty()) return new SearchReport(new Result(List.of()), 0, 0);
        int before = budget.remaining;
        Result best = new Result(List.of());
        Map<Move, Integer> shallowScores = Map.of();
        Map<Move, Integer> retainedCredits = Map.of();
        Set<Move> excludedRoots = Set.of();
        Set<Move> nonEquivalentRoots = Set.of();
        var horizonLeaders = new ArrayList<Move>();
        int completed = 0;
        // The seed, every completed horizon and frontier exchange share one charged budget.
        for (int depth = 1; depth <= policy.search().maxPlies(); depth++) {
            try {
                var confirmation = depth == MatchAiPolicy.BASE_PLIES + 1 ? shallowScores : Map.<Move, Integer>of();
                var evidence = confirmation.isEmpty() ? null : new HashMap<Move, Line>();
                var candidate = deepen(root, moves, depth, budget, policy.maxLossCp(), confirmation, evidence);
                if (depth == MatchAiPolicy.BASE_PLIES && repertoire == null
                        && candidate.best().size() == 1 && candidate.best().get(0).signedMatePlies() == 0
                        && MatchMaterial.victim(position, candidate.best().get(0).move()) == null
                        && attacksQueen(position, List.of(candidate.best().get(0).move()))
                        && policy.verificationRecapturePlies() > 0) {
                    // Confirm an already leading forcing idea, not every speculative queen attack.
                    budget.queenRecaptureExtension = 1;
                    try {
                        candidate = deepen(root, moves, depth, budget, policy.maxLossCp(), confirmation, evidence);
                    } catch (BudgetExhausted exhausted) {
                        // The ordinary horizon is already complete; keep it when confirmation runs out.
                    }
                }
                var outsideRawWindow = outsideRawHintWindow(evidence, policy.maxLossCp());
                var excluded = excludedHintRoots(position, candidate, outsideRawWindow, confirmation);
                if (!excluded.isEmpty()) candidate = new Result(candidate.ranked().stream()
                        .filter(line -> !excluded.contains(line.move())).collect(Collectors.toList()));
                if (candidate.ranked().isEmpty()) {
                    // The credited window may have hidden the exact alternative that disproved the offer.
                    var replacement = evidence.entrySet().stream().filter(entry -> entry.getValue().exact() && !excluded.contains(entry.getKey()))
                            .max(Comparator.comparingInt(entry -> entry.getValue().score())).orElseThrow();
                    var line = replacement.getValue();
                    int credit = line.ending() == Ending.HORIZON ? confirmation.getOrDefault(replacement.getKey(), 0) : 0;
                    candidate = new Result(List.of(new Variation(line.moves(), line.score() + credit,
                            line.balance() - material(position), line.ending())));
                }
                horizonLeaders.add(candidate.ranked().get(0).move());
                // A changed odd-horizon leader needs an equal-turn confirmation before replacing the completed ranking.
                if (depth > MatchAiPolicy.BASE_PLIES && depth % 2 == 1 && completed == depth - 1
                        && candidate.ranked().get(0).signedMatePlies() == 0
                        && !candidate.ranked().get(0).move().equals(best.ranked().get(0).move())) continue;
                best = candidate;
                completed = depth;
                retainedCredits = confirmation;
                excludedRoots = excluded;
                nonEquivalentRoots = outsideRawWindow;
                if (depth == MatchAiPolicy.BASE_PLIES && material(position) == 0) shallowScores = boundedShallowScores(position, candidate);
                if (candidate.ranked().get(0).signedMatePlies() > 0) break;
                var ordered = new ArrayList<>(moves);
                var leader = candidate.ranked().get(0).move();
                ordered.remove(leader);
                ordered.add(0, leader);
                moves = ordered;
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return new SearchReport(new Result(coachingOrder(position, best)), completed, before - budget.remaining,
                horizonLeaders, retainedCredits, excludedRoots, nonEquivalentRoots);
    }

    private static Set<Move> outsideRawHintWindow(Map<Move, Line> evidence, int gap) {
        if (evidence == null) return Set.of();
        int bestExact = evidence.values().stream().filter(Line::exact).mapToInt(Line::score).max().orElse(-LIMIT);
        if (bestExact == -LIMIT) return Set.of();
        // An unbounded lower child window makes a non-exact root value an upper bound.
        return evidence.entrySet().stream().filter(entry -> entry.getValue().score() < bestExact - gap)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static Set<Move> excludedHintRoots(ChessPosition position, Result candidate, Set<Move> outsideRawWindow,
                                               Map<Move, Integer> credits) {
        // Historical credit cannot justify an immediate material offer already outside the raw window.
        // Balanced exchanges and uncertain historical challengers still need free comparison.
        var incumbent = candidate.ranked().get(0);
        return credits.getOrDefault(incumbent.move(), 0) > 0
                && incumbent.ending() == Ending.HORIZON
                && offeredMaterial(position, incumbent) > 0
                && outsideRawWindow.contains(incumbent.move())
                ? Set.of(incumbent.move()) : Set.of();
    }

    private static List<Variation> coachingOrder(ChessPosition position, Result result) {
        var ranked = new ArrayList<>(result.ranked());
        ranked.sort(Comparator.comparingInt(Variation::score).reversed()
                .thenComparing(Comparator.comparingInt((Variation line) -> ChessMatch.activity(position, line.move())).reversed())
                .thenComparing(line -> line.move().uci()));
        return ranked;
    }

    static int positionValue(ChessPosition position, Side perspective) {
        int score = positionScore(position, material(position));
        return position.turn() == perspective ? score : -score;
    }

    private static int positionScore(ChessPosition position, int materialBalance) {
        return materialBalance + MatchPositionEvaluation.bonus(position);
    }

    public static List<Variation> recommendations(ChessPosition position) {
        return verifiedHints(position).lines();
    }

    record HintSearch(List<Variation> lines, SearchReport comparison, int extraNodes, int seedDepth, int selectedDepth) { }

    static HintSearch verifiedHints(ChessPosition position) {
        return verifiedHints(position, MatchAiPolicy.CURRENT.hint.search().extraNodes());
    }

    /** A complete decision, including its comparison, under one immutable experiment ceiling. */
    static HintSearch verifiedHints(ChessPosition position, int totalNodes) {
        if (totalNodes < 1_000 || totalNodes > 200_000) throw new IllegalArgumentException("Hint budget must be between 1,000 and 200,000 total nodes");
        var policy = MatchAiPolicy.CURRENT.hint;
        var repertoire = OpeningRepertoire.CURRENT.reply(position);
        int verificationLimit = Math.min(policy.verificationNodes(), repertoire == null
                ? totalNodes - totalNodes / 4 : totalNodes / 6);
        var budget = hintBudget(totalNodes);
        budget.reserve = verificationLimit;
        var seed = hintAnalysis(position, repertoire, policy, budget);
        budget.reserve = Math.max(0, budget.remaining - verificationLimit);
        var ranked = seed.result().ranked();
        var offered = hintLines(seed.result(), policy);
        var lines = Collections.unmodifiableList(offered.stream()
                .filter(line -> line == offered.get(0) || !seed.nonEquivalentRoots().contains(line.move()))
                .map(seed::rawLine).collect(Collectors.toList()));
        var legal = position.legalMoves();
        if (legal.size() < 2 || lines.isEmpty()) return hintSearch(lines, null, seed.extraNodes(), seed);

        var eligibleRoots = lines.get(0).signedMatePlies() == 0 ? ranked : lines;
        var roots = hintVerificationRoots(eligibleRoots, seed.horizonLeaders(), seed.excludedRoots());
        if (lines.get(0).signedMatePlies() == 0) roots = HintCandidateCoverage.expand(position, roots, seed.excludedRoots());
        int verificationNodes = budget.remaining;
        int shortlistNodes = 0;
        if (roots.size() == 1 && lines.get(0).signedMatePlies() == 0) {
            var shortlist = budget;
            var alternatives = new ArrayList<>(legal);
            alternatives.remove(roots.get(0));
            alternatives.removeAll(seed.excludedRoots());
            if (alternatives.isEmpty()) return hintSearch(lines, null, seed.extraNodes(), seed);
            try {
                var shallow = hintRootLines(position, alternatives, 2, Collections.nCopies(alternatives.size(), shortlist));
                var next = coachingOrder(position, new Result(shallow));
                roots.add(next.get(0).move());
                if (roots.size() < HINT_ROOT_LIMIT && next.size() > 1 && next.get(0).score() - next.get(1).score() <= policy.maxLossCp()) {
                    roots.add(next.get(1).move());
                }
            } catch (BudgetExhausted exhausted) {
                return hintSearch(lines, null, seed.extraNodes() + verificationNodes - shortlist.remaining, seed);
            }
            shortlistNodes = verificationNodes - shortlist.remaining;
        }
        if (roots.size() < 2) return hintSearch(lines, null, seed.extraNodes() + shortlistNodes, seed);
        var comparison = verifyHintRoots(position, roots, budget, policy.search().maxPlies());
        return verifiedHints(position, lines, seed, comparison, shortlistNodes, repertoire, policy, roots.size() > HINT_ROOT_LIMIT);
    }

    private record HintVerification(SearchReport search, List<Variation> balancedLines, boolean recaptureCheck) { }

    private static ArrayList<Move> hintVerificationRoots(List<Variation> ranked, List<Move> horizonLeaders, Set<Move> excluded) {
        var roots = new ArrayList<Move>();
        roots.add(ranked.get(0).move());
        // A close challenger from another completed horizon is more informative than a third tied static line.
        for (int index = horizonLeaders.size() - 1; index >= 0 && roots.size() < HINT_ROOT_LIMIT; index--) {
            var move = horizonLeaders.get(index);
            if (!roots.contains(move) && !excluded.contains(move)) roots.add(move);
        }
        for (var line : ranked) {
            if (roots.size() == HINT_ROOT_LIMIT) break;
            if (!roots.contains(line.move())) roots.add(line.move());
        }
        return roots;
    }

    private static HintSearch verifiedHints(ChessPosition position, List<Variation> lines, SearchReport seed, HintVerification verified, int shortlistNodes,
                                            Move repertoire, MatchAiPolicy.Hint policy, boolean widerCoverage) {
        var comparison = verified.search();
        int used = seed.extraNodes() + shortlistNodes + comparison.extraNodes();
        var ranked = comparison.result().ranked();
        if (ranked.isEmpty()) return hintSearch(lines, null, used, seed);
        var original = lines.get(0);
        var best = ranked.get(0);
        boolean lostMateProof = original.signedMatePlies() != 0
                && (best.signedMatePlies() == 0 || best.score() < original.score());
        boolean shallowerBookReversal = repertoire != null && !best.move().equals(repertoire) && comparison.depth() < seed.depth();
        // Reversals use the total position score. A material swing at an unanswered ply proves neither line is better.
        var comparedOriginal = ranked.stream().filter(line -> line.move().equals(original.move())).findFirst().orElseThrow();
        boolean beatsLosingSeed = original.score() < 0 && best.score() > 0;
        boolean decisiveExactGain = (comparison.depth() == seed.depth() || comparison.depth() > seed.depth() && beatsLosingSeed)
                && original.line().size() <= comparison.depth() && best.score() - comparedOriginal.score() > policy.maxLossCp();
        boolean defensiveTie = best.score() == comparedOriginal.score() && best.signedMatePlies() == 0
                && HintPinSupport.reinforces(position, best.move())
                && ranked.stream().anyMatch(line -> line.score() == best.score() && !HintPinSupport.reinforces(position, line.move()));
        boolean unsupportedReversal = !decisiveExactGain && !defensiveTie && !best.move().equals(original.move()) && best.signedMatePlies() == 0
                && (comparison.depth() <= seed.depth() || !supportsAtEqualTurns(verified.balancedLines(), best.move(), original.move()));
        if (lostMateProof || shallowerBookReversal || unsupportedReversal) {
            return hintSearch(lines, null, used, seed);
        }
        // A shorter comparison can corroborate a deeper replay when it follows the same moves.
        if (comparison.depth() < seed.depth() && !extendsLine(original, best)) return hintSearch(lines, null, used, seed);
        var eligible = hintLines(comparison.result(), policy).stream()
                .filter(line -> line.move().equals(best.move()) || !seed.nonEquivalentRoots().contains(line.move())).collect(Collectors.toList());
        if (defensiveTie) eligible.removeIf(line -> !line.move().equals(best.move()));
        if (verified.recaptureCheck()) {
            // A selective challenger can replace the leader, but cannot manufacture additional equivalent offers.
            eligible.removeIf(line -> !line.move().equals(best.move())
                    && lines.stream().noneMatch(saved -> saved.move().equals(line.move())));
        }
        var replays = eligible.stream().map(line -> lines.stream().filter(saved -> extendsLine(saved, line))
                .findFirst().orElse(line)).collect(Collectors.toList());
        int comparisonSize = widerCoverage ? ranked.size()
                : best.signedMatePlies() == 0 ? Math.min(ranked.size(), Math.max(2, eligible.size())) : eligible.size();
        comparison = new SearchReport(new Result(ranked.subList(0, comparisonSize)), comparison.depth(), comparison.extraNodes());
        return new HintSearch(Collections.unmodifiableList(replays), comparison.result().ranked().size() > 1 ? comparison : null,
                used, seed.depth(), comparison.depth());
    }

    private static HintSearch hintSearch(List<Variation> lines, SearchReport comparison, int used, SearchReport seed) {
        return new HintSearch(lines, comparison, used, seed.depth(), comparison == null ? seed.depth() : comparison.depth());
    }

    private static boolean extendsLine(Variation replay, Variation compared) {
        int length = compared.line().size();
        return replay.line().size() > length && replay.line().subList(0, length).equals(compared.line());
    }

    private static boolean supportsAtEqualTurns(List<Variation> lines, Move proposed, Move incumbent) {
        if (lines.isEmpty()) return false;
        var next = lines.stream().filter(line -> line.move().equals(proposed)).findFirst().orElseThrow();
        var previous = lines.stream().filter(line -> line.move().equals(incumbent)).findFirst().orElseThrow();
        return next.score() > previous.score();
    }

    /** Verifies both root moves with full windows and one completed horizon. */
    public static java.util.Optional<HintComparison> compareHints(ChessPosition position, Move preferred, Move alternative) {
        var legal = position.legalMoves();
        if (legal.size() < 2 || !legal.contains(preferred)
                || alternative != null && (!legal.contains(alternative) || alternative.equals(preferred))) {
            return java.util.Optional.empty();
        }
        var policy = MatchAiPolicy.CURRENT.hint;
        var roots = alternative == null ? legal : List.of(preferred, alternative);
        var comparison = verifyHintRoots(position, roots, policy.verificationNodes(), policy.search().maxPlies()).search();
        var lines = comparison.result().ranked();
        if (alternative == null && !lines.isEmpty()) {
            var selected = lines.stream().filter(line -> line.move().equals(preferred)).findFirst().orElseThrow();
            var other = lines.stream().filter(line -> !line.move().equals(preferred)).findFirst().orElseThrow();
            lines = new ArrayList<>(List.of(selected, other));
            lines.sort(RANK);
        }
        return lines.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(HintComparison.of(position, lines.get(0), lines.get(1), comparison.depth()));
    }

    private static HintVerification verifyHintRoots(ChessPosition position, List<Move> moves, int nodes, int maxPlies) {
        var budget = hintBudget(nodes);
        budget.queenRecaptureExtension = attacksQueen(position, moves)
                ? Math.min(1, MatchAiPolicy.CURRENT.hint.verificationRecapturePlies()) : 0;
        return verifyHintRoots(position, moves, budget, maxPlies);
    }

    private static HintVerification verifyHintRoots(ChessPosition position, List<Move> moves, SearchBudget budget, int maxPlies) {
        // A checking pawn reply can push the recapture of a valuable victim beyond the frontier.
        // Keep seed, ordinary opponent and display searches unchanged; all extra visits use this budget.
        budget.recaptureExtension = competingCaptures(position, moves)
                ? MatchAiPolicy.CURRENT.hint.verificationRecapturePlies() : 0;
        int before = budget.remaining;
        var budgets = Collections.nCopies(moves.size(), budget);
        List<Variation> ranked = List.of();
        List<Variation> balanced = List.of();
        List<Variation> latestEven = List.of();
        int completed = 0;
        for (int depth = 2; depth <= maxPlies; depth++) {
            try {
                ranked = hintRootLines(position, moves, depth, budgets);
                balanced = latestEven;
                if (depth % 2 == 0) latestEven = ranked;
                completed = depth;
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return new HintVerification(new SearchReport(new Result(ranked), completed, before - budget.remaining), balanced,
                budget.recaptureExtension > 0 || budget.queenRecaptures > 0);
    }

    private static boolean competingCaptures(ChessPosition position, List<Move> moves) {
        boolean capturesPawn = false;
        boolean forcingReply = false;
        if (moves.size() < 2) return false;
        for (var move : moves) {
            var victim = MatchMaterial.victim(position, move);
            if (victim == null) return false;
            capturesPawn |= victim.kind() == Kind.PAWN;
            if (victim.kind() != Kind.PAWN) {
                var after = position.playGenerated(move);
                forcingReply |= after.legalCapturesAndPromotions().stream().anyMatch(reply ->
                        after.pieceAt(reply.from()).kind() == Kind.PAWN
                                && MatchMaterial.victim(after, reply) != null && after.playGenerated(reply).isCheck());
            }
        }
        return forcingReply && capturesPawn;
    }

    private static boolean attacksQueen(ChessPosition position, List<Move> moves) {
        for (var move : moves) {
            var after = position.playGenerated(move);
            for (int square = 0; square < 64; square++) {
                var victim = after.pieceAt(square);
                if (victim != null && victim.kind() == Kind.QUEEN && victim.side() != position.turn()
                        && after.attacksFrom(move.to(), square) && !position.attacksFrom(move.from(), square)) return true;
            }
        }
        return false;
    }

    private static List<Variation> hintRootLines(ChessPosition position, List<Move> moves, int depth, List<SearchBudget> budgets) {
        var root = new RootSearch(position, Set.of());
        int balance = material(position);
        var ranked = new ArrayList<Variation>(moves.size());
        for (int index = 0; index < moves.size(); index++) {
            var line = root.line(moves.get(index), depth, LIMIT, budgets.get(index), Objective.POSITION, balance);
            ranked.add(new Variation(line.moves(), line.score(), line.balance() - balance, line.ending()));
        }
        HintPinSupport.sort(position, ranked);
        return ranked;
    }

    static int positionEstimate(ChessPosition position, int fallback) {
        var policy = MatchAiPolicy.CURRENT.evaluation;
        var budget = new SearchBudget(policy.nodes(), 2);
        int estimate = fallback;
        try {
            // Start with equal turns for both sides, then admit bounded evidence from the next ply.
            var even = search(position, 2, 0, -LIMIT, LIMIT, budget, Objective.POSITION);
            if (even.ending() != Ending.HORIZON) {
                return estimate;
            }
            estimate = even.score();
            var odd = search(position, 3, 0, -LIMIT, LIMIT, budget, Objective.POSITION);
            if (odd.ending() == Ending.HORIZON) {
                int correction = (odd.score() - estimate) / 2;
                int limit = policy.maxHorizonCorrectionCp();
                estimate += Math.max(-limit, Math.min(limit, correction));
                if ((long) even.score() * estimate < 0) {
                    // A sign reversal needs a second horizon with equal turns.
                    var confirmation = search(position, 4, 0, -LIMIT, LIMIT, budget, Objective.POSITION);
                    if (confirmation.ending() == Ending.HORIZON) estimate = (even.score() + confirmation.score()) / 2;
                }
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
                                            boolean confirmAcrossHorizons, boolean prioritizeChecks, Result base) {
        return matchSearch(root, policy, gap, tacticalExtension, confirmAcrossHorizons, prioritizeChecks, base, null);
    }

    private static SearchReport matchSearch(RootSearch root, MatchAiPolicy.Search policy, int gap, int tacticalExtension,
                                            boolean confirmAcrossHorizons, boolean prioritizeChecks, Result base,
                                            HardAdaptivePolicy adaptive) {
        int completed = MatchAiPolicy.BASE_PLIES;
        Result best = base;
        if (policy.maxPlies() == completed || best.ranked().isEmpty() || best.ranked().get(0).signedMatePlies() == 1) {
            return new SearchReport(best, completed, 0);
        }
        var moves = new ArrayList<Move>();
        for (var line : best.ranked()) moves.add(line.move());
        if (prioritizeChecks) orderCheckingRootMovesFirst(root.position(), moves);
        // In an unequal position, let the deeper search resolve immediate material recovery on its own.
        var shallowScores = confirmAcrossHorizons && material(root.position()) == 0
                ? boundedShallowScores(root.position(), best) : Map.<Move, Integer>of();
        int transpositionLimit = prioritizeChecks
                ? MatchAiPolicy.CURRENT.hardTranspositions.entryLimit(policy.extraNodes()) : 0;
        var budget = new SearchBudget(policy.extraNodes(), tacticalExtension, transpositionLimit);
        budget.adaptive = adaptive == null ? null : adaptive.budget(root.position());
        budget.checkOrderBonus = prioritizeChecks ? MatchAiPolicy.CURRENT.hardOrdering.checkBonus() : 0;
        budget.quietMoveOrdering = prioritizeChecks ? new QuietMoveOrdering() : null;
        int exchangeLimit = MatchAiPolicy.CURRENT.pawnExchangeNodes;
        var pawnCandidate = prioritizeChecks && gap == 0 && exchangeLimit > 0 && !shallowScores.isEmpty() && !root.position().isCheck()
                ? pawnExchangeCandidate(root.position(), base) : null;
        int exchangeNodes = pawnCandidate == null ? 0 : Math.min(exchangeLimit, policy.extraNodes() / 6);
        for (int depth = completed + 1; depth <= policy.maxPlies(); depth++) {
            try {
                // Give Hard a two-horizon ranking if the next full iteration cannot finish.
                var confirmation = depth == MatchAiPolicy.BASE_PLIES + 1 ? shallowScores : Map.<Move, Integer>of();
                if (depth == MatchAiPolicy.BASE_PLIES + 2 && exchangeNodes > 0 && best.ranked().get(0).ending() == Ending.HORIZON
                        && !best.ranked().get(0).move().equals(pawnCandidate)) {
                    budget.reserve = Math.min(exchangeNodes, budget.remaining);
                }
                var candidate = deepen(root, moves, depth, budget, gap, confirmation);
                if (budget.adaptive != null) budget.adaptive.completed(depth);
                if (prioritizeChecks && candidate.ranked().get(0).signedMatePlies() > 0) {
                    // The earlier horizons already had a chance to prove a shorter mate.
                    return new SearchReport(candidate, depth, policy.extraNodes() - budget.remaining);
                }
                if (prioritizeChecks && depth == policy.maxPlies()
                        && !stableHardLeader(moves.get(0), candidate)) break;
                // The later capture has no opponent reply at an odd horizon. Keep the completed even ranking.
                if (prioritizeChecks && gap == 0 && MatchAiPolicy.CURRENT.preservePawnCapture && depth % 2 == 1
                        && completed == depth - 1 && defersPawnCapture(root.position(), best.ranked().get(0), candidate.ranked().get(0), depth)) {
                    continue;
                }
                if (budget.adaptive != null && gap == 0 && depth == MatchAiPolicy.BASE_PLIES + 2
                        && exchangeNodes > 0 && candidate.ranked().get(0).ending() == Ending.HORIZON
                        && !candidate.ranked().get(0).move().equals(pawnCandidate)) {
                    int available = budget.remaining;
                    var verification = new SearchBudget(available, 2);
                    verification.adaptive = budget.adaptive;
                    verification.adaptiveOffset = policy.extraNodes() - budget.remaining;
                    budget.adaptive.exchangeProof(true);
                    Variation capture;
                    try {
                        capture = verifyPawnExchange(root, candidate.ranked().get(0).move(), pawnCandidate,
                                depth, verification);
                    } finally {
                        budget.adaptive.exchangeProof(false);
                    }
                    budget.remaining -= available - verification.remaining;
                    if (capture != null) candidate = new Result(List.of(capture));
                }
                best = candidate;
                completed = depth;
                if (budget.adaptive != null) budget.adaptive.completed(completed);
                budget.reserve = 0;
                var leader = best.ranked().get(0).move();
                moves.remove(leader);
                moves.add(0, leader);
            }
            catch (BudgetExhausted exhausted) { break; }
        }
        if (completed == MatchAiPolicy.BASE_PLIES + 1 && budget.reserve > 0) {
            var verification = new SearchBudget(budget.reserve, 2);
            var capture = verifyPawnExchange(root, best.ranked().get(0).move(), pawnCandidate, completed, verification);
            budget.remaining -= budget.reserve - verification.remaining;
            if (capture != null) best = new Result(List.of(capture));
        }
        return new SearchReport(best, completed, policy.extraNodes() - budget.remaining);
    }

    private static Move pawnExchangeCandidate(ChessPosition position, Result base) {
        var leader = base.ranked().get(0);
        var victim = position.pieceAt(leader.move().to());
        int pawn = POLICY.values[Kind.PAWN.ordinal()];
        return victim != null && victim.kind() == Kind.PAWN && leader.materialChange() >= 2 * pawn
                && leader.ending() == Ending.HORIZON && leader.move().promotion() == null ? leader.move() : null;
    }

    private static Variation verifyPawnExchange(RootSearch root, Move incumbent, Move capture, int depth, SearchBudget budget) {
        try {
            int balance = material(root.position());
            var stable = root.line(incumbent, depth, LIMIT, budget, Objective.POSITION, balance);
            var exchange = root.line(capture, depth, LIMIT, budget, Objective.POSITION, balance);
            int gain = exchange.balance() - balance;
            if (stable.ending() == Ending.HORIZON && exchange.ending() == Ending.HORIZON
                    && gain >= POLICY.values[Kind.PAWN.ordinal()] && exchange.score() > stable.score()) {
                return new Variation(exchange.moves(), exchange.score(), gain, exchange.ending());
            }
        } catch (BudgetExhausted exhausted) {
            // The incumbent stays if the comparison is incomplete.
        }
        return null;
    }

    private static boolean defersPawnCapture(ChessPosition position, Variation completed, Variation deeper, int depth) {
        Move capture = completed.move();
        var victim = position.pieceAt(capture.to());
        if (completed.ending() != Ending.HORIZON || completed.materialChange() < 0
                || victim == null || victim.kind() != Kind.PAWN || victim.side() == position.turn()
                || deeper.ending() != Ending.HORIZON || deeper.line().size() != depth || !deeper.line().get(depth - 1).equals(capture)
                || captureOrder(position, deeper.move()) != 0) return false;
        // Make sure the pawn remains the target throughout the continuation.
        for (Move move : deeper.line().subList(0, depth - 1)) {
            if (move.from() == capture.to() || move.to() == capture.to()) return false;
        }
        return true;
    }

    static boolean stableHardLeader(Move previousLeader, Result deeper) {
        return previousLeader != null && !deeper.ranked().isEmpty()
                && previousLeader.equals(deeper.ranked().get(0).move());
    }

    static void orderCheckingRootMovesFirst(ChessPosition position, List<Move> moves) {
        var checks = new HashMap<Move, Boolean>();
        for (Move move : moves) checks.put(move, position.playGenerated(move).isCheck());
        moves.sort(Comparator.comparingInt((Move move) -> checks.get(move) ? 1 : 0).reversed());
    }

    private static SearchReport rookEndgameSearch(RootSearch root, MatchAiPolicy.Search policy, int gap, Result base,
                                                 List<ChessPosition> repetitionHistory, MatchAiPolicy.RookEndgame endgamePolicy,
                                                 HardAdaptivePolicy adaptive) {
        int completed = MatchAiPolicy.BASE_PLIES;
        Result best = base;
        if (policy.maxPlies() == completed || best.ranked().isEmpty()) return new SearchReport(best, completed, 0);
        var moves = best.ranked().stream().map(Variation::move).collect(Collectors.toCollection(ArrayList::new));
        var budget = new RookSearchBudget(policy.extraNodes(), repetitionHistory, root.position());
        budget.adaptive = adaptive == null ? null : adaptive.budget(root.position());
        for (int depth = completed + 1; depth <= policy.maxPlies() + endgamePolicy.extensionPlies(); depth++) {
            try {
                var previousScores = new HashMap<Move, Integer>();
                best.ranked().forEach(line -> previousScores.put(line.move(), line.score()));
                best = deepenRookEndgame(root, moves, depth, budget, endgamePolicy.candidateGapCp(), previousScores);
                completed = depth;
                if (budget.adaptive != null) budget.adaptive.completed(completed);
                var nextMoves = best.ranked().stream().map(Variation::move).collect(Collectors.toCollection(ArrayList::new));
                boolean closeCandidates = best.ranked().size() > 1
                        && best.ranked().get(0).ending() == Ending.HORIZON
                        && best.ranked().get(1).ending() == Ending.HORIZON
                        && best.ranked().get(0).score() - best.ranked().get(1).score() <= gap;
                if (closeCandidates) {
                    nextMoves.sort(Comparator.comparingInt((Move move) -> ChessMatch.activity(root.position(), move)).reversed()
                            .thenComparing(Move::uci));
                } else {
                    Move leader = best.ranked().get(0).move();
                    nextMoves.remove(leader);
                    nextMoves.add(0, leader);
                }
                moves.clear();
                moves.addAll(nextMoves);
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return new SearchReport(best, completed, policy.extraNodes() - budget.remaining);
    }

    private static boolean rookEndgame(ChessPosition position) {
        int whiteRooks = 0;
        int blackRooks = 0;
        int minors = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece == null) continue;
            switch (piece.kind()) {
                case PAWN, KING -> { }
                case ROOK -> { if (piece.side() == ChessPosition.Side.WHITE) whiteRooks++; else blackRooks++; }
                case KNIGHT, BISHOP -> { if (++minors > 1) return false; }
                case QUEEN -> { return false; }
            }
        }
        return whiteRooks > 0 && blackRooks > 0;
    }

    private static Result deepenRookEndgame(RootSearch root, List<Move> moves, int plies, RookSearchBudget budget,
                                            int gap, Map<Move, Integer> previousScores) {
        var best = new ArrayList<Variation>();
        int top = -LIMIT;
        int startingBalance = material(root.position());
        for (int index = 0; index < moves.size(); index++) {
            Move move = moves.get(index);
            int cutoff = top - gap;
            int previous = previousScores.getOrDefault(move, -LIMIT);
            int alpha = index == 0 ? -LIMIT : cutoff;
            int beta = index == 0 ? LIMIT : top + 1;
            if (index == 0 && previous > -LIMIT) {
                int window = POLICY.values[Kind.PAWN.ordinal()] * 2;
                alpha = Math.max(-LIMIT, previous - window);
                beta = Math.min(LIMIT, previous + window);
            }
            var line = root.lineRook(move, plies, alpha, beta, budget, startingBalance);
            int score = line.score();
            if (index == 0 && previous > -LIMIT && (score <= alpha || score >= beta)) {
                line = root.lineRook(move, plies, -LIMIT, LIMIT, budget, startingBalance);
                score = line.score();
            } else if (index > 0 && score >= top + 1) {
                line = root.lineRook(move, plies, top, LIMIT, budget, startingBalance);
                score = line.score();
            }
            if (score < cutoff) continue;
            top = Math.max(top, score);
            best.add(new Variation(line.moves(), score, line.balance() - startingBalance, line.ending()));
        }
        int floor = top - gap;
        best.removeIf(line -> line.score() < floor);
        best.sort(RANK);
        return new Result(best);
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
        return deepen(root, moves, plies, budget, gap, shallowScores, null);
    }

    private static Result deepen(RootSearch root, List<Move> moves, int plies, SearchBudget budget, int gap,
                                 Map<Move, Integer> shallowScores, Map<Move, Line> evidence) {
        var best = new ArrayList<Variation>();
        int top = -LIMIT;
        int startingBalance = material(root.position());
        for (Move move : moves) {
            int cutoff = top - gap;
            int offset = root.repetitionOffers().contains(move) ? 0 : shallowScores.getOrDefault(move, 0);
            // Allow for the bonus before pruning and keep the boundary inclusive. Endings use the unadjusted score.
            int beta = Math.min(LIMIT, 1 - cutoff + Math.max(0, offset));
            var line = root.line(move, plies, beta, budget, Objective.POSITION, startingBalance);
            if (evidence != null) evidence.put(move, line);
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

        Line lineRook(Move move, int plies, int alpha, int beta, RookSearchBudget budget, int startingBalance) {
            if (repetitionOffers.contains(move)) return new Line(0, startingBalance, List.of(move), Ending.HORIZON);
            var child = position.playGenerated(move);
            budget.repetitions.push(child);
            try {
                return searchRook(child, plies - 1, 1, -beta, -alpha, budget).prepend(move);
            } finally {
                budget.repetitions.pop(child);
            }
        }
    }

    private static Line search(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget, Objective objective) {
        if (budget != null) budget.visit();
        // Cache only exact lines; the key preserves mate distance, objective, horizon and tactical extension.
        var key = budget != null && budget.transpositionLimit > 0
                ? new SearchTranspositionKey(position.searchKey(), left, distance, objective, budget.tacticalExtension,
                        budget.recaptureExtension, budget.queenRecaptureExtension) : null;
        if (key != null) {
            var cached = budget.transpositions.get(key);
            if (budget.adaptive != null) budget.adaptive.lookup(cached != null);
            if (cached != null) return cached;
        }
        if (left == 0) {
            Line result = budget == null || budget.tacticalExtension == 0 ? leaf(position, distance, position.hasLegalMove(), objective)
                    : settle(position, budget.tacticalExtension, distance, alpha, beta, budget);
            if (key != null && result.exact()) budget.remember(key, result);
            return result;
        }
        var moves = position.legalMoves();
        if (moves.isEmpty()) {
            Line result = leaf(position, distance, false, objective);
            if (key != null) budget.remember(key, result);
            return result;
        }
        Map<Move, Boolean> checks = Map.of();
        if (objective == Objective.POSITION) {
            moves = new ArrayList<>(moves);
            if (budget != null && budget.checkOrderBonus > 0) {
                var order = new HashMap<Move, Integer>();
                var detected = new HashMap<Move, Boolean>();
                for (Move move : moves) {
                    boolean givesCheck = position.playGenerated(move).isCheck();
                    detected.put(move, givesCheck);
                    int score = moveOrder(position, move) + (givesCheck ? budget.checkOrderBonus : 0)
                            + (givesCheck ? 0 : budget.quietMoveOrdering.bonus(position, move, false));
                    order.put(move, score);
                }
                moves.sort(Comparator.comparingInt(order::get).reversed());
                checks = detected;
            } else {
                moves.sort(Comparator.comparingInt((Move move) -> moveOrder(position, move)).reversed());
            }
        }

        int initialAlpha = alpha;
        Line best = null;
        boolean cutoff = false;
        for (Move move : moves) {
            var child = search(position.playGenerated(move), left - 1, distance + 1, -beta, -alpha, budget, objective);
            int score = -child.score();
            if (best == null || score > best.score()) best = child.prepend(move);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                if (budget != null && budget.checkOrderBonus > 0 && !checks.getOrDefault(move, false)
                        && captureOrder(position, move) == 0) {
                    budget.quietMoveOrdering.recordCutoff(position, move, left, false);
                }
                cutoff = true;
                break;
            }
        }
        // Bounds from discarded children do not invalidate an improving value inside this node's window.
        Line result = best.withExact(!cutoff && best.score() > initialAlpha);
        if (key != null && result.exact()) budget.remember(key, result);
        return result;
    }

    private static Line searchRook(ChessPosition position, int left, int distance, int alpha, int beta, RookSearchBudget budget) {
        budget.visit();
        var positionKey = position.searchKey();
        if (budget.repetitions.count(position) >= 3) {
            return new Line(0, material(position), Collections.emptyList(), Ending.HORIZON);
        }
        var key = new RookTranspositionKey(positionKey, left, budget.repetitions.path());
        var cached = budget.transpositions.get(key);
        if (budget.adaptive != null) budget.adaptive.lookup(cached != null);
        if (cached != null) return cached;
        if (left == 0) {
            Line result = leaf(position, distance, position.hasLegalMove(), Objective.POSITION);
            budget.remember(key, result);
            return result;
        }
        var moves = position.legalMoves();
        if (moves.isEmpty()) return leaf(position, distance, false, Objective.POSITION);
        moves = new ArrayList<>(moves);
        Move preferred = budget.preferredMoves.get(positionKey);
        moves.sort((first, second) -> {
            boolean preferredFirst = first.equals(preferred);
            boolean preferredSecond = second.equals(preferred);
            if (preferredFirst != preferredSecond) return preferredFirst ? -1 : 1;
            int firstOrder = moveOrder(position, first) + budget.orderingBonus(position, first, left);
            int secondOrder = moveOrder(position, second) + budget.orderingBonus(position, second, left);
            return Integer.compare(secondOrder, firstOrder);
        });
        int initialAlpha = alpha;
        Line best = null;
        boolean cutoff = false;
        for (Move move : moves) {
            var next = position.playGenerated(move);
            budget.repetitions.push(next);
            Line child;
            try {
                child = searchRook(next, left - 1, distance + 1, -beta, -alpha, budget);
            } finally {
                budget.repetitions.pop(next);
            }
            int score = -child.score();
            if (best == null || score > best.score()) best = child.prepend(move);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                cutoff = true;
                budget.recordCutoff(position, move, left);
                break;
            }
        }
        if (best != null && !best.moves().isEmpty()) {
            budget.preferredMoves.put(positionKey, best.moves().get(0));
            if (!cutoff && best.ending() == Ending.HORIZON && best.score() > initialAlpha && best.score() < beta) {
                budget.remember(key, best);
            }
        }
        return best;
    }

    /** Continue tactical exchanges at the search boundary before scoring material. */
    private static Line settle(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget) {
        return settle(position, left, distance, alpha, beta, budget, -1,
                budget.recaptureExtension > 0 ? budget.recaptureExtension : -1);
    }

    private static Line settle(ChessPosition position, int left, int distance, int alpha, int beta, SearchBudget budget,
                               int lastCapture, int recapturesLeft) {
        if (left == 0 && recapturesLeft < 0) {
            // A queen capture is not a completed exchange while an opposing pawn can legally recapture it.
            // Resolve this once within the existing verification budget, rather than rejecting the queen offer.
            recapturesLeft = budget.queenRecaptureExtension > 0 && pawnCanRecaptureQueen(position, lastCapture)
                    ? budget.queenRecaptureExtension : 0;
            if (recapturesLeft > 0) budget.queenRecaptures++;
        }
        boolean recapturing = left == 0 && lastCapture >= 0 && recapturesLeft > 0;
        if (left == 0 && !recapturing) return leaf(position, distance, position.hasLegalMove(), Objective.POSITION);
        int initialAlpha = alpha;
        boolean inCheck = position.isCheck();
        var moves = new ArrayList<Move>();
        Line best = null;
        if (inCheck) {
            moves.addAll(position.legalMoves());
            if (moves.isEmpty()) return leaf(position, distance, false, Objective.POSITION);
        } else {
            if (!position.hasLegalMove()) return leaf(position, distance, false, Objective.POSITION);
            best = leaf(position, distance, true, Objective.POSITION);
            if (best.score() >= beta) return best.withExact(false);
            alpha = Math.max(alpha, best.score());
            moves.addAll(position.legalCapturesAndPromotions());
            if (recapturing) moves.removeIf(move -> move.to() != lastCapture || MatchMaterial.victim(position, move) == null);
        }
        moves.sort(Comparator.comparingInt((Move move) -> captureOrder(position, move)).reversed());
        boolean cutoff = false;
        for (Move move : moves) {
            budget.visit();
            int target = recapturing || MatchMaterial.victim(position, move) != null ? move.to() : -1;
            var child = settle(position.playGenerated(move), recapturing ? 0 : left - 1, distance + 1, -beta, -alpha, budget,
                    target, recapturing ? recapturesLeft - 1 : recapturesLeft).prepend(move);
            if (best == null || child.score() > best.score()) best = child;
            alpha = Math.max(alpha, child.score());
            if (alpha >= beta) {
                cutoff = true;
                break;
            }
        }
        return best.withExact(!cutoff && best.score() > initialAlpha);
    }

    private static boolean pawnCanRecaptureQueen(ChessPosition position, int square) {
        if (square < 0) return false;
        var victim = position.pieceAt(square);
        return victim != null && victim.kind() == Kind.QUEEN && victim.side() != position.turn()
                && position.legalCapturesAndPromotions().stream().anyMatch(move -> move.to() == square
                        && position.pieceAt(move.from()).kind() == Kind.PAWN);
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

    private record SearchTranspositionKey(ChessPosition.SearchKey position, int left, int distance, Objective objective,
                                         int tacticalExtension, int recaptureExtension, int queenRecaptureExtension) { }

    private record RookSearchPath(RookSearchPath previous, ChessPosition.SearchKey position) { }

    private record RookTranspositionKey(ChessPosition.SearchKey position, int plies, RookSearchPath path) { }

    private static final class RookSearchBudget {
        private final int allocated;
        private HardAdaptivePolicy.Budget adaptive;
        private int remaining;
        private final int transpositionLimit;
        private final Map<RookTranspositionKey, Line> transpositions = new HashMap<>();
        private final Map<ChessPosition.SearchKey, Move> preferredMoves = new HashMap<>();
        private final Move[][] killers = new Move[16][2];
        private final int[][] history = new int[2][64 * 64];
        private final RookRepetitionContext repetitions;

        RookSearchBudget(int remaining, List<ChessPosition> history, ChessPosition root) {
            this.allocated = remaining;
            this.remaining = remaining;
            transpositionLimit = Math.min(50_000, Math.max(1, remaining / 2));
            repetitions = new RookRepetitionContext(history, root);
        }

        private static final class RookRepetitionContext {
            private final Map<ChessPosition.SearchKey, Integer> counts = new HashMap<>();
            private RookSearchPath path;

            RookRepetitionContext(List<ChessPosition> history, ChessPosition root) {
                history.forEach(position -> counts.merge(position.searchKey(), 1, Integer::sum));
                counts.merge(root.searchKey(), 1, Integer::sum);
            }

            int count(ChessPosition position) { return counts.getOrDefault(position.searchKey(), 0); }

            void push(ChessPosition position) {
                var key = position.searchKey();
                path = new RookSearchPath(path, key);
                counts.merge(key, 1, Integer::sum);
            }

            void pop(ChessPosition position) {
                var key = position.searchKey();
                if (path == null || !path.position().equals(key)) throw new IllegalStateException("Unbalanced rook search path");
                path = path.previous();
                int count = counts.get(key);
                if (count == 1) counts.remove(key); else counts.put(key, count - 1);
            }

            RookSearchPath path() { return path; }
        }

        int orderingBonus(ChessPosition position, Move move, int plies) {
            int depth = Math.min(plies, killers.length - 1);
            if (captureOrder(position, move) == 0) {
                if (move.equals(killers[depth][0])) return 100_000;
                if (move.equals(killers[depth][1])) return 90_000;
            }
            return history[position.turn().ordinal()][move.from() * 64 + move.to()];
        }

        void recordCutoff(ChessPosition position, Move move, int plies) {
            if (captureOrder(position, move) != 0) return;
            int depth = Math.min(plies, killers.length - 1);
            if (!move.equals(killers[depth][0])) {
                killers[depth][1] = killers[depth][0];
                killers[depth][0] = move;
            }
            int index = move.from() * 64 + move.to();
            int side = position.turn().ordinal();
            history[side][index] = Math.min(1_000_000, history[side][index] + plies * plies * 32);
        }

        void remember(RookTranspositionKey key, Line line) {
            if (line.ending() == Ending.HORIZON
                    && (transpositions.containsKey(key) || transpositions.size() < transpositionLimit)) {
                transpositions.putIfAbsent(key, line);
            }
        }

        void visit() {
            checkInterrupted();
            if (adaptive != null && !adaptive.allow(allocated - remaining)) throw new BudgetExhausted();
            if (remaining == 0) throw new BudgetExhausted();
            remaining--;
        }
    }

    static final class QuietMoveOrdering {
        private static final int MAX_ORDER_BONUS = 6_000;
        private static final int MAX_HISTORY = 1_000_000;
        private final int[][] history = new int[2][64 * 64];

        int bonus(ChessPosition position, Move move, boolean givesCheck) {
            if (givesCheck || captureOrder(position, move) != 0) return 0;
            return Math.min(MAX_ORDER_BONUS, history[position.turn().ordinal()][move.from() * 64 + move.to()]);
        }

        void recordCutoff(ChessPosition position, Move move, int plies, boolean givesCheck) {
            if (givesCheck || captureOrder(position, move) != 0) return;
            int index = move.from() * 64 + move.to();
            int side = position.turn().ordinal();
            history[side][index] = Math.min(MAX_HISTORY, history[side][index] + plies * plies * 32);
        }
    }

    private static final class SearchBudget {
        private final int allocated;
        private HardAdaptivePolicy.Budget adaptive;
        private int adaptiveOffset;
        private int remaining;
        private int reserve;
        private final int tacticalExtension;
        private int recaptureExtension;
        private int queenRecaptureExtension;
        private int queenRecaptures;
        private final int transpositionLimit;
        private int checkOrderBonus;
        private final Map<SearchTranspositionKey, Line> transpositions;
        private QuietMoveOrdering quietMoveOrdering;

        SearchBudget(int remaining, int tacticalExtension) {
            this(remaining, tacticalExtension, 0);
        }

        SearchBudget(int remaining, int tacticalExtension, int transpositionLimit) {
            this.allocated = remaining;
            this.remaining = remaining;
            this.tacticalExtension = tacticalExtension;
            this.transpositionLimit = transpositionLimit;
            this.transpositions = transpositionLimit > 0 ? new HashMap<>() : Map.of();
        }

        void remember(SearchTranspositionKey key, Line line) {
            if (transpositions.containsKey(key) || transpositions.size() < transpositionLimit) transpositions.putIfAbsent(key, line);
        }

        void visit() {
            checkInterrupted();
            if (adaptive != null && !adaptive.allow(adaptiveOffset + allocated - remaining, reserve)) throw new BudgetExhausted();
            if (remaining == reserve) throw new BudgetExhausted();
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

    private record Line(int score, int balance, List<Move> moves, Ending ending, boolean exact) {
        private Line(int score, int balance, List<Move> moves, Ending ending) { this(score, balance, moves, ending, true); }
        Line withExact(boolean value) { return new Line(score, balance, moves, ending, value); }
        Line prepend(Move move) {
            var variation = new ArrayList<Move>(moves.size() + 1);
            variation.add(move);
            variation.addAll(moves);
            return new Line(-score, -balance, variation, ending, exact);
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
