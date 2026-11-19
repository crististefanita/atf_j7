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
    @Test void offeredBishopRetreatLosesAgainstTheIndependentNimzoDevelopment(Checks check) throws Exception {
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

    static JsonNode fixture() throws Exception {
        try (var input = NimzoWitnessTest.class.getResourceAsStream("/nimzo-hint-witness.yaml")) {
            var data = LearningGameAudit.mapper().readTree(input);
            if (!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8))
                    .equals("961c8aa6be4d2c8f2d8dfd7d1bb43ca26462a6d9c8c9fe5e2f92217eb06dfed5")) {
                throw new IllegalArgumentException("Changed Nimzo witness");
            }
            rows(data);
            return data;
        }
    }

    static Map<String, JsonNode> rows(JsonNode data) {
        var rows = new HashMap<String, JsonNode>();
        for (var envelope : data.path("raw")) {
            var body = envelope.path("body");
            if (!envelope.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(StandardCharsets.UTF_8)))
                    || rows.putIfAbsent(body.path("key").asText(), body) != null) {
                throw new IllegalArgumentException("Changed or duplicated Nimzo phase");
            }
        }
        if (rows.size() != 4) throw new IllegalArgumentException("Missing independent Nimzo horizon");
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
