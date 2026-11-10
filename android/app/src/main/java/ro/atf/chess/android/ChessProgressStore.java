package ro.atf.chess.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.QuestProgress;
import ro.atf.chess.QuestRewards;

final class ChessProgressStore {
    private static final String CURRENT = "current-id";
    private static final String SOLVED = "solved-ids";
    private static final String REWARD_POINTS = "reward-points";
    private static final String REWARD_ASSISTED = "reward-assisted";
    private static final String REWARD_STREAK = "reward-streak";
    private static final String AWAITING_MATE = "awaiting-mate";
    private static final String RESUME_CHAPTER = "resume-chapter";
    private static final String MATCH = "match-checkpoint";
    private static final String RESUME_MATCH = "resume-match";
    private static final String MATCH_BLACK_BELOW = "match-black-below";
    private final SharedPreferences preferences;

    ChessProgressStore(Context context) { preferences = context.getSharedPreferences("quest-progress", Context.MODE_PRIVATE); }

    ChessMatch readMatch() {
        String encoded = preferences.getString(MATCH, null);
        return encoded == null ? null : ChessMatch.restore(encoded);
    }

    boolean resumeMatch() { return preferences.getBoolean(RESUME_MATCH, false); }
    boolean matchBlackBelow() { return preferences.getBoolean(MATCH_BLACK_BELOW, false); }
    boolean leaveMatch() { return preferences.edit().remove(RESUME_MATCH).commit(); }

    boolean saveMatch(ChessMatch match, boolean blackBelow) {
        return preferences.edit().putString(MATCH, match.checkpoint()).putBoolean(MATCH_BLACK_BELOW, blackBelow)
                .putBoolean(RESUME_MATCH, true).remove(RESUME_CHAPTER).commit();
    }

    QuestProgress read(List<String> order) {
        return QuestProgress.restore(preferences.getString(CURRENT, null), preferences.getStringSet(SOLVED, Collections.emptySet()), order);
    }

    QuestRewards readRewards() {
        return QuestRewards.restore(preferences.getStringSet(REWARD_POINTS, Collections.emptySet()),
                preferences.getStringSet(REWARD_ASSISTED, Collections.emptySet()), preferences.getInt(REWARD_STREAK, 0));
    }

    String remembered(String firstId) {
        String key = chapterKey(firstId);
        String current = preferences.getString(CURRENT, firstId);
        String fallback = key.equals(chapterKey(current)) ? current : firstId;
        return preferences.getString(key, fallback);
    }

    boolean resumeChapter() { return preferences.getBoolean(RESUME_CHAPTER, false); }

    boolean leaveChapter() { return preferences.edit().remove(RESUME_CHAPTER).commit(); }

    String finish(String id) { return preferences.getString("quest-finish-" + id, null); }

    int ply(String id) {
        String saved = "quest-ply-" + id;
        if (preferences.contains(saved)) return preferences.getInt(saved, 0);
        String key = chapterKey(id);
        if (id.equals(preferences.getString(key + "-stage-id", null))) {
            return preferences.getInt(key + "-ply", preferences.getInt(key + "-stage", 0) * 2);
        }
        return id.equals(preferences.getString(AWAITING_MATE, null)) ? 2 : 0;
    }

    boolean save(QuestProgress progress, QuestRewards rewards) {
        return save(progress, rewards, progress.currentSolved() ? 0 : ply(progress.currentId()));
    }

    boolean save(QuestProgress progress, QuestRewards rewards, int ply) {
        return save(progress, rewards, ply, null);
    }

    boolean saveFinish(QuestProgress progress, QuestRewards rewards, String finish) {
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
        return edit.putString(key, id).putBoolean(RESUME_CHAPTER, true).remove(RESUME_MATCH)
                .putString(CURRENT, id).putStringSet(SOLVED, progress.solvedIds())
                .putStringSet(REWARD_POINTS, rewards.encodedPoints()).putStringSet(REWARD_ASSISTED, rewards.assistedIds())
                .putInt(REWARD_STREAK, rewards.streak()).commit();
    }

    private static String chapterKey(String id) {
        if (id.startsWith("tactic-")) return "last-tactics";
        if (id.startsWith("mate3-")) return "last-mate-three";
        return id.startsWith("mate2-") ? "last-mate-two" : "last-mate-one";
    }
}
