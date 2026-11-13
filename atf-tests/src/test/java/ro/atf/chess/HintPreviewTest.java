package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintPreviewTest {
    @Test void followsTheEngineReplyAndReturnsToTheExactHistoricalPly(Checks check) {
        var saved = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6");
        var study = new AnalysisLine(saved, 1);
        var root = study.position();
        var hint = new MatchHint(saved.bestLinesAt(study.ply()));
        check.same("The preview stays unavailable until the bulb reveals a line", true, hint.preview(root).isEmpty());
        hint.revealNext();
        var selected = hint.revealed();
        var preview = hint.preview(root).orElseThrow();
        check.same("Step zero shows the selected Black position", List.of(0, Side.BLACK, false),
                List.of(preview.step(), preview.position().turn(), preview.hasPrevious()));
        preview.previous();
        preview.next();
        check.same("Next applies the recommendation on the preview board", true, root.play(selected.move()).sameForRepetition(preview.position()));
        preview.next();
        check.same("A second Next plays the engine's reply, not the recorded game move", selected.line().get(1), preview.lastMove());
        check.same("Both plies are displayed on their real resulting board", true,
                root.play(selected.move()).play(selected.line().get(1)).sameForRepetition(preview.position()));
        preview.previous();
        preview.previous();
        check.same("Back returns to the exact position and resets move highlighting", root, preview.position());
        check.same("Step zero carries no played move", null, preview.lastMove());
        check.same("Closing the preview preserves the study cursor, saved suffix and currently revealed hint",
                List.of(1, 4, selected), List.of(study.ply(), saved.ply(), hint.revealed()));
        for (int step = 0; step <= selected.line().size(); step++) preview.next();
        check.same("Next stops at the final engine ply", List.of(selected.line().size(), false), List.of(preview.step(), preview.hasNext()));
    }

    @Test void namesTheMatingPieceOnlyWhenTheLineReallyEndsInMate(Checks check) {
        var mate = ChessMatch.restore("2|analysis|f2f3 e7e5 g2g4");
        var top = mate.bestLinesAt(3).getFirst();
        var preview = HintPreview.open(mate.position(), top).orElseThrow();
        check.same("The preview names the black queen and h4 as the proven mate", List.of(HintPreview.Reason.CHECKMATE, Kind.QUEEN, "d8h4"),
                List.of(preview.evidence().reason(), preview.evidence().piece(), preview.evidence().move().uci()));
        preview.next();
        check.same("The displayed end position is checkmate", true, preview.position().isCheckmate());
        var promotion = ChessPosition.fromFen("8/P7/7k/8/8/8/8/4K3 w - - 0 1");
        var promoted = HintPreview.open(promotion, line(Ending.CHECKMATE, "a7a8q")).orElseThrow();
        check.same("A false mate marker cannot produce a mating explanation", HintPreview.Reason.ESTIMATE, promoted.evidence().reason());
    }

    @Test void distinguishesAConcreteCaptureFromACompensatedExchangeAndAnInvalidLine(Checks check) {
        var board = ChessPosition.fromFen("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1");
        var capture = HintPreview.open(board, line(Ending.HORIZON, "d2d5", "e8e7")).orElseThrow().evidence();
        check.same("The replay names the captured queen and the retained material", List.of(HintPreview.Reason.CAPTURE, Kind.QUEEN, 9),
                List.of(capture.reason(), capture.captured(), capture.netMaterial()));
        var exchanged = ChessMatch.restore("2|analysis|e2e4 d7d5");
        var proof = HintPreview.open(exchanged.position(), line(Ending.HORIZON, "e4d5", "d8d5")).orElseThrow().evidence();
        check.same("An immediately compensated capture is explained by the estimate", HintPreview.Reason.ESTIMATE, proof.reason());
        check.same("A corrupted reply rejects the whole preview", true,
                HintPreview.open(board, line(Ending.HORIZON, "d2d5", "d5d6")).isEmpty());
        check.same("An empty engine continuation cannot be opened", true, HintPreview.open(board, line(Ending.HORIZON)).isEmpty());
    }

    private static Variation line(Ending ending, String... moves) {
        return new Variation(Arrays.stream(moves).map(Move::fromUci).toList(), 0, 0, ending);
    }
}
