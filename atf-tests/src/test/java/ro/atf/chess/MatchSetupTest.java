package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.ColorChoice;
import ro.atf.chess.MatchSetup.Level;


@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchSetupTest {
    private record ColorCase(String id, ColorChoice choice, boolean randomBlack, Side side, int draws,
                             boolean blackBelow, boolean awaitingBot, Level level) { }

    @Test void sharedColorChoicesKeepTheResolvedBoardAndOpeningTurn(Checks check) throws IOException {
        ColorCase[] cases;
        try (var fixture = MatchSetupTest.class.getResourceAsStream("/match-color-choices.json")) {
            cases = new ObjectMapper().readValue(fixture, ColorCase[].class);
        }
        check.same("The shared fixture covers both fixed colors and both random outcomes", 4, cases.length);
        for (var example : cases) {
            var draws = new AtomicInteger();
            var side = example.choice().resolve(() -> {
                draws.incrementAndGet();
                return example.randomBlack();
            });
            var match = ChessMatch.start(new MatchSetup(side, example.level(), true));
            var preferences = new MemoryPreferences();
            var setupStore = store(preferences);
            check.same(example.id() + " writes the new match", true,
                    setupStore.startMatch(1, match, side == Side.BLACK, false, false));
            var reopened = store(preferences);
            var restored = reopened.readMatch(1);
            check.same(example.id() + " keeps the resolved color, level and orientation",
                    List.of(new MatchSetup(example.side(), example.level(), true), example.blackBelow()),
                    List.of(restored.setup(), reopened.matchBlackBelow(1)));
            check.same(example.id() + " keeps the opening turn and expected draw count",
                    List.of(0, Side.WHITE, example.awaitingBot(), example.draws()),
                    List.of(restored.ply(), restored.position().turn(), restored.awaitingBot(), draws.get()));
        }
    }

    private static MatchStore store(MemoryPreferences preferences) {
        return new MatchStore(preferences, () -> 1L, () -> "00000000-0000-4000-8000-000000000000");
    }

    @Test void resumesAfterTheComputersWhiteOpeningForABlackPlayer(Checks check) {
        var setup = new MatchSetup(Side.BLACK, Level.HARD, true);
        var before = ChessMatch.restore(ChessMatch.start(setup).checkpoint());
        var opening = before.chooseBotMove();
        check.same("The White opening is legal", true, before.position().legalMoves().contains(opening));
        var resumed = ChessMatch.restore(before.play(opening).checkpoint());
        check.same("The same match resumes on the human's Black turn", List.of(setup, 1, false, Side.BLACK),
                List.of(resumed.setup(), resumed.ply(), resumed.awaitingBot(), resumed.position().turn()));
        check.same("A legacy checkpoint still resumes as White with Basic", new MatchSetup(Side.WHITE, Level.BASIC, true),
                ChessMatch.restore("2|analysis|e2e4").setup());
        check.rejects("A malformed saved analysis option is rejected", IllegalArgumentException.class,
                () -> ChessMatch.restore("3|BLACK|HARD|yes|e2e4"));
    }

    @Test void theThreeLevelsReachDifferentCompletedHorizonsOnTheSamePosition(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/8/7p/P7/8/8/4K3 w - - 0 1");
        var depths = java.util.Arrays.stream(Level.values())
                .map(level -> TacticalAnalysis.botAnalysis(position, level).depth()).toList();
        check.same("In a quiet position Basic completes two plies and Advanced completes four", List.of(2, 4), depths.subList(0, 2));
        check.same("Hard completes the new sixth horizon on this position", 6, depths.get(2));
    }

    @Test void hardConfirmsUnstableOpeningMovesWithinTheExistingBudget(Checks check) {
        var catalog = OpeningCatalog.load();
        for (String id : List.of("french", "scandinavian")) {
            var lesson = catalog.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
            boolean french = id.equals("french");
            var position = lesson.positionAt(french ? 9 : 7);
            var match = ChessMatch.fromPosition(position, new MatchSetup(Side.WHITE, Level.HARD, false));
            var decision = match.botDecision();
            check.same(id + " Hard decision rejects the premature queen move", french ? "c5d4" : "g8f6", decision.move().uci());
            check.same(id + " uses at most the Hard budget", true,
                    decision.extraNodes() <= HardAdaptivePolicy.CURRENT.search().extraNodes());
        }
    }
}
