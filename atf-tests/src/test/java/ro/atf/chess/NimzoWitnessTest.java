package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Preserves independent evidence of a newly observed error, without asserting that the engine is fixed. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NimzoWitnessTest {
    @Test void offeredBishopDevelopmentLosesAgainstTheIndependentNimzoMove(Checks check) throws Exception {
        var data = fixture();
        var rows = rows(data);
        var position = position(data);
        var replay = position;
        for (var move : data.path("publicPath")) replay = replay.play(ChessPosition.Move.fromUci(move.asText()));
        check.same("The archived public PV is legal; legality alone is insufficient", 2, data.path("rank").asInt());
        check.same("The witness starts at Black's real opening decision", ChessPosition.Side.BLACK, position.turn());
        for (int depth : List.of(17, 19)) {
            var free = rows.get(data.path("bad").path("free").path("" + depth).asText());
            var forced = rows.get(data.path("bad").path("forced").path("" + depth).asText());
            check.same("A free opposing search prefers the Nimzo development H" + depth, "f8b4", free.path("pv").get(0).asText());
            check.same("The offered Be7 is independently at least 150 per mille worse H" + depth, true,
                    StockfishStudyAudit.loss(free, forced) >= 150);
        }
        check.same("The receipt identifies the actual pre-move source", "nimzo-variety-game-20261119",
                data.path("source").path("identity").path("run").asText());
    }

    @Test void changedOrForeignOraclePhasesCannotQualifyTheHint(Checks check) throws Exception {
        var changed = fixture().deepCopy();
        ((ObjectNode) changed.path("raw").get(0).path("body")).put("expectedPermille", 1000);
        check.rejects("Changing an outcome cannot retain a trusted oracle receipt", IllegalArgumentException.class, () -> rows(changed));
        var foreign = fixture().deepCopy();
        ((ObjectNode) foreign.path("raw").get(0).path("body").path("identity")).put("run", "another-game");
        check.rejects("Another run cannot replace a missing phase", IllegalArgumentException.class, () -> rows(foreign));
        var missing = fixture().deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) missing.path("raw")).remove(0);
        check.rejects("A missing horizon is not a quality PASS", IllegalArgumentException.class, () -> rows(missing));
    }

    @Test void aLegalRecaptureCannotReplaceTheStrongerQuietDefenseAtTheHintEnd(Checks check) throws Exception {
        var tail = fixture().path("tail");
        var base = checkedRows(tail.path("base"), 6);
        var replies = checkedRows(tail.path("reply"), 2);
        var board = position(tail);
        var path = tail.path("publicPath");
        for (int index = 0; index < path.size() - 1; index++) board = board.play(ChessPosition.Move.fromUci(path.get(index).asText()));
        var capture = ChessPosition.Move.fromUci(path.get(path.size() - 1).asText());
        check.same("The archived line ends with a bishop taking a pawn", List.of(ChessPosition.Kind.BISHOP, ChessPosition.Kind.PAWN),
                List.of(board.pieceAt(capture.from()).kind(), board.pieceAt(capture.to()).kind()));
        var after = board.play(capture);
        check.same("Qxc5 is legal but must not be presumed forced", true, after.legalMoves().contains(ChessPosition.Move.fromUci("a5c5")));
        for (int depth : List.of(17, 19)) {
            var free = base.get(tail.path("free").path("" + depth).asText());
            var forced = base.get(tail.path("forced").path("" + depth).asText());
            check.same("Bxc5 loses the win against free replies H" + depth, 504.0, StockfishStudyAudit.loss(free, forced));
            var recapture = replies.get(tail.path("recapture").path("" + depth).asText());
            check.same("Accepting the material trap instead preserves White's win H" + depth, 1000.0,
                    1000.0 - recapture.path("expectedPermille").asDouble());
            check.same("The recapture is refuted by the knight, not a missing legal move", "e4c5", recapture.path("pv").get(1).asText());
        }
        check.same("The decisive free reply is the quiet Ne5, not Qxc5", "c6e5",
                base.get(tail.path("endpoint").path("19").asText()).path("pv").get(0).asText());
    }

    @Test void aRehashedReplyFromAnotherRunStillCannotJustifyThePreview(Checks check) throws Exception {
        var reply = fixture().path("tail").path("reply").deepCopy();
        var envelope = (ObjectNode) reply.path("raw").get(0);
        ((ObjectNode) envelope.path("body").path("identity")).put("run", "foreign-but-rehashed");
        envelope.put("digest", HardLeagueAudit.digest(HardLeagueAudit.canonical(envelope.path("body")).getBytes(StandardCharsets.UTF_8)));
        check.rejects("A valid checksum cannot turn a foreign recapture phase into the expected teacher evidence",
                IllegalArgumentException.class, () -> checkedRows(reply, 2));
    }

    static JsonNode fixture() throws Exception {
        try (var input = NimzoWitnessTest.class.getResourceAsStream("/nimzo-hint-witness.yaml")) {
            var data = LearningGameAudit.mapper().readTree(input);
            if (!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8))
                    .equals("458f4cc22f20e58a974967368d2c877fec35a327dc0f4e8a1ecd60cb53174da8")) {
                throw new IllegalArgumentException("Changed Nimzo witness");
            }
            rows(data);
            checkedRows(data.path("tail").path("base"), 6);
            checkedRows(data.path("tail").path("reply"), 2);
            return data;
        }
    }

    static Map<String, JsonNode> rows(JsonNode data) {
        return checkedRows(data, 4);
    }

    private static Map<String, JsonNode> checkedRows(JsonNode data, int count) {
        var rows = new HashMap<String, JsonNode>();
        for (var envelope : data.path("raw")) {
            var body = envelope.path("body");
            if (!envelope.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(StandardCharsets.UTF_8)))
                    || rows.putIfAbsent(body.path("key").asText(), body) != null) {
                throw new IllegalArgumentException("Changed or duplicated Nimzo phase");
            }
        }
        if (rows.size() != count) throw new IllegalArgumentException("Missing independent Nimzo horizon");
        for (var query : data.path("plan").path("queries")) {
            StockfishStudyAudit.validate(query.path("query"), data.path("plan").path("identity"), rows.get(query.path("key").asText()));
        }
        return rows;
    }

    static ChessPosition position(JsonNode data) {
        var board = ChessPosition.fromFen(data.path("initialFen").asText());
        for (var move : data.path("history")) {
            var promotion = move.path("promotion").isNull() ? null : ChessPosition.Kind.valueOf(move.path("promotion").asText());
            board = board.play(new ChessPosition.Move(move.path("from").asInt(), move.path("to").asInt(), promotion));
        }
        return board;
    }
}
