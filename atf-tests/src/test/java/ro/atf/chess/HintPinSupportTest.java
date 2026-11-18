package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintPinSupportTest {
    private static final String HISTORY = "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1g5 e7e6 f2f4";

    @Test void theSharedSicilianHintReinforcesTheRelativePinWithoutInventingAScoreGain(Checks check) {
        var game = ChessMatch.restore("3|WHITE|HARD|1|" + HISTORY); var original = game.checkpoint();
        var search = TacticalAnalysis.verifiedHints(game.position(), 200_000);
        check.same("The first suggestion reinforces Nf6 with Nbd7", List.of("b8d7"), search.lines().stream().map(v -> v.move().uci()).toList());
        check.same("The executed seed / comparison / selection and whole budget stay explicit", List.of(3, 5, 5, 200_000),
                List.of(search.seedDepth(), search.comparison().depth(), search.selectedDepth(), search.extraNodes()));
        var compared = search.comparison().result().ranked();
        check.same("Raw H5 centipawns remain tied, not rewritten to an oracle score", List.of(-21, -21), compared.stream().map(Variation::score).toList());
        check.same("The formerly misleading root stays available diagnostically", List.of("b8d7", "b8c6"), compared.stream().map(v -> v.move().uci()).toList());
        var hint = game.hintAt(game.ply()); hint.revealNext();
        check.same("Bulb, comparison and destination use the same calculated move", List.of(move("b8d7"), move("b8d7"), move("b8d7")),
                List.of(hint.revealed().move(), hint.compare(game.position()).orElseThrow().stronger().move(), hint.moveForTap(game.position(), -1, move("b8d7").to())));
        var preview = hint.preview(game.position()).orElseThrow();
        while (preview.hasNext()) preview.next();
        check.same("The preview appends no unscored reply", false, preview.addedDefense());
        check.same("The game history is conserved", original, game.checkpoint());
        var manual = ChessMatch.restore("2|analysis|" + HISTORY);
        check.same("The manual board reuses the exact strongest-hint kernel", hint.first(), manual.hintAt(manual.ply()).first());
        var pair = TacticalAnalysis.compareHints(game.position(), move("b8c6"), move("b8d7")).orElseThrow();
        check.same("A fresh explicit comparison uses the same tie preference", move("b8d7"), pair.stronger().move());
    }

    @Test void thePreferenceNeedsAnExistingPinAndASafeAdditionalDefender(Checks check) {
        var board = ChessMatch.restore("2|analysis|" + HISTORY).position();
        check.same("Nbd7 adds a defender to the knight pinned against Qd8", true, HintPinSupport.reinforces(board, move("b8d7")));
        check.same("Nc6 adds no such defender", false, HintPinSupport.reinforces(board, move("b8c6")));
        for (String fen : List.of(
                "rnb1kb1r/1pq2ppp/p2ppn2/6B1/3NPP2/2N5/PPP3PP/R2QKB1R b KQkq - 0 7",
                "rnbqkb1r/1p3ppp/p2ppn2/1B4B1/3NPP2/2N5/PPP3PP/R2QK2R b KQkq - 0 7",
                "rnbqkb1r/1p3ppp/p3Pn2/6B1/3N1P2/2N5/PPP3PP/R2QKB1R b KQkq - 0 7",
                "1nbqkbr1/1p3p1p/p2ppn2/6B1/3NPP2/2N5/PPP4P/R2Q1RK1 b - - 0 7")) {
            var control = ChessPosition.fromFen(fen);
            check.same("The control move is legal before applying the preference", true, control.legalMoves().contains(move("b8d7")));
            check.same("A moved queen, king-pinned defender, pawn offer or king-pinned attacker invalidates the preference: " + fen,
                    false, HintPinSupport.reinforces(control, move("b8d7")));
        }
    }

    @Test void preferenceCannotOverrideCentipawnsMateOrTheScoredContinuation(Checks check) {
        var board = ChessMatch.restore("2|analysis|" + HISTORY).position();
        var ordinary = line("b8c6", -20); var defensive = line("b8d7", -21);
        var ranked = new ArrayList<>(List.of(defensive, ordinary)); HintPinSupport.sort(board, ranked);
        check.same("Even one centipawn outweighs the nonnumeric preference", List.of(ordinary, defensive), ranked);
        var tied = line("b8c6", -21); ranked = new ArrayList<>(List.of(tied, defensive)); HintPinSupport.sort(board, ranked);
        check.same("An exact tie can prefer the added defender without altering either variation", List.of(defensive, tied), ranked);
        var mate = new Variation(List.of(move("b8c6")), 99_995, 0, Ending.CHECKMATE);
        ranked = new ArrayList<>(List.of(defensive, mate)); HintPinSupport.sort(board, ranked);
        check.same("A proved mating line remains ahead", mate, ranked.getFirst());
        check.same("The strongest hint's governed ceiling is unchanged", 200_000, MatchAiPolicy.CURRENT.hint.search().extraNodes());
    }

    @Test void interruptedAndSmallerSearchesKeepLegalCompletedEvidence(Checks check) {
        var board = ChessMatch.restore("2|analysis|" + HISTORY).position();
        for (int nodes : List.of(1_000, 5_000, 50_000, 100_000, 200_000)) {
            var hint = TacticalAnalysis.verifiedHints(board, nodes);
            check.same("A search within " + nodes + " charges its entire calculation", true, hint.extraNodes() <= nodes);
            for (var variation : hint.lines()) {
                var end = board; for (var action : variation.line()) end = end.play(action);
                check.same("The legal scored PV retains material under " + nodes, variation.materialChange(), HintAuditSession.materialChangeCp(board, end));
            }
        }
    }

    @Test void theGeometricPreferenceHasNoColorOrBoardEdgeBias(Checks check) {
        var board = ChessPosition.fromFen("r2qkb1r/ppp3pp/2n5/3npp2/6b1/P2PPN2/1P3PPP/RNBQKB1R w KQkq - 0 7");
        check.same("The inverse knight move supports the knight pinned to Qd1", true, HintPinSupport.reinforces(board, move("b1d2")));
        check.same("The inverse central move adds no such support", false, HintPinSupport.reinforces(board, move("b1c3")));
        var first = line("b1c3", -21); var next = line("b1d2", -21);
        var ranked = new ArrayList<>(List.of(first, next)); HintPinSupport.sort(board, ranked);
        check.same("Both colors use the same unchanged scores and defensive tie order", List.of(next, first), ranked);
    }

    private static Move move(String uci) { return Move.fromUci(uci); }
    private static Variation line(String uci, int score) { return new Variation(List.of(move(uci)), score, 0, Ending.HORIZON); }
}
