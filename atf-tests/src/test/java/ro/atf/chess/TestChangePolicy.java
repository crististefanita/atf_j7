package ro.atf.chess;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import ro.atf.chess.release.GitTestChanges;
import ro.atf.chess.release.GitTestChanges.Kind;

final class TestChangePolicy {
    static final String REGISTER = "docs/testing/test-changes.yaml";
    private static final String JVM_ROOT = "atf-tests/src/test/java/";
    private static final String ANDROID_ROOT = "android/app/src/androidTest/";
    private static final Set<String> REQUIRED = Set.of("id", "base", "from", "replacement", "test", "contract", "allowDelete");
    private static final Set<String> NATIVE = Set.of("nativePath", "nativeProbe");
    private static final Pattern TEST = Pattern.compile("ro\\.atf\\.chess\\.(?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*#[A-Za-z_$][\\w$]*");

    private record Control(String id, String base, String from, String replacement, String test,
                           String contract, boolean allowDelete, String nativePath, String nativeProbe) {
        boolean migration() { return !nativePath.isEmpty(); }
    }

    record Review(Set<String> methods, Set<String> componentOnly, List<String> evidence) { }

    private TestChangePolicy() { }

    static Review review(Path root, GitTestChanges.Snapshot snapshot) throws IOException {
        var changes = new LinkedHashMap<String, Set<Kind>>();
        for (var change : snapshot.changes()) {
            if (change.kind() != Kind.ADDED) {
                changes.computeIfAbsent(change.path(), ignored -> new LinkedHashSet<>()).add(change.kind());
            }
        }
        var controls = new LinkedHashMap<String, Control>();
        for (var control : controls(root.resolve(REGISTER))) {
            if (control.base().equals(snapshot.base())) {
                require(controls.putIfAbsent(control.from(), control) == null, "Multiple controls for " + control.from());
            }
        }
        var methods = new LinkedHashSet<String>();
        var componentOnly = new LinkedHashSet<String>();
        var evidence = new ArrayList<String>();
        for (var change : changes.entrySet()) {
            String from = change.getKey();
            var kinds = change.getValue();
            require(!kinds.contains(Kind.CONFLICT) && !kinds.contains(Kind.TYPE_CHANGED), "Resolve unsafe Git state " + kinds + " for " + from);
            Control control = controls.remove(from);
            require(control != null, "Missing exact testChanges control for " + kinds + " " + from + " at base " + snapshot.base());
            boolean deleted = kinds.contains(Kind.DELETED);
            if (deleted) {
                require(control.allowDelete(), "Deletion needs allowDelete: true for " + from);
                require(!from.equals(control.replacement()), "Deletion needs a distinct replacement for " + from);
            }
            require(existing(root, control.replacement()), "Replacement source is missing: " + control.replacement());
            if (deleted && from.startsWith(ANDROID_ROOT)) {
                require(control.migration(), "An Android deletion must identify its retained native probe: " + from);
            }
            if (control.migration()) {
                require(existing(root, control.nativePath()), "Retained native source is missing: " + control.nativePath());
                String quoted = "\"" + control.nativeProbe() + "\"";
                require(Files.readString(root.resolve(control.nativePath())).contains(quoted),
                        "Retained native probe is missing: " + control.nativeProbe());
                componentOnly.add(control.test());
            }
            methods.add(control.test());
            evidence.add(control.id() + " " + kinds + " " + from + " -> " + control.replacement() + " (" + control.test()
                    + ") | " + control.contract() + (control.migration() ? " | Android: " + control.nativePath() + " / " + control.nativeProbe() : ""));
        }
        require(controls.isEmpty(), "Controls at this base without a corresponding review: " + controls.keySet());
        return new Review(Set.copyOf(methods), Set.copyOf(componentOnly), List.copyOf(evidence));
    }

    private static List<Control> controls(Path registry) throws IOException {
        require(Files.isRegularFile(registry, LinkOption.NOFOLLOW_LINKS), "Missing registry: " + registry.getFileName());
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (Reader input = Files.newBufferedReader(registry)) {
            Object yaml = new Yaml(new SafeConstructor(options)).load(input);
            if (!(yaml instanceof Map<?, ?> map) || !map.keySet().equals(Set.of("testChanges"))
                    || !(map.get("testChanges") instanceof List<?> entries)) {
                throw new IOException("Expected only a testChanges list in " + REGISTER);
            }
            var ids = new LinkedHashSet<String>();
            var result = new ArrayList<Control>();
            for (Object item : entries) {
                Control control = control(item);
                require(ids.add(control.id()), "Duplicate testChanges ID " + control.id());
                result.add(control);
            }
            return List.copyOf(result);
        } catch (YAMLException invalid) {
            throw new IOException("Invalid YAML in " + REGISTER, invalid);
        }
    }

    private static Control control(Object object) throws IOException {
        if (!(object instanceof Map<?, ?> values)) throw new IOException("testChanges entries must be mappings");
        var allowed = new LinkedHashSet<>(REQUIRED);
        boolean migration = values.containsKey("nativePath") || values.containsKey("nativeProbe");
        if (migration) allowed.addAll(NATIVE);
        require(values.keySet().equals(allowed), "Unknown or missing keys in testChanges control: " + values.keySet());
        String id = text(values, "id");
        require(id.matches("[A-Z][A-Z0-9-]+"), "Invalid testChanges ID: " + id);
        String base = text(values, "base");
        require(base.matches("[a-f0-9]{40}|[a-f0-9]{64}"), "Use a full Git commit in " + id);
        String from = text(values, "from");
        require(exact(from) && GitTestChanges.protectedPath(from), "Invalid protected source in " + id + ": " + from);
        String test = text(values, "test");
        require(TEST.matcher(test).matches(), "Use exact JVM class#method in " + id);
        String replacement = text(values, "replacement");
        String classFile = JVM_ROOT + test.substring(0, test.indexOf('#')).replace('.', '/') + ".java";
        require(replacement.equals(classFile), "Replacement path and test class disagree in " + id);
        require(values.get("allowDelete") instanceof Boolean, "allowDelete must be boolean in " + id);
        String nativePath = migration ? text(values, "nativePath") : "";
        String nativeProbe = migration ? text(values, "nativeProbe") : "";
        require(!migration || from.startsWith(ANDROID_ROOT) && exact(nativePath)
                        && nativePath.startsWith(ANDROID_ROOT) && nativePath.endsWith(".java"),
                "Retained native probes must name an exact Android test source in " + id);
        require(!migration || !nativeProbe.contains("\"") && !nativeProbe.contains("\\"), "Use the exact plain native assertion label in " + id);
        return new Control(id, base, from, replacement, test, text(values, "contract"), (Boolean) values.get("allowDelete"), nativePath, nativeProbe);
    }

    private static boolean exact(String value) {
        return !value.startsWith("/") && !value.matches(".*[\\\\:*?\\[\\]].*")
                && List.of(value.split("/", -1)).stream().noneMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."));
    }

    private static boolean existing(Path root, String value) throws IOException {
        Path target = root.resolve(value);
        return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && target.toRealPath().startsWith(root.toRealPath());
    }

    private static String text(Map<?, ?> values, String key) throws IOException {
        if (!(values.get(key) instanceof String text) || text.isBlank() || !text.equals(text.strip()) || text.lines().count() != 1) {
            throw new IOException("testChanges field needs a single nonempty string: " + key);
        }
        return text;
    }

    private static void require(boolean condition, String diagnostic) throws IOException {
        if (!condition) throw new IOException(diagnostic);
    }
}
