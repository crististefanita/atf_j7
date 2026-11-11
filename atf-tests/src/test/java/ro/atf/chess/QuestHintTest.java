package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestHintTest {
    @Test void revealsOnlyTheSourceUntilThePlayerRequestsTheMove(Checks check) {
        var quests = QuestCatalog.load();
        var levels = List.of(QuestHint.NONE, QuestHint.NONE.advance(), QuestHint.NONE.advance().advance());
        check.same("One action reveals the piece, a second reveals the move", List.of(QuestHint.NONE, QuestHint.PIECE, QuestHint.SOLUTION), levels);
        check.same("The unopened hint exposes neither square", true,
                quests.stream().allMatch(quest -> levels.get(0).cue(quest.solution()).equals(new QuestHint.Cue(-1, -1))));
        check.same("The piece hint hides the destination in every quest", true,
                quests.stream().allMatch(quest -> levels.get(1).cue(quest.solution()).equals(new QuestHint.Cue(quest.solution().from(), -1))));
        check.same("The complete hint follows the verified move even for castling, promotions and counter-mates", true,
                quests.stream().allMatch(quest -> levels.get(2).cue(quest.solution())
                        .equals(new QuestHint.Cue(quest.solution().from(), quest.solution().to()))));
        check.same("Requesting the explanation stays at the complete hint", QuestHint.SOLUTION, levels.get(2).advance());
    }

    @Test void aHintAfterTheDefensePointsToTheNewMove(Checks check) {
        var quest = QuestCatalog.loadMateInThree().getFirst();
        var step = QuestStep.restore(4, quest);
        var move = quest.playerMove(step.stage());
        var piece = QuestHint.PIECE.cue(move);
        var arrow = QuestHint.SOLUTION.cue(move);
        check.same("The old opening square no longer appears in the hint", "c8", BoardSquare.name(piece.origin()));
        check.same("The first level still hides the destination on the last turn", -1, piece.destination());
        check.same("The second level shows the final mating square", "d8", BoardSquare.name(arrow.destination()));
        check.same("The suggested move is legal for the active color", true, step.position(quest).legalMoves().contains(move));
    }

    @Test void displayMappingPreservesHintRolesInBothOrientations(Checks check) {
        var move = ChessPosition.Move.fromUci("d8h4");
        check.same("Black below anchors the piece at row 8 column 5 and destination at row 4 column 1",
                new QuestHint.DisplayCue(60, 24), QuestHint.SOLUTION.cue(move).onBoard(true));
        check.same("White below anchors the same piece at row 1 column 4 and destination at row 5 column 8",
                new QuestHint.DisplayCue(3, 39), QuestHint.SOLUTION.cue(move).onBoard(false));
        check.same("The piece-only hint leaves no destination after a flip",
                new QuestHint.DisplayCue(3, -1), QuestHint.PIECE.cue(move).onBoard(false));
        check.same("Closing the hint clears both links",
                new QuestHint.DisplayCue(-1, -1), QuestHint.NONE.cue(move).onBoard(true));
    }
}
