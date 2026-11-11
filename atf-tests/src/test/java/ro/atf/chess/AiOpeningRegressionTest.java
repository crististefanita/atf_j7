package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    @Test void repertoireRejectsIllegalRepliesAndTransposedDuplicates(Checks check) {
        var pirc = Map.of("id", "pirc", "moves", PIRC, "reply", "f8g7");
        var duplicate = Map.of("id", "same-board", "moves", TRANSPOSED_PIRC, "reply", "f8g7");
        check.same("A second entry for the same position is rejected", true, assertThrows(IllegalArgumentException.class,
                () -> OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(pirc, duplicate)))).getMessage().contains("Duplicate"));
        var illegal = Map.of("id", "illegal", "moves", PIRC, "reply", "f8f6");
        check.same("An illegal reply is rejected during configuration loading", true, assertThrows(IllegalArgumentException.class,
                () -> OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(illegal)))).getMessage().contains("Illegal"));
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
