package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintFrontierTest {
    @Test void theQueensCaptureIsScoredBeforeTheKnightSacrificeIsRanked(Checks check) {
        var position = position("e2e4 b8c6 f1b5 c6d4 b5c4");
        var pair = TacticalAnalysis.compareHints(position, move("d4c2"), move("e7e5")).orElseThrow();
        check.same("The quiet alternative outranks the refuted knight sacrifice", move("e7e5"), pair.stronger().move());
        check.same("The knight line contains the legal Qxc2 refutation", true,
                pair.alternative().line().contains(move("d1c2")));
        check.same("The sacrifice loses material after the reply", true, pair.alternative().materialChange() < 0);
        var search = TacticalAnalysis.verifiedHints(position);
        check.same("The public first move is recalculated rather than truncated", false,
                search.lines().getFirst().move().equals(move("d4c2")));
        var after = position.play(move("d4c2"));
        check.same("The same strongest hint finds the capture when it is already White's turn", move("d1c2"),
                MatchHint.analyze(after).first().move());
    }

    @Test void theInitialBishopCaptureIsComparedThroughTheRecapture(Checks check) {
        var position = position("e2e4 b8c6 f1b5 c6d4");
        var pair = TacticalAnalysis.compareHints(position, move("b5d7"), move("b5a4")).orElseThrow();
        check.same("Retaining the bishop outranks the unproved sacrifice", move("b5a4"), pair.stronger().move());
        check.same("The sacrificed bishop is recaptured in the scored line", true,
                pair.alternative().line().contains(move("d8d7")) || pair.alternative().line().contains(move("c8d7")));
        check.same("A captured pawn cannot conceal the lost bishop", true, pair.alternative().materialChange() < 0);
    }

    @Test void soundQueenAndKnightSacrificesStillProveMateWithFreeReplies(Checks check) {
        assertMate(check, "Queen", "2r3k1/pb3ppp/1p2pq2/1P6/2Q1PP2/6P1/P5BP/2R3K1 w - - 1 25", "c4c8", 5);
        assertMate(check, "Knight", "3q1r1k/p3r1pp/1p1b1p2/2p5/3pR2N/1QPn2P1/PP1B1P1P/R5K1 w - - 0 22", "h4g6", 3);
        for (String fen : List.of("2r3k1/pb3ppp/1p2pq2/1P6/2Q1PP2/6P1/P5BP/R5K1 w - - 1 25",
                "3q1r1k/p3r1pp/1p1b1p2/2p5/3p3N/1QPn2P1/PP1B1P1P/R3R1K1 w - - 0 22")) {
            var line = MatchHint.analyze(ChessPosition.fromFen(fen)).first();
            check.same("Relocating the supporting rook invalidates the original mate proof", 0, line.signedMatePlies());
        }
    }

    @Test void theBulbComparisonPreviewAndDestinationUseTheSameScoredMoves(Checks check) {
        for (String sequence : List.of("", "e2e4", "e2e4 b8c6", "e2e4 b8c6 f1b5 c6d4 b5c4")) {
            var game = ChessMatch.restore("2|analysis|" + sequence);
            var position = game.position();
            var hint = game.hintAt(game.ply());
            hint.revealNext();
            var first = hint.revealed();
            var preview = HintPreview.openDefended(position, first).orElseThrow();
            var moves = new ArrayList<Move>();
            while (preview.hasNext()) {
                preview.next();
                moves.add(preview.lastMove());
            }
            check.same(sequence + " replays exactly the scored line without an additional search", first.line(), moves);
            check.same(sequence + " never appends an unscored reply", false, preview.addedDefense());
            check.same(sequence + " has the same first move in the destination tap", first.move(),
                    hint.moveForTap(position, -1, first.move().to()));
            var comparison = hint.compare(position);
            if (comparison.isPresent()) check.same(sequence + " comparison keeps the bulb's actual leader", first, comparison.orElseThrow().stronger());
            check.same(sequence + " exploring the line preserves the match", "2|analysis|" + sequence, game.checkpoint());
        }
    }

    @Test void seedFrontierAndComparisonStayWithinOneGovernedTwoHundredThousandNodeBudget(Checks check) {
        var policy = MatchAiPolicy.CURRENT.hint;
        check.same("The strongest hint has a hard 200,000-node decision ceiling", 200_000, policy.search().extraNodes());
        check.same("The hint can search at least the adaptive Hard horizon", true,
                policy.search().maxPlies() >= HardAdaptivePolicy.CURRENT.search().maxPlies());
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var hint = new HashMap<Object, Object>(ChessYaml.map(root, "hint"));
        root.put("hint", hint);
        for (int invalid : List.of(200_001, 220_000)) {
            hint.put("extra-nodes", invalid);
            check.rejects("YAML rejects a hint ceiling of " + invalid, IllegalArgumentException.class, () -> MatchAiPolicy.read(root));
        }
        for (String sequence : List.of("", "e2e4", "e2e4 b8c6", "e2e4 b8c6 f1b5 c6d4 b5c4")) {
            var search = TacticalAnalysis.verifiedHints(position(sequence));
            check.same(sequence + " charges seed, full horizons, frontier and comparison to one ceiling", true,
                    search.extraNodes() > 0 && search.extraNodes() <= policy.search().extraNodes());
        }
    }

    @Test void narrowFrontierBoundsCannotPoisonTheWarmExactCache(Checks check) throws Exception {
        var position = ChessPosition.fromFen("7k/8/8/8/3qR3/8/8/4K3 w - - 0 1");
        for (int[] window : List.of(new int[]{-10_000, -1_000}, new int[]{0, 1})) {
            var warm = new Frontier(1_000);
            var bound = warm.search(position, window[0], window[1]);
            check.same("The narrow window produces a bound rather than an exact transposition", false, value(bound, "exact"));
            var warmed = warm.search(position, -100_000, 100_000);
            var cold = new Frontier(1_000).search(position, -100_000, 100_000);
            var disabled = new Frontier(0).search(position, -100_000, 100_000);
            check.same("Full-window score survives an earlier bound with a warm cache", value(cold, "score"), value(warmed, "score"));
            check.same("Disabling the cache preserves the exact frontier score", value(cold, "score"), value(disabled, "score"));
            check.same("The capture remains in the full-window proof", List.of(move("e4d4")), value(warmed, "moves"));
        }
    }

    @Test void closeOpeningChallengersReceiveACompletedCommonHorizon(Checks check) {
        for (var row : List.of(List.of("e2e4 c7c5 g1f3 d7d6 d2d4", "c5d4", "b8d7"),
                List.of("e2e4 c7c6 d2d4", "d7d5", "d7d6"))) {
            var board = position(row.get(0));
            var search = TacticalAnalysis.verifiedHints(board);
            check.same("A completed comparison selects the independently checked central move", move(row.get(1)), search.lines().getFirst().move());
            check.same("The inferior root is not published as an equivalent hint", false,
                    search.lines().stream().anyMatch(line -> line.move().equals(move(row.get(2)))));
            check.same("The decision records a real completed horizon beyond the seed", true,
                    search.selectedDepth() > search.seedDepth());
            check.same("The decision and comparison share one ceiling", true, search.extraNodes() <= 200_000);
            var compared = search.comparison();
            check.same("Both roots retain scored evidence at one completed horizon", true, compared != null
                    && compared.depth() == search.selectedDepth()
                    && compared.result().ranked().getFirst().score() >= compared.result().ranked().get(1).score());
            check.same("A closed frontier cannot readmit a challenger already disproved by the ordinary comparison",1,search.lines().size());
        }
    }

    @Test void experimentBudgetsAreImmutableAndChargeTheWholeDecision(Checks check) {
        var board = position("e2e4 c7c6 d2d4");
        var configured = MatchAiPolicy.CURRENT.hint;
        for (int invalid : List.of(-1, 0, 999, 200_001, 220_000)) {
            check.rejects("A bounded experiment rejects " + invalid, IllegalArgumentException.class,
                    () -> TacticalAnalysis.verifiedHints(board, invalid));
        }
        for (int nodes : List.of(50_000, 100_000, 200_000)) {
            var search = TacticalAnalysis.verifiedHints(board, nodes);
            check.same("Every experiment charges its completed and interrupted work", true,
                    search.extraNodes() > 0 && search.extraNodes() <= nodes);
            check.same("Depth comes from the executed decision rather than the replay length", true,
                    search.seedDepth() > 0 && search.selectedDepth() > 0
                            && search.selectedDepth() <= configured.search().maxPlies());
            for (var line : search.lines()) {
                var replay = HintPreview.openDefended(board, line).orElseThrow();
                check.same("An experiment preserves the exact scored continuation", line.line().size(), replay.length());
            }
        }
        check.same("An experiment cannot mutate the governed production policy", configured, MatchAiPolicy.CURRENT.hint);
    }

    @Test void anEarlyMateProofDoesNotDonateUnusedSeedNodesBeyondTheVerificationCap(Checks check) {
        var board = ChessPosition.fromFen("3q1r1k/p3r1pp/1p1b1p2/2p5/3pR2N/1QPn2P1/PP1B1P1P/R5K1 w - - 0 22");
        var policy = MatchAiPolicy.CURRENT.hint;
        var seed = TacticalAnalysis.hintAnalysis(board);
        check.same("The independent seed probe proves mate before its reserved allowance is exhausted", true,
                seed.result().ranked().getFirst().signedMatePlies() == 3
                        && seed.extraNodes() < policy.search().extraNodes() - policy.verificationNodes());
        var decision = TacticalAnalysis.verifiedHints(board);
        check.same("The public decision reaches the same early seed proof", seed.depth(), decision.seedDepth());
        check.same("Unused seed allowance cannot enlarge the configured verification maximum", true,
                decision.extraNodes() - seed.extraNodes() <= policy.verificationNodes());
        check.same("The final public proof retains the sound knight sacrifice", 3, decision.lines().getFirst().signedMatePlies());
    }

    @Test void aNewEvenHorizonCannotCorroborateItsOwnReversal(Checks check) {
        for (var row : List.of(List.of("d2d4 g8f6 c2c4 e7e6 g2g3 d7d5", "c4d5"),
                List.of("e2e4 b8c6 f1b5 c6d4 b5c4", "g8f6"))) {
            var search = TacticalAnalysis.verifiedHints(position(row.get(0)));
            check.same("A reversal unsupported by the preceding even horizon retains the incumbent", move(row.get(1)),
                    search.lines().getFirst().move());
            check.same("A rejected H6 comparison reports the retained seed horizon", search.seedDepth(), search.selectedDepth());
            check.same("An unsupported reversal cannot publish competing proof scores", null, search.comparison());
        }
    }

    @Test void horizonSupportComesFromAnEarlierCompletedEvenSearch(Checks check) throws Exception {
        var board = ChessPosition.fromFen("7k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        var roots = List.of(move("e2e8"), move("e2e7"));
        var method = TacticalAnalysis.class.getDeclaredMethod("verifyHintRoots", ChessPosition.class, List.class, int.class, int.class);
        method.setAccessible(true);
        for (var horizons : List.of(List.of(5, 4), List.of(6, 4), List.of(7, 6))) {
            var verified = method.invoke(null, board, roots, 150_000, horizons.get(0));
            var report = (TacticalAnalysis.SearchReport) value(verified, "search");
            check.same("The control finishes the requested verification horizon", horizons.get(0), report.depth());
            var reference = method.invoke(null, board, roots, 150_000, horizons.get(1));
            var prior = (TacticalAnalysis.SearchReport) value(reference, "search");
            @SuppressWarnings("unchecked") var balanced = (List<TacticalAnalysis.Variation>) value(verified, "balancedLines");
            check.same("H" + horizons.get(0) + " draws corroboration from H" + horizons.get(1),
                    rootScores(prior.result().ranked()), rootScores(balanced));
        }
    }

    private static java.util.Map<Move, Integer> rootScores(List<TacticalAnalysis.Variation> lines) {
        var result = new HashMap<Move, Integer>();
        for (var line : lines) result.put(line.move(), line.score());
        return result;
    }

    @Test void fullWindowPrincipalValuesSurviveDiscardedChildBoundsAndInterruptedSearches(Checks check) throws Exception {
        for (var board : List.of(position("e2e4 c7c5 g1f3 d7d6 d2d4"), position("e2e4 c7c6 d2d4"),
                ChessPosition.fromFen("7k/8/8/8/3qR3/8/8/4K3 w - - 0 1"),
                ChessPosition.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"))) {
            var cold = new Frontier(0).search(board, 3, -100_001, 100_001);
            int expected = (Integer) value(cold, "score");
            for (int[] window : List.of(new int[]{expected - 2, expected - 1}, new int[]{expected + 1, expected + 2})) {
                var warm = new Frontier(5_000);
                check.same("A fail-high or fail-low node is not an exact cache entry", false,
                        value(warm.search(board, 3, window[0], window[1]), "exact"));
                var full = warm.search(board, 3, -100_001, 100_001);
                check.same("Warm and disabled caches return the same full-window value", expected, value(full, "score"));
                check.same("A completed improving principal value is exact despite discarded child cutoffs", true, value(full, "exact"));
            }
            var interrupted = new Frontier(5_000, 5);
            check.rejects("An exhausted search cannot publish an unfinished principal value", java.lang.reflect.InvocationTargetException.class,
                    () -> interrupted.search(board, 3, -100_001, 100_001));
            interrupted.replenish(20_000);
            check.same("Partial work cannot poison a later completed search", expected,
                    value(interrupted.search(board, 3, -100_001, 100_001), "score"));
        }
    }

    private static void assertMate(Checks check, String name, String fen, String root, int plies) {
        var position = ChessPosition.fromFen(fen);
        var line = MatchHint.analyze(position).first();
        check.same(name + " sacrifice remains the best proved mate", List.of(move(root), plies), List.of(line.move(), line.signedMatePlies()));
        for (var move : line.line()) position = position.play(move);
        check.same(name + " continuation legally ends in checkmate", true, position.isCheckmate());
    }

    private static Object value(Object record, String field) throws Exception {
        var accessor = record.getClass().getDeclaredMethod(field);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    private static final class Frontier {
        private final Object budget;
        private final Method search;
        private final Object objective;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Frontier(int cacheEntries) throws Exception {
            this(cacheEntries, 20_000);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Frontier(int cacheEntries, int nodes) throws Exception {
            var budgetType = Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");
            var constructor = budgetType.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            budget = constructor.newInstance(nodes, 2, cacheEntries);
            var objectiveType = Class.forName("ro.atf.chess.TacticalAnalysis$Objective");
            objective = Enum.valueOf((Class) objectiveType, "POSITION");
            search = TacticalAnalysis.class.getDeclaredMethod("search", ChessPosition.class, int.class, int.class,
                    int.class, int.class, budgetType, objectiveType);
            search.setAccessible(true);
        }

        Object search(ChessPosition position, int alpha, int beta) throws Exception {
            return search(position, 0, alpha, beta);
        }

        Object search(ChessPosition position, int plies, int alpha, int beta) throws Exception {
            return search.invoke(null, position, plies, 0, alpha, beta, budget, objective);
        }

        void replenish(int nodes) throws Exception {
            var remaining = budget.getClass().getDeclaredField("remaining");
            remaining.setAccessible(true);
            remaining.setInt(budget, nodes);
        }
    }

    private static ChessPosition position(String sequence) { return ChessMatch.restore("2|analysis|" + sequence).position(); }
    private static Move move(String uci) { return Move.fromUci(uci); }
}
