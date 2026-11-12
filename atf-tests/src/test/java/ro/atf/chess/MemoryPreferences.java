package ro.atf.chess;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Preference transactions committed to an isolated in-memory fixture. */
final class MemoryPreferences implements QuestStore.Preferences {
    final Map<String, Object> data = new HashMap<>();
    boolean rejectCommit;
    int commitAttempts;

    @Override public String getString(String key, String fallback) { return (String) data.getOrDefault(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return (boolean) data.getOrDefault(key, fallback); }
    @Override public int getInt(String key, int fallback) { return (int) data.getOrDefault(key, fallback); }
    @Override public long getLong(String key, long fallback) { return (long) data.getOrDefault(key, fallback); }
    @Override public boolean contains(String key) { return data.containsKey(key); }
    @Override public QuestStore.Editor edit() { return new Transaction(); }

    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        var stored = (Set<?>) data.getOrDefault(key, fallback);
        var strings = new java.util.HashSet<String>();
        stored.forEach(value -> strings.add((String) value));
        return Set.copyOf(strings);
    }

    private final class Transaction implements QuestStore.Editor {
        private final Map<String, Object> writes = new HashMap<>();

        @Override public QuestStore.Editor putString(String key, String value) { writes.put(key, value); return this; }
        @Override public QuestStore.Editor putBoolean(String key, boolean value) { writes.put(key, value); return this; }
        @Override public QuestStore.Editor putInt(String key, int value) { writes.put(key, value); return this; }
        @Override public QuestStore.Editor putLong(String key, long value) { writes.put(key, value); return this; }
        @Override public QuestStore.Editor putStringSet(String key, Set<String> value) { writes.put(key, Set.copyOf(value)); return this; }
        @Override public QuestStore.Editor remove(String key) { writes.put(key, null); return this; }

        @Override public boolean commit() {
            commitAttempts++;
            if (rejectCommit) return false;
            writes.forEach((key, value) -> {
                if (value == null) data.remove(key);
                else data.put(key, value);
            });
            return true;
        }
    }
}
