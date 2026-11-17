package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.readObject;
import static ro.atf.chess.WebEvidencePublication.same;
import static ro.atf.chess.WebEvidencePublication.sha256;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts.Phase;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Publishes real MCP browser captures independently of the optional archived Android references. */
@Tag("web-evidence")
class ChessWebStudyPublication {
    private static final List<String> ROUTES = List.of("puzzles", "openings", "analysis");
    private static final List<Profile> PROFILES = List.of(new Profile("compact", 360, 640, 1.3),
            new Profile("desktop", 1280, 900, 1));
    private static final String SOURCE = "chess.web.study.source";

    record Profile(String name, int width, int height, double scale) { }
    record Capture(String format, String originalHash, byte[] png) { }

    @Test void publishStudyScreenshotsFromBrowserMcp() throws IOException {
        Path browser = configuredPath("chess.web.study.browser");
        Path report = configuredPath("chess.web.study.extent");
        String revision = System.getProperty(SOURCE, "");
        if (!revision.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Supply a full Git commit with -D" + SOURCE);
        if (Files.exists(report)) throw new IllegalArgumentException("Choose a fresh Study evidence report");
        boolean passed = true;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings())) {
            for (var profile : PROFILES) {
                for (String route : ROUTES) {
                    String primary = profile.name() + "-" + route;
                    passed &= publishBrowser(session, browser, revision, profile, route, primary, true);
                    for (String checkpoint : extraCheckpoints(browser, primary)) {
                        passed &= publishBrowser(session, browser, revision, profile, route, checkpoint, false);
                    }
                }
            }
            passed &= publishArchivedReferences(session);
            publishComparisonStatus(session);
        }
        System.out.println("J7 Study MCP Extent: " + report);
        assertTrue(passed, "Study browser or archived evidence is missing, inconsistent or did not pass. See " + report);
    }

    private static List<String> extraCheckpoints(Path directory, String primary) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.matches(primary + "-[a-z0-9-]+\\.json"))
                    .map(name -> name.substring(0, name.length() - 5)).sorted().toList();
        }
    }

    private static boolean publishBrowser(ExtentReportSession session, Path directory, String revision, Profile profile,
                                          String route, String id, boolean primary) {
        String label = primary ? title(route) + " on " + profile.name() : id;
        try (var scenario = session.startScenario("Study Browser MCP", label,
                List.of("e2e", "web", "browser-mcp", profile.name(), route)); var phase = scenario.startPhase("TEST BODY")) {
            boolean passed;
            try {
                JsonNode receipt = readObject(Files.readAllBytes(directory.resolve(id + ".json")), "Study MCP browser receipt " + id);
                var checks = new ArrayList<>(List.of(
                        same("Receipt belongs to this profile and route", List.of(profile.name(), route),
                                List.of(receipt.path("profile").asText(), receipt.path("route").asText())),
                        same("Browser entry points at the Study route", true, studyUri(receipt.path("url").asText(), route)),
                        same("Browser receipt matches the Web source revision", revision, receipt.path("sourceRevision").asText())));
                JsonNode viewport = receipt.path("viewport");
                JsonNode board = receipt.path("board");
                checks.add(same("Measured width and height in CSS px", List.of(profile.width(), profile.height()),
                        List.of(viewport.path("width").asInt(-1), viewport.path("height").asInt(-1))));
                checks.add(same("Measured root text matches the profile scale", true, expectedFont(viewport, profile)));
                if (primary || board.isObject()) {
                    checks.add(same("The square board fits within the visible viewport", true, boardFits(board, profile)));
                }
                double deviceScale = viewport.path("deviceScaleFactor").asDouble(Double.NaN);
                checks.add(same("Device pixel ratio was recorded", true, Double.isFinite(deviceScale) && deviceScale > 0));
                report(phase, "Browser observation", List.of(new ReportField("Source revision", revision),
                        new ReportField("URL", receipt.path("url").asText()), new ReportField("Viewport", viewport.toString()),
                        new ReportField("Board", board.toString()), new ReportField("Position", receipt.path("position").asText("—"))));
                passed = assertions(phase, "Verify measured geometry and source", checks);
                passed &= assertions(phase, "Verify browser interaction", behavior(receipt.path("checks")));
                Capture capture = capture(directory, id, receipt.path("captureSha256").asText(), profile.width(), profile.height());
                attach(phase, "Browser MCP · " + label, capture);
            } catch (IOException | IllegalArgumentException invalid) {
                passed = assertions(phase, "Read and validate browser evidence",
                        List.of(same("Bound receipt and readable capture", "Valid MCP evidence", invalid.getMessage())));
            }
            phase.complete(status(passed));
            scenario.complete(status(passed));
            return passed;
        }
    }

    private static boolean publishArchivedReferences(ExtentReportSession session) {
        if (System.getProperty("chess.web.study.native", "").isBlank()) return true;
        Path directory = configuredPath("chess.web.study.native");
        try (var scenario = session.startScenario("Archived APK reference", "Read the authentic Android reference captures",
                List.of("android", "archived-evidence")); var phase = scenario.startPhase("TEST BODY")) {
            boolean passed;
            try {
                JsonNode manifest = readObject(Files.readAllBytes(directory.resolve("manifest.json")), "Native Study capture manifest");
                if (!manifest.path("captures").isArray() || manifest.path("captures").isEmpty()) {
                    throw new IOException("The native reference manifest has no captures");
                }
                Path measured = directory.resolve("board-geometry.json");
                JsonNode geometry = Files.isRegularFile(measured) ? readObject(Files.readAllBytes(measured), "Native board measurements") : null;
                for (var entry : manifest.path("captures")) publishArchivedFrame(phase, directory, entry, geometry);
                passed = true;
            } catch (IOException | IllegalArgumentException invalid) {
                passed = assertions(phase, "Read and validate APK references",
                        List.of(same("Archive hash and embedded Android capture", "Bound APK evidence", invalid.getMessage())));
            }
            phase.complete(status(passed));
            scenario.complete(status(passed));
            return passed;
        }
    }

    private static void publishArchivedFrame(Phase phase, Path directory, JsonNode entry, JsonNode geometry) throws IOException {
        Path archive = Path.of(entry.path("sourceReport").asText()).normalize();
        Path receipt = Path.of(entry.path("receipt").asText()).normalize();
        if (!archive.isAbsolute() || !receipt.isAbsolute() || !archive.getParent().equals(receipt.getParent())
                || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The APK reference must identify the release report and its receipt in the same archive");
        }
        String file = entry.path("file").asText();
        if (!file.matches("[a-z0-9-]+\\.png")) throw new IOException("The archived reference needs an original PNG: " + file);
        String id = file.substring(0, file.length() - 4);
        Capture capture = capture(directory, id, entry.path("sha256").asText(),
                entry.path("width").asInt(), entry.path("height").asInt());
        byte[] contents = Files.readAllBytes(archive);
        String archiveHash = sha256(contents);
        String payload = Base64.getEncoder().encodeToString(capture.png());
        String sourceCommit = entry.path("sourceCommit").asText();
        String apk = entry.path("apkSha256").asText();
        String receiptText = Files.readString(receipt);
        if (!archiveHash.equals(entry.path("sourceReportSha256").asText())
                || !sourceCommit.matches("[0-9a-f]{40}") || !apk.matches("[0-9a-f]{64}")
                || List.of(archiveHash, capture.originalHash(), sourceCommit, apk).stream().anyMatch(value -> !receiptText.contains("\"" + value + "\""))
                || !new String(contents, StandardCharsets.UTF_8).contains("data:image/png;base64," + payload)) {
            throw new IOException("The recorded APK capture does not match the archived report: " + id);
        }
        String title = "APK " + entry.path("release").asText() + " · " + title(entry.path("flow").asText()) + " · " + id;
        var fields = new ArrayList<>(List.of(new ReportField("Archived report", archive.getFileName().toString()),
                new ReportField("Archived report SHA-256", archiveHash), new ReportField("APK SHA-256", apk),
                new ReportField("Source commit", sourceCommit), new ReportField("Position", entry.path("checkpoint").asText()),
                new ReportField("Native profile", entry.path("profileWidth").asText() + "×" + entry.path("profileHeight").asText()
                        + ", " + entry.path("densityDpi").asText() + " dpi, text " + entry.path("fontScale").asText()
                        + "×, " + entry.path("locale").asText())));
        if (geometry != null && geometry.path("bounds").has(file)) {
            fields.add(new ReportField("Approximate board in native capture (physical px)", geometry.path("bounds").path(file).toString()));
            fields.add(new ReportField("Native board measurement", geometry.path("method").asText()));
        }
        report(phase, title, fields);
        attach(phase, title, capture);
    }

    private static void publishComparisonStatus(ExtentReportSession session) {
        try (var scenario = session.startScenario("Study visual parity", "Confirm 1:1 rendering against the APK",
                List.of("web", "android", "parity")); var phase = scenario.startPhase("TEST BODY")) {
            report(phase, "Qualification status", List.of(new ReportField("Result", "Web interaction verdicts apply to the MCP captures."),
                    new ReportField("APK comparison", "The native archive cannot qualify all three matching states and profiles.")));
            phase.complete(AtfReportStatus.SKIP);
            scenario.complete(AtfReportStatus.SKIP);
        }
    }

    static boolean expectedFont(JsonNode viewport, Profile profile) {
        double base = viewport.path("baseRootFontPx").asDouble(Double.NaN);
        double current = viewport.path("rootFontPx").asDouble(Double.NaN);
        return Math.abs(base - 16) < .1 && Math.abs(current - base * profile.scale()) < .1;
    }

    static boolean boardFits(JsonNode board, Profile profile) {
        double x = board.path("x").asDouble(Double.NaN), y = board.path("y").asDouble(Double.NaN);
        double width = board.path("width").asDouble(Double.NaN), height = board.path("height").asDouble(Double.NaN);
        return x >= -.5 && y >= -.5 && width > 0 && height > 0 && Math.abs(width - height) < 1
                && x + width <= profile.width() + .5 && y + height <= profile.height() + .5;
    }

    static List<AtfAssertion> behavior(JsonNode checks) {
        var assertions = new ArrayList<AtfAssertion>();
        var labels = new HashSet<String>();
        for (var check : checks) {
            String label = check.path("label").asText();
            var expected = check.path("expected");
            var actual = check.path("actual");
            boolean comparable = !label.isBlank() && labels.add(label) && expected.isValueNode() && !expected.isNull()
                    && actual.isValueNode() && !actual.isNull();
            assertions.add(new AtfAssertion(label, expected.toString(), actual.toString(), comparable && expected.equals(actual)));
        }
        assertions.add(same("Browser provides at least one actual interaction observation", true, checks.isArray() && !labels.isEmpty()));
        return assertions;
    }

    private static boolean studyUri(String value, String route) {
        URI url = URI.create(value);
        boolean publicWeb = "https".equals(url.getScheme()) && "crististefanita.github.io".equals(url.getHost())
                && "/atf_j7/".equals(url.getPath());
        boolean local = "http".equals(url.getScheme()) && ("localhost".equals(url.getHost())
                || "127.0.0.1".equals(url.getHost()) || "[::1]".equals(url.getHost()));
        return (publicWeb || local) && route.equals(url.getFragment());
    }

    static Capture capture(Path directory, String id, String expectedHash, int width, int height) throws IOException {
        if (!id.matches("[a-z0-9-]+")) throw new IOException("Invalid capture ID: " + id);
        var candidates = List.of(directory.resolve(id + ".jpg"), directory.resolve(id + ".png")).stream()
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
        if (candidates.size() != 1) throw new IOException("Expected one original browser or Android frame for " + id);
        byte[] original = Files.readAllBytes(candidates.getFirst());
        String hash = sha256(original);
        if (!hash.equals(expectedHash)) throw new IOException("Original capture SHA-256 differs for " + id);
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unreadable screenshot " + id);
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                String expectedType = candidates.getFirst().toString().endsWith(".png") ? "PNG" : "JPEG";
                if (!expectedType.equals(format) || reader.getWidth(0) != width || reader.getHeight(0) != height) {
                    throw new IOException("Expected a " + width + "x" + height + " " + expectedType + " capture for " + id);
                }
                var image = reader.read(0);
                if ("PNG".equals(format)) return new Capture(format, hash, original);
                var png = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "PNG", png)) throw new IOException("PNG conversion failed for " + id);
                return new Capture(format, hash, png.toByteArray());
            } finally {
                reader.dispose();
            }
        }
    }

    private static void attach(Phase phase, String title, Capture capture) {
        try (var action = phase.startRequest("Evidence capture", "UI", title)) {
            action.detail("Original " + capture.format() + " SHA-256", capture.originalHash());
            action.detail("Embedded PNG SHA-256", sha256(capture.png()));
            action.screenshot(title, Base64.getEncoder().encodeToString(capture.png()));
            action.complete(AtfReportStatus.PASS);
        }
    }

    private static boolean assertions(Phase phase, String title, List<AtfAssertion> rows) {
        boolean passed = rows.stream().allMatch(AtfAssertion::passed);
        try (var step = phase.startAssertionStep(title)) {
            step.assertions(rows);
            step.complete(status(passed));
        }
        return passed;
    }

    private static void report(Phase phase, String title, List<ReportField> fields) {
        phase.observation(new ReportObservation(title, fields, List.of(), List.of()));
    }

    private static AtfReportStatus status(boolean passed) { return passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL; }

    private static String title(String route) {
        return switch (route) {
            case "puzzles" -> "Puzzles";
            case "openings" -> "Openings";
            case "review" -> "Game Review";
            default -> "Analysis board";
        };
    }
}
