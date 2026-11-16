package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class GameReviewSpeechEvidence {
    private static final Pattern RECEIPT = Pattern.compile("^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$");
    private static final Pattern ACCEPTED = Pattern.compile(
            ".* J7ReviewSpeech\\s*:\\s*ACCEPTED (\\d+) (\\S+) (\\d+) ([A-Za-z0-9+/=]+)$");
    private static final Map<String, String> LABELS = Map.of("GRAPH", "graphLabel", "MOVE", "moveLabel", "RETURN", "returnLabel");
    private static final List<String> STEPS = List.of("GRAPH", "MOVE", "RETURN");
    private static final List<String> PROVENANCE = List.of("runId", "serial", "api", "talkBackVersion",
            "apkSha256", "testApkSha256", "observerApkSha256");

    private GameReviewSpeechEvidence() { }

    static void publish(Path run) throws Exception {
        Path destination = run.resolve("talkback-review-extent.html");
        require(!Files.exists(destination), "Use a fresh Game Review TalkBack evidence folder");
        String runId = run.getFileName().toString().replaceFirst("^run-", "");
        require(runId.matches("[a-f0-9]{32}"), "Expected a Game Review run directory");
        List<String> log = Files.readAllLines(run.resolve("talkback.log"));
        List<Probe> probes = List.of(read(run, runId, "ro"), read(run, runId, "en"));
        for (String field : PROVENANCE) {
            String value = probes.getFirst().metadata().path(field).asText();
            require(!value.isBlank() && value.equals(probes.getLast().metadata().path(field).asText()), "Inconsistent " + field);
            require(!field.endsWith("Sha256") || value.matches("(?i)[0-9a-f]{64}"), "Invalid " + field);
        }
        boolean passed = true;
        try (var session = new ExtentReportSession(destination, AtfReportConfigurationLoader.load().reportingSettings())) {
            for (Probe probe : probes) {
                String locale = probe.locale().toUpperCase(Locale.ROOT);
                try (var scenario = session.startScenario("Game Review TalkBack", "Review announcements · " + locale,
                        List.of("e2e", "android", "accessibility"));
                     var phase = scenario.startPhase("TEST BODY")) {
                    phase.detail("Speech evidence", "Installed TalkBack UID · synthesized PCM accepted by the TTS observer");
                    phase.detail("Run provenance", probe.metadata().toString());
                    boolean localePassed = true;
                    for (int index = 0; index < STEPS.size(); index++) {
                        String step = STEPS.get(index);
                        var speech = speech(log, runId, "talkback-review-" + probe.locale(), step);
                        String label = probe.receipt().get(LABELS.get(step));
                        String spoken = String.join(" / ", speech.stream().map(Utterance::text).toList());
                        boolean spokenAsExpected = containsLabel(spoken, label);
                        var check = probe.receipt().get(String.format(Locale.ROOT, "check_%02d", index + 1)).split("\\t", -1);
                        try (var assertions = phase.startAssertionStep(switch (step) {
                            case "GRAPH" -> "Evaluation graph";
                            case "MOVE" -> "Selected move";
                            default -> "Return from Why";
                        })) {
                            assertions.assertions(List.of(
                                    new AtfAssertion(check[0], check[1], check[2], check[3].equals("true") && check[1].equals(check[2])),
                                    new AtfAssertion("TalkBack produces the localized announcement", label, spoken, spokenAsExpected)));
                            assertions.complete(status(spokenAsExpected));
                        }
                        try (var request = phase.startRequest("Android emulator", "Accessibility", step)) {
                            request.detail("Voice and accepted bytes", speech.stream().map(Utterance::voiceAndBytes).toList().toString());
                            request.screenshot(step, Base64.getEncoder().encodeToString(Files.readAllBytes(probe.capture(step))));
                            request.complete(status(spokenAsExpected));
                        }
                        localePassed &= spokenAsExpected;
                    }
                    phase.complete(status(localePassed));
                    scenario.complete(status(localePassed));
                    passed &= localePassed;
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(passed, "See " + destination);
    }

    private static Probe read(Path run, String runId, String locale) throws Exception {
        String scope = "talkback-review-" + locale;
        Path dir = run.resolve(scope);
        var metadata = new ObjectMapper().readTree(dir.resolve("execution.json").toFile());
        Path nativeReceipt = dir.resolve("native.txt");
        require(metadata.path("status").asText().equals("PASS") && metadata.path("runId").asText().equals(runId) && metadata.path("scope").asText().equals(scope)
                && metadata.path("locale").asText().equals(locale), "Execution identity differs for " + scope);
        require(sameDigest(nativeReceipt, metadata.path("receiptSha256").asText()), "Native receipt changed for " + scope);
        List<String> lines = Files.readAllLines(nativeReceipt);
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            var matcher = RECEIPT.matcher(line);
            if (matcher.matches()) require(values.putIfAbsent(matcher.group(1), matcher.group(2)) == null, "Duplicate native result");
        }
        require(lines.stream().filter("INSTRUMENTATION_CODE: -1"::equals).count() == 1
                && "PASS".equals(values.get("result")) && scope.equals(values.get("phase"))
                && runId.equals(values.get("runId")) && locale.equals(values.get("talkbackLocale")), "Native run identity or result differs for " + scope);
        var probe = new Probe(locale, dir, metadata, values);
        for (int index = 0; index < STEPS.size(); index++) {
            String step = STEPS.get(index);
            String name = "talkback-" + step.toLowerCase(Locale.ROOT);
            var check = values.getOrDefault(String.format(Locale.ROOT, "check_%02d", index + 1), "").split("\\t", -1);
            require(check.length == 4 && "true".equals(check[3]) && !check[0].isBlank() && check[1].equals(check[2]), "Invalid native checkpoint " + name);
            require(!values.getOrDefault(LABELS.get(step), "").isBlank(), "Missing localized label " + name);
            require(sameDigest(probe.capture(step), values.get("capture_" + name))
                    && sameDigest(probe.capture(step), metadata.path("captureSha256").path(name + ".png").asText()),
                    "Capture changed for " + name + " in " + locale);
        }
        return probe;
    }

    private static boolean sameDigest(Path file, String expected) throws Exception {
        return expected != null && expected.matches("(?i)[0-9a-f]{64}") && expected.equalsIgnoreCase(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
    }

    static List<Utterance> speech(List<String> lines, String runId, String scope, String step) {
        var marker = Pattern.compile(".* J7ReviewProbe\\s*:\\s*" + Pattern.quote(runId + " " + scope + " " + step) + "_(BEGIN|END)$");
        List<String> boundaries = new ArrayList<>();
        List<Utterance> accepted = new ArrayList<>();
        for (String line : lines) {
            var boundary = marker.matcher(line);
            if (boundary.matches()) boundaries.add(boundary.group(1));
            if (!boundaries.equals(List.of("BEGIN"))) continue;
            var actual = ACCEPTED.matcher(line);
            if (actual.matches() && Long.parseLong(actual.group(1)) > 0 && Long.parseLong(actual.group(3)) > 0) {
                String words = new String(Base64.getDecoder().decode(actual.group(4)), StandardCharsets.UTF_8);
                if (!words.isBlank()) accepted.add(new Utterance(words, actual.group(2), Long.parseLong(actual.group(3))));
            }
        }
        require(boundaries.equals(List.of("BEGIN", "END")), "An exact speech window is required for " + scope + " " + step);
        return List.copyOf(accepted);
    }

    static boolean containsLabel(String utterance, String expected) {
        String label = normalize(expected);
        return !label.isEmpty() && (" " + normalize(utterance) + " ").contains(" " + label + " ");
    }

    private static String normalize(String words) {
        return words.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static AtfReportStatus status(boolean passed) { return passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    record Utterance(String text, String voice, long bytes) {
        String voiceAndBytes() { return voice + " · " + bytes; }
    }

    private record Probe(String locale, Path dir, JsonNode metadata, Map<String, String> receipt) {
        Path capture(String step) { return dir.resolve("talkback-" + step.toLowerCase(Locale.ROOT) + ".png"); }
    }
}
