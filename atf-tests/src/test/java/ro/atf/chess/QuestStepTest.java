package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestStepTest {
    @Test void restoresTheBoardBeforeAndAfterEachDefense(Checks check) {
        var quest = QuestCatalog.loadMateInThree().getFirst();
        var pending = QuestStep.restore(1, quest);
        var afterPlayer = pending.position(quest);
        check.same("After Qxc8 White's queen is visible on c8", new Piece(Side.WHITE, Kind.QUEEN), afterPlayer.pieceAt(BoardSquare.parse("c8")));
        check.same("Black's bishop is still on b7 before the defense", new Piece(Side.BLACK, Kind.BISHOP), afterPlayer.pieceAt(BoardSquare.parse("b7")));
        check.same("The pending frame has Black to move", Side.BLACK, afterPlayer.turn());
        check.same("The player's move supplies the highlight before the defense", "c4c8", pending.lastMove(quest).uci());
        var defended = pending.advance();
        check.same("The defense replaces White's queen with Black's bishop", new Piece(Side.BLACK, Kind.BISHOP), defended.position(quest).pieceAt(BoardSquare.parse("c8")));
        check.same("The ready frame returns the turn to White", Side.WHITE, defended.position(quest).turn());
        check.same("The completed defense supplies the new highlight", "b7c8", defended.lastMove(quest).uci());
        var resumed = QuestStep.restore(3, quest);
        check.same("Restart after the second player move still owes one defense", true, resumed.defensePending());
        check.same("Finishing the pending defense reaches the third player move", "f6d8", resumed.advance().lastMove(quest).uci());
        check.same("A saved ply beyond the reference line resumes at the final playable position", resumed.advance(), QuestStep.restore(9, quest));
    }

    @Test void keepsTheCurrentChapterLockedUntilThePuzzleIsSolved(Checks check) {
        var quest = QuestCatalog.loadMateInTwo().getFirst();
        var progress = new QuestProgress(quest.id(), java.util.Set.of("mate-001"));
        var step = QuestStep.restore(1, quest);
        check.same("Mate in 2 restores the same pending defense", true, step.defensePending());
        check.same("A solve in another chapter cannot unlock Next", false, progress.canAdvance(0, 100));
        check.same("The defense restores the last playable stage", 1, step.advance().stage());
        check.same("A final mate unlocks the next position in the same chapter", true, progress.solveCurrent().canAdvance(0, 100));
        check.same("The final position cannot advance past the end of its chapter", false, progress.solveCurrent().canAdvance(99, 100));
    }
}
