package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintRecaptureRegressionTest {
    private static final String BEFORE = "e2e4 c7c6 d2d4 d7d5 e4e5 c6c5 b1c3 b8c6 d4c5 d5d4";

    @Test void theCaroKannHintSeesTheRecaptureBeforeRejectingTheWinningPawnCapture(Checks check) {
        var game = ChessMatch.restore("3|WHITE|HARD|1|" + BEFORE + " e5e6");
        var checkpoint = game.checkpoint();
        var search = TacticalAnalysis.verifiedHints(game.position(), 200_000);
        check.same("The independent H17/H19 witness prefers dxc3 over fxe6", List.of(move("d4c3")),
                search.lines().stream().map(TacticalAnalysis.Variation::move).toList());
        var comparison = search.comparison();
        check.same("Both roots have an actual completed comparison beyond the seed", true,
                comparison != null && comparison.depth() == search.selectedDepth() && search.selectedDepth() > search.seedDepth());
        check.same("The rejected capture remains visible as diagnostic evidence", List.of(move("d4c3"), move("f7e6")),
                comparison.result().ranked().stream().map(TacticalAnalysis.Variation::move).toList());
        var first = search.lines().getFirst();
        check.same("The queen exchange includes the legal Nxd8 recapture", true,
                java.util.Collections.indexOfSubList(first.line(), List.of(move("d1d8"), move("c6d8"))) >= 0);
        check.same("The whole decision, including interrupted work and recaptures, is charged", true,
                search.extraNodes() > 0 && search.extraNodes() <= 200_000);
        for (var line : comparison.result().ranked()) {
            var end = game.position();
            for (var action : line.line()) end = end.play(action);
            check.same("The scored continuation has real material after " + line.move().uci(), line.materialChange(),
                    HintAuditSession.materialChangeCp(game.position(), end));
            int extension = MatchAiPolicy.CURRENT.hint.tacticalExtensionPlies() + MatchAiPolicy.CURRENT.hint.verificationRecapturePlies();
            check.same("Recaptures do not relabel the nominal horizon", true,
                    line.line().size() >= search.selectedDepth() && line.line().size() <= search.selectedDepth() + extension);
        }
        var hint = game.hintAt(game.ply());
        hint.revealNext();
        check.same("The game bulb shares the fully scored hint", first, hint.revealed());
        var preview = hint.preview(game.position()).orElseThrow();
        while (preview.hasNext()) preview.next();
        check.same("The preview adds no unscored defense", false, preview.addedDefense());
        check.same("Destination tap can execute the same winning capture", move("d4c3"), hint.moveForTap(game.position(), -1, Move.fromUci("d4c3").to()));
        check.same("Inspecting the hint preserves the full game history", checkpoint, game.checkpoint());
    }

    @Test void theUsefulHintBeforeE6IsNotLostByRepairingTheFollowingPosition(Checks check) {
        var game = ChessMatch.restore("2|analysis|" + BEFORE);
        var hint = game.hintAt(game.ply());
        hint.revealNext();
        check.same("The preceding White move retains Nce2 rather than the losing e6", move("c3e2"), hint.revealedMove());
        check.same("Manual analysis and native-game hints use the same first move", hint.revealed(), MatchHint.analyze(game.position()).first());
        check.same("The preceding line remains playable", true, hint.preview(game.position()).isPresent());
    }

    @Test void independentCheckingPawnFamiliesRetainTheirImprovedFirstMoves(Checks check) {
        // Frozen forcing-holdout 055/090, independently graded at Stockfish H17/H19 before becoming regression cases.
        var histories = List.of(
                "d2d4 d7d6 g1f3 f7f5 b1c3 g8f6 c1g5 d6d5 e2e3 c7c6 f1d3 e7e6 g2g4 f8e7 g4f5 e6f5 h1g1 f6g4 g5e7 d8e7 d1d2 e7b4 e1d1 b4b2 h2h3 g4f2 d1e2 f2e4 c3e4 f5e4 f3e5 c8h3 a2a4 b8d7",
                "e2e4 e7e5 b1c3 g8f6 g2g3 b8c6 f1g2 f8c5 g1e2 d7d6 c3a4 c5b6 e1g1 e8g8 a4b6 a7b6 f2f4 d8e7 f4e5 e7e5 d2d4 e5h5 g2f3 h5g6 e2f4 g6h6 f4e2 g7g5 e4e5 f6d7 h2h4 h6g7 c1g5 h7h6 e5e6");
        var expected = List.of(move("e5d7"), move("h6g5"));
        for (int index = 0; index < histories.size(); index++) {
            var game = ChessMatch.restore("2|analysis|" + histories.get(index));
            var search = TacticalAnalysis.verifiedHints(game.position(), 200_000);
            check.same("Independent family " + index + " retains the lower-loss first recommendation", expected.get(index), search.lines().getFirst().move());
            check.same("Independent family " + index + " cannot buy more nodes", true, search.extraNodes() <= 200_000);
            var end = game.position();
            for (var action : search.lines().getFirst().line()) end = end.play(action);
            check.same("Independent family " + index + " retains a real material ledger", search.lines().getFirst().materialChange(),
                    HintAuditSession.materialChangeCp(game.position(), end));
        }
    }

    @Test void onlyCompetingCapturesOfMaterialActivateTheAdditionalExchangeCheck(Checks check) throws Exception {
        var position = ChessMatch.restore("2|analysis|" + BEFORE + " e5e6").position();
        var select = TacticalAnalysis.class.getDeclaredMethod("competingCaptures", ChessPosition.class, List.class);
        select.setAccessible(true);
        check.same("A pawn versus knight capture needs the material comparison", true,
                select.invoke(null, position, List.of(move("f7e6"), move("d4c3"))));
        check.same("Two pawn victims retain the ordinary bounded frontier", false,
                select.invoke(null, position, List.of(move("f7e6"), move("c8e6"))));
        check.same("A quiet challenger cannot activate the capture-only extension", false,
                select.invoke(null, position, List.of(move("d4c3"), move("d8a5"))));
        check.same("A singleton cannot impersonate a comparison", false,
                select.invoke(null, position, List.of(move("d4c3"))));
        var counterexample = ChessPosition.fromFen("r1bq1rk1/pp2nppp/2n1p3/b1pp4/Q1P5/2N2NP1/PP1PPPBP/R1BR2K1 b - - 1 9");
        check.same("A normal pawn versus knight exchange without a checking pawn reply retains ordinary verification", false,
                select.invoke(null, counterexample, List.of(move("d5c4"), move("a5c3"))));
    }

    @Test void aSeedCacheCannotImpersonateARecaptureVerifiedValue(Checks check) throws Exception {
        var position = ChessPosition.fromFen("3r2k1/8/8/8/3q4/8/3R4/3R2K1 w - - 0 1");
        var warm = new Frontier(64);
        var seed = warm.search(position, -100_001, 100_001);
        warm.recaptures(2);
        var actual = warm.search(position, -100_001, 100_001);
        var cold = new Frontier(64); cold.recaptures(2);
        var disabled = new Frontier(0); disabled.recaptures(2);
        check.same("The extra recapture changes a real exchange rather than a root label", true,
                (int) value(actual, "score") > (int) value(seed, "score"));
        check.same("Warm and cold full-window exchange evidence agree", value(cold.search(position, -100_001, 100_001), "moves"), value(actual, "moves"));
        check.same("Disabling the table preserves the verified score", value(disabled.search(position, -100_001, 100_001), "score"), value(actual, "score"));
        check.same("The opponent can decline the losing recapture instead of being forced into a pretty PV", List.of(move("d2d4")), value(actual, "moves"));
        var exchanged = position.play(move("d2d4")).play(move("d8d4"));
        var reply = new Frontier(0); reply.recaptures(2);
        check.same("After the opponent really recaptures, Rxd4 is independently available", List.of(move("d1d4")),
                value(reply.search(exchanged, -100_001, 100_001), "moves"));
        var bounded = new Frontier(64); bounded.recaptures(2);
        var bound = bounded.search(position, -1000, -900);
        check.same("A cutoff during exchange verification remains a bound", false, value(bound, "exact"));
        var verified = bounded.search(position, -100_001, 100_001);
        check.same("The bound cannot poison a later full-window result", value(actual, "score"), value(verified, "score"));
        check.same("Actual extra visits consume the same finite counter", true, warm.remaining() < 20_000 && warm.remaining() >= 0);
    }

    @Test void recaptureConfigurationIsBoundedAndCanBeDisabledWithoutEnlargingTheHint(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var hint = new HashMap<Object, Object>(ChessYaml.map(root, "hint")); root.put("hint", hint);
        check.same("The release enables two additional recapture plies", 2, MatchAiPolicy.CURRENT.hint.verificationRecapturePlies());
        hint.put("verification-recapture-plies", 0);
        check.same("Zero provides the original comparison for a controlled A/B run", 0, MatchAiPolicy.read(root).hint.verificationRecapturePlies());
        for (int invalid : List.of(-1, 3)) {
            hint.put("verification-recapture-plies", invalid);
            check.rejects("An unbounded recapture extension is rejected: " + invalid, IllegalArgumentException.class, () -> MatchAiPolicy.read(root));
        }
        hint.remove("verification-recapture-plies");
        check.rejects("Missing policy cannot silently enable a different comparison", IllegalArgumentException.class, () -> MatchAiPolicy.read(root));
        check.same("The whole hint retains the 200k ceiling", 200_000, MatchAiPolicy.CURRENT.hint.search().extraNodes());
        check.same("Verification cannot donate new nodes", 150_000, MatchAiPolicy.CURRENT.hint.verificationNodes());
    }

    @Test void anInterruptedExchangeCannotPopulateTheCacheWithAnExactRoot(Checks check) throws Exception {
        var position = ChessPosition.fromFen("3r2k1/8/8/8/3q4/8/3R4/3R2K1 w - - 0 1");
        var interrupted = new Frontier(64); interrupted.recaptures(2); interrupted.replenish(2);
        check.rejects("The finite allowance interrupts an unfinished exchange", java.lang.reflect.InvocationTargetException.class,
                () -> interrupted.search(position, -100_001, 100_001));
        check.same("An interrupted exchange cannot spend past zero", 0, interrupted.remaining());
        interrupted.replenish(20_000);
        var resumed = interrupted.search(position, -100_001, 100_001);
        var cold = new Frontier(64); cold.recaptures(2);
        var reference = cold.search(position, -100_001, 100_001);
        check.same("Retrying the interrupted root has the independently completed score", value(reference, "score"), value(resumed, "score"));
        check.same("Retrying retains the independently completed legal PV", value(reference, "moves"), value(resumed, "moves"));
    }

    private static Move move(String value) { return Move.fromUci(value); }
    private static Object value(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    private static final class Frontier {
        private final Object budget;
        private final Method search;
        private final Object objective;
        @SuppressWarnings({"unchecked", "rawtypes"}) Frontier(int cache) throws Exception {
            var type = Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");
            var constructor = type.getDeclaredConstructor(int.class, int.class, int.class); constructor.setAccessible(true);
            budget = constructor.newInstance(20_000, 2, cache);
            var goal = Class.forName("ro.atf.chess.TacticalAnalysis$Objective"); objective = Enum.valueOf((Class) goal, "POSITION");
            search = TacticalAnalysis.class.getDeclaredMethod("search", ChessPosition.class, int.class, int.class, int.class, int.class, type, goal);
            search.setAccessible(true);
        }
        void recaptures(int plies) throws Exception {
            var field = budget.getClass().getDeclaredField("recaptureExtension"); field.setAccessible(true); field.setInt(budget, plies);
        }
        int remaining() throws Exception {
            var field = budget.getClass().getDeclaredField("remaining"); field.setAccessible(true); return field.getInt(budget);
        }
        void replenish(int nodes) throws Exception {
            var field = budget.getClass().getDeclaredField("remaining"); field.setAccessible(true); field.setInt(budget, nodes);
        }
        Object search(ChessPosition position, int alpha, int beta) throws Exception { return search.invoke(null, position, 0, 0, alpha, beta, budget, objective); }
    }
}
