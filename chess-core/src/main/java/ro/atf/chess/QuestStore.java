package ro.atf.chess;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Persists puzzle selection, progress and rewards through a single preferences transaction. */
public final class QuestStore {
    public interface Preferences extends MatchStore.Preferences {
        Set<String> getStringSet(String key, Set<String> fallback);
        @Override Editor edit();
    }

    public interface Editor extends MatchStore.Editor {
        @Override Editor putString(String key, String value);
        @Override Editor putBoolean(String key, boolean value);
        @Override Editor putInt(String key, int value);
        @Override Editor remove(String key);
        Editor putStringSet(String key, Set<String> value);
    }

    private static final String CURRENT = "current-id";
    private static final String SOLVED = "solved-ids";
    private static final String REWARD_POINTS = "reward-points";
    private static final String REWARD_ASSISTED = "reward-assisted";
    private static final String REWARD_STREAK = "reward-streak";
    private static final String AWAITING_MATE = "awaiting-mate";
    private final Preferences preferences;

    public QuestStore(Preferences preferences) { this.preferences = preferences; }

    public QuestProgress read(List<String> order) {
        String saved = preferences.getString(CURRENT, null);
        if (saved != null && !order.contains(saved)) {
            String key = chapterKey(saved);
            var chapter = order.stream().filter(id -> chapterKey(id).equals(key)).collect(Collectors.toList());
            if (!chapter.isEmpty()) order = chapter;
        }
        return QuestProgress.restore(saved, preferences.getStringSet(SOLVED, Collections.emptySet()), order);
    }

    public QuestRewards readRewards() {
        return QuestRewards.restore(preferences.getStringSet(REWARD_POINTS, Collections.emptySet()),
                preferences.getStringSet(REWARD_ASSISTED, Collections.emptySet()), preferences.getInt(REWARD_STREAK, 0));
    }

    public String remembered(String firstId) {
        String key = chapterKey(firstId);
        String current = preferences.getString(CURRENT, firstId);
        String fallback = key.equals(chapterKey(current)) ? current : firstId;
        return preferences.getString(key, fallback);
    }

    public boolean resumeChapter() { return preferences.getBoolean(MatchStore.RESUME_CHAPTER, false); }

    public boolean leaveChapter() { return preferences.edit().remove(MatchStore.RESUME_CHAPTER).commit(); }

    public String finish(String id) { return preferences.getString("quest-finish-" + id, null); }

    public int ply(String id) {
        String saved = "quest-ply-" + id;
        if (preferences.contains(saved)) return preferences.getInt(saved, 0);
        String key = chapterKey(id);
        if (id.equals(preferences.getString(key + "-stage-id", null))) {
            return preferences.getInt(key + "-ply", preferences.getInt(key + "-stage", 0) * 2);
        }
        return id.equals(preferences.getString(AWAITING_MATE, null)) ? 2 : 0;
    }

    public boolean save(QuestProgress progress, QuestRewards rewards) {
        return save(progress, rewards, progress.currentSolved() ? 0 : ply(progress.currentId()));
    }

    public boolean save(QuestProgress progress, QuestRewards rewards, int ply) {
        return save(progress, rewards, ply, null);
    }

    public boolean saveFinish(QuestProgress progress, QuestRewards rewards, String finish) {
        return save(progress, rewards, 0, finish);
    }

    private boolean save(QuestProgress progress, QuestRewards rewards, int ply, String finish) {
        String id = progress.currentId();
        String previous = preferences.getString(CURRENT, null);
        var edit = preferences.edit();
        if (previous != null) edit.putString(chapterKey(previous), previous);
        String key = chapterKey(id);
        if (id.equals(preferences.getString(AWAITING_MATE, null))) edit.remove(AWAITING_MATE);
        if (id.equals(preferences.getString(key + "-stage-id", null))) edit.remove(key + "-stage-id").remove(key + "-stage").remove(key + "-ply");
        if (ply == 0) edit.remove("quest-ply-" + id);
        else edit.putInt("quest-ply-" + id, ply);
        if (finish != null) edit.putString("quest-finish-" + id, finish);
        return edit.putString(key, id).putBoolean(MatchStore.RESUME_CHAPTER, true).remove(MatchStore.RESUME_MATCH)
                .putString(CURRENT, id).putStringSet(SOLVED, progress.solvedIds())
                .putStringSet(REWARD_POINTS, rewards.encodedPoints()).putStringSet(REWARD_ASSISTED, rewards.assistedIds())
                .putInt(REWARD_STREAK, rewards.streak()).commit();
    }

    private static String chapterKey(String id) {
        if (id.startsWith("training-")) return "last-training";
        if (id.startsWith("tactic-")) return "last-tactics";
        if (id.startsWith("mate3-")) return "last-mate-three";
        return id.startsWith("mate2-") ? "last-mate-two" : "last-mate-one";
    }
}
