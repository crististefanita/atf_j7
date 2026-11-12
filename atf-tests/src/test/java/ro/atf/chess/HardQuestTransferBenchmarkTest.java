package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardQuestTransferBenchmarkTest {
    private record Chapter(String name, List<QuestCatalog.Quest> quests) { }

    @Test void hardPreservesItsMeasuredAccuracyAcrossEveryQuestChapter(Checks check) {
        var chapters = List.of(
                new Chapter("mate-in-one", QuestCatalog.load()),
                new Chapter("mate-in-two", QuestCatalog.loadMateInTwo()),
                new Chapter("mate-in-three", QuestCatalog.loadMateInThree()),
                new Chapter("tactics", QuestCatalog.loadTactics()),
                new Chapter("training", QuestCatalog.loadTraining()),
                new Chapter("intermezzo", QuestCatalog.loadIntermezzo()));
        var root = ChessYaml.resource("hard-quest-transfer.yaml");
        var limits = ChessYaml.map(root, "minimum-solved");
        check.same("Quest transfer baseline schema", 1, ChessYaml.number(root, "schema"));
        check.same("The frozen transfer baseline covers every live chapter",
                chapters.stream().map(Chapter::name).collect(java.util.stream.Collectors.toSet()), limits.keySet());
        for (var chapter : chapters) {
            int solved = 0;
            int legal = 0;
            int overBudget = 0;
            int depthTotal = 0;
            long nodeTotal = 0;
            var illegalMoves = new ArrayList<String>();
            var overBudgetMoves = new ArrayList<String>();
            var misses = new ArrayList<String>();
            for (var quest : chapter.quests()) {
                var position = quest.position();
                var setup = new MatchSetup(position.turn().other(), MatchSetup.Level.HARD, false);
                var decision = ChessMatch.fromPosition(position, setup).botDecision(MatchSetup.Level.HARD);
                if (position.legalMoves().contains(decision.move())) legal++;
                else illegalMoves.add(quest.id() + "->" + decision.move().uci());
                if (decision.extraNodes() > MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes()) {
                    overBudget++;
                    overBudgetMoves.add(quest.id() + "=" + decision.extraNodes());
                }
                depthTotal += decision.depth();
                nodeTotal += decision.extraNodes();
                boolean accepted = quest.objective() == QuestCatalog.Objective.MATERIAL ? quest.solves(decision.move())
                        : quest.playerMoves() == 1 ? position.play(decision.move()).isCheckmate()
                        : quest.assess(decision.move()).continues();
                if (accepted) solved++;
                else misses.add(quest.id() + " " + quest.solution().uci() + "->" + decision.move().uci()
                        + " depth=" + decision.depth() + " nodes=" + decision.extraNodes());
            }
            int minimum = ChessYaml.number(limits, chapter.name());
            String summary = chapter.name() + " " + solved + "/" + chapter.quests().size() + " minimum=" + minimum
                    + " mean-depth=" + String.format(java.util.Locale.ROOT, "%.2f", (double) depthTotal / chapter.quests().size())
                    + " mean-nodes=" + nodeTotal / chapter.quests().size() + " misses=" + misses;
            System.out.println("HARD_QUEST_TRANSFER " + summary);
            check.same("Hard quest transfer does not regress its frozen floor: " + summary, true, solved >= minimum);
            check.same("Every quest search returns a legal move: " + chapter.name() + " violations=" + illegalMoves,
                    chapter.quests().size(), legal);
            check.same("Every quest search respects the unchanged Hard node cap: " + chapter.name()
                    + " violations=" + overBudgetMoves, 0, overBudget);
        }
    }
}
