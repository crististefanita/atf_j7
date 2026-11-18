package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Validates a source-bound oracle receipt; it never substitutes Stockfish for the native decision. */
final class StockfishStudyAudit {
    private StockfishStudyAudit() { }

    static void validate(JsonNode expected, JsonNode identity, JsonNode receipt) {
        require(expected != null && expected.isObject() && receipt != null && receipt.isObject(), "Missing expected oracle phase");
        require(identity != null && identity.isObject() && identity.equals(receipt.get("identity")), "Foreign oracle run or source");
        require(expected.equals(receipt.get("query")), "Foreign oracle query or history");
        int depth = expected.path("depth").asInt();
        require(depth == 17 || depth == 19, "Unexpected oracle horizon");
        var options = expected.path("options");
        require(options.path("Threads").asInt() == 1 && options.path("Hash").asInt() == 32
                && options.path("UCI_LimitStrength").isBoolean() && !options.path("UCI_LimitStrength").asBoolean()
                && options.path("UCI_ShowWDL").asBoolean(), "Foreign oracle strength or cache policy");
        require(expected.path("binarySha256").asText().matches("[a-f0-9]{64}"), "Missing oracle binary identity");
        require(expected.path("binarySha256").equals(identity.path("binarySha256")), "Foreign oracle binary");
        var history = strings(expected.path("history"));
        var match = ChessMatch.restore("3|WHITE|HARD|1|" + String.join(" ", history));
        require(receipt.path("fen").asText().equals(HintAuditSession.fen(match)), "Oracle position or clocks differ");
        var wdl = receipt.path("wdl");
        require(wdl.isArray() && wdl.size() == 3, "Missing oracle WDL");
        int total = 0;
        for (var value : wdl) { require(value.isIntegralNumber() && value.asInt() >= 0, "Invalid oracle WDL"); total += value.asInt(); }
        require(total == 1000 && receipt.path("expectedPermille").isNumber()
                && receipt.path("expectedPermille").asDouble() == wdl.get(0).asInt() + wdl.get(1).asInt() / 2.0, "Foreign expected-score scale or side");
        if (receipt.path("terminal").asBoolean()) {
            require(expected.path("root").isNull() && match.result() != ChessMatch.Result.IN_PROGRESS, "Invented terminal oracle shortcut");
            var result = switch (match.result()) { case WHITE_WINS -> "1-0"; case BLACK_WINS -> "0-1"; default -> "1/2-1/2"; };
            require(result.equals(receipt.path("result").asText()), "Foreign terminal result");
            var board = match.position();
            double expectedScore = switch (match.result()) {
                case WHITE_WINS -> board.turn() == ChessPosition.Side.WHITE ? 1000 : 0;
                case BLACK_WINS -> board.turn() == ChessPosition.Side.BLACK ? 1000 : 0;
                default -> 500;
            };
            require(receipt.path("expectedPermille").asDouble() == expectedScore, "Foreign terminal side or result");
            return;
        }
        require(match.result() == ChessMatch.Result.IN_PROGRESS && receipt.path("actualDepth").asInt() >= depth, "Incomplete oracle horizon");
        var commands = new ArrayList<>(List.of("uci", "setoption name Threads value 1", "setoption name Hash value 32",
                "setoption name UCI_LimitStrength value false", "setoption name UCI_ShowWDL value true", "isready",
                "position startpos" + (history.isEmpty() ? "" : " moves " + String.join(" ", history)),
                "go depth " + depth + (expected.path("root").isNull() ? "" : " searchmoves " + expected.path("root").asText()), "quit"));
        require(commands.equals(strings(receipt.path("uci").path("commands"))), "Missing or changed raw UCI commands");
        var output = strings(receipt.path("uci").path("stdout"));
        require(output.contains("id name Stockfish 19") && output.contains("uciok") && output.contains("readyok"), "Foreign raw oracle handshake");
        List<String> exact = null;
        for (var line : output) {
            var tokens = Arrays.asList(line.split(" "));
            if (line.startsWith("info depth ") && tokens.contains("score") && tokens.contains("wdl") && tokens.contains("pv")
                    && !tokens.contains("lowerbound") && !tokens.contains("upperbound")
                    && Integer.parseInt(tokens.get(tokens.indexOf("depth") + 1)) >= depth) exact = tokens;
        }
        require(exact != null, "Raw oracle output has no completed exact horizon");
        require(Integer.parseInt(exact.get(exact.indexOf("depth") + 1)) == receipt.path("actualDepth").asInt()
                && Long.parseLong(exact.get(exact.indexOf("nodes") + 1)) == receipt.path("nodes").asLong(), "Rewritten raw depth or nodes");
        int score = exact.indexOf("score"), rawWdl = exact.indexOf("wdl");
        require(exact.get(score + 1).equals(receipt.path("scoreType").asText())
                && Integer.parseInt(exact.get(score + 2)) == receipt.path("score").asInt(), "Rewritten oracle score");
        for (int i = 0; i < 3; i++) require(Integer.parseInt(exact.get(rawWdl + i + 1)) == wdl.get(i).asInt(), "Rewritten raw WDL");
        var pv = exact.subList(exact.indexOf("pv") + 1, exact.size());
        require(!pv.isEmpty() && pv.equals(strings(receipt.path("pv"))) && pv.getFirst().equals(receipt.path("bestmove").asText()), "Foreign or missing oracle PV");
        var rawBest = output.stream().filter(line -> line.startsWith("bestmove ")).reduce((first, last) -> last).orElseThrow();
        require(rawBest.split(" ")[1].equals(receipt.path("bestmove").asText()), "Rewritten raw best move");
        require(expected.path("root").isNull() || expected.path("root").asText().equals(pv.getFirst()), "Foreign forced oracle root");
        var board = match.position();
        for (var move : pv) board = board.play(ChessPosition.Move.fromUci(move));
    }

    static double loss(JsonNode free, JsonNode forced) {
        require(free.path("query").path("history").equals(forced.path("query").path("history"))
                && free.path("query").path("depth").equals(forced.path("query").path("depth"))
                && free.path("identity").equals(forced.path("identity"))
                && free.path("query").path("root").isNull() && forced.path("query").path("root").isTextual(),
                "Cannot grade different positions, horizons or oracle roles");
        return Math.max(0, free.path("expectedPermille").asDouble() - forced.path("expectedPermille").asDouble());
    }

    private static List<String> strings(JsonNode value) {
        require(value.isArray(), "Missing raw oracle array");
        var result = new ArrayList<String>();
        for (var item : value) { require(item.isTextual(), "Non-text raw oracle field"); result.add(item.asText()); }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
