package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestRewardsTest {
    @Test void cleanCompletionsAccumulateAcrossChaptersAndCapTheBonus(Checks check) {
        var order = List.of("mate-001", "tactic-001", "mate2-00001", "tactic-002", "mate3-00001", "tactic-003", "tactic-004");
        var progress = QuestProgress.restore(null, Set.of(), order);
        var rewards = QuestRewards.restore(Set.of(), Set.of(), 0);
        int[] expected = {10, 12, 14, 16, 18, 20, 20};
        for (int index = 0; index < order.size(); index++) {
            progress = progress.select(order.get(index));
            var award = rewards.complete(progress);
            check.same("First completion of " + progress.currentId() + " earns its series bonus", expected[index], award.amount());
            progress = progress.solveCurrent();
            rewards = award.rewards();
            check.same("The flame starts with the second consecutive completion", index > 0, rewards.showsStreak());
        }
        check.same("All chapters contribute once to the overall totals", new QuestRewards.Summary(7, 7, 110), rewards.summarize(order, progress));
        check.same("The tactics chapter shows only its own points", new QuestRewards.Summary(4, 4, 68),
                rewards.summarize(List.of("tactic-001", "tactic-002", "tactic-003", "tactic-004"), progress));
    }

    @Test void persistedHintOrMistakeBreaksTheSeriesAndEarnsOnlyBasePoints(Checks check) {
        var order = List.of("mate-001", "mate-002", "tactic-001", "tactic-002");
        var progress = QuestProgress.restore("tactic-001", Set.of("mate-001", "mate-002"), order);
        var rewards = QuestRewards.restore(Set.of("mate-001=10", "mate-002=12"), Set.of(), 2);
        check.same("The flame survives restoration of a clean series", true, rewards.showsStreak());
        rewards = rewards.interrupt("tactic-001");
        check.same("Interruption takes effect before the current quest is solved", 0, rewards.streak());
        check.same("The flame disappears when the series breaks", false, rewards.showsStreak());
        rewards = QuestRewards.restore(rewards.encodedPoints(), rewards.assistedIds(), rewards.streak());
        var assisted = rewards.complete(progress);
        check.same("Restarting cannot convert a hinted attempt into a clean solve", 10, assisted.amount());
        check.same("A solve with assistance does not advance the streak", 0, assisted.rewards().streak());
        progress = progress.solveCurrent();
        check.same("Revisiting the solved problem cannot earn points again", 0, assisted.rewards().complete(progress).amount());
        var next = assisted.rewards().complete(progress.select("tactic-002"));
        check.same("A new clean solve starts a new streak", 1, next.rewards().streak());
        check.same("The first clean solve after interruption has base points", 10, next.amount());
    }

    @Test void legacySolvesKeepTheirCreditWithoutAnInventedSeries(Checks check) {
        var progress = QuestProgress.restore("mate-102", Set.of("mate-102", "retired-id"), List.of("mate-102", "tactic-001"));
        var rewards = QuestRewards.restore(Set.of("mate-102=invalid", "tactic-001=100", "obsolete"), Set.of(), 99);
        check.same("Previously saved solves get base credit and retired IDs are outside the totals",
                new QuestRewards.Summary(1, 2, 10), rewards.summarize(List.of("tactic-001", "mate-102"), progress));
        check.same("A previously solved quest cannot earn a second award", 0, rewards.complete(progress).amount());
        check.same("Missing or invalid reward data cannot invent a flame", false, rewards.showsStreak());
    }
}
