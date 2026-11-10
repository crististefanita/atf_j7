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
        check.same("The piece hint hides the destination in all 102 quests", true,
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
}
