package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintCreditRegressionTest {
    @Test void historicalCreditCannotReintroduceTheRefutedItalianBishopOffer(Checks check) {
        var match = ChessMatch.restore("3|WHITE|HARD|1|e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b1c3 d8f6 c3d5 f6d6 d5c3");
        var original = match.checkpoint();
        var search = TacticalAnalysis.verifiedHints(match.position(), 200_000);
        check.same("The independent H17/H19 defense replaces the false bishop compensation", Move.fromUci("d6g6"), search.lines().getFirst().move());
        check.same("Neither the refuted offer nor the weaker historical knight move is offered as equivalent", List.of("d6g6"),
                search.lines().stream().map(line -> line.move().uci()).toList());
        check.same("The historical challenger remains diagnostic evidence at one completed comparison horizon", true,
                search.comparison() != null && search.comparison().depth() == search.selectedDepth()
                        && search.comparison().result().ranked().stream().anyMatch(line -> line.move().uci().equals("g8e7")));
        check.same("The whole decision stays within the unchanged strongest-hint ceiling", true,
                search.extraNodes() > 0 && search.extraNodes() <= 200_000);
        var publicHint = MatchHint.analyze(match.position());
        check.same("The bulb uses the same qualified first move", search.lines().getFirst(), publicHint.first());
        var replay = HintPreview.openDefended(match.position(), publicHint.first()).orElseThrow();
        check.same("The scored line is replayed without appending another defense", List.of(publicHint.first().line().size(), false),
                List.of(replay.length(), replay.addedDefense()));
        check.same("Analysing the defense preserves the entire saved game", original, match.checkpoint());
    }

    @Test void theSameBishopMoveRemainsAvailableWhenItsTraxlerContextIsSound(Checks check) {
        var board = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 f8c5 g5f7").position();
        var search = TacticalAnalysis.verifiedHints(board, 200_000);
        check.same("Stockfish H17/H19 keeps the bishop offer in the distinct Traxler context", Move.fromUci("c5f2"), search.lines().getFirst().move());
        check.same("A non-mating sacrifice is not rejected merely for losing material", true,
                search.lines().getFirst().ending() == TacticalAnalysis.Ending.HORIZON
                        && search.lines().getFirst().materialChange() < 0);
        check.same("The sound-sacrifice control keeps the original ceiling", true, search.extraNodes() <= 200_000);
    }

    @Test void theItalianDefenseAlsoSurvivesTheColorInvertedPosition(Checks check) {
        var board = ChessPosition.fromFen("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NQ4/PPPP1PPP/R1B1K1NR w KQkq - 9 6");
        var search = TacticalAnalysis.verifiedHints(board, 200_000);
        check.same("The raw-evidence rule is independent of piece color", List.of("d3g3"),
                search.lines().stream().map(line -> line.move().uci()).toList());
        check.same("The color inversion retains the same node ceiling", true, search.extraNodes() <= 200_000);
    }

    @Test void anImmediateBalancedExchangeIsNotMistakenForAnUnbackedSacrifice(Checks check) {
        var board = ChessMatch.restore("2|analysis|e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f1b5 c8d7 f3d4").position();
        var search = TacticalAnalysis.verifiedHints(board, 100_000);
        check.same("The independently checked Sicilian exchange remains first", Move.fromUci("d7b5"), search.lines().getFirst().move());
        check.same("The capture and its legal recapture have no net offered material", 0,
                HintAuditSession.materialChangeCp(board, board.play(Move.fromUci("d7b5")).play(Move.fromUci("d4b5"))));
        check.same("The balanced exchange still earns a completed deeper comparison", true,
                search.comparison() != null && search.selectedDepth() > search.seedDepth());
        check.same("The counterexample cannot receive additional nodes", true, search.extraNodes() <= 100_000);
    }

    @Test void fallbackScoresDoNotExposeTheOlderOrderingCreditAsAnExactEvaluation(Checks check) {
        var board = ChessMatch.restore("2|analysis|d2d4 g8f6 c2c4 e7e6 g2g3 d7d5").position();
        var search = TacticalAnalysis.verifiedHints(board, 200_000);
        check.same("An unsupported reversal retains the independently checked Catalan capture", Move.fromUci("c4d5"), search.lines().getFirst().move());
        check.same("The rejected comparison is not presented as accepted evidence", null, search.comparison());
        check.same("The retained search reports its actual seed horizon", search.seedDepth(), search.selectedDepth());
        for (var line : search.lines()) {
            var end = board;
            for (var move : line.line()) end = end.play(move);
            check.same("The scored PV endpoint excludes any historical ordering credit", TacticalAnalysis.positionValue(end, board.turn()), line.score());
        }
    }
}
