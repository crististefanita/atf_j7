package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class StockfishStudyAuditTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void rawCommandsScoreWindowAndFullHistoryAreRequired(Checks check) {
        var receipt = fixture("e2e4", "c7c5");
        var query = receipt.get("query"); var identity = receipt.get("identity");
        StockfishStudyAudit.validate(query, identity, receipt);
        for (String kind : List.of("depth", "raw-depth", "bound", "score", "pv", "run", "history", "binary")) {
            var changed = receipt.deepCopy();
            switch (kind) {
                case "depth" -> changed.put("actualDepth", 17);
                case "raw-depth" -> changed.withObject("/uci").withArray("stdout").set(3, JSON.getNodeFactory().textNode("info depth 17 score cp 20 wdl 50 900 50 nodes 100 pv c7c5 g1f3"));
                case "bound" -> changed.withObject("/uci").withArray("stdout").set(3, JSON.getNodeFactory().textNode("info depth 19 score cp 20 lowerbound wdl 50 900 50 nodes 100 pv c7c5 g1f3"));
                case "score" -> changed.put("score", 999);
                case "pv" -> changed.withArray("pv").set(0, JSON.getNodeFactory().textNode("e7e5"));
                case "run" -> changed.withObject("/identity").put("run", "borrowed-run");
                case "history" -> changed.withObject("/query").withArray("history").add("e7e5");
                case "binary" -> changed.withObject("/query").put("binarySha256", "b".repeat(64));
                default -> throw new AssertionError(kind);
            }
            check.rejects("Mock oracle rejects substituted " + kind, IllegalArgumentException.class,
                    () -> StockfishStudyAudit.validate(query, identity, changed));
        }
        var missingCommand = receipt.deepCopy(); missingCommand.withObject("/uci").withArray("commands").remove(3);
        check.rejects("A raw score without the full-strength command cannot pass", IllegalArgumentException.class,
                () -> StockfishStudyAudit.validate(query, identity, missingCommand));
    }

    @Test void expectedScoreUsesTheCurrentSideAndTheSameCompletedHorizon(Checks check) {
        var forced = fixture("e2e4", "c7c5");
        var free = fixture("e2e4", null);
        free.set("wdl", JSON.valueToTree(List.of(100, 800, 100))); free.put("expectedPermille", 500);
        check.same("Black-to-move expected score uses Black WDL rather than a White sign flip", 500.0, forced.get("expectedPermille").asDouble());
        check.same("The same-position tied oracle estimate has zero clamped loss", 0.0, StockfishStudyAudit.loss(free, forced));
        var otherHorizon = forced.deepCopy(); otherHorizon.withObject("/query").put("depth", 17);
        check.rejects("H17 cannot be subtracted from H19", IllegalArgumentException.class, () -> StockfishStudyAudit.loss(free, otherHorizon));
        check.rejects("Two forced searches do not form the free-root loss baseline", IllegalArgumentException.class, () -> StockfishStudyAudit.loss(forced, forced));
        var wrongScale = forced.deepCopy(); wrongScale.put("expectedPermille", 50);
        check.rejects("A percent score cannot masquerade as permille", IllegalArgumentException.class,
                () -> StockfishStudyAudit.validate(forced.get("query"), forced.get("identity"), wrongScale));
    }

    @Test void terminalShortcutsNeedAnActualRulesResult(Checks check) {
        var receipt = fixture("f2f3 e7e5 g2g4 d8h4", null);
        receipt.put("terminal", true); receipt.set("wdl", JSON.valueToTree(List.of(0, 0, 1000))); receipt.put("expectedPermille", 0);
        receipt.put("result", "0-1");
        receipt.remove("uci"); receipt.remove("actualDepth");
        receipt.put("fen", HintAuditSession.fen(ChessMatch.restore("3|WHITE|HARD|1|f2f3 e7e5 g2g4 d8h4")));
        StockfishStudyAudit.validate(receipt.get("query"), receipt.get("identity"), receipt);
        var forged = fixture("e2e4", null); forged.put("terminal", true);
        check.rejects("A live position cannot skip Stockfish by claiming a terminal result", IllegalArgumentException.class,
                () -> StockfishStudyAudit.validate(forged.get("query"), forged.get("identity"), forged));
    }

    @Test void identicalFenAndClocksDoNotAuthorizeAnotherPlayedHistory(Checks check) {
        var first = fixture("e2e4 g8f6 g1f3 f6g8 f3g1", "c7c5");
        var other = fixture("e2e4 b8c6 b1c3 c6b8 c3b1", "c7c5");
        check.same("The negative really shares the same board and clocks", first.get("fen"), other.get("fen"));
        StockfishStudyAudit.validate(first.get("query"), first.get("identity"), first);
        check.rejects("Full game history remains part of oracle identity", IllegalArgumentException.class,
                () -> StockfishStudyAudit.validate(first.get("query"), first.get("identity"), other));
    }

    @Test void thePlanCannotOmitAnOfferedHintOrBorrowAnotherPhase(Checks check) {
        var receipt = fixture("e2e4", "c7c5");
        var step = JSON.createObjectNode(); step.putObject("queries").putObject("HINT-1").put("19", "receipt-one");
        var receipts = java.util.Map.<String, JsonNode>of("receipt-one", receipt);
        check.same("The offered root resolves to its actual phase", receipt,
                StockfishStudyAudit.phase(step, "HINT-1", 19, List.of("e2e4"), "c7c5", receipts));
        check.rejects("A missing second offered suggestion cannot be silently skipped", IllegalArgumentException.class,
                () -> StockfishStudyAudit.phase(step, "HINT-2", 19, List.of("e2e4"), "e7e5", receipts));
        check.rejects("A different first move cannot borrow the phase", IllegalArgumentException.class,
                () -> StockfishStudyAudit.phase(step, "HINT-1", 19, List.of("e2e4"), "e7e5", receipts));
        step.withObject("/queries/HINT-1").put("17", "receipt-one");
        check.rejects("A present slot cannot relabel H19 as H17", IllegalArgumentException.class,
                () -> StockfishStudyAudit.phase(step, "HINT-1", 17, List.of("e2e4"), "c7c5", receipts));
    }

    @Test void studyPolicyKeepsTheFixedOracleAndLargeLossDefinition(Checks check) {
        var policy = ChessYaml.resource("hard-hint-study.yaml");
        check.same("Independent audit uses both complete horizons", List.of(17, 19), ChessYaml.list(policy, "oracle-depths"));
        check.same("The existing large-loss definition remains 150 permille", 150, ChessYaml.number(policy, "large-loss-permille"));
        check.same("Hint and native ceilings retain their distinct semantics", List.of(200_000, 200_000),
                List.of(ChessYaml.number(policy, "native-max-extra-nodes"), ChessYaml.number(policy, "hint-max-total-nodes")));
    }

    private static ObjectNode fixture(String history, String root) {
        var query = JSON.createObjectNode(); query.set("history", JSON.valueToTree(history.isEmpty() ? List.of() : List.of(history.split(" "))));
        if (root == null) query.putNull("root"); else query.put("root", root);
        query.put("depth", 19); query.put("binarySha256", "a".repeat(64));
        var options = query.putObject("options"); options.put("Threads", 1); options.put("Hash", 32); options.put("UCI_LimitStrength", false); options.put("UCI_ShowWDL", true);
        var receipt = JSON.createObjectNode(); receipt.set("query", query);
        receipt.putObject("identity").put("run", "mock-fixture-not-an-actual-oracle").put("binarySha256", "a".repeat(64));
        receipt.put("fen", HintAuditSession.fen(ChessMatch.restore("3|WHITE|HARD|1|" + history)));
        receipt.put("terminal", false); receipt.put("actualDepth", 19); receipt.put("nodes", 100); receipt.put("scoreType", "cp"); receipt.put("score", 20);
        receipt.set("wdl", JSON.valueToTree(List.of(50, 900, 50))); receipt.put("expectedPermille", 500); receipt.put("bestmove", "c7c5");
        receipt.set("pv", JSON.valueToTree(List.of("c7c5", "g1f3")));
        var commands = new ArrayList<>(List.of("uci", "setoption name Threads value 1", "setoption name Hash value 32", "setoption name UCI_LimitStrength value false",
                "setoption name UCI_ShowWDL value true", "isready", "position startpos" + (history.isEmpty() ? "" : " moves " + history),
                "go depth 19" + (root == null ? "" : " searchmoves " + root), "quit"));
        var raw = receipt.putObject("uci"); raw.set("commands", JSON.valueToTree(commands));
        raw.set("stdout", JSON.valueToTree(List.of("id name Stockfish 19", "uciok", "readyok", "info depth 19 score cp 20 wdl 50 900 50 nodes 100 pv c7c5 g1f3", "bestmove c7c5")));
        return receipt;
    }
}
