package ro.atf.chess;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rewards are recorded by quest ID so moving a chapter or replaying a solution cannot change totals. */
public record QuestRewards(Map<String, Integer> points, Set<String> assistedIds, int streak) {
    private static final int BASE = 10;
    private static final int BONUS_STEP = 2;
    private static final int MAX_BONUS_STEPS = 5;

    public QuestRewards {
        points = Collections.unmodifiableMap(new HashMap<>(points));
        assistedIds = Collections.unmodifiableSet(new HashSet<>(assistedIds));
        streak = Math.max(0, Math.min(streak, points.size()));
    }

    public static QuestRewards restore(Set<String> encoded, Set<String> assistedIds, int streak) {
        var points = new HashMap<String, Integer>();
        for (String entry : encoded) {
            int separator = entry.indexOf('=');
            if (separator < 1) continue;
            try {
                int amount = Integer.parseInt(entry.substring(separator + 1));
                if (amount >= BASE && amount <= BASE + BONUS_STEP * MAX_BONUS_STEPS && (amount - BASE) % BONUS_STEP == 0) {
                    points.merge(entry.substring(0, separator), amount, Math::min);
                }
            } catch (NumberFormatException invalid) {
                // A stale entry cannot invalidate the player's solved quest IDs.
            }
        }
        return new QuestRewards(points, assistedIds, streak);
    }

    public Set<String> encodedPoints() {
        var encoded = new HashSet<String>();
        points.forEach((id, amount) -> encoded.add(id + "=" + amount));
        return encoded;
    }

    public QuestRewards interrupt(String id) {
        if (streak == 0 && assistedIds.contains(id)) return this;
        var assisted = new HashSet<>(assistedIds);
        assisted.add(id);
        return new QuestRewards(points, assisted, 0);
    }

    public Award complete(QuestProgress progress) {
        String id = progress.currentId();
        if (progress.currentSolved() || points.containsKey(id)) return new Award(this, 0);
        var assisted = new HashSet<>(assistedIds);
        int nextStreak = assisted.remove(id) ? 0 : streak + 1;
        int amount = BASE + BONUS_STEP * Math.min(MAX_BONUS_STEPS, Math.max(0, nextStreak - 1));
        var awarded = new HashMap<>(points);
        awarded.put(id, amount);
        return new Award(new QuestRewards(awarded, assisted, nextStreak), amount);
    }

    public Summary summarize(List<String> order, QuestProgress progress) {
        int solved = 0;
        int totalPoints = 0;
        for (String id : order) {
            if (!progress.solvedIds().contains(id)) continue;
            solved++;
            totalPoints += points.getOrDefault(id, BASE);
        }
        return new Summary(solved, order.size(), totalPoints);
    }

    public boolean showsStreak() { return streak >= 2; }

    public record Award(QuestRewards rewards, int amount) { }
    public record Summary(int solved, int total, int points) { }
}
