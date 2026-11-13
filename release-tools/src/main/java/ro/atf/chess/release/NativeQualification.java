package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Compares signed-candidate Android instrumentation and captures with the published Extent result. */
final class NativeQualification {
    private static final String RESULT = "INSTRUMENTATION_RESULT: ";
    private static final Pattern CHECK = Pattern.compile(RESULT + "check_([0-9]{2})=(.*)");
    private static final Pattern SCENARIO = Pattern.compile("<li class=\"test-item\"\\s+status=\"([^\"]+)\"");
    private static final Pattern ASSERTION_TABLE = Pattern.compile("(?s)<div class=\"atf-ui-assertions\"[^>]*"
            + "data-atf-evidence-status=\"([^\"]+)\"[^>]*data-atf-preview-limit=\"([1-9][0-9]{0,8})\"[^>]*>"
            + "<div class=\"atf-validation-table-wrap\"[^>]*>" + tableMarkup("helm-evidence-table helm-validation-table"));
    private static final Pattern OBSERVATION_TABLE = table("helm-evidence-table");
    private static final Pattern ROW = Pattern.compile("(?s)<tr>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?s)<td>(.*?)</td>");
    private static final Pattern CAPTURE = Pattern.compile("<a class=\"atf-ui-action-screenshot-link\"[^>]+?href=\"data:image/png;base64,([A-Za-z0-9+/=]+)\"");
    private static final Pattern ENTITY = Pattern.compile("&(amp|lt|gt|quot|apos|#(?:[0-9]{1,7}|x[0-9a-fA-F]{1,6}));");
    private static final List<Scope> SCOPES = List.of(
            new Scope("", "game-review", 23,
                    List.of("A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row"),
                    List.of("game-review.png", "game-review-navigation.png", "game-review-line.png",
                            "game-review-comparison.png", "game-review-actions.png")),
            new Scope("why", "game-review-why", 5, List.of(
                    "Why shows both expected scores and search depth when the line proves no concrete reason",
                    "Why names the mating queen on h4 for the archived g4 blunder",
                    "The recommended branch starts before g4 and replays its first legal move",
                    "The played branch replays g4 and the mating reply from the same starting position",
                    "Cancelling Try again keeps the played line and the original review open"),
                    List.of("game-review-why-score.png", "game-review-why.png", "game-review-why-recommended.png")),
            new Scope("why-restore", "game-review-why-restore", 2, List.of(
                    "A restarted process reopens the archived analysis on the played mating reply",
                    "Confirming from the reopened review creates a separate game and keeps the archived analysis"),
                    List.of("game-review-why-restore.png")));

    private NativeQualification() {}

    record Result(String runId, int assertions, List<String> phases) {}

    static Result verify(ReleaseEvidence.Candidate candidate, Path receiptDirectory, Path extent) throws IOException {
        Path root = receiptDirectory.toRealPath();
        var phases = new ArrayList<Phase>();
        for (Scope scope : SCOPES) {
            Path directory = root.resolve(scope.directory()).toRealPath();
            require(scope.directory().isEmpty() || directory.getParent().equals(root),
                    "Android phase directory escapes the raw evidence: " + scope.name());
            phases.add(readPhase(candidate, directory, scope));
        }
        Phase first = phases.getFirst();
        require(phases.stream().allMatch(phase -> phase.execution().equals(first.execution())),
                "Android receipts come from different executions (run ID, device or API)");
        require(!phases.get(1).pid().equals(phases.get(2).pid()), "Android Why restore did not restart the process");
        var captureDigests = phases.stream().flatMap(phase -> phase.captureDigests().stream()).toList();
        require(new HashSet<>(captureDigests).size() == captureDigests.size(), "Android evidence repeats a captured frame");
        verifyExtent(candidate, extent, phases, captureDigests);
        return new Result(first.execution().runId(), phases.stream().mapToInt(phase -> phase.assertions().size()).sum(),
                phases.stream().map(phase -> phase.scope().name()).toList());
    }

