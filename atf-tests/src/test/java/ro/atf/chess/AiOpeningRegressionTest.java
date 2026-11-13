package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AiOpeningRegressionTest {
    private static final String PIRC = "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4";
    private static final String TRANSPOSED_PIRC = "d2d4 d7d6 e2e4 g8f6 b1c3 g7g6 f2f4";
    private static final String BENONI = "d2d4 g8f6 c2c4 c7c5 d4d5 e7e6 b1c3 e6d5 c4d5";

    @Test void hardAndHintsUseVerifiedRepliesOnlyAtTheMatchingPosition(Checks check) {
        assertReply(check, "Pirc", PIRC, "f8g7");
        assertReply(check, "Transposed Pirc", TRANSPOSED_PIRC, "f8g7");
        assertReply(check, "Benoni", BENONI, "d7d6");
        var adjacent = match(PIRC.replace("f2f4", "g1f3"), "WHITE").position();
        check.same("The Pirc with Nf3 instead of f4 uses search", null, OpeningRepertoire.CURRENT.reply(adjacent));
        var next = match(BENONI + " d7d6", "WHITE").position();
        check.same("The position after the verified Benoni reply uses search", null, OpeningRepertoire.CURRENT.reply(next));
    }

    @Test void hardUsesTheVerifiedCaptureAtTheRepertoirePosition(Checks check) {
        String prefix = "e2e4 c7c5 g1f3 e7e5";
        var game = match(prefix, "BLACK");
        var capture = Move.fromUci("f3e5");
        check.same("Hard takes the undefended central pawn", capture, game.chooseBotMove());
        check.same("The coach recommends the same verified capture", capture,
                TacticalAnalysis.hintAnalysis(game.position()).result().ranked().getFirst().move());
        for (var level : List.of(MatchSetup.Level.BASIC, MatchSetup.Level.ADVANCED)) {
            check.same(level + " has its own evaluation at this position", null, OpeningRepertoire.CURRENT.reply(game.position(), level));
        }
        var neighbor = match(prefix.replace("e7e5", "d7d6"), "BLACK");
        check.same("An adjacent Sicilian position uses search", null, OpeningRepertoire.CURRENT.reply(neighbor.position()));
        check.same("The position after the capture returns to search", null, OpeningRepertoire.CURRENT.reply(game.position().play(capture)));
    }

    @Test void hardFindsTheImmediatePawnCaptureOutsideItsRepertoire(Checks check) {
        for (String moves : List.of("e2e4 c7c5 g1f3 g7g6 f1e2 e7e5", "e2e4 c7c5 g1f3 a7a5 a2a4 e7e5",
                "e2e4 c7c5 g1f3 g7g6 a2a3 e7e5", "e2e4 c7c5 g1f3 g7g6 h2h3 e7e5")) {
            var game = match(moves, "BLACK");
            check.same("Hard must search this position: " + moves, null, OpeningRepertoire.CURRENT.reply(game.position()));
            var decision = game.botDecision();
            check.same("Hard takes the pawn immediately", "f3e5", decision.move().uci());
            check.same("The completed reply horizon is preserved", 4, decision.depth());
            check.same("The extra search respects the Hard cap", true,
                    decision.extraNodes() <= MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes());
        }
    }

    @Test void hardCompletesTheExchangeBeforeRejectingAnExposedPawn(Checks check) {
        for (String moves : List.of("e2e4 c7c5 g1f3 h7h6 b1c3 e7e5", "e2e4 c7c5 g1f3 h7h6 b1c3 a7a6 a2a3 e7e5",
                "e2e4 c7c5 g1f3 h7h6 b1c3 b7b6 h2h3 e7e5", "e2e4 c7c5 g1f3 g7g6 b1c3 e7e5")) {
            var game = match(moves, "BLACK");
            check.same("The position has no Hard repertoire entry: " + moves, null, OpeningRepertoire.CURRENT.reply(game.position()));
            var decision = game.botDecision();
            check.same("Hard takes the exposed pawn on e5", "f3e5", decision.move().uci());
            check.same("Main search and exchange verification share the Hard cap", true, decision.extraNodes() <= 44_000);
        }
    }

    @Test void hardKeepsTheIncumbentWhenThePawnCanBeRecaptured(Checks check) {
        for (String moves : List.of("e2e4 c7c5 g1f3 h7h6 b1c3 d7d6 a2a3 e7e5", "e2e4 c7c5 g1f3 h7h6 b1c3 d8c7 a2a3 e7e5",
                "e2e4 c7c5 g1f3 h7h6 b1c3 b8c6 a2a3 e7e5")) {
            var game = match(moves, "BLACK");
            var capture = Move.fromUci("f3e5");
            check.same("The legal capture is available outside the repertoire", true,
                    game.position().legalMoves().contains(capture) && OpeningRepertoire.CURRENT.reply(game.position()) == null);
            check.same("Hard declines the defended pawn", false, capture.equals(game.botDecision().move()));
        }
    }

    @Test void hardFindsTheImmediateCaptureWithBlackToMove(Checks check) {
        var game = match("h2h3 e7e5 c2c4 g8f6 a2a4 a7a5 e2e4", "WHITE");
        check.same("The Black position has no repertoire entry", null, OpeningRepertoire.CURRENT.reply(game.position()));
        var decision = game.botDecision();
        check.same("Black takes the pawn on e4 immediately", "f6e4", decision.move().uci());
        check.same("The search retains the opponent reply at the completed horizon", 4, decision.depth());
        check.same("Black also uses the governed node cap", 44_000, decision.extraNodes());
    }

    @Test void hardDoesNotForceThePawnCaptureWhenThePositionDoesNotSupportIt(Checks check) {
        for (String moves : List.of("e2e4 c7c5 g1f3 d7d6 f1e2 e7e5", "e2e4 c7c5 g1f3 d8c7 a2a3 e7e5")) {
            var game = match(moves, "BLACK");
            check.same("The legal capture is present and the position uses search", true,
                    game.position().legalMoves().contains(Move.fromUci("f3e5")) && OpeningRepertoire.CURRENT.reply(game.position()) == null);
            check.same("Hard develops when the pawn is defended", "b1c3", game.botDecision().move().uci());
        }
        var black = match("d2d4 d7d5 g2g3 g8f6 e2e4", "WHITE");
        check.same("Hard still searches the Black control", null, OpeningRepertoire.CURRENT.reply(black.position()));
        check.same("Black keeps its original capture choice", "d5e4", black.botDecision().move().uci());
    }

    @Test void repertoireRejectsIllegalRepliesAndTransposedDuplicates(Checks check) {
        var pirc = Map.of("id", "pirc", "moves", PIRC, "reply", "f8g7");
        var duplicate = Map.of("id", "same-board", "moves", TRANSPOSED_PIRC, "reply", "f8g7");
        check.same("A second entry for the same position is rejected", true, assertThrows(IllegalArgumentException.class,
                () -> OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(pirc, duplicate)))).getMessage().contains("Duplicate"));
        var illegal = Map.of("id", "illegal", "moves", PIRC, "reply", "f8f6");
        check.same("An illegal reply is rejected during configuration loading", true, assertThrows(IllegalArgumentException.class,
                () -> OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(illegal)))).getMessage().contains("Illegal"));
        for (var invalidLevels : List.of(List.of("BASIC"), List.of("ADVANCED", "ADVANCED"), List.of("UNKNOWN"))) {
            var entry = new java.util.HashMap<String, Object>(pirc);
            entry.put("levels", invalidLevels);
            check.rejects("Unsupported or repeated repertoire level " + invalidLevels, IllegalArgumentException.class,
                    () -> OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(entry))));
        }
    }

    @Test void advancedUsesVerifiedBenoniRepliesWithoutBorrowingOtherProfiles(Checks check) {
        var black = ChessMatch.restore("3|WHITE|ADVANCED|0|" + BENONI);
        check.same("Advanced develops the d-pawn at the first repeated Black loss", "d7d6", black.chooseBotMove().uci());
        for (var row : List.of(List.of("g7g6", "d5d6"), List.of("b8a6", "e2e4"), List.of("h7h6", "e2e4"))) {
            var white = ChessMatch.restore("3|BLACK|ADVANCED|0|" + BENONI + " " + row.get(0));
            var decision = white.botDecision();
            check.same("Advanced White follows the exact position after " + row.get(0), row.get(1), decision.move().uci());
            check.same("The existing Advanced search budget still applies", true,
                    decision.extraNodes() <= MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.ADVANCED).extraNodes());
            check.same("The Advanced-only reply stays out of Hard and hints", null, OpeningRepertoire.CURRENT.reply(white.position()));
            check.same("Basic keeps its own search", null, OpeningRepertoire.CURRENT.reply(white.position(), MatchSetup.Level.BASIC));
        }
        var neighboring = ChessMatch.restore("1|" + BENONI + " d7d6");
        check.same("The adjacent review-only line stays out of Advanced play", null,
                OpeningRepertoire.CURRENT.reply(neighboring.position(), MatchSetup.Level.ADVANCED));
    }

    @Test void postgameReviewComparesTheVerifiedReplyAndThePlayedMove(Checks check) {
        for (var row : List.of(List.of(PIRC, "f8g7", "f8h6"), List.of(TRANSPOSED_PIRC, "f8g7", "f8h6"),
                List.of(BENONI, "d7d6", "d8a5"),
                List.of(PIRC.replace("f2f4", "g1f3"), "f8g7", "c8g4"),
                List.of(BENONI + " d7d6", "h2h3", "c1g5"))) {
            var position = match(row.get(0), "WHITE").position();
            var expected = Move.fromUci(row.get(1));
            var played = Move.fromUci(row.get(2));
            var review = TacticalAnalysis.review(position, played, Set.of(), GameReviewPolicy.CURRENT.search);
            check.same("The verified reply is the recommendation for " + row.get(0), expected, review.best().move());
            check.same("The played line still starts with the actual move", played, review.played().move());
            for (var variation : List.of(review.best(), review.played())) {
                var board = position;
                for (var move : variation.line()) board = board.play(move);
                check.same("The continuation reaches the common search horizon", true, variation.line().size() >= review.depth());
            }
        }
        var position = match(PIRC, "WHITE").position();
        var reply = Move.fromUci("f8g7");
        var drawn = TacticalAnalysis.review(position, reply, Set.of(reply), GameReviewPolicy.CURRENT.search);
        check.same("An automatic draw is evaluated at its actual zero score", 0, drawn.played().score());
        check.same("A better search result can outrank the drawn repertoire move", true,
                !drawn.best().move().equals(reply) && drawn.best().score() > 0);
    }

    @Test void reviewOnlyRepliesStayOutOfLivePlayAndDoNotExtendToTheNextPosition(Checks check) {
        var classical = match(PIRC.replace("f2f4", "g1f3"), "WHITE").position();
        var benoni = match(BENONI + " d7d6", "BLACK").position();
        for (var position : List.of(classical, benoni)) {
            var reply = OpeningRepertoire.CURRENT.reviewReply(position);
            check.same("The review has a legal reference", true, position.legalMoves().contains(reply));
            check.same("Live play and hints use their normal search", null, OpeningRepertoire.CURRENT.reply(position));
            check.same("The next board still uses review search", null, OpeningRepertoire.CURRENT.reviewReply(position.play(reply)));
        }
    }

    @Test void hardLeavesTheWinningCycleAndCanStillTakeAForcedDraw(Checks check) {
        var winning = match(alekhinePrefix("escape-ply"), "BLACK");
        var escape = winning.chooseBotMove();
        var scores = TacticalAnalysis.botAnalysis(winning.position(), MatchSetup.Level.HARD, MatchAiPolicy.CURRENT.repetition.maxScoreLossCp());
        var ranked = scores.result().ranked();
        var line = ranked.stream().filter(item -> item.move().equals(escape)).findFirst().orElseThrow();
        var policy = MatchAiPolicy.CURRENT.repetition;
        check.same("Hard escapes the queen cycle with a legal move", true, winning.position().legalMoves().contains(escape) && !escape.equals(Move.fromUci("e6f5")));
        check.same("The escape preserves the minimum advantage and the allowed loss", true,
                line.score() >= Math.max(policy.minAdvantageCp(), ranked.getFirst().score() - policy.maxScoreLossCp()));
        check.same("After the escape the game remains active", ChessMatch.Result.IN_PROGRESS, winning.play(escape).result());

        var defending = match(alekhinePrefix("draw-ply"), "WHITE");
        var draw = defending.chooseBotMove();
        check.same("Losing Black can still choose the fifth occurrence", "g8f8", draw.uci());
        check.same("The game records the fifth occurrence as a draw", ChessMatch.Result.REPETITION_DRAW, defending.play(draw).result());
    }

    @Test void hardUsesThePlayedHistoryToCompareTheConversionWithTheRepeat(Checks check) {
        var fixture = ChessYaml.resource("alekhine-conversion.yaml");
        var replay = match(ChessYaml.string(fixture, "moves"), "BLACK");
        var repeat = Move.fromUci(ChessYaml.string(fixture, "repeat"));
        var repeated = replay.play(repeat);
        long previousVisits = IntStream.rangeClosed(0, replay.ply())
                .filter(ply -> repeated.position().sameForRepetition(replay.positionAt(ply))).count();
        check.same("Historical checkpoint after 72 plies", 72, replay.ply());
        check.same("Qc5 would revisit a position that appeared twice", 2L, previousVisits);
        check.same("The third visit does not end the game", ChessMatch.Result.IN_PROGRESS, repeated.result());
        var positionOnly = ChessMatch.fromPosition(replay.position(), replay.setup());
        check.same("On the same board without repetition history Hard still prefers Qc5", repeat, positionOnly.chooseBotMove());
        String earlierMoves = String.join(" ", Arrays.copyOf(ChessYaml.string(fixture, "moves").split(" "), 68));
        var earlier = match(earlierMoves, "BLACK");
        check.same("Qc5 remains available on its second occurrence", repeat, earlier.chooseBotMove());
        var decision = replay.botDecision();
        check.same("With the complete history Hard chooses the queen exchange Qxf6+", ChessYaml.string(fixture, "conversion"), decision.move().uci());
        var budget = MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD);
        check.same("Hard completes its configured depth within its existing extra node budget", true,
                decision.depth() == budget.maxPlies() && decision.extraNodes() <= budget.extraNodes());
    }

    static String alekhinePrefix(String ply) {
        var replay = ChessYaml.resource("repetition-regression.yaml");
        return String.join(" ", Arrays.copyOf(ChessYaml.string(replay, "moves").split(" "), ChessYaml.number(replay, ply)));
    }

    private static void assertReply(Checks check, String label, String prefix, String expected) {
        var blackToMove = match(prefix, "WHITE");
        var hint = blackToMove.bestLinesAt(blackToMove.ply()).getFirst();
        check.same(label + " Hard chooses the verified reply", expected, blackToMove.chooseBotMove().uci());
        check.same(label + " coaching chooses the same verified reply", expected, hint.move().uci());
        var board = blackToMove.position();
        for (var move : hint.line()) board = board.play(move);
        check.same(label + " coaching returns a legal continuation", true, hint.line().size() > 1);
    }

    private static ChessMatch match(String prefix, String human) {
        return ChessMatch.restore("3|" + human + "|HARD|1|" + prefix);
    }
}
