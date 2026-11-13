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
import java.util.stream.Collectors;
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
        var repertoire = gap == 0 ? OpeningRepertoire.CURRENT.reply(position, level) : null;
        var root = new RootSearch(position, repetitionOffers);
        var moves = repertoire == null ? position.legalMoves() : List.of(repertoire);
        int depth = level == MatchSetup.Level.BASIC && !bareKingMateEndgame(position)
                ? MatchAiPolicy.CURRENT.basicPlies : MatchAiPolicy.BASE_PLIES;
        var base = analyze(root, depth, moves, Objective.POSITION);
        if (depth < MatchAiPolicy.BASE_PLIES) return new SearchReport(base, depth, 0);
        if (hard && repertoire == null && rookEndgame(position)) {
            var policy = MatchAiPolicy.CURRENT.rookEndgame;
            return rookEndgameSearch(root, MatchAiPolicy.CURRENT.opponent(level),
                    Math.max(gap, policy.candidateGapCp()), base, repetitionHistory, policy);
        }
        return matchSearch(root, MatchAiPolicy.CURRENT.opponent(level), gap, 0, hard && repertoire == null,
                hard && repertoire == null, base);
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
        var root = new RootSearch(position, Set.of());
        if (repertoire != null) {
            var base = analyze(position, MatchAiPolicy.BASE_PLIES, List.of(repertoire), Objective.POSITION);
            return matchSearch(root, policy.search(), 0, policy.tacticalExtensionPlies(), false, false, base);
        }
        var hardPolicy = MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD);
        var base = openingSearch(position);
        var hard = matchSearch(root, hardPolicy, 0, 0, true, false, base);
        int available = policy.search().extraNodes() - hard.extraNodes();
        var tacticalPolicy = new MatchAiPolicy.Search(policy.search().maxPlies(), available - policy.verificationNodes());
        var tactical = matchSearch(root, tacticalPolicy, policy.maxLossCp(), policy.tacticalExtensionPlies(), false, false, base);
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

    /** Verifies both root moves with full windows and one completed horizon. */
    public static java.util.Optional<HintComparison> compareHints(ChessPosition position, Move preferred, Move alternative) {
        var legal = position.legalMoves();
        if (legal.size() < 2 || !legal.contains(preferred)
                || alternative != null && (!legal.contains(alternative) || alternative.equals(preferred))) {
            return java.util.Optional.empty();
        }
        if (alternative == null) {
            alternative = analyze(position, 2, legal, Objective.POSITION).ranked().stream().map(Variation::move)
                    .filter(move -> !move.equals(preferred)).findFirst().orElseThrow();
        }
        var root = new RootSearch(position, Set.of());
        int balance = material(position);
        int nodesPerMove = MatchAiPolicy.CURRENT.hint.verificationNodes() / 2;
        var firstBudget = new SearchBudget(nodesPerMove, 0);
        var secondBudget = new SearchBudget(nodesPerMove, 0);
        Variation first = null;
        Variation second = null;
        int completed = 0;
        for (int depth = 2; depth <= MatchAiPolicy.CURRENT.hint.search().maxPlies(); depth++) {
            try {
                var nextFirst = root.line(preferred, depth, LIMIT, firstBudget, Objective.POSITION, balance);
                var nextSecond = root.line(alternative, depth, LIMIT, secondBudget, Objective.POSITION, balance);
                first = new Variation(nextFirst.moves(), nextFirst.score(), nextFirst.balance() - balance, nextFirst.ending());
                second = new Variation(nextSecond.moves(), nextSecond.score(), nextSecond.balance() - balance, nextSecond.ending());
                completed = depth;
            } catch (BudgetExhausted exhausted) {
                break;
            }
        }
        return completed == 0 ? java.util.Optional.empty()
                : java.util.Optional.of(HintComparison.of(position, first, second, completed));
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
                best = candidate;
                completed = depth;
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
                                                 List<ChessPosition> repetitionHistory, MatchAiPolicy.RookEndgame endgamePolicy) {
        int completed = MatchAiPolicy.BASE_PLIES;
        Result best = base;
        if (policy.maxPlies() == completed || best.ranked().isEmpty()) return new SearchReport(best, completed, 0);
        var moves = best.ranked().stream().map(Variation::move).collect(Collectors.toCollection(ArrayList::new));
        var budget = new RookSearchBudget(policy.extraNodes(), repetitionHistory, root.position());
        for (int depth = completed + 1; depth <= policy.maxPlies() + endgamePolicy.extensionPlies(); depth++) {
            try {
                var previousScores = new HashMap<Move, Integer>();
                best.ranked().forEach(line -> previousScores.put(line.move(), line.score()));
                best = deepenRookEndgame(root, moves, depth, budget, endgamePolicy.candidateGapCp(), previousScores);
                completed = depth;
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
                ? new SearchTranspositionKey(position.searchKey(), left, distance, objective, budget.tacticalExtension) : null;
        if (key != null) {
            var cached = budget.transpositions.get(key);
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

        Line best = null;
        boolean exact = true;
        boolean cutoff = false;
        for (Move move : moves) {
            var child = search(position.playGenerated(move), left - 1, distance + 1, -beta, -alpha, budget, objective);
            exact &= child.exact();
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
        Line result = best.withExact(exact && !cutoff);
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

    private record SearchTranspositionKey(ChessPosition.SearchKey position, int left, int distance, Objective objective,
                                         int tacticalExtension) { }

    private record RookSearchPath(RookSearchPath previous, ChessPosition.SearchKey position) { }

    private record RookTranspositionKey(ChessPosition.SearchKey position, int plies, RookSearchPath path) { }

    private static final class RookSearchBudget {
        private int remaining;
        private final int transpositionLimit;
        private final Map<RookTranspositionKey, Line> transpositions = new HashMap<>();
        private final Map<ChessPosition.SearchKey, Move> preferredMoves = new HashMap<>();
        private final Move[][] killers = new Move[16][2];
        private final int[][] history = new int[2][64 * 64];
        private final RookRepetitionContext repetitions;

        RookSearchBudget(int remaining, List<ChessPosition> history, ChessPosition root) {
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
        private int remaining;
        private int reserve;
        private final int tacticalExtension;
        private final int transpositionLimit;
        private int checkOrderBonus;
        private final Map<SearchTranspositionKey, Line> transpositions;
        private QuietMoveOrdering quietMoveOrdering;

        SearchBudget(int remaining, int tacticalExtension) {
            this(remaining, tacticalExtension, 0);
        }

        SearchBudget(int remaining, int tacticalExtension, int transpositionLimit) {
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