    private static Phase readPhase(ReleaseEvidence.Candidate candidate, Path directory, Scope scope) throws IOException {
        Path receipt = evidenceFile(directory, scope.name() + ".txt");
        var lines = Files.readAllLines(receipt);
        var checks = new LinkedHashMap<Integer, List<String>>();
        for (String line : lines) {
            if (!line.startsWith(RESULT + "check_")) continue;
            var match = CHECK.matcher(line);
            require(match.matches(), scope.name() + " contains a malformed native assertion");
            int number = Integer.parseInt(match.group(1));
            String[] fields = match.group(2).split("\t", -1);
            require(number >= 1 && number <= scope.count() && fields.length == 4 && !fields[0].isBlank()
                            && fields[1].equals(fields[2]) && fields[3].equals("true")
                            && checks.putIfAbsent(number, List.of(fields[0], fields[1], fields[2], "✅")) == null,
                    scope.name() + " has a failed, duplicate or malformed native assertion " + number);
        }
        var assertions = List.copyOf(checks.values());
        var names = assertions.stream().map(row -> row.getFirst()).toList();
        require(assertions.size() == scope.count() && new HashSet<>(names).size() == names.size()
                        && names.containsAll(scope.required()),
                scope.name() + " does not contain all " + scope.count() + " required native assertions");
        require(marker(lines, RESULT + "phase=").equals(scope.name()) && marker(lines, RESULT + "result=").equals("PASS")
                        && marker(lines, "INSTRUMENTATION_CODE: ").equals("-1")
                        && lines.stream().noneMatch(NativeQualification::failureMarker),
                scope.name() + " has a failed or conflicting native result");
        String pid = marker(lines, RESULT + "pid=");
        require(pid.matches("[1-9][0-9]{0,9}"), scope.name() + " has an invalid Android PID");

        Path metadataFile = evidenceFile(directory, "execution.json");
        var metadata = new ExecutionJson(Files.readString(metadataFile), metadataFile).read();
        require("PASS".equals(metadata.get("status")) && scope.name().equals(metadata.get("scope"))
                        && Integer.valueOf(scope.count()).equals(metadata.get("assertions"))
                        && ReleaseEvidence.digest(receipt).equals(metadata.get("receiptSha256")),
                scope.name() + " execution metadata does not match the raw receipt and assertion count");
        require("release".equals(metadata.get("variant")) && candidate.commit().equals(metadata.get("sourceCommit"))
                        && candidate.apkSha256().equals(metadata.get("apkSha256")),
                scope.name() + " receipt belongs to another source, APK or build variant");
        String runId = marker(lines, RESULT + "runId=");
        require(runId.matches("[0-9a-f]{32}") && runId.equals(metadata.get("runId")),
                scope.name() + " native run ID does not match its execution metadata");
        Object device = metadata.get("device");
        Object api = metadata.get("api");
        require(device instanceof String name && !name.isBlank() && api instanceof String level && level.matches("[1-9][0-9]{0,2}"),
                scope.name() + " lacks an Android device or API level");
        return new Phase(scope, new Execution(runId, (String) device, (String) api), pid, assertions,
                captures(directory, scope, metadata.get("captureSha256")));
    }

    private static List<String> captures(Path directory, Scope scope, Object hashes) throws IOException {
        if (!(hashes instanceof Map<?, ?> declared) || !declared.keySet().equals(Set.copyOf(scope.frames()))) {
            throw new IOException(scope.name() + " execution metadata does not declare exactly the expected PNG files");
        }
        var digests = new ArrayList<String>();
        for (String frame : scope.frames()) {
            Path image = evidenceFile(directory, frame);
            String actual = ReleaseEvidence.digest(image);
            require(actual.equals(declared.get(frame)), "Android capture SHA-256 mismatch: " + frame);
            verifyPng(image, frame);
            digests.add(actual);
        }
        return List.copyOf(digests);
    }

