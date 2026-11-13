package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestStoreTest {
    @Test void restoresIndependentCursorsAndSharedRewardsAcrossChapterSwitches(Checks check) {
        var tactics = QuestCatalog.loadTactics().stream().map(QuestCatalog.Quest::id).toList();
        var training = QuestCatalog.loadTraining().stream().map(QuestCatalog.Quest::id).toList();
        var order = new ArrayList<>(tactics);
        order.addAll(training);
        var disk = new MemoryPreferences();
        disk.data.put(MatchStore.RESUME_MATCH, true);
        var store = new QuestStore(disk);
        var progress = store.read(order);
        var rewards = store.readRewards();
        var positions = List.of(tactics.get(0), tactics.get(1), training.getFirst());
        var points = new ArrayList<Integer>();
        for (String id : positions) {
            progress = progress.select(id);
            var award = rewards.complete(progress);
            points.add(award.amount());
            progress = progress.solveCurrent();
            rewards = award.rewards();
            check.same("Save the first completion of " + id, true, store.save(progress, rewards));
        }
        check.same("Clean solves share one series between the two chapters", List.of(10, 12, 14), points);
        check.same("Training navigation saves the next unsolved ID", true, store.save(progress.select(training.get(1)), rewards));

        var reopened = new QuestStore(disk);
        var saved = reopened.read(order);
        var awards = reopened.readRewards();
        check.same("The training cursor and the previous tactics cursor both survive",
                List.of(training.get(1), tactics.get(1), training.get(1)),
                List.of(saved.currentId(), reopened.remembered(tactics.getFirst()), reopened.remembered(training.getFirst())));
        check.same("Restart preserves exactly the solved positions and leaves the current one unsolved", List.of(Set.copyOf(positions), false),
                List.of(saved.solvedIds(), saved.currentSolved()));
        check.same("Tactics points survive and remain in their chapter", new QuestRewards.Summary(2, 100, 22), awards.summarize(tactics, saved));
        check.same("Training points survive and remain in their chapter", new QuestRewards.Summary(1, 96, 14), awards.summarize(training, saved));
        check.same("Overall progress combines the two chapters once", new QuestRewards.Summary(3, 196, 36), awards.summarize(order, saved));
        check.same("Revisiting a saved completion earns no second award", 0, awards.complete(saved.select(training.getFirst())).amount());
        check.same("Saving a puzzle selects puzzle resume and clears the match resume flag", List.of(true, false),
                List.of(reopened.resumeChapter(), disk.contains(MatchStore.RESUME_MATCH)));
        check.same("Leaving a chapter clears auto-resume", true, reopened.leaveChapter());
        check.same("Leaving preserves the remembered training ID", training.get(1), new QuestStore(disk).remembered(training.getFirst()));
    }

    @Test void trainingExpansionRestoresAnExistingCursorAndAwardById(Checks check) {
        var newOrder = QuestCatalog.loadTraining().stream().map(QuestCatalog.Quest::id).toList();
        var oldOrder = newOrder.stream().filter(id -> id.compareTo("training-072") <= 0).toList();
        check.same("All 72 previous exercises remain available", 72, oldOrder.size());
        var disk = new MemoryPreferences();
        var oldStore = new QuestStore(disk);
        var completed = oldStore.read(oldOrder).select("training-060");
        var award = oldStore.readRewards().complete(completed);
        check.same("Save a completion and the next unsolved exercise in the previous edition", true,
                oldStore.save(completed.solveCurrent().select("training-063"), award.rewards()));

        var store = new QuestStore(disk);
        var restored = store.read(newOrder);
        var rewards = store.readRewards();
        check.same("Reordering keeps the cursor and completed ID", List.of("training-063", Set.of("training-060")),
                List.of(restored.currentId(), restored.solvedIds()));
        check.same("The old award is included once in the new chapter total",
                new QuestRewards.Summary(1, 96, 10), rewards.summarize(newOrder, restored));
        check.same("The reimported position cannot earn a second award", 0, rewards.complete(restored.select("training-060")).amount());
    }

    @Test void intermezzoKeepsItsCursorAndOnlyAwardsTheFirstCompletion(Checks check) {
        var chapter = QuestCatalog.loadIntermezzo().stream().map(QuestCatalog.Quest::id).toList();
        var training = QuestCatalog.loadTraining().stream().map(QuestCatalog.Quest::id).toList();
        var order = java.util.stream.Stream.concat(training.stream(), chapter.stream()).toList();
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        String previousEditionId = "intermezzo-001";
        String next = chapter.get(chapter.indexOf(previousEditionId) + 1);
        var attempt = store.read(order).select(previousEditionId);
        var award = store.readRewards().complete(attempt);
        check.same("The new chapter saves the next unsolved position", true,
                store.save(attempt.solveCurrent().select(next), award.rewards()));
        var reopened = new QuestStore(disk);
        var saved = reopened.read(order);
        var rewards = reopened.readRewards();
        check.same("The cursor and the solved ID survive a reopened store", List.of(next, Set.of(previousEditionId)),
                List.of(saved.currentId(), saved.solvedIds()));
        check.same("Intermezzo owns its score", new QuestRewards.Summary(1, 24, 10), rewards.summarize(chapter, saved));
        check.same("Training points remain separate", new QuestRewards.Summary(0, 96, 0), rewards.summarize(training, saved));
        check.same("The saved completion cannot score twice", 0, rewards.complete(saved.select(previousEditionId)).amount());
    }

    @Test void restoresLegacyPliesAndMigratesOnlyTheSelectedQuest(Checks check) {
        var disk = new MemoryPreferences();
        disk.data.put("last-mate-two-stage-id", "mate2-older");
        disk.data.put("last-mate-two-stage", 1);
        disk.data.put("last-mate-three-stage-id", "mate3-older");
        disk.data.put("last-mate-three-stage", 1);
        disk.data.put("last-mate-three-ply", 3);
        disk.data.put("awaiting-mate", "mate2-pending");
        var store = new QuestStore(disk);
        check.same("Legacy stages, explicit plies and pending mate map to the right quest", List.of(2, 3, 2, 0),
                List.of(store.ply("mate2-older"), store.ply("mate3-older"), store.ply("mate2-pending"), store.ply("training-001")));
        var current = store.read(List.of("mate3-older"));
        check.same("Save a current position using the per-quest field", true, store.save(current, store.readRewards(), 4));
        var reopened = new QuestStore(disk);
        check.same("The new representation wins and clears only the migrated stage", List.of(4, false, 2, 2),
                List.of(reopened.ply(current.currentId()), disk.contains("last-mate-three-stage-id"),
                        reopened.ply("mate2-older"), reopened.ply("mate2-pending")));
    }

    @Test void aRejectedFinishDoesNotPublishPartialProgressOrRewards(Checks check) {
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        var current = store.read(List.of("training-001"));
        var pending = store.readRewards().interrupt(current.currentId());
        check.same("Persist the assisted attempt before solving", true, store.save(current, pending));
        var reopened = new QuestStore(disk);
        var award = reopened.readRewards().complete(current);
        check.same("An interrupted attempt retains base credit after reopening", List.of(10, 0), List.of(award.amount(), award.rewards().streak()));
        var snapshot = Map.copyOf(disk.data);
        disk.rejectCommit = true;
        int priorAttempts = disk.commitAttempts;
        check.same("A failed finish transaction is reported", false,
                reopened.saveFinish(current.solveCurrent(), award.rewards(), "recorded-finish"));
        check.same("The failed transaction leaves the entire previous state intact", List.of(priorAttempts + 1, snapshot),
                List.of(disk.commitAttempts, Map.copyOf(disk.data)));
        disk.rejectCommit = false;
        check.same("The same finish can be retried safely", true, reopened.saveFinish(current.solveCurrent(), award.rewards(), "recorded-finish"));
        var saved = new QuestStore(disk);
        check.same("Finish, solve, and single award become visible together", List.of("recorded-finish", true, Set.of("training-001=10")),
                List.of(saved.finish(current.currentId()), saved.read(List.of(current.currentId())).currentSolved(), saved.readRewards().encodedPoints()));
    }

    @Test void aRetiredTrainingIdFallsBackInsideItsChapter(Checks check) {
        var disk = new MemoryPreferences();
        disk.data.put("current-id", "training-retired");
        disk.data.put("solved-ids", Set.of("training-retired", "training-001"));
        disk.data.put("reward-points", Set.of("training-retired=18"));
        var store = new QuestStore(disk);
        var active = List.of("mate-001", "tactic-001", "training-001", "training-002");
        var progress = store.read(active);
        check.same("A missing current ID resumes at the first unsolved training ID", "training-002", progress.currentId());
        check.same("Retired history is retained without counting it in the active totals", Set.of("training-retired", "training-001"), progress.solvedIds());
        check.same("The surviving legacy solve gets base credit only", new QuestRewards.Summary(1, 4, 10), store.readRewards().summarize(active, progress));
    }

    @Test void counterMateSolvesExtendTheOriginalProgressByStableId(Checks check) {
        var order = QuestCatalog.load().stream().map(QuestCatalog.Quest::id).toList();
        var disk = new MemoryPreferences();
        disk.data.put("solved-ids", Set.of(order.get(0), order.get(1)));
        var store = new QuestStore(disk);
        for (String id : List.of("mate-101", "mate-102")) {
            var selected = store.read(order).select(id);
            check.same("Save the selected counter-mate " + id, true, store.save(selected, store.readRewards()));
            var reopened = new QuestStore(disk);
            check.same("The catalog reopens the exact saved position " + id, id, reopened.read(order).currentId());
            check.same("Save the completed counter-mate " + id, true, reopened.save(selected.solveCurrent(), reopened.readRewards()));
        }
        var saved = new QuestStore(disk).read(order);
        check.same("Both original solves and both counter-mates are retained",
                Set.of(order.get(0), order.get(1), "mate-101", "mate-102"), saved.solvedIds());
        check.same("Four unique solves leave the first milestone pending",
                new QuestProgress.Milestone(4, 5, 500, false), saved.milestone(order));
    }
}
