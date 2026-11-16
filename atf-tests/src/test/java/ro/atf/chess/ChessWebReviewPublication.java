package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

/** Audits browser review of an analysis already stored with a finished game. */
@Tag("web-evidence")
class ChessWebReviewPublication {
    private static final Scenario REVIEW = new Scenario("ChessWebReviewPublication",
            "Review both lines and restore the browser bookmark", "game-review", "web-game-review.html",
            "Verify browser interactions and cold reload", List.of(
                    new Frame("history-ply3.png", "Recorded g4 at ply three"),
                    new Frame("recommended-step1.png", "Recommended move from the common replay position"),
                    new Frame("restored-played-mate.png", "Reload restores the played mating reply"),
                    new Frame("returned-history.png", "Closing replay returns to the same archived move")));
    private static final List<String> OBSERVATIONS = List.of("archive-opened", "exact-ply", "board-square", "board-stable",
            "why-expected-mate", "played-root", "played-move", "played-mate", "recommended-root", "recommended-move",
            "reload-bookmark", "return-history", "archive-preserved");

    @Test void publishBrowserGameReview() throws Exception {
        var fixture = configuredPath("chess.web.review.fixture");
        WebEvidencePublication.publish(configuredPath("chess.web.review.receipt"), configuredPath("chess.web.bundle"), REVIEW,
                receipt -> {
                    var fixtureBytes = Files.readAllBytes(fixture);
                    var metadata = WebEvidencePublication.readObject(fixtureBytes, "Archive fixture");
                    var hash = WebEvidencePublication.sha256(fixtureBytes);
                    var checks = new LinkedHashMap<String, JsonNode>();
                    for (String id : OBSERVATIONS) checks.put(id, BooleanNode.TRUE);
                    checks.put("exact-ply", IntNode.valueOf(3));
                    checks.put("played-move", TextNode.valueOf("g2g4"));
                    checks.put("recommended-move", metadata.path("expected").path("recommendedUci"));
                    return new Contract(List.of(new ReportField("Archive fixture SHA-256", hash)),
                            List.of(same("Receipt identifies the archived review", hash, receipt.path("fixtureSha256").asText())),
                            checks);
                });
    }
}
