package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.GameReviewSession.Action;
import ro.atf.chess.GameReviewSession.Outcome;
import ro.atf.chess.GameReviewTimeline.Branch;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewGraphParityTest {
    @Test void longGameTouchesSelectTheNativeRecordedPositionAndReviewRow(Checks check) throws Exception {
        var fixture = new GameReviewGraphFixture();
        var match = fixture.match();
        var review = fixture.review();
        var session = new GameReviewSession(match, review, null);
        check.same("The graph begins at the initial White score", 500, review.initialWhitePoints());
        check.same("The plotted penultimate point has the recorded native spike", 750, review.turns().get(183).whitePoints());
        check.same("Mapping the native touch without the inset would select the wrong move", 183,
                (int) Math.round(980.7027 / 992 * match.ply()));
        for (var geometry : List.of(new Plot(992, 6), new Plot(358, 2), new Plot(216, 2))) {
            for (var sample : fixture.samples()) {
                int expectedPly = sample.path("ply").intValue();
                double x = GameReviewGraphLayout.xForPly(expectedPly, match.ply(), geometry.width(), geometry.inset());
                int selected = GameReviewGraphLayout.plyAtX(x, match.ply(), geometry.width(), geometry.inset());
                String context = geometry.width() + "px, ply " + expectedPly;
                var expectedPosition = ChessPosition.fromFen(sample.path("fen").textValue());
                var outcome = session.dispatch(session.revision(), Action.SEEK, selected);
                check.same(context + " selects the intended history cursor", List.of(Outcome.APPLIED, expectedPly, Branch.HISTORY),
                        List.of(outcome, session.snapshot().ply(), session.snapshot().branch()));
                check.same(context + " displays the same position as the APK fixture", true,
                        expectedPosition.sameForRepetition(session.position()));
                String visibleMove = session.lastMove() == null ? "" : session.lastMove().uci();
                String row = session.timeline().turn().map(turn -> turn.played().uci()).orElse("");
                check.same(context + " selects the same move on the board and list", List.of(sample.path("lastMove").textValue(), sample.path("lastMove").textValue()),
                        List.of(visibleMove, row));
            }
        }
    }

    @Test void nativeStauntonArtworkKeepsAllOriginalContoursAndDetails(Checks check) throws Exception {
        var paths = new StringJoiner("\n");
        for (Kind kind : Kind.values()) {
            var art = ChessPieceArtwork.forKind(kind);
            paths.add(kind.name() + "|" + art.body() + "|" + art.details());
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(paths.toString().getBytes(StandardCharsets.UTF_8)));
        check.same("All six 48-unit vector path pairs match the APK before Web adaptation",
                "f5d3676a4339811a63fadb0ac3db877f561de16804d8a2b11e707cbafcfe28ea", digest);
    }

    @Test void playbackAndRatedReviewKeepTheSameAnalysisCurve(Checks check) {
        var match = ChessMatch.restore("3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4");
        var study = GameReview.analyzeLine(match, ignored -> { });
        var rated = GameReview.analyze(match, ignored -> { });
        check.same("The same recorded game produces the same evaluations and moves", study.turns(), rated.turns());
        check.same("The rated archive retains the APK's AI reference", true, rated.white().rating().isPresent());
        check.same("An independent playback carries no inferred rating", true, study.white().rating().isEmpty());
    }

    @Test void browserEvidenceRequiresTheDimensionsDeclaredForEachImage(Checks check) throws Exception {
        var compact = new WebEvidencePublication.Frame("compact.png", "Compact");
        var tablet = new WebEvidencePublication.Frame("tablet.png", "Tablet", 1024, 768);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(compact.width(), compact.height(), BufferedImage.TYPE_INT_RGB), "png", output);
            byte[] embedded = output.toByteArray();
            check.same("The default compact dimensions are still accepted", true, WebEvidencePublication.validPng(embedded, compact));
            check.same("Substituting the compact capture for the tablet frame fails", false, WebEvidencePublication.validPng(embedded, tablet));
        }
    }

    private record Plot(int width, int inset) { }
}
