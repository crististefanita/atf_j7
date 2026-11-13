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

    private static List<Chapter> publicChapters() {
        return List.of(
                new Chapter("mate-in-one", QuestCatalog.load()),
                new Chapter("mate-in-two", QuestCatalog.loadMateInTwo()),
                new Chapter("mate-in-three", QuestCatalog.loadMateInThree()),
                new Chapter("tactics", QuestCatalog.loadTactics()),
                new Chapter("training", QuestCatalog.loadTraining()),
                new Chapter("intermezzo", QuestCatalog.loadIntermezzo()));
    }

    @Test void hardPreservesItsMeasuredAccuracyAcrossEveryQuestChapter(Checks check) {
        var chapters = publicChapters();
        var root = ChessYaml.resource("hard-quest-transfer.yaml");
        var limits = ChessYaml.map(root, "minimum-solved");
        String lastBaselineTrainingId = ChessYaml.string(root, "training-baseline-through");
        check.same("Quest transfer baseline schema", 1, ChessYaml.number(root, "schema"));
        check.same("The frozen transfer baseline covers every live chapter",
                chapters.stream().map(Chapter::name).collect(java.util.stream.Collectors.toSet()), limits.keySet());
        for (var chapter : chapters) {
            int solved = 0;
            int baselineSolved = 0;
            int baselineSize = 0;
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
                if (!chapter.name().equals("training") || quest.id().compareTo(lastBaselineTrainingId) <= 0) {
                    baselineSize++;
                    if (accepted) baselineSolved++;
                }
            }
            int minimum = ChessYaml.number(limits, chapter.name());
            String summary = chapter.name() + " " + solved + "/" + chapter.quests().size() + " minimum=" + minimum
                    + " baseline=" + baselineSolved + "/" + baselineSize
                    + " mean-depth=" + String.format(java.util.Locale.ROOT, "%.2f", (double) depthTotal / chapter.quests().size())
                    + " mean-nodes=" + nodeTotal / chapter.quests().size() + " misses=" + misses;
            System.out.println("HARD_QUEST_TRANSFER " + summary);
            check.same("Hard quest transfer preserves its frozen floor: " + summary, true, baselineSolved >= minimum);
            check.same("Every quest search returns a legal move: " + chapter.name() + " violations=" + illegalMoves,
                    chapter.quests().size(), legal);
            check.same("Every quest search respects the unchanged Hard node cap: " + chapter.name()
                    + " violations=" + overBudgetMoves, 0, overBudget);
        }
    }

    @Test void solvesTheFullPublicCatalogWithoutReadingItsKeys(Checks check) {
        var chapters = new ArrayList<>(publicChapters());
        chapters.add(new Chapter("independent material", QuestCatalog.readMaterial(ChessYaml.resource("tactical-transfer-holdout.yaml"), "holdout")));
        for (var chapter : chapters) {
            var misses = new ArrayList<String>();
            for (var quest : chapter.quests()) {
                var solution = quest.objective() == QuestCatalog.Objective.MATE
                        ? HardQuestSolver.mate(quest.position(), quest.playerMoves()) : HardQuestSolver.material(quest.position());
                if (!matchesObjective(quest, solution)) misses.add(quest.id() + " " + solution);
            }
            String summary = chapter.name() + " solved " + (chapter.quests().size() - misses.size()) + "/" + chapter.quests().size();
            System.out.println("HARD_QUEST_SOLVER " + summary);
            check.same(summary + " with a legal line for the stated objective", List.of(), misses);
        }
    }

    private static boolean matchesObjective(QuestCatalog.Quest quest, HardQuestSolver.Solution solution) {
        if (solution.status() != HardQuestSolver.Status.SOLVED || !solution.line().getFirst().equals(quest.solution())) return false;
        var replay = quest.position();
        for (var move : solution.line()) {
            if (!replay.legalMoves().contains(move)) return false;
            replay = replay.playGenerated(move);
        }
        return quest.objective() == QuestCatalog.Objective.MATERIAL
                ? solution.line().size() == 3
                : solution.line().size() == 2 * quest.playerMoves() - 1 && replay.isCheckmate();
    }
}
