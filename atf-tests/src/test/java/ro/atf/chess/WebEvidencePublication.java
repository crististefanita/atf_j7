package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts.Phase;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assertions;

/** Checks a compact browser-driver receipt and embeds its verified evidence in Extent. */
final class WebEvidencePublication {
    private static final int WIDTH = 390;
    private static final int HEIGHT = 844;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    record Frame(String file, String title, int width, int height) {
        Frame(String file, String title) { this(file, title, WIDTH, HEIGHT); }
    }
    record Scenario(String className, String title, String tag, String report, String observationTitle, List<Frame> frames) { }
    record Contract(List<ReportField> fields, List<AtfAssertion> identities, Map<String, JsonNode> observations, String locale) {
        Contract(List<ReportField> fields, List<AtfAssertion> identities, Map<String, JsonNode> observations) {
            this(fields, identities, observations, "ro-RO");
        }
    }

    @FunctionalInterface interface ReceiptContract { Contract inspect(JsonNode receipt) throws IOException; }

    private WebEvidencePublication() { }

    static void publish(Path receipt, Path bundle, Scenario specification, ReceiptContract contract) throws IOException {
        Path report = receipt.resolveSibling(specification.report());
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Web evidence folder");
        boolean valid;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario(specification.className(), specification.title(),
                     List.of("e2e", "web", specification.tag())); var phase = scenario.startPhase("TEST BODY")) {
            try {
                valid = publish(phase, receipt, bundle, specification, contract);
            } catch (IOException invalid) {
                valid = false;
                step(phase, "Read browser evidence", List.of(new AtfAssertion("Readable bound evidence",
                        "Receipt, inputs and bundle", invalid.getMessage(), false)));
            }
            phase.complete(status(valid));
            scenario.complete(status(valid));
        }
        System.out.println("J7 Web Extent: " + report);
        Assertions.assertTrue(valid, "Missing, inconsistent or failing browser evidence: " + receipt);
    }

    private static boolean publish(Phase phase, Path receiptFile, Path bundle, Scenario specification,
                                   ReceiptContract contract) throws IOException {
        byte[] receiptBytes = Files.readAllBytes(receiptFile);
        JsonNode receipt = readObject(receiptBytes, "Browser receipt");
        var specific = contract.inspect(receipt);
        String runId = receipt.path("runId").asText();
        String browser = receipt.path("browser").asText();
        String profile = receipt.path("profile").asText();
        var viewport = receipt.path("viewport");
        String bundleHash = bundleSha256(bundle);

        var fields = new ArrayList<>(List.of(new ReportField("Run ID", runId), new ReportField("Browser", browser),
                new ReportField("Profile", profile), new ReportField("Site manifest SHA-256", bundleHash),
                new ReportField("Driver receipt SHA-256", sha256(receiptBytes))));
        fields.addAll(specific.fields());
        phase.observation(new ReportObservation("Browser evidence identity", fields, List.of(), List.of()));
        var identity = new ArrayList<>(List.of(rule("Run ID", "Canonical UUID v4", runId,
                        runId.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")),
                rule("Browser is recorded", "Browser build", browser, !browser.isBlank()),
                same("Compact browser display profile is recorded", "390x844 CSS px, DPR 1, locale " + specific.locale(), profile),
                same("Compact browser viewport and locale", List.of(WIDTH, HEIGHT, 1, specific.locale()), List.of(
                        viewport.path("width").asInt(), viewport.path("height").asInt(),
                        viewport.path("deviceScaleFactor").asInt(), viewport.path("locale").asText())),
                same("Receipt identifies the complete tested Web bundle", bundleHash, receipt.path("bundleSha256").asText())));
        identity.addAll(specific.identities());
        boolean passed = step(phase, "Verify execution and inputs", identity);
        passed &= observations(phase, receipt.path("observations"), specification.observationTitle(), specific.observations());
        passed &= frames(phase, receipt.path("captures"), receiptFile.getParent(), specification.frames());
        return passed;
    }

    private static boolean observations(Phase phase, JsonNode rows, String title, Map<String, JsonNode> expected) {
        var byId = index(rows, "id");
        var assertions = new ArrayList<AtfAssertion>();
        assertions.add(same("Exactly the required browser assertions occur once", expected.keySet().stream().sorted().toList(),
                byId.keySet().stream().sorted().toList()));
        assertions.add(same("There are no duplicate browser assertions", expected.size(), rows.size()));
        for (var expectation : expected.entrySet()) {
            var wanted = expectation.getValue();
            JsonNode reported = byId.get(expectation.getKey());
            JsonNode actual = reported == null ? null : reported.get("actual");
            boolean passed = reported != null && wanted.isValueNode() && !wanted.isNull()
                    && wanted.equals(reported.get("expected")) && wanted.equals(actual)
                    && BooleanNode.TRUE.equals(reported.get("passed"));
            assertions.add(rule(expectation.getKey(), String.valueOf(wanted), String.valueOf(actual), passed));
        }
        return step(phase, title, assertions);
    }

    private static boolean frames(Phase phase, JsonNode frames, Path directory, List<Frame> required) throws IOException {
        var byFile = index(frames, "file");
        var expectedFiles = required.stream().map(Frame::file).sorted().toList();
        var actualFiles = byFile.keySet().stream().sorted().toList();
        boolean passed = step(phase, "Check required capture inventory", List.of(
                same("Every required PNG is identified", expectedFiles, actualFiles),
                same("Each capture is identified once", expectedFiles.size(), frames.size())));
        for (Frame frame : required) {
            var claimed = byFile.get(frame.file());
            Path image = directory.resolve(frame.file());
            byte[] bytes = Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(image) : null;
            String hash = bytes == null ? "" : sha256(bytes);
            boolean matches = claimed != null && claimed.path("sha256").asText().equals(hash) && validPng(bytes, frame);
            if (!matches) {
                passed = false;
                step(phase, frame.title(), List.of(rule(frame.file(),
                        "Readable " + frame.width() + "x" + frame.height() + " PNG matching receipt SHA-256",
                        hash.isEmpty() ? "Missing PNG" : "Observed SHA-256 " + hash, false)));
                continue;
            }
            try (var action = phase.startRequest("Browser driver", "UI", frame.title())) {
                action.detail("Capture SHA-256", hash);
                action.screenshot(frame.title(), Base64.getEncoder().encodeToString(bytes));
                action.complete(AtfReportStatus.PASS);
            }
        }
        return passed;
    }

    private static Map<String, JsonNode> index(JsonNode records, String key) {
        var indexed = new HashMap<String, JsonNode>();
        if (records.isArray()) for (var entry : records) indexed.putIfAbsent(entry.path(key).asText(), entry);
        return indexed;
    }

    static boolean validPng(byte[] bytes, Frame frame) {
        if (bytes == null) return false;
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (!"PNG".equalsIgnoreCase(reader.getFormatName())) return false;
                var image = reader.read(0);
                return image != null && image.getWidth() == frame.width() && image.getHeight() == frame.height();
            } finally {
                reader.dispose();
            }
        } catch (IOException invalid) {
            return false;
        }
    }

    static String bundleSha256(Path bundle) throws IOException {
        try (var paths = Files.walk(bundle)) {
            var files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> relative(bundle, path))).toList();
            if (files.isEmpty()) throw new IOException("The Web site has no files");
            var manifest = new StringBuilder();
            for (Path file : files) {
                manifest.append(sha256(Files.readAllBytes(file))).append("  ").append(relative(bundle, file)).append('\n');
            }
            return sha256(manifest.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String relative(Path root, Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    static JsonNode readObject(byte[] bytes, String name) throws IOException {
        var parsed = JSON.readTree(bytes);
        if (parsed == null || !parsed.isObject()) throw new IOException(name + " must be a JSON object");
        return parsed;
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static Path configuredPath(String key) {
        String value = System.getProperty(key, "");
        if (value.isBlank()) throw new IllegalArgumentException("Supply -D" + key);
        return Path.of(value).toAbsolutePath().normalize();
    }

    static AtfAssertion same(String name, Object expected, Object actual) {
        return rule(name, String.valueOf(expected), String.valueOf(actual), Objects.equals(expected, actual));
    }

    private static AtfAssertion rule(String name, String expected, String actual, boolean passed) {
        return new AtfAssertion(name, expected, actual, passed);
    }

    private static boolean step(Phase phase, String title, List<AtfAssertion> rows) {
        boolean passed = rows.stream().allMatch(AtfAssertion::passed);
        try (var step = phase.startAssertionStep(title)) {
            step.assertions(rows);
            step.complete(status(passed));
        }
        return passed;
    }

    private static AtfReportStatus status(boolean passed) { return passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL; }
}
