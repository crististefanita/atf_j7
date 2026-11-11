package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestProgressTest {
    @Test void restartAndReorderRetainCurrentAndAllSolvedIdentities(Checks check) {
        var before = QuestProgress.restore(null, Set.of(), List.of("a", "b", "c"))
                .solveCurrent().select("b").solveCurrent().select("c");
        var after = QuestProgress.restore(before.currentId(), before.solvedIds(), List.of("c", "b", "a"));
        check.same("Resume the same quest now at a different index", "c", after.currentId());
        check.same("Both earlier successes are retained", Set.of("a", "b"), after.solvedIds());
        check.same("The selected unfinished quest remains unfinished", false, after.currentSolved());
        check.same("Returning to a solved quest restores completion", true, after.select("a").currentSolved());
    }

    @Test void missingCurrentFallsBackAndHistoricalSolvesSurviveCatalogChanges(Checks check) {
        var saved = new HashSet<>(Set.of("retired", "visible"));
        var progress = QuestProgress.restore("retired", saved, List.of("visible", "first"));
        saved.clear();
        check.same("Fallback skips an already-solved exercise", "first", progress.currentId());
        check.same("Stored identities are a defensive copy", Set.of("retired", "visible"), progress.solvedIds());
        check.same("Only current catalog IDs count toward visible progress", 1, progress.solvedCount(List.of("first", "visible")));
        check.same("A reintroduced quest retains its solved state", true, progress.select("retired").currentSolved());
    }

    @Test void solvingTheSameQuestTwiceIsIdempotent(Checks check) {
        var solved = new QuestProgress("a", Set.of()).solveCurrent();
        check.same("Repeated solve retains one stable ID", Set.of("a"), solved.solveCurrent().solvedIds());
    }

    @Test void milestonesTrackUniqueVisibleSolvesAcrossRestartAndCatalogChanges(Checks check) {
        var order = QuestCatalog.load().stream().map(QuestCatalog.Quest::id).toList();
        var fresh = QuestProgress.restore(null, Set.of("archived"), order);
        check.same("The first goal counts only visible solved IDs", new QuestProgress.Milestone(0, 5, 102, false), fresh.milestone(order));
        var firstFour = new QuestProgress(order.get(4), Set.copyOf(order.subList(0, 4)));
        var reached = firstFour.solveCurrent();
        var restored = QuestProgress.restore(reached.currentId(), reached.solvedIds(), order.reversed());
        check.same("The fifth unique solve unlocks the next milestone after restart and reorder",
                new QuestProgress.Milestone(5, 15, 102, true), restored.milestone(order.reversed()));
        check.same("Revisiting a solved quest leaves the milestone unchanged", restored.milestone(order), restored.solveCurrent().milestone(order));
        check.same("The final goal matches the live catalog size", new QuestProgress.Milestone(101, 102, 102, false),
                new QuestProgress(order.getLast(), Set.copyOf(order.subList(0, 101))).milestone(order));
        var complete = new QuestProgress(order.getLast(), Set.copyOf(order)).milestone(order);
        check.same("A complete collection reports the final goal without an extra target", new QuestProgress.Milestone(102, 102, 102, true), complete);
        check.same("A shorter catalog completes at its actual size", true, reached.milestone(order.subList(0, 5)).complete());
    }

    @Test void replacingRepeatedPositionsPreservesActiveSolvesAndDoesNotCreditTheirReplacements(Checks check) {
        var current = QuestCatalog.load().stream().map(QuestCatalog.Quest::id).toList();
        Set<String> solved = Set.of("mate-001", "mate-010", "mate-011");
        var kept = QuestProgress.restore("mate-010", solved, current);
        check.same("The same surviving ID reopens after reordering", "mate-010", kept.currentId());
        var retired = QuestProgress.restore("mate-011", solved, current);
        check.same("Historical solves are retained by ID", solved, retired.solvedIds());
        check.same("Only surviving IDs count toward the current chapter", 2, retired.solvedCount(current));
        check.same("A new exercise starts unsolved", false, retired.select("mate-103").currentSolved());
        check.same("The retired checkpoint resumes on the first unsolved active position",
                current.stream().filter(id -> !solved.contains(id)).findFirst().orElseThrow(), retired.currentId());
    }
}
