package ro.atf.chess;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** External oracle used only by the opt-in JVM benchmarks. */
final class StockfishUci implements AutoCloseable {
    private static final String END = "end of UCI output";

    record Oracle(String move, String score, int expected) { }

    private static final Pattern SCORE = Pattern.compile("\\bscore (cp|mate) (-?\\d+)\\b");
    private static final Pattern WDL = Pattern.compile("\\bwdl (\\d+) (\\d+) (\\d+)\\b");
    private static final Pattern PERFT_MOVE = Pattern.compile("^([a-h][1-8][a-h][1-8][qrbn]?): \\d+$");
    private final Process process;
    private final BufferedWriter input;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final String name;

    StockfishUci(Path binary) throws Exception {
        process = new ProcessBuilder(binary.toAbsolutePath().toString()).redirectErrorStream(true).start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread.ofVirtual().start(() -> {
            try (var output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = output.readLine()) != null;) lines.add(line);
            } catch (IOException error) { lines.add(error.toString()); }
            finally { lines.add(END); }
        });
        try {
            send("uci");
            String found = "";
            for (String line; !(line = next()).equals("uciok");) if (line.startsWith("id name ")) found = line.substring(8);
            name = found;
            send("setoption name Threads value 1");
            send("setoption name Hash value 16");
            send("setoption name UCI_LimitStrength value false");
            send("setoption name UCI_ShowWDL value true");
            ready();
        } catch (Exception error) {
            process.destroyForcibly();
            throw error;
        }
    }

    Oracle evaluate(String fen, int depth, String move) throws Exception {
        return evaluatePosition("fen " + fen, depth, move);
    }

    Set<String> legalMoves(String position) throws Exception {
        send("position " + position);
        send("go perft 1");
        var moves = new HashSet<String>();
        for (String line; !(line = next()).startsWith("Nodes searched:");) {
            var move = PERFT_MOVE.matcher(line);
            if (move.matches()) moves.add(move.group(1));
        }
        return Set.copyOf(moves);
    }

    Oracle evaluatePosition(String position, int depth, String move) throws Exception {
        send("ucinewgame");
        ready();
        send("position " + position);
        send("go depth " + depth + (move == null ? "" : " searchmoves " + move));
        String score = null;
        int expected = -1;
        String line;
        while (!(line = next()).startsWith("bestmove ")) {
            if (!line.contains(" pv ") || line.contains("lowerbound") || line.contains("upperbound")) continue;
            var value = SCORE.matcher(line);
            var wdl = WDL.matcher(line);
            if (!value.find() || !wdl.find()) continue;
            score = value.group(1) + " " + value.group(2);
            expected = Integer.parseInt(wdl.group(1)) + Integer.parseInt(wdl.group(2)) / 2;
        }
        if (score == null) throw new IOException("Stockfish did not return an exact score for " + position);
        return new Oracle(line.split(" ")[1], score, expected);
    }

    private String next() throws Exception {
        String line = lines.poll(20, TimeUnit.SECONDS);
        if (line == null || END.equals(line) || line.contains("CRITICAL ERROR")) {
            throw new IOException("Stockfish stopped responding: " + line);
        }
        return line;
    }

    private void ready() throws Exception {
        send("isready");
        while (!next().equals("readyok")) { }
    }

    private void send(String line) throws IOException {
        input.write(line);
        input.newLine();
        input.flush();
    }

    @Override public void close() throws Exception {
        try {
            if (process.isAlive()) {
                send("quit");
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } finally {
            process.destroyForcibly();
            input.close();
        }
    }

    String name() { return name; }

    void limitStrength(int elo) throws Exception {
        send("setoption name UCI_LimitStrength value true");
        send("setoption name UCI_Elo value " + elo);
        ready();
    }

    void newGame() throws Exception {
        send("ucinewgame");
        ready();
    }

    String play(String moves, int nodes) throws Exception {
        send("position startpos moves " + moves);
        send("go nodes " + nodes);
        String line;
        while (!(line = next()).startsWith("bestmove ")) { }
        return line.split(" ")[1];
    }
}