    private static void verifyPng(Path image, String name) throws IOException {
        try (var stream = ImageIO.createImageInputStream(image.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            require(readers.hasNext(), "Invalid Android PNG: " + name);
            var reader = readers.next();
            try {
                reader.setInput(stream);
                require(reader.getFormatName().equalsIgnoreCase("PNG") && reader.read(0) != null,
                        "Invalid Android PNG: " + name);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void verifyExtent(ReleaseEvidence.Candidate candidate, Path extent, List<Phase> phases,
                                     List<String> captureDigests) throws IOException {
        String html = Files.readString(extent);
        var scenario = SCENARIO.matcher(html);
        require(scenario.find() && scenario.group(1).equals("pass") && !scenario.find()
                        && html.contains("<p class=\"name\">BoardSanity</p>"),
                "Android Extent does not contain the passing BoardSanity scenario");

        var assertionTables = ASSERTION_TABLE.matcher(html);
        require(assertionTables.find(), "Android Extent has no rendered assertion results");
        var restart = List.of("Explanation and restore process IDs", "Two distinct Android PIDs",
                phases.get(1).pid() + " → " + phases.get(2).pid(), "✅");
        var sameRunText = "Matching run ID, APK, source, variant, device and API";
        require(matchesRenderedAssertions(assertionTables, List.of(restart,
                        List.of("All phases belong to the same execution", sameRunText, sameRunText, "✅"))),
                "Android Extent execution and restart assertions do not match the native receipts");
        for (Phase phase : phases) {
            require(assertionTables.find() && matchesRenderedAssertions(assertionTables, phase.assertions()),
                    "Android Extent assertion results do not match the " + phase.scope().name() + " receipt");
        }
        require(!assertionTables.find(), "Android Extent contains unexpected assertion results");

        var identities = new HashMap<String, Map<String, String>>();
        var observationTables = OBSERVATION_TABLE.matcher(html);
        while (observationTables.find()) {
            List<List<String>> fields = rows(observationTables.group(1));
            if (fields.isEmpty() || !fields.getFirst().getFirst().equals("PHASE")) continue;
            var values = new HashMap<String, String>();
            for (var field : fields) {
                require(field.size() == 2 && values.putIfAbsent(field.get(0), field.get(1)) == null,
                        "Android Extent contains a malformed execution identity");
            }
            require(identities.putIfAbsent(values.get("PHASE"), values) == null,
                    "Android Extent repeats a phase execution identity");
        }
        require(identities.size() == phases.size(), "Android Extent lacks a phase execution identity");
        for (Phase phase : phases) {
            var execution = phase.execution();
            require(Map.of("PHASE", phase.scope().name(), "ANDROID API", execution.api(), "DEVICE", execution.device(),
                            "VARIANT", "release", "SOURCE COMMIT", candidate.commit(), "APK SHA-256", candidate.apkSha256(),
                            "RUN ID", execution.runId(), "ANDROID PROCESS", phase.pid()).equals(identities.get(phase.scope().name())),
                    "Android Extent execution identity does not match the " + phase.scope().name() + " receipt");
        }
        verifyRenderedCaptures(html, captureDigests);
    }

    private static void verifyRenderedCaptures(String html, List<String> digests) throws IOException {
        var images = CAPTURE.matcher(html);
        for (String expected : digests) {
            require(images.find(), "Android Extent is missing an embedded native capture");
            try {
                var sha = MessageDigest.getInstance("SHA-256");
                byte[] bytes = Base64.getDecoder().decode(images.group(1));
                require(expected.equals(HexFormat.of().formatHex(sha.digest(bytes))),
                        "Android Extent embedded capture does not match the raw PNG");
            } catch (IllegalArgumentException invalidBase64) {
                throw new IOException("Android Extent contains an invalid embedded PNG", invalidBase64);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        require(!images.find(), "Android Extent contains unexpected screenshot evidence");
    }

    private static Pattern table(String classes) {
        return Pattern.compile("(?s)" + tableMarkup(classes));
    }

    private static String tableMarkup(String classes) {
        return "<table class=\"" + classes + "\"><thead>.*?</thead><tbody>(.*?)</tbody></table>";
    }

    private static boolean matchesRenderedAssertions(Matcher table, List<List<String>> nativeRows) {
        int limit = Integer.parseInt(table.group(2));
        return table.group(1).equals("pass") && rows(table.group(3)).equals(nativeRows.stream()
                .map(row -> List.of(row.get(0), preview(row.get(1), limit), preview(row.get(2), limit), row.get(3)))
                .toList());
    }

    private static String preview(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = limit - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }

    private static List<List<String>> rows(String tableBody) {
        var result = new ArrayList<List<String>>();
        var row = ROW.matcher(tableBody);
        while (row.find()) {
            var cells = new ArrayList<String>();
            var cell = CELL.matcher(row.group(1));
            while (cell.find()) cells.add(decodeHtml(cell.group(1)));
            if (!cells.isEmpty()) result.add(List.copyOf(cells));
        }
        return result;
    }

    private static String decodeHtml(String value) {
        return ENTITY.matcher(value).replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> {
                String digits = match.group(1).substring(1);
                int point = digits.startsWith("x") ? Integer.parseInt(digits.substring(1), 16) : Integer.parseInt(digits);
                yield Character.isValidCodePoint(point) ? Character.toString(point) : match.group();
            }
        }));
    }

    private static String marker(List<String> lines, String prefix) throws IOException {
        var values = lines.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length())).toList();
        require(values.size() == 1, "Android raw receipt must contain exactly one " + prefix.trim());
        return values.getFirst();
    }

    private static boolean failureMarker(String line) {
        return line.startsWith(RESULT + "diagnostic=") || line.startsWith("INSTRUMENTATION_FAILED:")
                || line.startsWith("INSTRUMENTATION_ABORTED:");
    }

    private static Path evidenceFile(Path directory, String name) throws IOException {
        Path path = directory.resolve(name).toRealPath();
        require(path.getParent().equals(directory) && Files.isRegularFile(path), "Invalid Android evidence file: " + path);
        return path;
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }

    private record Scope(String directory, String name, int count, List<String> required, List<String> frames) {}
    private record Execution(String runId, String device, String api) {}
    private record Phase(Scope scope, Execution execution, String pid, List<List<String>> assertions, List<String> captureDigests) {}

    /** The runner writes one object containing strings, an assertion count and a capture hash map. */
    private static final class ExecutionJson {
        private static final Pattern TOKEN = Pattern.compile("[ \\t\\r\\n]*(\"(?:[^\"\\\\\\p{Cntrl}]|\\\\[\"\\\\/bfnrt]|\\\\u[0-9a-fA-F]{4})*\"|0|[1-9][0-9]*|[{}:,])");
        private static final Pattern ESCAPE = Pattern.compile("\\\\(u[0-9a-fA-F]{4}|.)");
        private final String source;
        private final Path file;
        private int index;

        ExecutionJson(String source, Path file) {
            this.source = source;
            this.file = file;
        }

        Map<String, Object> read() throws IOException {
            check(next().equals("{"));
            var object = readObject(false);
            check(source.substring(index).isBlank());
            return object;
        }

        private Map<String, Object> readObject(boolean captureMap) throws IOException {
            var result = new HashMap<String, Object>();
            String token = next();
            if (token.equals("}")) return result;
            while (true) {
                String key = string(token);
                check(next().equals(":"));
                token = next();
                Object value;
                if (captureMap || token.startsWith("\"")) {
                    value = string(token);
                } else if (token.equals("{") && key.equals("captureSha256")) {
                    value = readObject(true);
                } else {
                    check(token.matches("[0-9]{1,9}"));
                    value = Integer.valueOf(token);
                }
                check(result.putIfAbsent(key, value) == null);
                token = next();
                if (token.equals("}")) return result;
                check(token.equals(","));
                token = next();
            }
        }

        private String next() throws IOException {
            var token = TOKEN.matcher(source).region(index, source.length());
            check(token.lookingAt());
            index = token.end();
            return token.group(1);
        }

        private String string(String token) throws IOException {
            check(token.startsWith("\""));
            return ESCAPE.matcher(token.substring(1, token.length() - 1)).replaceAll(match -> {
                String escape = match.group(1);
                String decoded = switch (escape) {
                    case "b" -> "\b";
                    case "f" -> "\f";
                    case "n" -> "\n";
                    case "r" -> "\r";
                    case "t" -> "\t";
                    default -> escape.startsWith("u") ? Character.toString(Integer.parseInt(escape.substring(1), 16)) : escape;
                };
                return Matcher.quoteReplacement(decoded);
            });
        }

        private void check(boolean condition) throws IOException {
            require(condition, "Malformed or duplicate Android execution metadata at offset " + index + ": " + file);
        }
    }
}
