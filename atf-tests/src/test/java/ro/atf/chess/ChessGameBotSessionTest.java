package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessGameSession.Outcome;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessGameBotSessionTest {
    @Test void keepsTheChosenColorProfileAndAnalysisSettingWhenReopeningTheGame(Checks check) {
        for (Side side : Side.values()) {
            for (Level level : Level.values()) {
                for (boolean analysis : List.of(false, true)) {
                    var setup = new MatchSetup(side, level, analysis);
                    var checkpoint = ChessMatch.start(setup).checkpoint();
                    var restored = new ChessGameSession(ChessMatch.restore(checkpoint), true);
                    String expectedCheckpoint = side == Side.WHITE && level == Level.BASIC ? analysis ? "2|analysis|" : "1|"
                            : "3|" + side + "|" + level + "|" + (analysis ? "1|" : "0|");
                    check.same(side + " / " + level + " / analysis " + analysis + " preserves the exact setup and White's first turn",
                            List.of(setup, expectedCheckpoint, Side.WHITE, side == Side.BLACK, 0L),
                            List.of(restored.match().setup(), restored.match().checkpoint(), restored.match().position().turn(),
                                    restored.awaitingBot(), restored.revision()));
                }
            }
        }
    }

    @Test void resumesTheBotOpeningOnceAndDoesNotReplayItAfterTheReplyWasSaved(Checks check) {
        var unopened = ChessMatch.restore("3|BLACK|HARD|0|");
        var pending = new ChessGameSession(unopened, true);
        check.same("The reopened Black seat is waiting for White's computer move", true, pending.awaitingBot());
        check.same("Human input cannot play for the computer", Outcome.UNAVAILABLE, pending.play(0, move("e2e4")));
        check.same("A rejected click keeps the pending checkpoint and revision", List.of(unopened.checkpoint(), 0L),
                List.of(pending.match().checkpoint(), pending.revision()));
        var expected = unopened.chooseBotMove();

        check.same("The resumed computer move applies once at the live revision", Outcome.APPLIED, pending.playBot(0));
        var finishedReply = pending.match();
        check.same("The committed reply agrees with the production bot policy and gives control to Black",
                List.of(expected, unopened.play(expected).checkpoint(), 1L, Side.BLACK, false),
                List.of(finishedReply.lastMove(), finishedReply.checkpoint(), pending.revision(), finishedReply.position().turn(),
                        pending.awaitingBot()));
        check.same("Retried delivery of the old revision is stale", Outcome.STALE, pending.playBot(0));
        check.same("The current revision cannot schedule a reply on the human turn", Outcome.UNAVAILABLE, pending.playBot(1));
        check.same("The repeated requests leave one move in the resulting game", List.of(1, 1L),
                List.of(pending.match().ply(), pending.revision()));

        var restored = new ChessGameSession(ChessMatch.restore(finishedReply.checkpoint()), true);
        check.same("Reopening after the durable reply leaves the human to move", List.of(false, expected, 1),
                List.of(restored.awaitingBot(), restored.match().lastMove(), restored.match().ply()));
        check.same("A fresh revision cannot duplicate a reply already present in the checkpoint", Outcome.UNAVAILABLE,
                restored.playBot(restored.revision()));
        check.same("The prior pending snapshot remains byte-identical", "3|BLACK|HARD|0|", unopened.checkpoint());
    }

    @Test void waitsForTheHumanMoveAndLeavesTheManualModeIndependent(Checks check) {
        var game = new ChessGameSession(ChessMatch.start(), true);
        check.same("White starts a new automatic game with the human turn", false, game.awaitingBot());
        check.same("No bot move is allowed before the human moves", Outcome.UNAVAILABLE, game.playBot(0));
        check.same("The human records the first move", Outcome.APPLIED, game.play(0, move("e2e4")));
        check.same("Only the committed human move starts the computer turn", List.of(true, 1L, "1|e2e4"),
                List.of(game.awaitingBot(), game.revision(), game.match().checkpoint()));
        check.same("An older revision is stale even if the computer now has the turn", Outcome.STALE, game.playBot(0));

        var manual = new ChessGameSession(ChessMatch.restore(game.match().checkpoint()));
        check.same("The same raw checkpoint opens in manual mode without a pending computer move", false, manual.awaitingBot());
        check.same("Manual mode never runs the computer", Outcome.UNAVAILABLE, manual.playBot(manual.revision()));
        check.same("Manual mode still accepts a legal move for Black", Outcome.APPLIED, manual.play(manual.revision(), move("e7e5")));
        check.same("A manual continuation cannot overwrite the automatic session", List.of("1|e2e4 e7e5", "1|e2e4"),
                List.of(manual.match().checkpoint(), game.match().checkpoint()));
    }

    @Test void cannotScheduleAComputerMoveAfterCheckmate(Checks check) {
        var match = ChessMatch.restore("3|BLACK|HARD|0|f2f3 e7e5 g2g4 d8h4");
        var game = new ChessGameSession(match, true);
        check.same("A completed checkpoint has no computer turn", false, game.awaitingBot());
        check.same("The computer cannot play past the completed result", Outcome.UNAVAILABLE, game.playBot(game.revision()));
        check.same("The completed checkpoint and result stay intact", List.of(match, ChessMatch.Result.BLACK_WINS, 0L),
                List.of(game.match(), game.match().result(), game.revision()));
    }

    private static Move move(String uci) { return Move.fromUci(uci); }
}
