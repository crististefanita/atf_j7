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
}
