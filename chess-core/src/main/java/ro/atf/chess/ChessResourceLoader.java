package ro.atf.chess;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class ChessResourceLoader {
    private ChessResourceLoader() { }

    static InputStream open(String name) {
        return ChessResourceLoader.class.getResourceAsStream("/" + name);
    }

    static Map<?, ?> yaml(String name) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (InputStream stream = open(name)) {
            if (stream == null) throw new IllegalStateException("Missing chess resource " + name);
            Object data = new Yaml(new SafeConstructor(options)).load(stream);
            if (data instanceof Map<?, ?> map) return map;
            throw new IllegalArgumentException("Expected YAML mapping in " + name);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read chess resource " + name, error);
        }
    }
}
