package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class RookEndgameHistoryTest {
    private static final String CONTINUATION = "f5f6 g7f6 g1f1 a7a5 a1b1 b7b5 b1b5 e8d8 e2c3 d8c8 c3e2 a5a4 b5f5 c8c2 f2f4 c2a2 e2c1 a2d2 f1e1 d2g2 c1d3 f8g7 f5a5 h8e8";

    @Test void reportsHistoricalRookDecisions(Checks check) throws Exception {
        String prefix = ChessYaml.string(ChessYaml.resource("alekhine-conversion.yaml"), "moves") + " " + CONTINUATION;
        var moves = new ArrayList<>(List.of(prefix.split(" ")));
        var match = ChessMatch.restore("3|BLACK|HARD|0|" + prefix);
        int majorLoss = ChessYaml.number(ChessYaml.resource("match-ai-benchmark.yaml"), "major-loss-permille");
        check.same("Reconstructed history reaches the documented 96-ply position", 96, match.ply());
        String binary = System.getProperty("chess.stockfish");
        try (var engine = binary == null ? null : new StockfishUci(Path.of(binary))) {
            for (int target : List.of(96, 108, 112)) {
                while (match.ply() < target) {
                    var side = match.position().turn();
                    var level = side == ChessPosition.Side.WHITE ? MatchSetup.Level.HARD : MatchSetup.Level.ADVANCED;
                    var reply = match.botDecision(level);
                    moves.add(reply.move().uci());
                    match = match.play(reply.move());
                }
                var side = match.position().turn();
                var level = side == ChessPosition.Side.WHITE ? MatchSetup.Level.HARD : MatchSetup.Level.ADVANCED;
                var decision = match.botDecision(level);
                String position = "startpos moves " + String.join(" ", moves);
                String result = "ROOK_PROBE ply=" + target + " side=" + side + " pick=" + decision.move().uci()
                        + " depth=" + decision.depth() + " nodes=" + decision.extraNodes();
                check.same("Ply " + target + " decision is legal", true, match.position().legalMoves().contains(decision.move()));
                check.same("Ply " + target + " respects configured node budget", true,
                        decision.extraNodes() <= MatchAiPolicy.CURRENT.opponent(level).extraNodes());
                check.same("Ply " + target + " stays within one documented endgame extension", true,
                        decision.depth() <= MatchAiPolicy.CURRENT.opponent(level).maxPlies()
                                + MatchAiPolicy.CURRENT.rookEndgame.extensionPlies());
                if (target == 108) {
                    var atTarget = match;
                    var repeated = atTarget.position().playGenerated(Move.fromUci("a7a8"));
                    long repeatedCount = java.util.stream.IntStream.range(0, atTarget.ply())
                            .filter(index -> repeated.sameForRepetition(atTarget.positionAt(index))).count();
                    var positionOnly = ChessMatch.fromPosition(atTarget.position(), atTarget.setup())
                            .botDecision(MatchSetup.Level.HARD);
                    System.out.println("ROOK_HISTORY ply=108 visited-result=" + repeatedCount + " without-history="
                            + positionOnly.move().uci() + " with-history=" + decision.move().uci());
                    check.same("Ply 108 fixture exposes the history-dependent repeat", 1L, repeatedCount);
                    check.same("Board-only search reproduces the old draw-seeking choice", "a7a8", positionOnly.move().uci());
                    check.same("History-aware Hard rejects the draw line from the repeated root", true,
                            !decision.move().equals(Move.fromUci("a7a8")));
                }
                if (engine != null) {
                    var best = engine.evaluatePosition(position, 13, null);
                    var picked = best.move().equals(decision.move().uci()) ? best
                            : engine.evaluatePosition(position, 13, decision.move().uci());
                    int loss = Math.max(0, best.expected() - picked.expected());
                    result += " stockfish=" + best.move() + "/" + best.score() + " picked=" + picked.score() + " loss=" + loss + "‰";
                    check.same("Stockfish evaluates the reconstructed ply " + target, true,
                            best.score().startsWith("cp ") || best.score().startsWith("mate "));
                    check.same("Ply " + target + " avoids a major expected-score loss " + loss + "‰", true, loss < majorLoss);
                    if (target == 108) {
                        var repeatScore = engine.evaluatePosition(position, 13, "a7a8");
                        var winningScore = engine.evaluatePosition(position, 13, "h1g1");
                        check.same("Stockfish confirms the repeated line gives away the winning position", true,
                                winningScore.expected() - repeatScore.expected() >= 400);
                    }
                }
                System.out.println(result);
                moves.add(decision.move().uci());
                match = match.play(decision.move());
            }
        }
    }
}



