package ro.atf.chess;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the same resource values as the JVM loader from data prepared before Web compilation. */
final class ChessResourceLoader {
    private ChessResourceLoader() { }

    static Map<?, ?> yaml(String name) {
        String[] chunks = name.endsWith(".yaml") ? GeneratedChessData.chunks(name) : null;
        if (chunks == null) throw new IllegalStateException("Missing chess resource " + name);
        try (var input = new DataInputStream(decode(chunks))) {
            if (readValue(input) instanceof Map<?, ?> map && input.read() == -1) return map;
            throw new IllegalArgumentException("Expected YAML mapping in " + name);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read chess resource " + name, error);
        }
    }

    static InputStream open(String name) {
        String[] chunks = name.endsWith(".yaml") ? null : GeneratedChessData.chunks(name);
        return chunks == null ? null : decode(chunks);
    }

    private static ByteArrayInputStream decode(String[] chunks) {
        var encoded = new StringBuilder();
        for (String chunk : chunks) encoded.append(chunk);
        return new ByteArrayInputStream(Base64.getDecoder().decode(encoded.toString()));
    }

    private static Object readValue(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 'm' -> readMap(input);
            case 'l' -> readList(input);
            case 's' -> readString(input);
            case 'i' -> input.readInt();
            case 'j' -> input.readLong();
            case 'd' -> input.readDouble();
            case 't' -> true;
            case 'f' -> false;
            case 'n' -> null;
            default -> throw new IOException("Invalid generated YAML value type");
        };
    }

    private static Map<Object, Object> readMap(DataInputStream input) throws IOException {
        int size = input.readInt();
        var map = new LinkedHashMap<Object, Object>();
        for (int index = 0; index < size; index++) map.put(readValue(input), readValue(input));
        return map;
    }

    private static List<Object> readList(DataInputStream input) throws IOException {
        int size = input.readInt();
        var list = new ArrayList<Object>(size);
        for (int index = 0; index < size; index++) list.add(readValue(input));
        return list;
    }

    private static String readString(DataInputStream input) throws IOException {
        byte[] bytes = new byte[input.readInt()];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
