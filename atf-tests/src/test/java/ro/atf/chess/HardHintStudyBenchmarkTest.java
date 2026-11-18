package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Offline receipt replay: evidence integrity and measured hint quality are separate verdicts. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardHintStudyBenchmarkTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    record Grade(int steps, int oracleQueries, int firstHintLarge, int offeredLarge, int replyLarge) { }

    @Test void publishesEveryPlayedMoveAndEveryOfferedHintAgainstTheOracle(Checks check) throws Exception {
        var grade = inspect(check, true);
        check.same("All played positions have complete native, public-hint and oracle evidence", true, grade.steps() > 0 && grade.oracleQueries() > grade.steps());
    }

    @Test void measuredHintsMeetTheFrozenLargeLossFloor(Checks check) throws Exception {
        var grade = inspect(check, false);
        check.same("Hint quality is distinct from evidence integrity: first / offered / opposing-PV-reply large losses",
                List.of(0, 0, 0), List.of(grade.firstHintLarge(), grade.offeredLarge(), grade.replyLarge()));
    }

    private static Grade inspect(Checks check, boolean details) throws Exception {
        String configured = System.getProperty("chess.hardHintStudy.output", "");
        require(!configured.isBlank(), "Select an external hard/hint study bundle");
        var folder = Path.of(configured).toAbsolutePath().normalize();
        require(!folder.startsWith(ProjectRoot.locate().toAbsolutePath().normalize()), "Study evidence must stay outside the checkout");
        var plan = trusted(folder, "oracle-audit/plan.json", "planSha256", check);
        var freeze = trusted(folder, "games/freeze.json", "freezeSha256", check);
        trustedBytes(folder, "oracle-audit/oracle.jsonl", "oracleSha256", check);
        trustedBytes(folder, "games/moves.jsonl", "gamesSha256", check);
        String revision = System.getProperty("chess.hardHintStudy.revision", "");
        require(revision.matches("[a-f0-9]{40}") && revision.equals(plan.path("identity").path("source").path("revision").asText()), "Foreign measured source");
        require(plan.path("identity").path("source").equals(freeze.path("identity")), "Foreign native runtime");
        require(plan.path("identity").path("gamesSha256").asText().equals(HardLeagueAudit.digest(Files.readAllBytes(folder.resolve("games/moves.jsonl")))), "Oracle plan belongs to another game run");
        var expectedQueries = new HashMap<String, JsonNode>();
        for (var query : plan.path("queries")) require(expectedQueries.putIfAbsent(query.path("key").asText(), query.path("query")) == null, "Repeated planned oracle request");
        require(!expectedQueries.isEmpty(), "Empty oracle plan");
        var receipts = new HashMap<String, JsonNode>();
        try (var reader = Files.newBufferedReader(folder.resolve("oracle-audit/oracle.jsonl"))) {
            for (String raw; (raw = reader.readLine()) != null;) {
                var envelope = JSON.readTree(raw); var body = envelope.path("body");
                require(envelope.path("digest").asText().equals(hash(body)), "Modified oracle receipt");
                var key = body.path("key").asText();
                StockfishStudyAudit.validate(expectedQueries.get(key), plan.path("identity"), body);
                require(receipts.putIfAbsent(key, body) == null, "Repeated raw oracle receipt");
            }
        }
        check.same("Every planned H17/H19 request has one validated raw UCI receipt", expectedQueries.keySet(), receipts.keySet());
        var steps = new LinkedHashMap<String, JsonNode>();
        for (var step : plan.path("steps")) require(steps.putIfAbsent(stepKey(step.path("case").asText(), step.path("ply").asInt()), step) == null, "Repeated planned game phase");
        require(!steps.isEmpty(), "Empty study cannot pass");
        var matches = new HashMap<String, ChessMatch>(); var cases = new HashMap<String, JsonNode>(); var previous = new HashMap<String, String>();
        for (var item : freeze.path("cases")) {
            var id = item.path("id").asText();
            var human = item.path("hardWhite").asBoolean() ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
            require(matches.putIfAbsent(id, ChessMatch.start(new MatchSetup(human, MatchSetup.Level.HARD, true))) == null, "Repeated study case");
            cases.put(id, item); previous.put(id, "0".repeat(64));
        }
        require(!cases.isEmpty(), "Missing study cases");
        int count = 0, firstLarge = 0, offeredLarge = 0, replyLarge = 0;
        int floor = ChessYaml.number(ChessYaml.resource("hard-hint-study.yaml"), "large-loss-permille");
        var rows = new ArrayList<Map<String, Object>>();
        try (var reader = Files.newBufferedReader(folder.resolve("games/moves.jsonl"))) {
            for (String raw; (raw = reader.readLine()) != null;) {
                var envelope = JSON.readTree(raw); var body = envelope.path("body");
                require(freeze.path("identity").equals(body.path("identity")) && envelope.path("digest").asText().equals(hash(body)), "Foreign or modified native receipt");
                String id = body.path("case").asText(); var match = matches.get(id);
                require(match != null && previous.get(id).equals(body.path("previousDigest").asText()), "Foreign or reordered game chain");
                var entry = JSON.treeToValue(body.path("entry"), HardHintAuditedGame.Entry.class);
                HardHintAuditedGame.validate(entry);
                require(match.checkpoint().equals(entry.checkpoint()) && body.path("ply").asInt() == match.ply(), "Skipped or borrowed game phase");
                var step = steps.get(stepKey(id, match.ply()));
                require(step != null && step.path("entry").equals(body.path("entry")) && step.path("played").equals(body.path("played"))
                        && step.path("actor").equals(body.path("actor")), "Oracle plan substitutes the played phase");
                var played = ChessPosition.Move.fromUci(body.path("played").asText());
                var prefixText = cases.get(id).path("prefix").asText();
                var prefix = prefixText.isBlank() ? new String[0] : prefixText.split(" ");
                String actor = match.ply() < prefix.length ? "OPENING_PREFIX"
                        : (match.position().turn() == ChessPosition.Side.WHITE) == cases.get(id).path("hardWhite").asBoolean() ? "HARD" : "STOCKFISH";
                require(actor.equals(body.path("actor").asText()), "Foreign played actor");
                if (actor.equals("OPENING_PREFIX")) require(prefix[match.ply()].equals(played.uci()), "Changed frozen opening prefix");
                if (actor.equals("HARD")) require(entry.hard().selected().equals(played.uci()), "A different move replaced the native decision");
                var history = List.of(entry.checkpoint().split("\\|", -1)[4].split(" "));
                if (match.ply() == 0) history = List.of();
                var metrics = new LinkedHashMap<String, Object>(); metrics.put("case", id); metrics.put("ply", match.ply()); metrics.put("fen", entry.fen());
                metrics.put("actor", actor); metrics.put("played", played.uci()); metrics.put("hard", entry.hard().selected());
                metrics.put("nativeDepth", entry.hard().depth()); metrics.put("nativeExtraNodes", entry.hard().extraNodes());
                metrics.put("hintSelectedDepth", entry.hint().selectedDepth()); metrics.put("hintSeedDepth", entry.hint().seedDepth());
                metrics.put("hintComparisonDepth", entry.hint().comparisonDepth()); metrics.put("hintTotalNodes", entry.hint().extraNodes());
                var horizons = new LinkedHashMap<String, Object>();
                for (int depth : List.of(17, 19)) {
                    var free = phase(step, "FREE", depth, history, null, receipts);
                    var nativeRoot = phase(step, "HARD", depth, history, entry.hard().selected(), receipts);
                    var playedRoot = phase(step, "PLAYED", depth, history, played.uci(), receipts);
                    var offered = new ArrayList<Double>(); var opposing = new ArrayList<Double>();
                    for (int index = 0; index < entry.hint().lines().size(); index++) {
                        var line = entry.hint().lines().get(index); String rank = Integer.toString(index + 1);
                        var forced = phase(step, "HINT-" + rank, depth, history, line.move().uci(), receipts);
                        double loss = StockfishStudyAudit.loss(free, forced); offered.add(loss);
                        if (depth == 19 && loss >= floor) { offeredLarge++; if (index == 0) firstLarge++; }
                        if (details) check.same(id + " ply " + match.ply() + " hint " + rank + " H" + depth + " loss=" + loss
                                + " nativeScore=" + line.score() + " modelPV=" + path(line.line()) + " SF-best=" + free.path("san")
                                + " SF-after-hint=" + forced.path("san"), line.move().uci(), forced.path("query").path("root").asText());
                        if (line.line().size() > 1) {
                            var afterRoot = append(history, line.line().subList(0, 1));
                            var bestReply = phase(step, "HINT_REPLY_FREE-" + rank, depth, afterRoot, null, receipts);
                            var shownReply = phase(step, "HINT_REPLY_PLAYED-" + rank, depth, afterRoot, line.line().get(1).uci(), receipts);
                            double replyLoss = StockfishStudyAudit.loss(bestReply, shownReply); opposing.add(replyLoss);
                            if (depth == 19 && replyLoss >= floor) replyLarge++;
                            if (details) check.same(id + " ply " + match.ply() + " hint " + rank + " opponent H" + depth + " loss=" + replyLoss,
                                    line.line().get(1).uci(), shownReply.path("query").path("root").asText());
                        }
                        phase(step, "HINT_ENDPOINT-" + rank, depth, append(history, line.line()), null, receipts);
                    }
                    if (entry.hard().selectedRank() > 0) {
                        var line = entry.hard().variations().get(entry.hard().selectedRank() - 1);
                        if (line.line().size() > 1) {
                            var hist = append(history, line.line().subList(0, 1));
                            phase(step, "HARD_REPLY_FREE", depth, hist, null, receipts);
                            phase(step, "HARD_REPLY_PLAYED", depth, hist, line.line().get(1).uci(), receipts);
                        }
                        var endpoint = phase(step, "HARD_ENDPOINT", depth, append(history, line.line()), null, receipts);
                        if (details) check.same(id + " ply " + match.ply() + " actual Hard H" + entry.hard().depth() + " extraNodes=" + entry.hard().extraNodes()
                                + " nativePV=" + path(line.line()) + " oracle endpoint score=" + endpoint.path("scoreType") + ":" + endpoint.path("score")
                                + " endpoint-side-to-move, SF-continuation=" + endpoint.path("san"),
                                append(history, line.line()), strings(endpoint.path("query").path("history")));
                    }
                    var values = new LinkedHashMap<String, Object>();
                    values.put("firstHintLoss", offered.getFirst()); values.put("offeredHintLosses", offered); values.put("opposingReplyLosses", opposing);
                    values.put("hardLoss", StockfishStudyAudit.loss(free, nativeRoot)); values.put("playedLoss", StockfishStudyAudit.loss(free, playedRoot));
                    values.put("oracleBestLine", strings(free.path("san"))); values.put("oracleHardLine", strings(nativeRoot.path("san")));
                    if (free.path("scoreType").asText().equals("cp")) {
                        int white = free.path("score").asInt() * (match.position().turn() == ChessPosition.Side.WHITE ? 1 : -1);
                        values.put("displayedWhiteCp", entry.evaluation().whiteCentipawns()); values.put("oracleWhiteCp", white);
                        if (details) check.same(id + " ply " + match.ply() + " H" + depth + " displayed White cp="
                                + entry.evaluation().whiteCentipawns() + " oracle White cp=" + white, entry.fen(), free.path("fen").asText());
                    }
                    horizons.put(Integer.toString(depth), values);
                }
                metrics.put("horizons", horizons); rows.add(metrics);
                matches.put(id, match.play(played)); previous.put(id, envelope.path("digest").asText()); count++;
            }
        }
        check.same("Every planned played phase was replayed without additional searches", steps.size(), count);
        for (var item : cases.entrySet()) {
            var match = matches.get(item.getKey());
            require(match.result() != ChessMatch.Result.IN_PROGRESS || match.ply() == item.getValue().path("cap").asInt(), "Unexplained premature study stop");
        }
        var grade = new Grade(count, receipts.size(), firstLarge, offeredLarge, replyLarge);
        var output = folder.resolve("oracle-audit/qualified-grades.json");
        var serialized = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("source", revision, "grade", grade, "rows", rows));
        if (Files.exists(output)) require(JSON.readTree(Files.readString(output)).equals(JSON.readTree(serialized)), "Re-publication changed qualified observations");
        else Files.writeString(output, serialized, java.nio.file.StandardOpenOption.CREATE_NEW);
        return grade;
    }

    private static JsonNode phase(JsonNode step, String role, int depth, List<String> history, String root, Map<String, JsonNode> receipts) {
        return StockfishStudyAudit.phase(step, role, depth, history, root, receipts);
    }
    private static List<String> append(List<String> history, List<ChessPosition.Move> moves) {
        var result = new ArrayList<>(history); moves.forEach(move -> result.add(move.uci())); return result;
    }
    private static List<String> strings(JsonNode nodes) { var result = new ArrayList<String>(); nodes.forEach(node -> result.add(node.asText())); return result; }
    private static String stepKey(String id, int ply) { return id + ":" + ply; }
    private static String path(List<ChessPosition.Move> moves) { return moves.stream().map(ChessPosition.Move::uci).collect(java.util.stream.Collectors.joining(" ")); }
    private static String hash(JsonNode body) { return HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(StandardCharsets.UTF_8)); }
    private static JsonNode trusted(Path folder, String path, String property, Checks check) throws Exception {
        return JSON.readTree(trustedBytes(folder, path, property, check));
    }
    private static byte[] trustedBytes(Path folder, String path, String property, Checks check) throws Exception {
        var expected = System.getProperty("chess.hardHintStudy." + property, "");
        require(expected.matches("[a-f0-9]{64}"), "A separately trusted digest is required for " + path);
        var bytes = Files.readAllBytes(folder.resolve(path)); check.same("Trusted raw file " + path, expected, HardLeagueAudit.digest(bytes)); return bytes;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
