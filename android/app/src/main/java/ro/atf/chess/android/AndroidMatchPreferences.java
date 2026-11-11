package ro.atf.chess.android;

import android.content.SharedPreferences;
import ro.atf.chess.MatchStore;

record AndroidMatchPreferences(SharedPreferences delegate) implements MatchStore.Preferences {
    @Override public String getString(String key, String fallback) { return delegate.getString(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return delegate.getBoolean(key, fallback); }
    @Override public int getInt(String key, int fallback) { return delegate.getInt(key, fallback); }
    @Override public long getLong(String key, long fallback) { return delegate.getLong(key, fallback); }
    @Override public boolean contains(String key) { return delegate.contains(key); }
    @Override public MatchStore.Editor edit() { return new Transaction(delegate.edit()); }

    private record Transaction(SharedPreferences.Editor delegate) implements MatchStore.Editor {
        @Override public MatchStore.Editor putString(String key, String value) { delegate.putString(key, value); return this; }
        @Override public MatchStore.Editor putBoolean(String key, boolean value) { delegate.putBoolean(key, value); return this; }
        @Override public MatchStore.Editor putInt(String key, int value) { delegate.putInt(key, value); return this; }
        @Override public MatchStore.Editor putLong(String key, long value) { delegate.putLong(key, value); return this; }
        @Override public MatchStore.Editor remove(String key) { delegate.remove(key); return this; }
        @Override public boolean commit() { return delegate.commit(); }
    }
}
