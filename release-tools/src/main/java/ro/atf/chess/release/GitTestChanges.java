package ro.atf.chess.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Reads protected test changes from a Git checkout relative to an explicit review commit. */
public final class GitTestChanges {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final Set<String> CONFIGURATION = Set.of(
            "android/app/build.gradle", "android/settings.gradle", "scripts/run-android-sanity.ps1");
    private static final List<String> PATHS = List.of("atf-tests/src/test/", "android/app/src/androidTest/",
            "pom.xml", ":(glob)**/pom.xml", "android/app/build.gradle", "android/settings.gradle",
            "scripts/run-android-sanity.ps1");

    private GitTestChanges() { }

    public enum Kind { ADDED, MODIFIED, DELETED, TYPE_CHANGED, CONFLICT }

    public record Change(Kind kind, String path) { }

    public record Snapshot(String base, String head, boolean clean, List<Change> changes) {
        public Snapshot { changes = List.copyOf(changes); }
    }

    public static Snapshot read(Path root, String base) throws IOException {
        if (base == null || !COMMIT.matcher(base).matches()) {
            throw new IOException("Test-change baseline must be an explicit full lowercase commit SHA");
        }
        Path checkout = root.toRealPath();
        Path gitRoot = Path.of(line(command(checkout, "rev-parse", "--show-toplevel"))).toRealPath();
        if (!checkout.equals(gitRoot)) throw new IOException("Test-change scan must start at the Git checkout root");
        String resolved = line(command(checkout, "rev-parse", "--verify", "--end-of-options", base + "^{commit}"));
        if (!base.equals(resolved)) throw new IOException("Test-change baseline must identify a commit, not a tag object");
        String head = line(command(checkout, "rev-parse", "--verify", "HEAD^{commit}"));
        command(checkout, "merge-base", "--is-ancestor", base, head);

        var changes = new LinkedHashSet<Change>();
        // Inspect all three views so restoring the work tree or index cannot conceal an earlier deletion.
        changes.addAll(tracked(checkout, base, head));
        changes.addAll(tracked(checkout, "--cached", base));
        changes.addAll(tracked(checkout, base));
        boolean untracked = addUntracked(changes, checkout, false);
        boolean ignored = addUntracked(changes, checkout, true);
        boolean clean = !untracked && !ignored && command(checkout, "status", "--porcelain=v1", "-z",
                "--untracked-files=all", "--ignore-submodules=none").isEmpty();
        if (!head.equals(line(command(checkout, "rev-parse", "--verify", "HEAD^{commit}")))) {
            throw new IOException("Git HEAD changed during the test-change scan");
        }
        return new Snapshot(base, head, clean, changes.stream()
                .sorted(Comparator.comparing(Change::path).thenComparing(Change::kind)).toList());
    }

    public static boolean protectedPath(String path) {
        return path.startsWith("atf-tests/src/test/") || path.startsWith("android/app/src/androidTest/")
                || path.equals("pom.xml") || path.endsWith("/pom.xml") || CONFIGURATION.contains(path);
    }

    private static List<Change> tracked(Path root, String... comparison) throws IOException {
        var args = new ArrayList<>(List.of("diff", "--no-ext-diff", "--no-textconv", "--no-relative",
                "--name-status", "-z", "--no-renames", "--ignore-submodules=none"));
        args.addAll(List.of(comparison));
        args.add("--");
        args.addAll(PATHS);
        var fields = fields(command(root, args.toArray(String[]::new)));
        if (fields.size() % 2 != 0) throw new IOException("Git diff returned an incomplete test-change record");
        var changes = new ArrayList<Change>();
        for (int i = 0; i < fields.size(); i += 2) {
            String path = fields.get(i + 1);
            Kind kind = switch (fields.get(i)) {
                case "A" -> Kind.ADDED;
                case "M" -> Kind.MODIFIED;
                case "D" -> Kind.DELETED;
                case "T" -> Kind.TYPE_CHANGED;
                default -> Kind.CONFLICT;
            };
            if (protectedPath(path)) changes.add(new Change(kind, path));
        }
        return changes;
    }

    private static boolean addUntracked(Set<Change> changes, Path root, boolean ignored) throws IOException {
        var args = new ArrayList<>(List.of("ls-files", "--others", "--exclude-standard", "-z"));
        if (ignored) args.add("--ignored");
        args.add("--");
        args.addAll(PATHS);
        var paths = fields(command(root, args.toArray(String[]::new))).stream()
                .filter(GitTestChanges::protectedPath).toList();
        paths.forEach(path -> changes.add(new Change(Kind.ADDED, path)));
        return !paths.isEmpty();
    }

    private static List<String> fields(String value) throws IOException {
        if (value.isEmpty()) return List.of();
        if (value.charAt(value.length() - 1) != 0) throw new IOException("Git output is missing its NUL terminator");
        var parts = Arrays.asList(value.split("\u0000", -1));
        var result = parts.subList(0, parts.size() - 1);
        if (result.stream().anyMatch(String::isEmpty)) throw new IOException("Git output contains an empty field");
        return result;
    }

    private static String line(String output) {
        return output.replaceFirst("\\r?\\n\\z", "");
    }

    private static String command(Path root, String... args) throws IOException {
        var invocation = new ArrayList<>(List.of("git", "-C", root.toString(), "-c", "core.fsmonitor=false"));
        invocation.addAll(List.of(args));
        var builder = new ProcessBuilder(invocation);
        var environment = builder.environment();
        for (String variable : List.of("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE", "GIT_CONFIG_COUNT",
                "GIT_CONFIG_PARAMETERS", "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES",
                "GIT_LITERAL_PATHSPECS", "GIT_GLOB_PATHSPECS", "GIT_NOGLOB_PATHSPECS", "GIT_ICASE_PATHSPECS")) {
            environment.remove(variable);
        }
        environment.put("GIT_OPTIONAL_LOCKS", "0");
        environment.put("GIT_NO_REPLACE_OBJECTS", "1");
        var process = builder.start();
        var output = drain(process.getInputStream());
        var errors = drain(process.getErrorStream());
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("Git timed out during " + args[0]);
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            String diagnostic = new String(errors.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) {
                throw new IOException("Git " + args[0] + " failed (exit " + process.exitValue() + ")"
                        + (diagnostic.isEmpty() ? "" : ": " + diagnostic));
            }
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Git test-change scan interrupted", interrupted);
        } catch (ExecutionException | TimeoutException unavailable) {
            throw new IOException("Cannot read Git " + args[0] + " output", unavailable);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            output.cancel(true);
            errors.cancel(true);
        }
    }

    private static FutureTask<byte[]> drain(InputStream input) {
        var task = new FutureTask<>(() -> {
            try (input) {
                return input.readAllBytes();
            }
        });
        Thread.ofVirtual().start(task);
        return task;
    }
}
