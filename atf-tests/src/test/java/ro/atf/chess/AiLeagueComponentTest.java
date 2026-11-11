package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.MatchSetup.Level;
import ro.atf.chess.ChessMatch.Result;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AiLeagueComponentTest {
    @Test void convertsQueenAndRookEndgamesWithoutCyclingAndKeepsCoachPerspective(Checks check) {
        String queenWhite = "8/8/8/4k3/8/1K6/Q7/8 w - - 0 1";
        String queenBlack = "8/q7/1k6/8/4K3/8/8/8 b - - 0 1";
        for (Level level : Level.values()) {
            var white = AiLeague.game(endgame(queenWhite), level, Level.BASIC, 40);
            var black = AiLeague.game(endgame(queenBlack), Level.BASIC, level, 40);
            check.same(level + " converts queen against king with either color", List.of(Result.WHITE_WINS, Result.BLACK_WINS),
                    List.of(white.match().result(), black.match().result()));
        }
        var rook = AiLeague.game(endgame("8/8/4k3/8/8/8/4K3/R7 w - - 0 1"), Level.BASIC, Level.BASIC, 30);
        check.same("Basic escorts the rook with its king and delivers mate", Result.WHITE_WINS, rook.match().result());
        var board = endgame(queenBlack).start().position();
        var best = TacticalAnalysis.recommendations(board).getFirst();
        var score = MatchEvaluation.forPosition(board, best);
        check.same("The same endgame advantage reaches the analysis bar from White's perspective", true, score.whiteShare() < .5);
        var replay = board;
        for (var move : best.line()) replay = replay.play(move);
        check.same("The coaching line remains legal from the same endgame position", true, replay.hasLegalMove() || replay.isCheckmate());

        var coached = endgame(queenBlack).start();
        while (coached.result() == Result.IN_PROGRESS && coached.ply() < 40) {
            var move = coached.position().turn() == ChessPosition.Side.BLACK ? TacticalAnalysis.recommendations(coached.position()).getFirst().move()
                    : coached.botDecision(Level.BASIC).move();
            coached = coached.play(move);
        }
        check.same("Following the strongest coaching line converts the same Black endgame", Result.BLACK_WINS, coached.result());
    }

    private static AiLeague.Opening endgame(String fen) {
        return new AiLeague.Opening("endgame", "", ChessMatch.fromPosition(ChessPosition.fromFen(fen)));
    }

    @Test void usesWholeOpeningPairsAndSeparatesUnfinishedGamesFromDraws(Checks check) {
        var pairs = List.of(new PairedElo.Pair(1, 1, 0, 0), new PairedElo.Pair(0, 0, 1, 1));
        var estimate = PairedElo.estimate(pairs, 10_000, 42);
        check.same("An unfinished game contributes neither a loss nor a recorded draw", List.of(.375, .625),
                List.of(estimate.scoreLow(), estimate.scoreHigh()));
        check.same("The displayed midpoint is neutral for this sample", 0.0, estimate.eloMidpoint());
        check.same("The paired interval includes the full uncertainty of each sampled opening", true,
                estimate.eloLow95() < PairedElo.elo(.375) && estimate.eloHigh95() > PairedElo.elo(.625));
        check.same("The bootstrap is reproducible with a fixed seed", estimate, PairedElo.estimate(pairs, 10_000, 42));
        check.same("Two wins on every opening have no finite Elo point estimate", Double.POSITIVE_INFINITY,
                PairedElo.estimate(List.of(new PairedElo.Pair(2, 0, 0, 0), new PairedElo.Pair(2, 0, 0, 0)), 1_000, 42).eloLow95());
        check.same("A 75% result corresponds to about +191 Elo", 191L, Math.round(PairedElo.elo(.75)));
    }

    @Test void replaysTheProductionBotWithBothColorsAndPreservesTheUnfinishedStatus(Checks check) {
        var opening = AiLeague.load(ChessYaml.resource("ai-league.yaml")).openings().getFirst();
        var cap = opening.start().ply() + 2;
        var forward = AiLeague.game(opening, Level.HARD, Level.BASIC, cap);
        var reverse = AiLeague.game(opening, Level.BASIC, Level.HARD, cap);
        var ordinary = ChessMatch.restore("3|BLACK|HARD|0|" + opening.moves());
        check.same("The tournament makes the same decision as a normal Hard match", ordinary.chooseBotMove(), forward.match().moveAt(cap - 1));
        check.same("Each bot plays one move in each short game", List.of(1, 1, 1, 1),
                List.of(forward.whiteStats().turns(), forward.blackStats().turns(), reverse.whiteStats().turns(), reverse.blackStats().turns()));
        check.same("A capped pair is recorded as two unfinished games", new PairedElo.Pair(0, 0, 0, 2), forward.pairWith(reverse));
        check.same("The checkpoint replays with the same outcome", forward.match().result(),
                ChessMatch.restore(forward.match().checkpoint()).result());
        check.same("A second game with the same colors is rejected", "The second game must reverse the opening's profiles",
                assertThrows(IllegalArgumentException.class, () -> forward.pairWith(forward)).getMessage());
    }

    @Test void frozenLeagueRejectsTranspositionsAndInvalidSamplingLimits(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("ai-league.yaml"));
        var positions = new ArrayList<Object>(ChessYaml.list(root, "openings"));
        var policy = AiLeague.load(root);
        check.same("The audited league has at least twelve independent openings", true, policy.openings().size() >= 12);
        check.same("Both colors appear as the first bot to move", 2L,
                policy.openings().stream().map(opening -> opening.start().position().turn()).distinct().count());
        positions.add(Map.of("id", "english-transposed", "moves", "g2g3 g7g6 c2c4 c7c5 b1c3 b8c6"));
        root.put("openings", positions);
        check.same("The same board via a transposition cannot count as another independent pair", "Invalid or duplicate opening english-transposed",
                assertThrows(IllegalArgumentException.class, () -> AiLeague.load(root)).getMessage());
        root.put("bootstrap-samples", 1);
        check.same("A single bootstrap sample is rejected", "Invalid AI league limits",
                assertThrows(IllegalArgumentException.class, () -> AiLeague.load(root)).getMessage());
    }
}
