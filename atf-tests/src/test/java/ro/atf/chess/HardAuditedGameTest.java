package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardAuditedGameTest {
    @Test void everyMoveNeedsOneCachedAnalysisIncludingTheOpponentsMove(Checks check) {
        var calls = new AtomicInteger();
        var game = new HardAuditedGame(start(), match -> { calls.incrementAndGet(); return mock(match); });
        var white = ChessPosition.Move.fromUci("e2e4");
        check.rejects("Playing without native evidence is refused", IllegalStateException.class, () -> game.play(white));
        var first = game.analyze();
        check.same("Repeated inspection reuses the exact current receipt", true, first == game.analyze());
        check.same("Repeated inspection does not call the analyzer again", 1, calls.get());
        game.play(white);
        check.same("A played move advances exactly one checkpoint", 1, game.match().ply());
        var black = ChessPosition.Move.fromUci("e7e5");
        check.rejects("The opponent also requires a new native analysis", IllegalStateException.class, () -> game.play(black));
        var second = game.analyze();
        check.same("The second receipt belongs to the new full history", game.match().checkpoint(), second.checkpoint());
        check.same("There has been one analysis for each observed position", 2, calls.get());
        game.play(black);
        check.same("A legal opposing choice may differ from the Hard shadow recommendation", 2, game.match().ply());
    }

    @Test void foreignEvidenceAndIllegalMovesDoNotConsumeTheValidCheckpoint(Checks check) {
        var current = start();
        var foreign = mock(current.play(ChessPosition.Move.fromUci("d2d4")));
        var rejected = new HardAuditedGame(current, ignored -> foreign);
        check.rejects("An analysis from another game is refused", IllegalArgumentException.class, rejected::analyze);
        check.same("Rejecting a foreign receipt cannot advance the game", current.checkpoint(), rejected.match().checkpoint());
        var game = new HardAuditedGame(current, HardAuditedGameTest::mock);
        var receipt = game.analyze();
        check.rejects("An illegal play is refused without spending the pending receipt", IllegalArgumentException.class,
                () -> game.play(ChessPosition.Move.fromUci("e2e5")));
        check.same("The same valid receipt remains after an invalid click", true, receipt == game.analyze());
        game.play(ChessPosition.Move.fromUci("e2e4"));
        check.same("The valid move remains playable after the rejection", 1, game.match().ply());
    }

    private static ChessMatch start() {
        return ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE, MatchSetup.Level.HARD, true));
    }

    private static HardMoveAudit.Entry mock(ChessMatch match) {
        var board = match.position();
        var move = board.legalMoves().getFirst();
        var end = board.play(move);
        var line = new TacticalAnalysis.Variation(List.of(move), 0, HintAuditSession.materialChangeCp(board, end), TacticalAnalysis.Ending.HORIZON);
        var theory = OpeningRepertoire.CURRENT.playReply(board, MatchSetup.Level.HARD);
        return new HardMoveAudit.Entry(match.checkpoint(), HintAuditSession.fen(match), match.ply(), board.turn().name(), move.uci(), 1, 1,
                theory == null ? null : theory.uci(), "NATIVE_POSITION_SCORE_WITH_POSSIBLE_HORIZON_CREDIT_NOT_ORACLE", 1, List.of(line));
    }
}
