package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
@EnabledIfSystemProperty(named = "chess.anchor", matches = ".+")
class AiAnchorBenchmarkTest {
    private record Played(AiLeague.Opening opening, Level level, Side side, ChessMatch match) {
        int scoreTwice() {
            return switch (match.result()) {
                case IN_PROGRESS -> -1;
                case WHITE_WINS -> side == Side.WHITE ? 2 : 0;
                case BLACK_WINS -> side == Side.BLACK ? 2 : 0;
                default -> 1;
            };
        }
    }

    @Test void estimatesTheThreeProfilesAgainstOneNamedStockfishReference(Checks check) throws Exception {
        var league = AiLeague.load(ChessYaml.resource("ai-league.yaml"));
        var anchor = ChessYaml.resource("ai-anchor.yaml");
        check.same("Anchor protocol schema and keys", true, ChessYaml.number(anchor, "schema") == 1
                && anchor.keySet().equals(Set.of("schema", "stockfish", "uci-elo", "nodes-per-move", "openings")));
        int elo = ChessYaml.number(anchor, "uci-elo");
        int nodes = ChessYaml.number(anchor, "nodes-per-move");
        check.same("The external reference uses a bounded fixed node request", true, nodes >= 1_000 && nodes <= 50_000);
        String reference = ChessYaml.string(anchor, "stockfish");
        Path binary = Path.of(System.getProperty("chess.stockfish"));
        var openings = ChessYaml.list(anchor, "openings").stream().map(id -> league.openings().stream()
                .filter(opening -> opening.id().equals(id)).findFirst().orElseThrow()).toList();
        check.same("The anchor uses each frozen league position exactly once per color", league.openings(), openings);
        var tasks = new ArrayList<Callable<Played>>();
        for (Level level : Level.values()) for (var opening : openings) for (Side side : Side.values()) {
            tasks.add(() -> play(binary, reference, elo, nodes, level, opening, side, league.maxPlies()));
        }
        var games = new ArrayList<Played>();
        try (var pool = Executors.newFixedThreadPool(3)) {
            for (var task : pool.invokeAll(tasks)) games.add(task.get());
        }
        var rows = new ArrayList<String>();
        rows.add("opening\tprofile\tJ7-side\treference\tresult\tplies\tmoves");
        int next = 0;
        for (Level level : Level.values()) {
            var pairs = new ArrayList<PairedElo.Pair>();
            for (var opening : openings) {
                var white = games.get(next++);
                var black = games.get(next++);
                check.same(level + " " + opening.id() + " has both colors", List.of(opening.id(), opening.id(), Side.WHITE, Side.BLACK),
                        List.of(white.opening().id(), black.opening().id(), white.side(), black.side()));
                int[] counts = new int[4];
                for (var game : List.of(white, black)) {
                    counts[game.scoreTwice() + 1]++;
                    String checkpoint = game.match().checkpoint();
                    check.same(level + " " + game.side() + " " + opening.id() + " replays to the recorded result", game.match().result(),
                            ChessMatch.restore(checkpoint).result());
                    rows.add(String.join("\t", opening.id(), level.name(), game.side().name(), reference + " UCI_Elo " + elo,
                            game.match().result().name(), Integer.toString(game.match().ply()), checkpoint.substring(2)));
                }
                pairs.add(new PairedElo.Pair(counts[3], counts[2], counts[1], counts[0]));
            }
            var fit = PairedElo.estimate(pairs, league.samples(), league.seed());
            String summary = String.format(Locale.ROOT, "%s vs %s UCI_Elo %d at %d nodes | W/D/L/U %d/%d/%d/%d | performance midpoint %.0f | paired 95%% range %.0f to %.0f",
                    level, reference, elo, nodes, pairs.stream().mapToInt(PairedElo.Pair::wins).sum(),
                    pairs.stream().mapToInt(PairedElo.Pair::draws).sum(), pairs.stream().mapToInt(PairedElo.Pair::losses).sum(),
                    pairs.stream().mapToInt(PairedElo.Pair::unfinished).sum(), elo + fit.eloMidpoint(),
                    elo + fit.eloLow95(), elo + fit.eloHigh95());
            System.out.println("Anchor " + summary);
            check.same(summary + " | opening pairs", openings.size(), pairs.size());
        }
        Files.write(Path.of(System.getProperty("chess.anchor")), rows, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static Played play(Path binary, String reference, int elo, int nodes, Level level, AiLeague.Opening opening,
                               Side side, int cap) throws Exception {
        try (var engine = new StockfishUci(binary)) {
            if (!engine.name().equals(reference)) throw new IllegalArgumentException("Unexpected anchor: " + engine.name());
            engine.limitStrength(elo);
            engine.newGame();
            var match = opening.start();
            while (match.result() == Result.IN_PROGRESS && match.ply() < cap) {
                var move = match.position().turn() == side ? match.botDecision(level).move()
                        : Move.fromUci(engine.play(match.checkpoint().substring(2), nodes));
                match = match.play(move);
            }
            return new Played(opening, level, side, match);
        }
    }
}
