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
    @Test void replaysThreeBranchesWithExactScoresAndPreservesACompatibleLongerLine(Checks check) {
        var board = ChessPosition.initial();
        var best = line(Ending.HORIZON, "e2e4", "e7e5");
        var alternative = line(Ending.HORIZON, "d2d4", "d7d5");
        var third = line(Ending.HORIZON, "g1f3", "d7d5");
        var fourth = line(Ending.HORIZON, "c2c4", "e7e5");
        var deeper = line(Ending.HORIZON, "e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4",
                "g8f6", "e1g1", "f8e7", "f1e1");
        var differentReply = line(Ending.HORIZON, "d2d4", "g8f6", "c2c4");
        var exact = new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of(best, alternative, third, fourth)), 2, 0);
        var hint = new MatchHint(board, List.of(deeper, differentReply, third, fourth), exact);
        check.same("Hidden or stale recommendations never open a playback", List.of(true, true),
                List.of(hint.playback(board).isEmpty(), hint.playback(board.play(best.move())).isEmpty()));
        hint.revealNext();
        hint.revealNext();
        hint.revealNext();
        var playback = hint.playback(board).orElseThrow();
        check.same("The preview opens the revealed branch and displays at most three legal moves", List.of(third.move(), deeper, alternative, third),
                List.of(playback.selected(), playback.lines().get(0), playback.lines().get(1), playback.lines().get(2)));
        check.same("A fourth branch is not presented", 3, playback.lines().size());
        check.same("The selected alternative is compared with the same exact two-ply recommendation", List.of(2, best, third),
                playback.comparison(board, 2).map(pair -> List.of(pair.depth(), pair.stronger(), pair.alternative())).orElseThrow());
        check.same("The older conflicting reply is absent from the replay", alternative, playback.lines().get(1));

        var replay = HintPreview.open(board, playback.lines().getFirst()).orElseThrow();
        var expected = board;
        for (var move : deeper.line()) {
            replay.next();
            expected = expected.play(move);
            check.same("Replay follows the calculated board after ply " + replay.step(), true,
                    expected.sameForRepetition(replay.position()));
        }
        check.same("The full 11-ply line remains navigable", List.of(11, false), List.of(replay.step(), replay.hasNext()));
        while (replay.hasPrevious()) replay.previous();
        check.same("Rewinding returns to the same root and leaves the revealed move unchanged", List.of(board, third),
                List.of(replay.position(), hint.revealed()));
    }

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
        var frontier = ChessPosition.fromFen("4k3/8/8/3qp3/4R3/8/8/4K3 w - - 0 1");
        var horizon = line(Ending.HORIZON, "e4e5");
        var rookCheck = HintPreview.open(frontier, horizon).orElseThrow();
        check.same("The rook check allows an immediate queen recapture", true,
                frontier.play(horizon.move()).legalMoves().contains(Move.fromUci("d5e5")));
        check.same("A gain the opponent can undo just beyond the recorded line is reported as an estimate", HintPreview.Reason.ESTIMATE,
                rookCheck.evidence().reason());
        check.same("A corrupted reply rejects the whole preview", true,
                HintPreview.open(board, line(Ending.HORIZON, "d2d5", "d5d6")).isEmpty());
        check.same("An empty engine continuation cannot be opened", true, HintPreview.open(board, line(Ending.HORIZON)).isEmpty());
    }

    @Test void extendsTheSicilianCaptureThroughTheBestImmediateDefence(Checks check) {
        var root = ChessMatch.restore("2|analysis|e2e4 c7c5 g1f3 d7d6 d2d4").position();
        var variation = line(Ending.HORIZON, "c5d4", "f3d4", "d8b6", "d4e2", "b6f2");
        var preview = HintPreview.openDefended(root, variation).orElseThrow();
        preview.seek(5);
        int beforeReply = TacticalAnalysis.positionValue(preview.position(), Side.BLACK);
        preview.next();
        check.same("The sixth ply exposes the reply that wins Black's queen", List.of(true, 6, "e1f2", Side.BLACK),
                List.of(preview.addedDefense(), preview.length(), preview.lastMove().uci(), preview.position().turn()));
        var estimate = preview.estimate().orElseThrow();
        check.same("The same Black perspective turns an apparent gain into a loss", List.of(-68, -832, -764, true),
                List.of(estimate.initialCp(), estimate.finalCp(), estimate.changeCp(), beforeReply > estimate.initialCp()));
        check.same("The queen sacrifice carries no material-gain explanation", HintPreview.Reason.ESTIMATE, preview.evidence().reason());
        preview.seek(0);
        check.same("The defended playback rewinds to the original position", root, preview.position());
    }

    @Test void showsPositionalChangeWithoutMaterialGainAndKeepsMateSeparate(Checks check) {
        var start = ChessPosition.initial();
        var development = HintPreview.openDefended(start, line(Ending.HORIZON, "e2e4", "h7h6")).orElseThrow();
        development.seek(2);
        check.same("A quiet line can improve the position without a material change", List.of(0, true, HintPreview.Reason.ESTIMATE),
                List.of(MatchMaterial.balance(development.position(), Side.WHITE), development.estimate().orElseThrow().changeCp() > 0,
                        development.evidence().reason()));
        var root = ChessMatch.restore("2|analysis|f2f3 e7e5 g2g4").position();
        var mate = HintPreview.openDefended(root, line(Ending.CHECKMATE, "d8h4")).orElseThrow();
        check.same("Proven mate carries no pawn-unit delta or appended move", List.of(HintPreview.Reason.CHECKMATE, true, false),
                List.of(mate.evidence().reason(), mate.estimate().isEmpty(), mate.addedDefense()));
    }

    private static Variation line(Ending ending, String... moves) {
        return new Variation(Arrays.stream(moves).map(Move::fromUci).toList(), 0, 0, ending);
    }
}
