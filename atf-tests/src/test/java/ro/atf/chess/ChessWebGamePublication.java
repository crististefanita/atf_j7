package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebGamePublication {
    private static final String INITIAL = "1|";
    private static final String ACTIVE = INITIAL + "f2f3 e7e5 g2g4";
    private static final String COMPLETED = ACTIVE + " d8h4";
    private static final String SCOPE = "active-game-review";
    private static final Scenario GAME = new Scenario("ChessWebGamePublication",
            "Play, reload and analyze a newly recorded browser game", SCOPE, "web-active-game.html",
            "Verify the active game and freshly generated analysis", List.of(
                    new Frame("active-after-ply3.png", "Active game after g4"),
                    new Frame("active-reloaded.png", "Cold reload restores the active game"),
                    new Frame("fresh-review.png", "Review generated from the newly played game"),
                    new Frame("fresh-review-why.png", "Why g4 permits the queen's mating reply")));
    private static final List<String> OBSERVATIONS = List.of("new-match", "first-move", "recorded-line", "reloaded-active",
            "continued-move", "analysis-archive", "analysis-opened", "analysis-why", "history-restored",
            "analysis-canceled", "unfinished-analysis");

    @Test void publishBrowserActiveGame() throws Exception {
        WebEvidencePublication.publish(configuredPath("chess.web.game.receipt"), configuredPath("chess.web.bundle"), GAME,
                receipt -> {
                    var initial = receipt.path("initialCheckpoint").asText();
                    var resumed = receipt.path("resumedCheckpoint").asText();
                    var completed = receipt.path("recordedCheckpoint").asText();
                    var checks = new LinkedHashMap<String, JsonNode>();
                    for (String id : OBSERVATIONS) checks.put(id, BooleanNode.TRUE);
                    checks.put("first-move", TextNode.valueOf("f2f3"));
                    checks.put("continued-move", TextNode.valueOf("d8h4"));
                    var inputs = new ArrayList<>(List.of(
                            same("Evidence belongs to the active-game flow", SCOPE, receipt.path("scope").asText()),
                            same("A new game begins with an empty move checkpoint", INITIAL, initial),
                            same("Reload restores the recorded active moves in order", ACTIVE, resumed),
                            same("Generated analysis uses the exact completed game", COMPLETED, completed)));
                    verifyUnfinished(receipt.path("unfinishedReview").asText(), inputs);
                    verifyCompleted(receipt.path("generatedReview").asText(), inputs);
                    return new Contract(List.of(new ReportField("Initial checkpoint", initial),
                            new ReportField("Resumed checkpoint", resumed), new ReportField("Analyzed checkpoint", completed)), inputs, checks);
                });
    }

    private static void verifyUnfinished(String encoded, List<AtfAssertion> checks) {
        String title = "The browser-generated unfinished analysis decodes against three plies and has no rating";
        var expected = List.of(3, true, true);
        var match = ChessMatch.restore(ACTIVE);
        try {
            var review = GameReviewCodec.decodeLine(match, encoded, GameReviewCodec.checkpointDigest(match));
            checks.add(same(title, expected, List.of(review.turns().size(), review.white().rating().isEmpty(),
                    review.black().rating().isEmpty())));
            compareWithJvm("Unfinished game", match, review, checks);
        } catch (IllegalArgumentException invalid) {
            checks.add(same(title, expected, invalid.getMessage()));
        }
    }

    private static void verifyCompleted(String encoded, List<AtfAssertion> checks) {
        String title = "The browser-generated archive decodes against all four plies and proves the mating reply";
        var expected = List.of(4, GameReviewExplanation.Reason.ALLOWS_MATE, "d8h4");
        var match = ChessMatch.restore(COMPLETED);
        try {
            var review = GameReviewCodec.decode(match, encoded);
            var timeline = new GameReviewTimeline(match, review);
            timeline.seek(3);
            var observed = timeline.explanation().orElseThrow();
            String mate = observed.evidence().map(evidence -> evidence.move().uci()).orElse("missing");
            checks.add(same(title, expected, List.of(review.turns().size(), observed.reason(), mate)));
            compareWithJvm("Completed game", match, review, checks);
        } catch (IllegalArgumentException invalid) {
            checks.add(same(title, expected, invalid.getMessage()));
        }
    }

    private static void compareWithJvm(String label, ChessMatch match, GameReview browser, List<AtfAssertion> checks) {
        var jvm = GameReview.analyzeLine(match, ignored -> { });
        checks.add(same(label + " has equal analysis on JVM and TeaVM", true, jvm.equals(browser)));
        for (int index = 0; index < jvm.turns().size(); index++) {
            checks.add(same(label + " ply " + (index + 1) + " grade, scores, depth and lines",
                    turn(jvm.turns().get(index)), turn(browser.turns().get(index))));
        }
    }

    private static String turn(GameReview.Turn turn) {
        var played = turn.playedLine().stream().map(ChessPosition.Move::uci).toList();
        var best = turn.bestLine().stream().map(ChessPosition.Move::uci).toList();
        return turn.played().uci() + " " + turn.grade() + " · played " + turn.playedPoints() + " · best " + turn.bestPoints()
                + " · depth " + turn.depth() + " · played " + played + " · best " + best;
    }
}
