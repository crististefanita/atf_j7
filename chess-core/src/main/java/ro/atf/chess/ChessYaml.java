package ro.atf.chess;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class ChessYaml {
    private ChessYaml() { }

    static Map<?, ?> resource(String name) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (InputStream stream = ChessYaml.class.getResourceAsStream("/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing chess resource " + name);
            Object data = new Yaml(new SafeConstructor(options)).load(stream);
            if (data instanceof Map<?, ?> map) return map;
            throw new IllegalArgumentException("Expected YAML mapping in " + name);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read chess resource " + name, error);
        }
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
