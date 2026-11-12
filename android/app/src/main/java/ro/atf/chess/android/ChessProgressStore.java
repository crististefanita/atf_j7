package ro.atf.chess.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Clock;
import ro.atf.chess.MatchStore;
import ro.atf.chess.QuestStore;

final class ChessProgressStore {
    private static final String PLAYER_NAME = "player-name";
    private final SharedPreferences preferences;
    private final QuestStore quests;
    private final MatchStore matches;

    ChessProgressStore(Context context) {
        preferences = context.getSharedPreferences("quest-progress", Context.MODE_PRIVATE);
        var adapter = new AndroidGamePreferences(preferences);
        quests = new QuestStore(adapter);
        matches = new MatchStore(adapter, Clock.systemUTC());
    }

    QuestStore quests() { return quests; }
    MatchStore matches() { return matches; }

    String playerName() { return preferences.getString(PLAYER_NAME, ""); }

    boolean savePlayerName(String name) {
        return (name.isEmpty() ? preferences.edit().remove(PLAYER_NAME) : preferences.edit().putString(PLAYER_NAME, name)).commit();
    }
}
