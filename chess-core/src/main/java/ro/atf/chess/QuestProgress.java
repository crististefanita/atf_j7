package ro.atf.chess;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Stable quest identities are independent of their current display order. */
public record QuestProgress(String currentId, Set<String> solvedIds) {
    private static final List<Integer> MILESTONES = List.of(5, 15, 30, 50, 75);

    public QuestProgress {
        Objects.requireNonNull(currentId, "Current quest");
        solvedIds = Collections.unmodifiableSet(new HashSet<>(solvedIds));
    }

    public static QuestProgress restore(String savedId, Set<String> solvedIds, List<String> order) {
        if (order.isEmpty()) throw new IllegalArgumentException("Cannot restore progress with an empty catalog");
        String current = savedId;
        if (current == null) current = order.get(0);
        else if (!order.contains(current)) current = order.stream().filter(id -> !solvedIds.contains(id)).findFirst().orElse(order.get(0));
        return new QuestProgress(current, solvedIds);
    }

    public QuestProgress select(String id) { return new QuestProgress(id, solvedIds); }

    public QuestProgress solveCurrent() {
        if (solvedIds.contains(currentId)) return this;
        var updated = new HashSet<>(solvedIds);
        updated.add(currentId);
        return new QuestProgress(currentId, updated);
    }

    public boolean currentSolved() { return solvedIds.contains(currentId); }

    public boolean canAdvance(int index, int chapterSize) { return currentSolved() && index + 1 < chapterSize; }

    public int nextIndex(int index, List<String> order) {
        if (index < 0 || index >= order.size() || !order.get(index).equals(currentId)) return -1;
        if (canAdvance(index, order.size())) return index + 1;
        if (currentSolved()) for (int earlier = 0; earlier < index; earlier++) {
            if (!solvedIds.contains(order.get(earlier))) return earlier;
        }
        return -1;
    }

    public int solvedCount(List<String> order) {
        int count = 0;
        for (String id : order) if (solvedIds.contains(id)) count++;
        return count;
    }

    public Milestone milestone(List<String> order) {
        int solved = solvedCount(order);
        int total = order.size();
        int next = MILESTONES.stream().filter(goal -> goal > solved && goal < total).findFirst().orElse(total);
        return new Milestone(solved, next, total, solved > 0 && (solved == total || MILESTONES.contains(solved)));
    }

    public record Milestone(int solved, int next, int total, boolean reached) {
        public boolean complete() { return solved == total; }
    }
}
