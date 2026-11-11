package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
@EnabledIfSystemProperty(named = "chess.league", matches = ".+")
class AiLeagueBenchmarkTest {
    @Test void playsAllLevelsWithBothColorsAndReportsPairedStrength(Checks check) throws Exception {
        var policy = AiLeague.load(ChessYaml.resource("ai-league.yaml"));
        int workers = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
        var games = AiLeague.play(policy, workers);
        Path replay = Path.of(System.getProperty("chess.league"));
        var lines = new ArrayList<String>();
        lines.add("opening\twhite\tblack\tresult\tplies\tmoves");
        var stats = new EnumMap<Level, List<AiLeague.Seat>>(Level.class);
        for (Level level : Level.values()) stats.put(level, new ArrayList<>());
        for (var game : games) {
            var match = game.match();
            String checkpoint = match.checkpoint();
            lines.add(String.join("\t", game.opening().id(), game.white().name(), game.black().name(), match.result().name(),
                    Integer.toString(match.ply()), checkpoint.substring(2)));
            stats.get(game.white()).add(game.whiteStats());
            stats.get(game.black()).add(game.blackStats());
            check.same(game.opening().id() + " " + game.white() + "–" + game.black() + " at ply " + match.ply() + " replays to the recorded result",
                    match.result(), ChessMatch.restore(checkpoint).result());
            check.same("Every played turn has a profile and an unfinished result uses the full cap", true,
                    game.whiteStats().turns() + game.blackStats().turns() == match.ply() - game.opening().start().ply()
                            && (match.result() != ChessMatch.Result.IN_PROGRESS || match.ply() == policy.maxPlies()));
        }
        Files.write(replay, lines, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        var levels = Level.values();
        int index = 0;
        for (int first = 1; first < levels.length; first++) for (int second = 0; second < first; second++) {
            var pairs = new ArrayList<PairedElo.Pair>();
            for (int opening = 0; opening < policy.openings().size(); opening++) {
                pairs.add(games.get(index).pairWith(games.get(index + 1)));
                index += 2;
            }
            var rating = PairedElo.estimate(pairs, policy.samples(), policy.seed());
            String summary = String.format(Locale.ROOT, "%s vs %s | W/D/L/U %d/%d/%d/%d | score %.1f–%.1f%% | Elo midpoint %.0f | paired 95%% range %.0f to %.0f",
                    levels[first], levels[second], pairs.stream().mapToInt(PairedElo.Pair::wins).sum(),
                    pairs.stream().mapToInt(PairedElo.Pair::draws).sum(), pairs.stream().mapToInt(PairedElo.Pair::losses).sum(),
                    pairs.stream().mapToInt(PairedElo.Pair::unfinished).sum(), rating.scoreLow() * 100, rating.scoreHigh() * 100,
                    rating.eloMidpoint(), rating.eloLow95(), rating.eloHigh95());
            System.out.println("League " + summary);
            check.same(summary + " | pairs", policy.openings().size(), pairs.size());
        }
        for (var entry : stats.entrySet()) {
            var seats = entry.getValue();
            int turns = seats.stream().mapToInt(AiLeague.Seat::turns).sum();
            String style = String.format(Locale.ROOT, "%s | turns %d | captures %.1f%% | checks %.1f%% | castles %d/%d | mean depth %.2f | mean extra nodes %.0f",
                    entry.getKey(), turns, 100.0 * seats.stream().mapToInt(AiLeague.Seat::captures).sum() / turns,
                    100.0 * seats.stream().mapToInt(AiLeague.Seat::checks).sum() / turns,
                    seats.stream().mapToInt(AiLeague.Seat::castles).sum(), seats.size(),
                    (double) seats.stream().mapToInt(AiLeague.Seat::depthSum).sum() / turns,
                    (double) seats.stream().mapToLong(AiLeague.Seat::extraNodes).sum() / turns);
            System.out.println("League style " + style);
            check.same(style + " | seats", 4 * policy.openings().size(), seats.size());
        }
        check.same("Every game is present in the replay", games.size() + 1, Files.readAllLines(replay).size());
    }
}
