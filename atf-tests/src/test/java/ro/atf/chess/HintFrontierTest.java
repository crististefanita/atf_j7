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
            var budgetType = Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");
            var constructor = budgetType.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            budget = constructor.newInstance(20_000, 2, cacheEntries);
            var objectiveType = Class.forName("ro.atf.chess.TacticalAnalysis$Objective");
            objective = Enum.valueOf((Class) objectiveType, "POSITION");
            search = TacticalAnalysis.class.getDeclaredMethod("search", ChessPosition.class, int.class, int.class,
                    int.class, int.class, budgetType, objectiveType);
            search.setAccessible(true);
        }

        Object search(ChessPosition position, int alpha, int beta) throws Exception {
            return search.invoke(null, position, 0, 0, alpha, beta, budget, objective);
        }
    }

    private static ChessPosition position(String sequence) { return ChessMatch.restore("2|analysis|" + sequence).position(); }
    private static Move move(String uci) { return Move.fromUci(uci); }
}
