package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
@EnabledIfSystemProperty(named = "chess.stockfish", matches = ".+")
class MatchAiBenchmarkTest {
    private static final List<String> PROFILES = List.of("BASIC", "ADVANCED", "HARD", "HINT");

    @Test void confirmsTheOpeningRepliesAndTheAlekhineEscapeWithStockfish(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        int depth = ChessYaml.number(settings, "depth");
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned regression reference", ChessYaml.string(settings, "stockfish"), engine.name());
            for (Object item : ChessYaml.list(ChessYaml.resource("ai-repertoire.yaml"), "positions")) {
                var row = (Map<?, ?>) item;
                String id = ChessYaml.string(row, "id");
                String prefix = ChessYaml.string(row, "moves");
                var reference = engine.evaluatePosition("startpos moves " + prefix, depth, null);
                check.same(id + " verified reply matches Stockfish at depth " + depth, reference.move(), ChessYaml.string(row, "reply"));
            }
            String prefix = AiOpeningRegressionTest.alekhinePrefix("escape-ply");
            var match = ChessMatch.restore("3|BLACK|HARD|0|" + prefix);
            String move = match.chooseBotMove().uci();
            String position = "startpos moves " + prefix;
            var reference = engine.evaluatePosition(position, depth, null);
            var escape = engine.evaluatePosition(position, depth, move);
            int loss = Math.max(0, reference.expected() - escape.expected());
            check.same("Alekhine escape " + move + " | Stockfish " + escape.score() + " | loss " + loss + "‰",
                    true, escape.expected() > 500 && loss < ChessYaml.number(settings, "major-loss-permille"));
        }
    }

    @Test void confirmsTheAlekhineConversionWithTheCompleteHistory(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        var fixture = ChessYaml.resource("alekhine-conversion.yaml");
        int depth = ChessYaml.number(settings, "depth");
        String prefix = ChessYaml.string(fixture, "moves");
        String position = "startpos moves " + prefix;
        var match = ChessMatch.restore("3|BLACK|HARD|0|" + prefix);
        String choice = match.chooseBotMove().uci();
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned conversion reference", ChessYaml.string(settings, "stockfish"), engine.name());
            var best = engine.evaluatePosition(position, depth, null);
            var repeat = engine.evaluatePosition(position, depth, ChessYaml.string(fixture, "repeat"));
            var played = engine.evaluatePosition(position, depth, choice);
            int loss = Math.max(0, best.expected() - played.expected());
            check.same("Full history: " + repeat.move() + " gives away the advantage | " + repeat.score(), 500, repeat.expected());
            check.same("Hard " + played.move() + " " + played.score() + " vs Stockfish " + best.move() + " " + best.score()
                    + " | loss " + loss + "‰", true, played.expected() > 500 && loss < ChessYaml.number(settings, "major-loss-permille"));
        }
    }

    @Test void coachingKeepsUpWithHardOnTheIndependentLeagueStarts(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        int depth = ChessYaml.number(settings, "depth");
        int major = ChessYaml.number(settings, "major-loss-permille");
        var league = AiLeague.load(ChessYaml.resource("ai-league.yaml"));
        int hardLoss = 0;
        int hintLoss = 0;
        int hardMajor = 0;
        int hintMajor = 0;
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned independent coaching reference", ChessYaml.string(settings, "stockfish"), engine.name());
            for (var opening : league.openings()) {
                var match = opening.start();
                var hint = TacticalAnalysis.verifiedHints(match.position());
                var hard = match.botDecision(MatchSetup.Level.HARD);
                String position = "startpos moves " + opening.moves();
                var reference = engine.evaluatePosition(position, depth, null);
                String hardMove = hard.move().uci();
                String hintMove = hint.lines().getFirst().move().uci();
                int hardCase = moveLoss(engine, position, depth, reference, hardMove);
                int hintCase = hardMove.equals(hintMove) ? hardCase : moveLoss(engine, position, depth, reference, hintMove);
                hardLoss += hardCase;
                hintLoss += hintCase;
                if (hardCase >= major) hardMajor++;
                if (hintCase >= major) hintMajor++;
                String row = opening.id() + " | Hard " + hardMove + " " + hardCase + "‰ | Hint " + hintMove + " " + hintCase
                        + "‰ | Stockfish " + reference.move();
                check.same(row + " | Coaching uses the configured budget", true, hint.extraNodes() <= MatchAiPolicy.CURRENT.hint.search().extraNodes());
            }
        }
        int count = league.openings().size();
        System.out.printf(java.util.Locale.ROOT, "Coach vs Hard on %d starts: %.1f vs %.1f‰, major %d vs %d%n",
                count, (double) hintLoss / count, (double) hardLoss / count, hintMajor, hardMajor);
        check.same("Coaching cumulative loss " + hintLoss + "‰ stays at or below Hard " + hardLoss + "‰", true, hintLoss <= hardLoss);
        check.same("Coaching large losses stay at or below Hard", true, hintMajor <= hardMajor);
    }

    private static int moveLoss(StockfishUci engine, String position, int depth, StockfishUci.Oracle reference, String move) throws Exception {
        if (reference.move().equals(move)) return 0;
        return Math.max(0, reference.expected() - engine.evaluatePosition(position, depth, move).expected());
    }

    @Test void evaluatesAllLevelsAndTheHintAgainstIndependentSearch(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        check.same("Benchmark schema", 1, ChessYaml.number(settings, "schema"));
        int depth = ChessYaml.number(settings, "depth");
        int major = ChessYaml.number(settings, "major-loss-permille");
        var metrics = new HashMap<String, List<Sample>>();
        for (String profile : PROFILES) metrics.put(profile, new ArrayList<>());
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned reference engine", ChessYaml.string(settings, "stockfish"), engine.name());
            for (Object item : ChessYaml.list(settings, "positions")) {
                var row = (Map<?, ?>) item;
                String id = ChessYaml.string(row, "id");
                String fen = ChessYaml.string(row, "fen");
                var board = ChessPosition.fromFen(fen);
                var best = engine.evaluate(fen, depth, null);
                var cache = new HashMap<String, StockfishUci.Oracle>();
                cache.put(best.move(), best);
                for (String profile : PROFILES) {
                    var pick = choose(board, profile);
                    String move = pick.move().uci();
                    check.same(id + " " + profile + " returns a legal move", true, board.legalMoves().contains(pick.move()));
                    if (!cache.containsKey(move)) cache.put(move, engine.evaluate(fen, depth, move));
                    var oracle = cache.get(move);
                    int loss = Math.max(0, best.expected() - oracle.expected());
                    var sample = new Sample(loss, move.equals(best.move()), pick.depth(), pick.extraNodes(), pick.millis());
                    metrics.get(profile).add(sample);
                    check.same(id + " " + profile + " picked " + move + " vs " + best.move()
                            + " | Stockfish score " + oracle.score() + " vs " + best.score()
                            + " | loss " + loss + "‰ | depth " + pick.depth() + " | extra nodes " + pick.extraNodes()
                            + " | " + pick.millis() + " ms | reference searched the same move", move, oracle.move());
                }
            }
        }
        for (String profile : PROFILES) {
            var samples = metrics.get(profile);
            double mean = samples.stream().mapToInt(Sample::loss).average().orElseThrow();
            long majorCount = samples.stream().filter(sample -> sample.loss() >= major).count();
            long top = samples.stream().filter(Sample::top).count();
            double meanDepth = samples.stream().mapToInt(Sample::depth).average().orElseThrow();
            long peakNodes = samples.stream().mapToInt(Sample::extraNodes).max().orElseThrow();
            var timings = samples.stream().map(Sample::millis).sorted().toList();
            long median = timings.get((timings.size() - 1) / 2);
            long p95 = timings.get((int) Math.ceil(timings.size() * .95) - 1);
            System.out.printf(java.util.Locale.ROOT, "%s n=%d hits=%d loss=%.1f major=%d mean-depth=%.1f peak-extra-nodes=%d median=%dms p95=%dms%n",
                    profile, samples.size(), top, mean, majorCount, meanDepth, peakNodes, median, p95);
            var limits = ChessYaml.map(ChessYaml.map(settings, "limits"), profile);
            check.same(profile + " mean expected-score loss " + mean + "‰ with " + top + "/" + samples.size()
                    + " exact reference picks", true, mean <= ChessYaml.number(limits, "mean-loss"));
            check.same(profile + " major losses " + majorCount + " at " + major + "‰", true,
                    majorCount <= ChessYaml.number(limits, "major-losses"));
        }
    }

    private static Pick choose(ChessPosition board, String profile) {
        long start = System.nanoTime();
        if ("HINT".equals(profile)) {
            var search = TacticalAnalysis.hintAnalysis(board);
            return new Pick(search.result().ranked().getFirst().move(), search.depth(), search.extraNodes(), elapsed(start));
        }
        var setup = new MatchSetup(board.turn().other(), MatchSetup.Level.valueOf(profile), false);
        var decision = ChessMatch.fromPosition(board, setup).botDecision();
        return new Pick(decision.move(), decision.depth(), decision.extraNodes(), elapsed(start));
    }

    @Test void qualifiesHardFrenchOnNewPositions(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        var suite = ChessYaml.resource("opening-calibration.yaml");
        int depth = ChessYaml.number(settings, "depth");
        int major = ChessYaml.number(settings, "major-loss-permille");
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned calibration reference", ChessYaml.string(settings, "stockfish"), engine.name());
            var french = ChessYaml.map(suite, "hard-regression");
            int frenchLoss = hardLoss(engine, depth, french, check);
            check.same("Hard French regression stays within " + ChessYaml.number(french, "max-loss") + "‰", true,
                    frenchLoss <= ChessYaml.number(french, "max-loss"));
            var hard = ChessYaml.map(suite, "hard-controls");
            int hardTotal = 0;
            var controls = ChessYaml.list(hard, "positions");
            for (Object item : controls) {
                int loss = hardLoss(engine, depth, (Map<?, ?>) item, check);
                hardTotal += loss;
                check.same("Hard control remains below major loss threshold " + major + "‰", true, loss < major);
            }
            double hardMean = (double) hardTotal / controls.size();
            System.out.printf(java.util.Locale.ROOT, "Calibration Hard anchor=%d‰ controls=%.1f‰%n", frenchLoss, hardMean);
            check.same("Hard held-out mean loss " + hardMean + "‰", true, hardMean <= ChessYaml.number(hard, "max-mean-loss"));
        }
    }

    @Test void qualifiesTheDisplayedEvaluationOnNewPositions(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        var evaluation = ChessYaml.map(ChessYaml.resource("opening-calibration.yaml"), "evaluation");
        int depth = ChessYaml.number(settings, "depth");
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned display reference", ChessYaml.string(settings, "stockfish"), engine.name());
            int anchor = displayedError(engine, depth, ChessYaml.map(evaluation, "anchor"), check);
            check.same("Displayed open-game anchor error " + anchor + "cp", true, anchor <= ChessYaml.number(evaluation, "max-anchor-error-cp"));
            int total = 0;
            var positions = ChessYaml.list(evaluation, "controls");
            for (Object item : positions) {
                int error = displayedError(engine, depth, (Map<?, ?>) item, check);
                total += error;
                check.same("Display control absolute error " + error + "cp", true,
                        error <= ChessYaml.number(evaluation, "max-control-case-error-cp"));
            }
            double mean = (double) total / positions.size();
            System.out.printf(java.util.Locale.ROOT, "Calibration display anchor=%dcp controls=%.1fcp%n", anchor, mean);
            check.same("Display controls mean absolute error " + mean + "cp", true,
                    mean <= ChessYaml.number(evaluation, "max-control-mean-error-cp"));
        }
    }

    private static int hardLoss(StockfishUci engine, int depth, Map<?, ?> row, Checks check) throws Exception {
        String id = ChessYaml.string(row, "id");
        String prefix = ChessYaml.string(row, "moves");
        String position = "startpos moves " + prefix;
        var pick = choose(replay(prefix), "HARD");
        var reference = engine.evaluatePosition(position, depth, null);
        var oracle = reference.move().equals(pick.move().uci()) ? reference : engine.evaluatePosition(position, depth, pick.move().uci());
        int loss = Math.max(0, reference.expected() - oracle.expected());
        check.same(id + " Hard " + pick.move().uci() + " vs " + reference.move() + " | " + oracle.score() + " vs " + reference.score()
                + " | loss " + loss + "‰ | depth " + pick.depth() + " | nodes " + pick.extraNodes(), pick.move().uci(), oracle.move());
        System.out.printf(java.util.Locale.ROOT, "Hard control %s %s vs %s loss=%d‰%n", id, pick.move().uci(), reference.move(), loss);
        return loss;
    }

    private static int displayedError(StockfishUci engine, int depth, Map<?, ?> row, Checks check) throws Exception {
        String id = ChessYaml.string(row, "id");
        String prefix = ChessYaml.string(row, "moves");
        var board = replay(prefix);
        var line = TacticalAnalysis.recommendations(board).getFirst();
        var display = MatchEvaluation.forPosition(board, line);
        var reference = engine.evaluatePosition("startpos moves " + prefix, depth, null);
        check.same(id + " compares finite scores", true, display.ending() == TacticalAnalysis.Ending.HORIZON && reference.score().startsWith("cp "));
        int perspective = board.turn() == ChessPosition.Side.WHITE ? 1 : -1;
        int referenceCp = perspective * Integer.parseInt(reference.score().substring(3));
        int raw = MatchEvaluation.from(board.turn(), line).whiteCentipawns();
        int error = Math.abs(display.whiteCentipawns() - referenceCp);
        String detail = id + " | raw " + raw + "cp | display " + display.whiteCentipawns()
                + "cp | Stockfish " + referenceCp + "cp | error " + error + "cp";
        check.same(detail, true, Double.isFinite(display.whiteShare()));
        System.out.println("Display control " + detail);
        return error;
    }

    private static ChessPosition replay(String prefix) {
        var board = ChessPosition.initial();
        for (String uci : prefix.split(" ")) board = board.play(Move.fromUci(uci));
        return board;
    }

    @Test void validatesTheFullAuthoredLinesAgainstStockfishMoveGeneration(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned opening legality oracle", ChessYaml.string(settings, "stockfish"), engine.name());
            for (var opening : OpeningCatalog.load()) {
                var prefix = new StringBuilder("startpos");
                var missing = new ArrayList<String>();
                for (int ply = 0; ply < opening.steps().size(); ply++) {
                    String move = opening.steps().get(ply).move().uci();
                    if (!engine.legalMoves(prefix.toString()).contains(move)) {
                        missing.add("ply " + (ply + 1) + " " + move);
                        break;
                    }
                    prefix.append(ply == 0 ? " moves " : " ").append(move);
                }
                check.same(opening.id() + " contains " + opening.steps().size() + " independently legal moves", List.of(), missing);
            }
        }
    }

    @Test void evaluatesOpeningPositionsAgainstIndependentSearch(Checks check) throws Exception {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        var openings = ChessYaml.resource("opening-ai-benchmark.yaml");
        int depth = ChessYaml.number(settings, "depth");
        int major = ChessYaml.number(settings, "major-loss-permille");
        var profiles = List.of("HARD", "HINT");
        var metrics = new HashMap<String, List<Sample>>();
        profiles.forEach(profile -> metrics.put(profile, new ArrayList<>()));
        try (var engine = new StockfishUci(Path.of(System.getProperty("chess.stockfish")))) {
            check.same("Pinned opening reference engine", ChessYaml.string(settings, "stockfish"), engine.name());
            for (Object item : ChessYaml.list(openings, "checkpoints")) {
                var row = (Map<?, ?>) item;
                String id = ChessYaml.string(row, "id");
                String prefix = ChessYaml.string(row, "moves");
                String position = "startpos moves " + prefix;
                var moves = prefix.split(" ");
                var board = ChessPosition.initial();
                for (String uci : moves) board = board.play(Move.fromUci(uci));
                var reference = engine.evaluatePosition(position, depth, null);
                var cache = new HashMap<String, StockfishUci.Oracle>();
                cache.put(reference.move(), reference);
                check.same(id + " reference move is also legal in J7", true, board.legalMoves().contains(Move.fromUci(reference.move())));
                for (String profile : profiles) {
                    var pick = choose(board, profile);
                    String move = pick.move().uci();
                    if (!cache.containsKey(move)) cache.put(move, engine.evaluatePosition(position, depth, move));
                    var oracle = cache.get(move);
                    int loss = Math.max(0, reference.expected() - oracle.expected());
                    metrics.get(profile).add(new Sample(loss, move.equals(reference.move()), pick.depth(), pick.extraNodes(), pick.millis()));
                    System.out.printf(java.util.Locale.ROOT, "Opening %s %s %s vs %s | %s vs %s | %d‰ | depth %d | %d ms%n",
                            id, profile, move, reference.move(), oracle.score(), reference.score(), loss, pick.depth(), pick.millis());
                    check.same(id + " at ply " + moves.length + " " + profile + " | " + move + " vs " + reference.move()
                            + " | " + oracle.score() + " vs " + reference.score() + " | loss " + loss + "‰",
                            move, oracle.move());
                }
            }
        }
        for (String profile : profiles) {
            var samples = metrics.get(profile);
            double mean = samples.stream().mapToInt(Sample::loss).average().orElseThrow();
            long majorCount = samples.stream().filter(sample -> sample.loss() >= major).count();
            System.out.printf(java.util.Locale.ROOT, "Opening summary %s n=%d hits=%d mean-loss=%.1f major=%d worst=%d%n",
                    profile, samples.size(), samples.stream().filter(Sample::top).count(), mean, majorCount,
                    samples.stream().mapToInt(Sample::loss).max().orElseThrow());
            var limits = ChessYaml.map(ChessYaml.map(openings, "limits"), profile);
            check.same(profile + " opening mean expected-score loss " + mean + "‰ stays within its cap", true,
                    mean <= ChessYaml.number(limits, "mean-loss"));
            check.same(profile + " opening losses at or over " + major + "‰", true,
                    majorCount <= ChessYaml.number(limits, "major-losses"));
        }
    }

    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }

    private record Pick(Move move, int depth, int extraNodes, long millis) { }
    private record Sample(int loss, boolean top, int depth, int extraNodes, long millis) { }
}
