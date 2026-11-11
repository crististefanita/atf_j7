package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.ColorChoice;
import ro.atf.chess.MatchSetup.Level;


@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchSetupTest {
    @Test void savesTheResolvedSeatAndTheWhiteOpeningForABlackPlayer(Checks check) {
        check.same("The explicit seats ignore the random source", List.of(Side.WHITE, Side.BLACK),
                List.of(ColorChoice.WHITE.resolve(() -> true), ColorChoice.BLACK.resolve(() -> false)));
        check.same("Random can resolve to either seat", List.of(Side.WHITE, Side.BLACK),
                List.of(ColorChoice.RANDOM.resolve(() -> false), ColorChoice.RANDOM.resolve(() -> true)));
        var setup = new MatchSetup(ColorChoice.RANDOM.resolve(() -> true), Level.HARD, true);
        var before = ChessMatch.restore(ChessMatch.start(setup).checkpoint());
        check.same("The resolved settings survive before any move", setup, before.setup());
        check.same("The computer opens for White when the human is Black", true, before.awaitingBot());
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
        check.same("Basic, Advanced and Hard complete three distinct depths", List.of(3, 4, 5), depths);
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
            check.same(id + " uses at most the Hard budget", true, decision.extraNodes() <= MatchAiPolicy.CURRENT.opponent(Level.HARD).extraNodes());
        }
    }
}
