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
        var hint = game.hintAt(game.ply());
        check.same("A hidden hint cannot start a comparison", true, hint.compare(start).isEmpty());
        hint.revealNext();
        var pair = hint.compare(start).orElseThrow();
        check.same("The verified leader supplies the bulb and the dialog", hint.revealed(), pair.stronger());
        check.same("Bc4 stays available in the comparison outside the bulb's score window", List.of(1, move("f1c4")),
                List.of(hint.count(), pair.alternative().move()));
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
        var first = hint.revealed().move();
        check.same("The same first move cannot be compared to itself", true, TacticalAnalysis.compareHints(start, first, first).isEmpty());
    }

    @Test void usesOneVerifiedOrderForTheBulbDialogAndTapInThreeOpenings(Checks check) {
        assertOpening(check, "Sicilian, White", "e2e4 c7c5", "b1c3", "f1c4", 5);
        assertOpening(check, "Sicilian, Black", "e2e4 c7c5 g1f3 d7d6 d2d4", "c5d4", "b8d7", 5);
        assertOpening(check, "Caro–Kann, White", "e2e4 c7c6", "b1c3", "f1c4", 5);
        assertOpening(check, "Caro–Kann, Black", "e2e4 c7c6 d2d4", "g8f6", "d7d6", 4);
        assertOpening(check, "Catalan, White", "d2d4 g8f6 c2c4 e7e6 g2g3 d7d5", "b1d2", "d1a4", 5);
        assertOpening(check, "Catalan, Black", "d2d4 g8f6 c2c4 e7e6 g2g3 d7d5 f1g2", "d5c4", "f8b4", 5);
    }

    @Test void keepsTheLeaderWhenCyclingAndRejectsAStaleHistoricalFrame(Checks check) {
        String checkpoint = "2|analysis|e2e4 e7e5";
        var game = ChessMatch.restore(checkpoint);
        var position = game.positionAt(1);
        var hint = game.hintAt(1);
        hint.revealNext();
        var first = hint.revealed();
        hint.revealNext();
        var second = hint.revealed();
        var pair = hint.compare(position).orElseThrow();
        check.same("Cycling selects the displayed alternative while the best label keeps its verified leader", List.of(first, second),
                List.of(pair.stronger(), pair.alternative()));
        var replay = hint.preview(position).orElseThrow();
        replay.next();
        check.same("The preview and destination tap use the selected alternative", List.of(second.move(), second.move()),
                List.of(replay.lastMove(), hint.moveForTap(position, -1, second.move().to())));
        var reloaded = ChessMatch.restore(checkpoint).positionAt(1);
        check.same("The candidate is legal on a different frame object with the same board", true, reloaded.legalMoves().contains(second.move()));
        check.same("A stale cursor cannot play even a legal move after a frame change", null,
                hint.moveForTap(reloaded, -1, second.move().to()));
        check.same("A different frame cannot open a stale comparison", true, hint.compare(game.positionAt(0)).isEmpty());
        check.same("Returning to history preserves the checkpoint and selected variant", List.of(checkpoint, second),
                List.of(game.checkpoint(), hint.revealed()));
    }

    @Test void keepsADeeperRepertoireDecisionWhenTheComparisonCannotReachItsHorizon(Checks check) {
        var position = ChessMatch.restore("2|analysis|e2e4 c7c5 g1f3 e7e5").position();
        var hint = MatchHint.analyze(position);
        hint.revealNext();
        check.same("The deeper Sicilian repertoire line keeps its pawn capture", move("f3e5"), hint.revealed().move());
        check.same("A shallower reversal is not published as a competing best line", true, hint.compare(position).isEmpty());
        var replay = hint.preview(position).orElseThrow();
        replay.next();
        check.same("Playback and tapping still use the proved line's first move", List.of(move("f3e5"), move("f3e5")),
                List.of(replay.lastMove(), hint.moveForTap(position, -1, BoardSquare.parse("e5"))));
    }

    @Test void rejectsUnconfirmedReversalsInRetiAndAlekhine(Checks check) {
        assertRetainedHint(check, "Réti", "g1f3 d7d5 c2c4 d5d4", "d2d3");
        assertRetainedHint(check, "Alekhine", "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6", "c2c4");
    }

    private static void assertRetainedHint(Checks check, String name, String moves, String retained) {
        var position = ChessMatch.restore("2|analysis|" + moves).position();
        var hint = MatchHint.analyze(position);
        hint.revealNext();
        var move = move(retained);
        check.same(name + " retains the supported recommendation in the bulb and on the board", List.of(move, move),
                List.of(hint.revealed().move(), hint.moveForTap(position, -1, move.to())));
        check.same(name + " withholds the comparison when it cannot support the reversal", true, hint.compare(position).isEmpty());
    }

    private static void assertOpening(Checks check, String name, String moves, String best, String other, int depth) {
        var game = ChessMatch.restore("2|analysis|" + moves);
        var position = game.position();
        var hint = game.hintAt(game.ply());
        hint.revealNext();
        var pair = hint.compare(position).orElseThrow();
        var preview = hint.preview(position).orElseThrow();
        preview.next();
        var expected = move(best);
        check.same(name + " shares the best move across bulb, dialog, preview and destination tap", List.of(expected, expected, expected, expected),
                List.of(hint.revealed().move(), pair.stronger().move(), preview.lastMove(), hint.moveForTap(position, -1, expected.to())));
        check.same(name + " records the alternative and common horizon", List.of(other, depth, depth, depth),
                List.of(pair.alternative().move().uci(), pair.depth(), pair.stronger().line().size(), pair.alternative().line().size()));
        var search = TacticalAnalysis.verifiedHints(position);
        int limit = MatchAiPolicy.CURRENT.hint.search().extraNodes();
        check.same(name + " reuses the verified line and consumes " + search.extraNodes() + " of " + limit + " extra nodes",
                List.of(pair.stronger(), true), List.of(search.lines().getFirst(), search.extraNodes() <= limit));
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
