package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class WebHintReplayComponentTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path output;

    @Test void currentWebAdapterReplaysExactlyTheScoredPvAndRejectsStaleFrames(Checks check) throws Exception {
        var classes = compileCurrentAdapter(check);
        for (String prefix : List.of("e2e4", "e2e4 b8c6", "e2e4 c7c5 g1f3 e7e5", "f2f3 e7e5 g2g4")) {
            var board = ChessMatch.restore("2|analysis|" + prefix).position();
            var hint = MatchHint.analyze(board);
            hint.revealNext();
            var playback = hint.playback(board).orElseThrow();
            verify(check, classes, prefix, playback.lines(), playback.depth());
        }
        // A complete losing line remains honest evidence; the adapter must neither hide Qxc2 nor invent a later reply.
        var line = new TacticalAnalysis.Variation(List.of("b8c6", "f1b5", "c6d4", "b5c4", "d4c2", "d1c2")
                .stream().map(Move::fromUci).toList(), -300, -200, TacticalAnalysis.Ending.HORIZON);
        verify(check, classes, "e2e4", List.of(line), 6);
    }

    private Path compileCurrentAdapter(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        var source = Files.writeString(output.resolve("WebHintReplayProbe.java"), PROBE);
        var classes = Files.createDirectories(output.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        check.same("The component probe compiles the current Web adapter with the active JDK", true, compiler != null);
        var diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(null, diagnostics, diagnostics, "--release", "21", "-proc:none", "-encoding", "UTF-8",
                "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), source.toString(),
                root.resolve("web/src/main/java/ro/atf/chess/WebHintReplay.java").toString(),
                root.resolve("web/src/main/java/ro/atf/chess/WebBoardState.java").toString());
        check.same("Fresh WebHintReplay and WebBoardState sources compile: " + diagnostics, 0, result);
        return classes;
    }

    private void verify(Checks check, Path classes, String prefix, List<TacticalAnalysis.Variation> lines, int depth)
            throws Exception {
        var board = ChessMatch.restore("2|analysis|" + prefix).position();
        var input = new ArrayList<String>();
        input.add(prefix);
        input.add(String.valueOf(depth));
        for (var line : lines) input.add(line.score() + "\t" + line.materialChange() + "\t" + line.ending() + "\t"
                + String.join(" ", line.line().stream().map(Move::uci).toList()));
        var data = Files.createTempFile(output, "scored-", ".tsv");
        Files.write(data, input);
        var transcript = Files.createTempFile(output, "adapter-", ".jsonl");
        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var process = new ProcessBuilder(javaExecutable, "-Djava.io.tmpdir=" + output, "-cp",
                classes + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                "ro.atf.chess.WebHintReplayProbe", data.toString()).redirectErrorStream(true).redirectOutput(transcript.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The fresh Web adapter completes the scored replay after " + prefix, true, finished);
        check.same("The adapter replay succeeds: " + Files.readString(transcript), 0, process.exitValue());
        int seen = 0;
        for (String encoded : Files.readAllLines(transcript)) {
            if (encoded.startsWith("Picked up ")) continue;
            var row = JSON.readTree(encoded);
            if (row.has("controls")) {
                check.same("Hidden and stale hints cannot open a preview", List.of(false, false), booleans(row.get("controls"), 0, 2));
                check.same("Open preview rejects reentry and out-of-range seeks without moving", List.of(false, false, true),
                        booleans(row.get("controls"), 2, 5));
                check.same("Closing clears the adapter and reopening restores the root", List.of(true, false, true),
                        booleans(row.get("controls"), 5, 8));
                continue;
            }
            int branch = row.path("branch").asInt(), ply = row.path("ply").asInt();
            var state = row.get("state");
            var line = lines.get(branch);
            var expected = board;
            for (int index = 0; index < ply; index++) expected = expected.play(line.line().get(index));
            String label = prefix + " branch " + branch + " ply " + ply;
            check.same(label + " retains the scored line length", line.line().size(), state.path("total").asInt());
            check.same(label + " uses the native branch order", lines.stream().map(item -> item.move().uci()).toList(), strings(state.path("branches")));
            check.same(label + " shows the exact legally replayed board", squares(expected), state.path("board").asText());
            check.same(label + " retains the side to move", expected.turn().name(), state.path("sideToMove").asText());
            check.same(label + " identifies the exact last scored move", ply == 0 ? null : line.line().get(ply - 1).uci(),
                    state.path("lastMove").isNull() ? null : state.path("lastMove").asText());
            var nativeReplay = HintPreview.openDefended(board, line).orElseThrow();
            check.same(label + " retains the native evidence and material gain",
                    List.of(nativeReplay.evidence().reason().name(), nativeReplay.evidence().netMaterial()),
                    List.of(state.path("proof").asText(), state.path("gain").asInt()));
            check.same(label + " exposes the shared comparison horizon", lines.size() > 1 ? depth : 0,
                    state.path("depth").asInt());
            seen++;
        }
        check.same("Every scored frame is checked, including the return to the root", lines.stream().mapToInt(line -> line.line().size() + 2).sum(), seen);
    }

    private static List<Boolean> booleans(JsonNode values, int from, int to) {
        var result = new ArrayList<Boolean>();
        for (int index = from; index < to; index++) result.add(values.get(index).asBoolean());
        return result;
    }

    private static List<String> strings(JsonNode values) {
        var result = new ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static String squares(ChessPosition position) {
        var result = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) for (int file = 0; file < 8; file++) {
            var piece = position.pieceAt(BoardSquare.of(file, rank));
            if (piece == null) { result.append(' '); continue; }
            char value = "pnbrqk".charAt(piece.kind().ordinal());
            result.append(piece.side() == ChessPosition.Side.WHITE ? Character.toUpperCase(value) : value);
        }
        return result.toString();
    }

    private static final String PROBE = """
            package ro.atf.chess;
            import java.nio.file.*;
            import java.util.*;
            public final class WebHintReplayProbe {
                public static void main(String[] args) throws Exception {
                    var rows = Files.readAllLines(Path.of(args[0]));
                    var root = ChessMatch.restore("2|analysis|" + rows.get(0)).position();
                    int depth = Integer.parseInt(rows.get(1));
                    var lines = new ArrayList<TacticalAnalysis.Variation>();
                    for (String row : rows.subList(2, rows.size())) {
                        var fields = row.split("\\t");
                        lines.add(new TacticalAnalysis.Variation(Arrays.stream(fields[3].split(" "))
                                .map(ChessPosition.Move::fromUci).toList(), Integer.parseInt(fields[0]),
                                Integer.parseInt(fields[1]), TacticalAnalysis.Ending.valueOf(fields[2])));
                    }
                    var hint = new MatchHint(root, lines, new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(lines), depth, 0));
                    var replay = new WebHintReplay();
                    boolean hidden = replay.open(root, hint);
                    hint.revealNext();
                    boolean stale = replay.open(root.play(root.legalMoves().getFirst()), hint);
                    if (!replay.open(root, hint)) throw new AssertionError("Cannot open a current scored hint");
                    String initial = replay.state();
                    boolean reentry = replay.open(root, hint), invalid = replay.seek(Integer.MAX_VALUE);
                    boolean unchanged = initial.equals(replay.state());
                    for (int branch = 0; branch < lines.size(); branch++) {
                        if (!replay.select(branch)) throw new AssertionError("Missing branch " + branch);
                        for (int ply = 0; ply <= lines.get(branch).line().size(); ply++) {
                            if (!replay.seek(ply)) throw new AssertionError("Missing scored frame");
                            print(branch, ply, replay.state());
                        }
                        replay.seek(0);
                        print(branch, 0, replay.state());
                    }
                    boolean closed = replay.close(), open = replay.isOpen();
                    if (!replay.open(root, hint)) throw new AssertionError("Cannot reopen scored hint");
                    boolean restored = initial.equals(replay.state());
                    System.out.println("{\\"controls\\":[" + hidden + "," + stale + "," + reentry + "," + invalid + ","
                            + unchanged + "," + closed + "," + open + "," + restored + "]}");
                }
                private static void print(int branch, int ply, String state) {
                    System.out.println("{\\"branch\\":" + branch + ",\\"ply\\":" + ply + ",\\"state\\":" + state + "}");
                }
            }
            """;
}
