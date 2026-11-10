package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
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

    @Test void restoresTheActualMateAndItsExplanation(Checks check) {
        var quest = QuestCatalog.loadMateInThree().stream()
                .filter(entry -> entry.id().equals("mate3-01Kz0")).findFirst().orElseThrow();
        var step = QuestStep.restore(4, quest);
        var played = Move.fromUci("f8f1");
        check.same("The catalog offers a different reference finish", "e1f1", quest.finishingMove().uci());
        var result = quest.assess(played, step.stage());
        check.same("The alternate rook move is accepted as mate", true, result.solved());

        var restored = quest.restoreFinish(step.finishCheckpoint(played));
        check.same("The prefix survives and the actual finish is restored", quest.line().subList(0, 4), restored.line().subList(0, 4));
        check.same("The highlight follows the move the player made", played, restored.finishingMove());
        check.same("The restored board matches the position produced by the alternate mate",
                true, result.position().sameForRepetition(restored.solvedPosition()));
        check.same("The explanation describes the mate on the restored board", result.position().explainMate(), restored.explanation());
    }

    @Test void fallsBackToTheReferenceForLegacyOrInvalidFinishes(Checks check) {
        var quest = QuestCatalog.loadMateInTwo().stream()
                .filter(entry -> entry.id().equals("mate2-00Vdx")).findFirst().orElseThrow();
        check.same("Legacy progress without a saved finish keeps its reference line", quest, quest.restoreFinish(null));
        check.same("An out-of-range stage cannot replace the reference line", quest, quest.restoreFinish("2:g2g3"));
        check.same("An illegal rook diagonal cannot replace the reference line", quest, quest.restoreFinish("1:g2h3"));
        var before = quest.positionAt(1);
        var notMate = before.legalMoves().stream().filter(move -> !before.play(move).isCheckmate()).findFirst().orElseThrow();
        check.same("A legal move that does not mate cannot replace the reference line",
                quest, quest.restoreFinish(QuestStep.restore(2, quest).finishCheckpoint(notMate)));
    }
}
