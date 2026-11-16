package ro.atf.chess;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Adapts the portable preference snapshot to the shared puzzle store's sets. */
final class WebQuestPreferences implements QuestStore.Preferences {
    private static final String POSITIONS = "web-quest-positions";
    private final MatchLedgerPreferences ledger;
    private Map<String, String> positions;

    WebQuestPreferences(String saved) {
        ledger = saved == null || saved.isEmpty() ? new MatchLedgerPreferences() : new MatchLedgerPreferences(saved);
        positions = new TreeMap<>();
        for (String row : ledger.getString(POSITIONS, "").split("\n")) {
            if (row.isEmpty()) continue;
            int separator = row.indexOf('\t');
            if (separator < 1 || !positionKey(row.substring(0, separator))) throw new IllegalArgumentException("Invalid puzzle positions");
            if (positions.putIfAbsent(row.substring(0, separator), row.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Duplicate saved puzzle");
            }
        }
    }

    String snapshot() { return ledger.snapshot(); }

    @Override public String getString(String key, String fallback) {
        return positionKey(key) ? positions.getOrDefault(key, fallback) : ledger.getString(key, fallback);
    }
    @Override public boolean getBoolean(String key, boolean fallback) { return ledger.getBoolean(key, fallback); }
    @Override public int getInt(String key, int fallback) {
        if (!positionKey(key)) return ledger.getInt(key, fallback);
        String value = positions.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }
    @Override public long getLong(String key, long fallback) { return ledger.getLong(key, fallback); }
    @Override public boolean contains(String key) { return positionKey(key) ? positions.containsKey(key) : ledger.contains(key); }

    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        String value = getString(key, null);
        if (value == null) return fallback;
        return value.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(value.split("\\n")));
    }

    @Override public QuestStore.Editor edit() { return new Transaction(ledger.edit()); }

    private static boolean positionKey(String key) { return key.startsWith("quest-ply-") || key.startsWith("quest-finish-"); }

    private final class Transaction implements QuestStore.Editor {
        private final MatchStore.Editor editor;
        private final Map<String, String> next = new TreeMap<>(positions);

        Transaction(MatchStore.Editor editor) { this.editor = editor; }

        @Override public QuestStore.Editor putString(String key, String value) {
            if (positionKey(key)) next.put(key, value);
            else editor.putString(key, value);
            return this;
        }
        @Override public QuestStore.Editor putBoolean(String key, boolean value) { editor.putBoolean(key, value); return this; }
        @Override public QuestStore.Editor putInt(String key, int value) {
            if (positionKey(key)) return putString(key, Integer.toString(value));
            editor.putInt(key, value);
            return this;
        }
        @Override public QuestStore.Editor putLong(String key, long value) { editor.putLong(key, value); return this; }
        @Override public QuestStore.Editor remove(String key) {
            if (positionKey(key)) next.remove(key);
            else editor.remove(key);
            return this;
        }
        @Override public QuestStore.Editor putStringSet(String key, Set<String> value) {
            var encoded = new StringBuilder();
            for (String item : new TreeSet<>(value)) {
                if (encoded.length() > 0) encoded.append('\n');
                encoded.append(item);
            }
            return putString(key, encoded.toString());
        }
        @Override public boolean commit() {
            // Catalog growth is independent of the match ledger's preference-key limit.
            var rows = new StringBuilder();
            next.forEach((key, value) -> {
                if (rows.length() > 0) rows.append('\n');
                rows.append(key).append('\t').append(value);
            });
            editor.putString(POSITIONS, rows.toString());
            if (!editor.commit()) return false;
            positions = next;
            return true;
        }
    }
}
