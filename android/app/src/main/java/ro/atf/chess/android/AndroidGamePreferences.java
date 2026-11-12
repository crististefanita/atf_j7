package ro.atf.chess.android;

import android.content.SharedPreferences;
import java.util.Set;
import ro.atf.chess.QuestStore;

record AndroidGamePreferences(SharedPreferences delegate) implements QuestStore.Preferences {
    @Override public String getString(String key, String fallback) { return delegate.getString(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return delegate.getBoolean(key, fallback); }
    @Override public int getInt(String key, int fallback) { return delegate.getInt(key, fallback); }
    @Override public long getLong(String key, long fallback) { return delegate.getLong(key, fallback); }
    @Override public boolean contains(String key) { return delegate.contains(key); }
    @Override public Set<String> getStringSet(String key, Set<String> fallback) { return delegate.getStringSet(key, fallback); }
    @Override public QuestStore.Editor edit() { return new Transaction(delegate.edit()); }

    private record Transaction(SharedPreferences.Editor delegate) implements QuestStore.Editor {
        @Override public QuestStore.Editor putString(String key, String value) { delegate.putString(key, value); return this; }
        @Override public QuestStore.Editor putBoolean(String key, boolean value) { delegate.putBoolean(key, value); return this; }
        @Override public QuestStore.Editor putInt(String key, int value) { delegate.putInt(key, value); return this; }
        @Override public QuestStore.Editor putLong(String key, long value) { delegate.putLong(key, value); return this; }
        @Override public QuestStore.Editor remove(String key) { delegate.remove(key); return this; }
        @Override public QuestStore.Editor putStringSet(String key, Set<String> value) { delegate.putStringSet(key, value); return this; }
        @Override public boolean commit() { return delegate.commit(); }
    }
}
