package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchAnalysisParityTest {
    record Case(String id, Side humanSide, Level level, Boolean analysis, String moves, String checkpoint, int displayedPly,
                Side sideToMove, Result result, String ending, String recommendation, boolean compareManualBoard, HistoryReplay historyReplay) {
        boolean enabled() { return Boolean.TRUE.equals(analysis); }
    }

    record HistoryReplay(String reply, String resumeRecommendation) { }

    record Frame(String ending, Integer centipawns, Integer mate, Double whiteShare, int hintCount,
                 String recommendation, int previewLength, Side previewSide, boolean mateAfterFirstMove) {
        String audit() {
            return ending + "/" + centipawns + "/" + mate + "/" + whiteShare + "/" + hintCount + "/" + recommendation
                    + "/" + previewLength + "/" + previewSide + "/" + mateAfterFirstMove;
        }
    }

    static Case[] fixture() throws IOException {
        try (var source = MatchAnalysisParityTest.class.getResourceAsStream("/match-analysis-cases.json")) {
            return new ObjectMapper().readValue(source, Case[].class);
        }
    }

    @Test void nativeModelProjectsTheDisplayedMatchFrame(Checks check) throws IOException {
        for (var example : fixture()) {
            var setup = new MatchSetup(example.humanSide(), example.level(), example.enabled());
            var match = ChessMatch.start(setup);
            for (String move : example.moves().split(" ")) match = match.play(Move.fromUci(move));

            var preferences = new MemoryPreferences();
            var store = new MatchStore(preferences, () -> 1L, () -> "00000000-0000-4000-8000-000000000001");
            check.same(example.id() + " saves the match with its setup", true,
                    store.startMatch(1, match, example.humanSide() == Side.BLACK, false, false));
            var reopened = new MatchStore(preferences, () -> 1L, () -> "00000000-0000-4000-8000-000000000002");
            var restored = reopened.readMatch(1);
            check.same(example.id() + " restores the setup, checkpoint, result and side at the displayed ply",
                    List.of(setup, example.checkpoint(), example.result(), example.sideToMove()),
                    List.of(restored.setup(), restored.checkpoint(), restored.result(), restored.positionAt(example.displayedPly()).turn()));

            var frame = frame(restored, example.displayedPly());
            check.same(example.id() + " exposes the expected evaluation ending", example.ending(), frame.ending());
            check.same(example.id() + " starts the preview with the independently verified recommendation",
                    example.recommendation(), frame.recommendation());
            if (frame.ending() == null) {
                check.same(example.id() + " has no evaluation share or hint when analysis is disabled",
                        new Frame(null, null, null, null, 0, null, 0, null, false), frame);
            } else if ("HORIZON".equals(frame.ending())) {
                check.same(example.id() + " retains a small White advantage",
                        true, frame.centipawns() > 0 && frame.centipawns() < 70 && frame.whiteShare() > .5);
            } else {
                check.same(example.id() + " fills the Black side of the evaluation bar", 0.0, frame.whiteShare());
                if ("CHECKMATE".equals(frame.ending())) {
                    check.same(example.id() + " proves a Black mate in one from the historical frame",
                            List.of(-1, 1, Side.WHITE, true),
                            List.of(frame.mate(), frame.previewLength(), frame.previewSide(), frame.mateAfterFirstMove()));
                }
            }
            if (example.compareManualBoard()) {
                var study = new AnalysisLine(restored, example.displayedPly());
                check.same(example.id() + " shares the meter and engine recommendation with the manual board",
                        frame, frame(study.position(), study.hint(), study.result()));
            }
            if (example.historyReplay() != null) verifyActiveHistoryReplay(check, example, restored, reopened, frame);
            check.same(example.id() + " leaves the stored game unchanged", example.checkpoint(), reopened.readMatch(1).checkpoint());
        }
    }

    private static void verifyActiveHistoryReplay(Checks check, Case example, ChessMatch restored, MatchStore reopened, Frame historical) {
        var position = restored.positionAt(example.displayedPly());
        var hint = restored.hintAt(example.displayedPly());
        var preview = suggestedLine(position, hint);
        check.same("History replay starts from the exact position displayed after e4", position, preview.position());
        preview.next();
        var firstBoard = preview.position();
        check.same("The first replay step is the displayed historical recommendation", example.recommendation(), preview.lastMove().uci());
        preview.next();
        check.same("The second replay step explains the opponent response", example.historyReplay().reply(), preview.lastMove().uci());
        preview.previous();
        check.same("Previous restores the exact first replay board", firstBoard, preview.position());
        preview.previous();
        check.same("Returning to the replay start keeps the saved game's history board", position, preview.position());

        var resumed = reopened.readMatch(1);
        var live = frame(resumed, resumed.ply());
        check.same("Resuming the stored game starts from its latest move", 2, resumed.ply());
        check.same("The resumed position has its own recommendation", example.historyReplay().resumeRecommendation(), live.recommendation());
        check.same("The resumed position uses its own evaluation share", true, !historical.whiteShare().equals(live.whiteShare()));
    }

    static Frame frame(ChessMatch match, int ply) {
        return frame(match.positionAt(ply), match.hintAt(ply),
                match.analysisEnabled() && ply == match.ply() ? match.result() : Result.IN_PROGRESS);
    }

    private static Frame frame(ChessPosition position, MatchHint hint, Result result) {
        var evaluation = hint.evaluation(position);
        if (evaluation == null) {
            boolean finished = result != Result.IN_PROGRESS;
            return new Frame(finished ? "FINISHED" : null, null, null,
                    finished ? MatchEvaluation.terminalWhiteShare(result) : null, hint.count(), null, 0, null, false);
        }
        var preview = suggestedLine(position, hint);
        preview.next();
        return new Frame(evaluation.ending().name(), evaluation.whiteCentipawns(), evaluation.whiteMateMoves(), evaluation.whiteShare(),
                hint.count(), preview.lastMove().uci(), preview.length(), preview.position().turn(), preview.position().isCheckmate());
    }

    private static HintPreview suggestedLine(ChessPosition position, MatchHint hint) {
        hint.revealNext();
        var move = hint.revealedMove();
        var playback = hint.playback(position).orElseThrow();
        var line = playback.lines().stream().filter(branch -> branch.move().equals(move)).findFirst().orElseThrow();
        return HintPreview.openDefended(position, line).orElseThrow();
    }
}
