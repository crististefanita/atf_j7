package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Frozen observations of the next defect, kept separate from the repaired Nimzo root. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class KingsIndianWitnessTest {
    @Test void theFirstPlayedLossAndItsHintShareIndependentEvidence(Checks check) throws Exception {
        var data = fixture();
        var rows = new HashMap<String, JsonNode>();
        for (var envelope : data.path("raw")) {
            var body = envelope.path("body");
            check.same("The original teacher phase remains intact", envelope.path("digest").asText(),
                    HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(StandardCharsets.UTF_8)));
            if (rows.putIfAbsent(body.path("key").asText(), body) != null) throw new IllegalArgumentException("Duplicate teacher phase");
        }
        check.same("Both free and forced horizons are retained", 4, rows.size());
        for (var query : data.path("plan").path("queries")) {
            StockfishStudyAudit.validate(query.path("query"), data.path("plan").path("identity"), rows.get(query.path("key").asText()));
        }
        check.same("The actual move and pre-move public hint shared the same defect", List.of("c8d7", "c8d7"),
                List.of(data.path("played").asText(), data.path("archivedNative").path("firstHint").asText()));
        for (int depth : List.of(17, 19)) {
            var free = rows.get(data.path("free").path("" + depth).asText());
            var forced = rows.get(data.path("forced").path("" + depth).asText());
            check.same("Free opposing play prefers the central break H" + depth, "e7e6", free.path("pv").get(0).asText());
            check.same("The played bishop move loses at least 150 per mille H" + depth, true, StockfishStudyAudit.loss(free, forced) >= 150);
        }
        check.same("The archived native allowance is evidence, not a new runtime setting", 200000,
                data.path("archivedNative").path("nodes").asInt());
    }

    static JsonNode fixture() throws Exception {
        try (var input = KingsIndianWitnessTest.class.getResourceAsStream("/kings-indian-first-loss.yaml")) {
            var data = LearningGameAudit.mapper().readTree(input);
            if (!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8))
                    .equals("b3abdfed98c6c1c0f36cad4c19ecb5f0849f6bde50502e67b9edbb695e359ed2")) {
                throw new IllegalArgumentException("Changed King's Indian witness");
            }
            return data;
        }
    }

    static ChessMatch match(JsonNode data) {
        var moves = new java.util.ArrayList<String>();
        data.path("history").forEach(move -> moves.add(move.asText()));
        return ChessMatch.restore("3|WHITE|HARD|1|" + String.join(" ", moves));
    }
}
