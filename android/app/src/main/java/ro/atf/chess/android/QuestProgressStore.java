package ro.atf.chess.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;
import ro.atf.chess.QuestProgress;

final class QuestProgressStore {
    private static final String CURRENT = "current-id";
    private static final String SOLVED = "solved-ids";
    private final SharedPreferences preferences;

    QuestProgressStore(Context context) { preferences = context.getSharedPreferences("quest-progress", Context.MODE_PRIVATE); }

    QuestProgress read(List<String> order) {
        return QuestProgress.restore(preferences.getString(CURRENT, null), preferences.getStringSet(SOLVED, Collections.emptySet()), order);
    }

    boolean save(QuestProgress progress) {
        return preferences.edit().putString(CURRENT, progress.currentId()).putStringSet(SOLVED, progress.solvedIds()).commit();
    }
}
