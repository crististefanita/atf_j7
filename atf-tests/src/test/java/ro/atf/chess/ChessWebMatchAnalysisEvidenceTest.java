package ro.atf.chess;

import static ro.atf.chess.ChessWebMatchAnalysisPublication.FIXTURE;
import static ro.atf.chess.ChessWebMatchAnalysisPublication.validateCheckpoint;
import static ro.atf.chess.ChessWebMatchAnalysisPublication.validateIdentity;

import atf.reporting.contract.AtfAssertion;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessWebMatchAnalysisPublication.Identity;
import ro.atf.chess.ChessWebMatchAnalysisPublication.Position;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebMatchAnalysisEvidenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Identity RUN = new Identity("1".repeat(32), "2".repeat(40), "3".repeat(64));
    private static final Position HISTORY = new Position(48, new MatchEvaluation(TacticalAnalysis.Ending.HORIZON, -32, 0), "e7e5");
    private static final Position LIVE = new Position(52, new MatchEvaluation(TacticalAnalysis.Ending.HORIZON, 32, 0), "g1f3");
    private static final List<String> REPLY = List.of("e7e5", "g1f3", "e7e5");

    @Test void requiresBothLanguagesToShareTheRuntimeAndOrderedRunIdentity(Checks check) throws IOException {
        var receipt = (ObjectNode) JSON.readTree("""
                {"locale":"ro","url":"http://127.0.0.1:8080/chess/#ai",
                 "viewport":{"width":360,"height":640,"baseRootFontPx":16,"rootFontPx":20.8,"deviceScaleFactor":1},
                 "checkpoints":[{"id":"history"},{"id":"preview"},{"id":"returned"},{"id":"resumed"}]}
                """);
        receipt.put("runId", RUN.runId()).put("sourceRevision", RUN.revision()).put("runtimeDigest", RUN.runtimeDigest());
        check.same("The full identity and timeline pass", 0L, failures(validateIdentity(receipt, RUN, "ro")));
        receipt.put("runId", "4".repeat(32));
        check.same("Rejects an otherwise valid receipt from another run", 1L, failures(validateIdentity(receipt, RUN, "ro")));
        receipt.put("runId", RUN.runId()).put("runtimeDigest", "5".repeat(64));
        ((ObjectNode) receipt.path("checkpoints").get(3)).put("id", "history");
        check.same("Rejects the wrong runtime and a duplicated checkpoint", 2L, failures(validateIdentity(receipt, RUN, "ro")));
    }

    @Test void rejectsTheWrongPositionAndMovementThatWouldStretchTheReplay(Checks check) throws IOException {
        var historical = (ObjectNode) JSON.readTree("""
                {"id":"history","slot":1,"cursor":1,"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR",
                 "board":{"x":10,"y":70,"width":350,"height":350},"meter":{"x":0,"y":70,"width":8,"height":350},
                 "bulb":{"x":294,"y":570,"width":48,"height":48},"meterValue":48,"score":"−0,32 · Negru","hint":"e7e5","bulbPressed":true}
                """);
        historical.put("checkpoint", "ai|" + FIXTURE);
        JsonNode matchBoard = historical.path("board").deepCopy();
        var arrows = historical.putObject("arrows");
        for (String step : List.of("live", "forward", "backward")) arrows.set(step, matchBoard.deepCopy());
        check.same("The historical slot, position, score and recommended move pass",
                0L, failures(validateCheckpoint(historical, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));
        var inset = historical.deepCopy();
        ((ObjectNode) inset.path("board")).put("x", 16).put("width", 328).put("height", 328);
        ((ObjectNode) inset.path("meter")).put("x", 6).put("height", 328);
        for (String step : List.of("live", "forward", "backward")) {
            ((ObjectNode) inset.path("arrows")).set(step, inset.path("board").deepCopy());
        }
        check.same("A fixed square with wide side insets fails the full-width contract",
                1L, failures(validateCheckpoint(inset, 1, inset.path("board"), "ro", HISTORY, LIVE, REPLY)));

        var clipped = historical.deepCopy();
        ((ObjectNode) clipped.path("bulb")).put("y", 610);
        check.same("A clipped bulb fails even if the hint has been recorded",
                1L, failures(validateCheckpoint(clipped, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));

        var shifted = historical.deepCopy();
        ((ObjectNode) shifted.path("arrows").path("forward")).put("y", 71);
        check.same("A board shift on the forward arrow fails even if it returns on the backward arrow",
                1L, failures(validateCheckpoint(shifted, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));

        historical.put("cursor", 2).put("hint", "g1f3").put("score", "+0,32 · Alb");
        check.same("The live cursor, hint and score cannot stand in for historical ones",
                3L, failures(validateCheckpoint(historical, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));

        var replay = historical.deepCopy();
        replay.put("cursor", 1).put("id", "preview");
        replay.set("previewSteps", JSON.valueToTree(List.of(0, 1, 2, 1)));
        replay.set("previewMoves", JSON.valueToTree(REPLY));
        replay.set("previewBoards", JSON.valueToTree(List.of(matchBoard, matchBoard, matchBoard, matchBoard)));
        check.same("A square replay with the full line and rewind passes", 0L,
                failures(validateCheckpoint(replay, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));
        ((ObjectNode) replay.path("board")).put("height", 400);
        replay.set("previewMoves", JSON.valueToTree(List.of("e7e5", "f1c4", "e7e5")));
        check.same("Rejects the stretched board, the recorded geometry disagreement and the substituted reply", 3L,
                failures(validateCheckpoint(replay, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));
    }

    private static long failures(List<AtfAssertion> checks) {
        return checks.stream().filter(check -> !check.passed()).count();
    }
}
