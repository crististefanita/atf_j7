package ro.atf.chess;

import java.util.List;
import java.util.Map;

final class ChessYaml {
    private ChessYaml() { }

    static Map<?, ?> resource(String name) {
        return ChessResourceLoader.yaml(name);
    }

    static Map<?, ?> map(Map<?, ?> object, String key) {
        if (object.get(key) instanceof Map<?, ?> value) return value;
        throw new IllegalArgumentException("Expected mapping for " + key);
    }

    static String string(Map<?, ?> object, String key) {
        if (object.get(key) instanceof String value && !value.trim().isEmpty()) return value;
        throw new IllegalArgumentException("Expected text for " + key);
    }

    static int number(Map<?, ?> object, String key) {
        if (object.get(key) instanceof Integer value) return value;
        throw new IllegalArgumentException("Expected integer for " + key);
    }

    static List<?> list(Map<?, ?> object, String key) {
        if (object.get(key) instanceof List<?> value && !value.isEmpty()) return value;
        throw new IllegalArgumentException("Expected nonempty list for " + key);
    }
}
