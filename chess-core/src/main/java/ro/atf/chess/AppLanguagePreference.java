package ro.atf.chess;

import java.util.Optional;

/** Stores the explicit UI language independently of game checkpoints. */
public final class AppLanguagePreference {
    private static final String KEY = "language";
    private final MatchStore.Preferences preferences;

    public AppLanguagePreference(MatchStore.Preferences preferences) {
        this.preferences = preferences;
    }

    public Optional<AppLanguage> selected() {
        return AppLanguage.fromTag(preferences.getString(KEY, ""));
    }

    public boolean save(AppLanguage language) {
        String previous = preferences.getString(KEY, null);
        String requested = language.locale().getLanguage();
        if (requested.equals(previous)) return true;
        if (preferences.edit().putString(KEY, requested).commit()) return true;

        // Android can update in-memory preferences even when the disk write fails.
        var restore = preferences.edit();
        if (previous == null) restore.remove(KEY);
        else restore.putString(KEY, previous);
        restore.commit();
        return false;
    }
}
