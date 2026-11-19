package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintReplayPresentationTest {
    private record Viewport(String name, int width, int height, int controls, int reportHeight, GameReviewLayout expected) { }

    @Test void keepsTheActualReplayBoardFixedAcrossLongLinesAndEnlargedControls(Checks check) {
        var profiles = List.of(
                new Viewport("compact portrait, normal text", 360, 688, 104, 224, new GameReviewLayout(false, 360, 360)),
                new Viewport("compact portrait, 130% text", 320, 420, 120, 100, new GameReviewLayout(false, 200, 320)),
                new Viewport("large portrait, 130% text", 600, 960, 120, 240, new GameReviewLayout(false, 600, 600)),
                new Viewport("wide landscape, 130% text", 720, 340, 120, 340, new GameReviewLayout(true, 220, 240)),
                new Viewport("short landscape, 130% text", 480, 340, 120, 73, new GameReviewLayout(false, 147, 480)));
        var root = ChessPosition.initial();
        var line = variation("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6",
                "b5a4", "g8f6", "e1g1", "f8e7", "f1e1", "b7b5");
        for (var profile : profiles) {
            var preview = HintPreview.openDefended(root, line).orElseThrow();
            for (int ply = 0; ply <= preview.length(); ply++) {
                preview.seek(ply);
                var layout = GameReviewLayout.fit(profile.width(), profile.height(), profile.controls(), 240, 132, 248, 8);
                check.same(profile.name() + " retains its board at replay ply " + ply, profile.expected(), layout);
                int reportHeight = layout.sideBySide() ? profile.height()
                        : profile.height() - layout.boardSide() - profile.controls();
                check.same(profile.name() + " preserves its report viewport at replay ply " + ply, profile.reportHeight(), reportHeight);
                check.same(profile.name() + " keeps the fixed controls in bounds at replay ply " + ply, true,
                        layout.boardSide() + profile.controls() <= profile.height());
            }
        }
        check.same("The replay preserves all twelve plies", 12, line.line().size());
    }

    @Test void sharedHintPreviewsKeepIndependentBranchCursorsAndTheOriginalGame(Checks check) {
        var match = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6");
        var root = match.positionAt(0);
        String original = match.checkpoint();
        var lines = List.of(variation("e2e4", "e7e5"), variation("d2d4", "d7d5"),
                variation("g1f3", "d7d5", "d2d4", "g8f6"));
        var previews = lines.stream().map(line -> HintPreview.openDefended(root, line).orElseThrow()).toList();
        previews.get(0).seek(2);
        previews.get(1).seek(1);
        previews.get(2).seek(3);
        check.same("Returning to each of the three lines restores its own cursor", List.of(2, 1, 3),
                previews.stream().map(HintPreview::step).toList());
        for (int branch = 0; branch < lines.size(); branch++) {
            var replay = previews.get(branch);
            var expected = root;
            for (int ply = 0; ply < replay.step(); ply++) expected = expected.play(lines.get(branch).line().get(ply));
            check.same("Line " + (branch + 1) + " displays the exact position for its retained cursor", true,
                    expected.sameForRepetition(replay.position()));
            while (replay.hasPrevious()) replay.previous();
            check.same("Line " + (branch + 1) + " returns to the source position", root, replay.position());
            check.same("Line " + (branch + 1) + " clears the last move at step zero", null, replay.lastMove());
        }
        check.same("Exploring and rewinding all branches leaves the game checkpoint intact", original, match.checkpoint());
    }

    @Test void calculatedAndTheoryHintsShareTheFullscreenNativeAdapter(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        String calculated = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/HintPreviewDialog.java"));
        String surface = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/AnalysisReplayDialog.java"));
        String workspace = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/GameReviewWorkspace.java"));
        String worker = Files.readString(root.resolve("web/src/main/java/ro/atf/chess/WebHintReplay.java"));
        check.same("Calculated and theory hints select their mode in the same native surface", true,
                calculated.contains("AnalysisReplayDialog.Mode.CALCULATED") && calculated.contains("AnalysisReplayDialog.Mode.THEORY"));
        check.same("The native adapter uses the same defended replay model as the Web worker", true,
                calculated.contains("HintPreview.openDefended(root, line)") && worker.contains("HintPreview.openDefended(root, line)"));
        check.same("Calculated hints use the measured fullscreen workspace with fixed controls", true,
                surface.contains("new GameReviewWorkspace(context, board, controls, scroll, sourceBoardSide)")
                        && workspace.contains("ReplayBoardLayout.fit(") && workspace.contains("GameReviewLayout.fit("));
        check.same("The replay has a header return action and stable two-line move caption", true,
                surface.contains("R.id.hint_preview_return") && surface.contains("BoardCaption.reserve(step, 2)"));
        check.same("The calculated adapter has no independent alert or screen-fraction sizing policy", false,
                calculated.contains("AlertDialog") || calculated.contains("screenHeightDp"));
        check.same("Score depth and decisive continuation remain available through the shared surface", true,
                calculated.contains("workspace.setSummary(") && calculated.contains("workspace.setCaptionDescription(")
                        && calculated.contains("workspace.setReasonAction("));
    }

    private static Variation variation(String... moves) {
        return new Variation(Arrays.stream(moves).map(Move::fromUci).toList(), 0, 0, Ending.HORIZON);
    }
}
