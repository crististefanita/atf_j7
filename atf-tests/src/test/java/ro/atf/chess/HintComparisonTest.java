package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintComparisonTest {
    @Test void verifiesBothRootsAndReturnsTwoPlayableLinesAtTheCompletedHorizon(Checks check) {
        var game = ChessMatch.restore("2|analysis|e2e4 c7c5");
        var start = game.position();
        var hint = new MatchHint(game.bestLinesAt(game.ply()));
        check.same("A hidden hint cannot start a comparison", true, hint.compare(start).isEmpty());
        hint.revealNext();
        var first = hint.revealed().move();
        hint.revealNext();
        var second = hint.revealed().move();
        var pair = hint.compare(start).orElseThrow();
        check.same("The selected alternative and leader both survive verification", true,
                java.util.Set.of(first, second).equals(java.util.Set.of(pair.stronger().move(), pair.alternative().move())));
        check.same("Both complete lines use the same horizon", List.of(pair.depth(), pair.depth()),
                List.of(pair.stronger().line().size(), pair.alternative().line().size()));
        check.same("The recommended label follows the verified score", true, pair.stronger().score() >= pair.alternative().score());
        for (var branch : List.of(pair.stronger(), pair.alternative())) {
            var playback = HintPreview.open(start, branch).orElseThrow();
            playback.next();
            playback.next();
            check.same("The second ply is the reply found for " + branch.move().uci(), branch.line().get(1), playback.lastMove());
            playback.seek(0);
            check.same("Returning to the branch root preserves the source position", start, playback.position());
        }
        check.same("Exploring both lines never writes to the game", "2|analysis|e2e4 c7c5", game.checkpoint());
        check.same("The same first move cannot be compared to itself", true, TacticalAnalysis.compareHints(start, first, first).isEmpty());
    }

    @Test void namesTheMatingReplyInTheInferiorLineAndAllowsJumpingToIt(Checks check) {
        var start = ChessMatch.restore("2|analysis|f2f3 e7e5").position();
        var pair = TacticalAnalysis.compareHints(start, move("b2b3"), move("g2g4")).orElseThrow();
        var reply = pair.decisive().orElseThrow();
        check.same("The alternative permits a black queen mate at its second ply",
                new HintComparison.DecisiveMove(false, 2, move("d8h4"), Kind.QUEEN, HintComparison.Reason.MATE), reply);
        var branch = HintPreview.open(start, pair.alternative()).orElseThrow();
        branch.seek(reply.ply());
        check.same("Jumping to the explanation shows the actual mating board", true, branch.position().isCheckmate());
        branch.previous();
        check.same("Previous shows the move that permitted the reply", move("g2g4"), branch.lastMove());
    }

    @Test void reportsOneUncompensatedCaptureAndLeavesAmbiguousComparisonsAsScores(Checks check) {
        var start = ChessPosition.fromFen("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1");
        var held = variation(50, 0, "d2d1", "d5e6");
        var losesRook = variation(-450, -500, "d2d3", "d5d3");
        var pair = HintComparison.of(start, losesRook, held, 2);
        check.same("Scores can reverse the original display order", held, pair.stronger());
        check.same("The explanation names the actual opposing capture",
                new HintComparison.DecisiveMove(false, 2, move("d5d3"), Kind.ROOK, HintComparison.Reason.CAPTURE),
                pair.decisive().orElseThrow());
        check.same("A score-only difference does not invent a tactical cause", true,
                HintComparison.of(start, held, variation(20, 0, "d2d3", "d5e6"), 2).decisive().isEmpty());
        check.same("A tie never attributes an advantage to a capture", true,
                HintComparison.of(start, held, variation(50, -500, "d2d3", "d5d3"), 2).decisive().isEmpty());
    }

    private static Move move(String uci) { return Move.fromUci(uci); }

    private static Variation variation(int score, int material, String first, String reply) {
        return new Variation(List.of(move(first), move(reply)), score, material, Ending.HORIZON);
    }
}
