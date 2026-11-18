package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardHintAuditedGameTest {
    @Test void cachesNativeAndPublicHintSeparatelyBeforeEveryPlayedMove(Checks check) {
        var nativeCalls = new AtomicInteger();
        var hintCalls = new AtomicInteger();
        var game = new HardHintAuditedGame(ChessMatch.start(true), match -> { nativeCalls.incrementAndGet(); return nativeMock(match); },
                board -> { hintCalls.incrementAndGet(); return hintMock(board); });
        check.rejects("A prefix move also requires both analyses", IllegalStateException.class, () -> game.play(move("e2e4")));
        var first = game.analyze();
        check.same("Repeated inspection reuses the exact paired receipt", true, first == game.analyze());
        check.same("Each native and hint search runs exactly once", List.of(1, 1), List.of(nativeCalls.get(), hintCalls.get()));
        check.same("A differing hint never replaces the native decision", false, first.hard().selected().equals(first.hint().lines().getFirst().move().uci()));
        check.rejects("An illegal move cannot consume the paired receipt", IllegalArgumentException.class, () -> game.play(move("e2e5")));
        check.same("A failed play retains the same evidence", true, first == game.analyze());
        game.play(move("e2e4"));
        check.rejects("The opposing turn requires a fresh paired analysis", IllegalStateException.class, () -> game.play(move("e7e5")));
        game.analyze();
        check.same("The two engines are independently observed at the next checkpoint", List.of(2, 2), List.of(nativeCalls.get(), hintCalls.get()));
        game.play(move("e7e5"));
        check.same("The legal opposing move is preserved even when different from a recommendation", 2, game.match().ply());
    }

    @Test void rejectsMissingAlteredOrBorrowedHintEvidence(Checks check) {
        var match = ChessMatch.start(true);
        var hard = nativeMock(match);
        var hint = hintMock(match.position());
        var entry = new HardHintAuditedGame.Entry(match.checkpoint(), HintAuditSession.fen(match), hard, hint,
                new MatchEvaluation(TacticalAnalysis.Ending.HORIZON, 0, 0));
        HardHintAuditedGame.validate(entry);
        check.rejects("A hint without a result cannot authorize play", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(withHint(entry, new HintAuditSession.Search(List.of(), 1, 1, 1, 0))));
        check.rejects("One node above the hint's total ceiling is rejected", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(withHint(entry, new HintAuditSession.Search(hint.lines(), 200_001, 1, 1, 0))));
        var line = hint.lines().getFirst();
        var changed = new TacticalAnalysis.Variation(line.line(), line.score(), line.materialChange() + 1, line.ending());
        check.rejects("Changing a material ledger cannot remain a valid public PV", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(withHint(entry, new HintAuditSession.Search(List.of(changed), 1, 1, 1, 0))));
        check.rejects("A borrowed comparison depth without its raw candidates is rejected", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(withHint(entry, new HintAuditSession.Search(hint.lines(), List.of(), 1, 1, 1, 2))));
        var other = match.play(move("d2d4"));
        check.rejects("A native receipt from a different checkpoint cannot be attached to the hint", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(new HardHintAuditedGame.Entry(entry.checkpoint(), entry.fen(), nativeMock(other), hint, entry.evaluation())));
        check.rejects("The displayed estimate cannot silently disappear", IllegalArgumentException.class,
                () -> HardHintAuditedGame.validate(new HardHintAuditedGame.Entry(entry.checkpoint(), entry.fen(), hard, hint, null)));
    }

    private static HardHintAuditedGame.Entry withHint(HardHintAuditedGame.Entry entry, HintAuditSession.Search hint) {
        return new HardHintAuditedGame.Entry(entry.checkpoint(), entry.fen(), entry.hard(), hint, entry.evaluation());
    }

    private static HardMoveAudit.Entry nativeMock(ChessMatch match) {
        var board = match.position();
        var line = variation(board, board.legalMoves().getFirst());
        var theory = OpeningRepertoire.CURRENT.playReply(board, MatchSetup.Level.HARD);
        return new HardMoveAudit.Entry(match.checkpoint(), HintAuditSession.fen(match), match.ply(), board.turn().name(), line.move().uci(),
                1, 1, theory == null ? null : theory.uci(), "NATIVE_POSITION_SCORE_WITH_POSSIBLE_HORIZON_CREDIT_NOT_ORACLE", 1, List.of(line));
    }

    private static HintAuditSession.Search hintMock(ChessPosition board) {
        return new HintAuditSession.Search(List.of(variation(board, board.legalMoves().getLast())), 1, 1, 1, 0);
    }

    private static TacticalAnalysis.Variation variation(ChessPosition board, ChessPosition.Move move) {
        return new TacticalAnalysis.Variation(List.of(move), 0, HintAuditSession.materialChangeCp(board, board.play(move)), TacticalAnalysis.Ending.HORIZON);
    }

    private static ChessPosition.Move move(String uci) { return ChessPosition.Move.fromUci(uci); }
}
