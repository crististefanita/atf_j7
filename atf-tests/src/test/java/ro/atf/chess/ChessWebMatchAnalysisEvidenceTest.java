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
                 "board":{"x":16,"y":70,"width":328,"height":328},"meter":{"x":9,"y":70,"width":5,"height":328},
                 "meterValue":48,"score":"−0,32 · Negru","hint":"e7e5","bulbPressed":true}
                """);
        historical.put("checkpoint", "ai|" + FIXTURE);
        JsonNode matchBoard = historical.path("board").deepCopy();
        check.same("The historical slot, position, score and recommended move pass",
                0L, failures(validateCheckpoint(historical, 1, matchBoard, "ro", HISTORY, LIVE, REPLY)));
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
