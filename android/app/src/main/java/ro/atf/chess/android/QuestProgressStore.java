package ro.atf.chess.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;
import ro.atf.chess.QuestProgress;

final class QuestProgressStore {
    private static final String CURRENT = "current-id";
    private static final String SOLVED = "solved-ids";
    private static final String AWAITING_MATE = "awaiting-mate";
    private final SharedPreferences preferences;

    QuestProgressStore(Context context) { preferences = context.getSharedPreferences("quest-progress", Context.MODE_PRIVATE); }

    QuestProgress read(List<String> order) {
        return QuestProgress.restore(preferences.getString(CURRENT, null), preferences.getStringSet(SOLVED, Collections.emptySet()), order);
    }

    String remembered(String firstId) { return preferences.getString(chapterKey(firstId), firstId); }

    boolean awaitingMate(String questId) {
        return questId.equals(preferences.getString(AWAITING_MATE, null));
    }

    boolean save(QuestProgress progress) { return save(progress, false); }

    boolean save(QuestProgress progress, boolean pending) {
        String id = progress.currentId();
        String previous = preferences.getString(CURRENT, null);
        var edit = preferences.edit();
        if (previous != null) edit.putString(chapterKey(previous), previous);
        if (pending) edit.putString(AWAITING_MATE, id);
        else if (id.startsWith("mate2-") && (!awaitingMate(id) || progress.currentSolved())) edit.remove(AWAITING_MATE);
        return edit.putString(chapterKey(id), id).putString(CURRENT, id).putStringSet(SOLVED, progress.solvedIds()).commit();
    }

    private static String chapterKey(String id) { return id.startsWith("mate2-") ? "last-mate-two" : "last-mate-one"; }
}
