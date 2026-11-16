package ro.atf.chess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** An atomic, portable snapshot of the MatchStore preferences for a browser profile. */
final class MatchLedgerPreferences implements MatchStore.Preferences {
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private static final int STRING = 1;
    private static final int BOOLEAN = 2;
    private static final int INTEGER = 3;
    private static final int LONG = 4;
    private Map<String, Object> values = new LinkedHashMap<>();

    MatchLedgerPreferences() { }

    MatchLedgerPreferences(String encoded) {
        if (encoded == null || encoded.length() > (MAX_BYTES + 2) / 3 * 4) throw invalid();
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length > MAX_BYTES) throw invalid();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != VERSION) throw invalid();
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw invalid();
            for (int index = 0; index < count; index++) {
                String key = readText(input);
                Object value = switch (input.readUnsignedByte()) {
                    case STRING -> readText(input);
                    case BOOLEAN -> input.readBoolean();
                    case INTEGER -> input.readInt();
                    case LONG -> input.readLong();
                    default -> throw invalid();
                };
                if (values.putIfAbsent(key, value) != null) throw invalid();
            }
            if (input.read() != -1) throw invalid();
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid saved match library", error);
        }
    }

    String snapshot() {
        if (values.size() > MAX_ENTRIES) throw new IllegalStateException("Too many match preferences");
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(VERSION);
            output.writeInt(values.size());
            for (var entry : values.entrySet()) {
                writeText(output, entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String text) {
                    output.writeByte(STRING);
                    writeText(output, text);
                } else if (value instanceof Boolean flag) {
                    output.writeByte(BOOLEAN);
                    output.writeBoolean(flag);
                } else if (value instanceof Integer number) {
                    output.writeByte(INTEGER);
                    output.writeInt(number);
                } else if (value instanceof Long number) {
                    output.writeByte(LONG);
                    output.writeLong(number);
                } else {
                    throw new IllegalStateException("Unsupported match preference");
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot encode the saved match library", error);
        }
        if (bytes.size() > MAX_BYTES) throw new IllegalStateException("Saved match library is too large");
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    @Override public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return (Boolean) values.getOrDefault(key, fallback); }
    @Override public int getInt(String key, int fallback) { return (Integer) values.getOrDefault(key, fallback); }
    @Override public long getLong(String key, long fallback) { return (Long) values.getOrDefault(key, fallback); }
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public MatchStore.Editor edit() { return new Transaction(values); }

    private final class Transaction implements MatchStore.Editor {
        private final Map<String, Object> base;
        private final Map<String, Object> next;

        private Transaction(Map<String, Object> base) {
            this.base = base;
            next = new LinkedHashMap<>(base);
        }

        @Override public MatchStore.Editor putString(String key, String value) { next.put(key, value); return this; }
        @Override public MatchStore.Editor putBoolean(String key, boolean value) { next.put(key, value); return this; }
        @Override public MatchStore.Editor putInt(String key, int value) { next.put(key, value); return this; }
        @Override public MatchStore.Editor putLong(String key, long value) { next.put(key, value); return this; }
        @Override public MatchStore.Editor remove(String key) { next.remove(key); return this; }

        @Override public boolean commit() {
            if (values != base) return false;
            values = new LinkedHashMap<>(next);
            return true;
        }
    }

    private static String readText(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > input.available()) throw invalid();
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid saved match library"); }
}
