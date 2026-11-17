package ro.atf.chess;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static ro.atf.chess.ChessWebStudyPublication.assertions;
import static ro.atf.chess.ChessWebStudyPublication.attach;
import static ro.atf.chess.ChessWebStudyPublication.report;
import static ro.atf.chess.ChessWebStudyPublication.status;
import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.readObject;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts.Phase;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.ChessWebStudyPublication.Profile;

/** Binds compact Browser MCP observations and original captures to the displayed match and playback position. */
@Tag("web-evidence")
class ChessWebMatchAnalysisPublication {
    static final String FIXTURE = "2|analysis|e2e4 e7e5";
    static final List<String> STAGES = List.of("history", "preview", "returned", "resumed");
    private static final Profile PROFILE = new Profile("compact", 360, 640, 1.3);
    private static final String HISTORICAL_FEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR";
    private static final String LIVE_FEN = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR";
    private static final ObjectMapper JSON = new ObjectMapper();

    record Identity(String runId, String revision, String runtimeDigest) { }

    record Position(int meter, MatchEvaluation evaluation, String recommendation) {
        static Position at(ChessMatch match, int ply) {
            var hint = match.hintAt(ply);
            var evaluation = hint.evaluation(match.positionAt(ply));
            hint.revealNext();
            return new Position((int) Math.round(evaluation.whiteShare() * 100), evaluation, hint.revealedMove().uci());
        }

        String score(String language) {
            int cp = evaluation.whiteCentipawns();
            String lead = "ro".equals(language) ? cp == 0 ? "Echilibru" : cp > 0 ? "Alb" : "Negru"
                    : cp == 0 ? "Balanced" : cp > 0 ? "White" : "Black";
            return evaluation.pawnLabel(Locale.forLanguageTag(language)) + " · " + lead;
        }
    }

    @Test void publishCompactMatchAnalysisFromBrowserMcp() throws IOException {
        Path evidence = configuredPath("chess.web.match.browser");
        Path output = configuredPath("chess.web.match.extent");
        if (Files.exists(output)) throw new IllegalArgumentException("Choose a fresh match analysis Extent report");
        var identity = new Identity(token("chess.web.match.runId", 32), token("chess.web.match.source", 40), runtimeDigest());
        var match = ChessMatch.restore(FIXTURE);
        var historical = Position.at(match, 1);
        var live = Position.at(match, 2);
        var fixture = Arrays.stream(MatchAnalysisParityTest.fixture())
                .filter(example -> example.checkpoint().equals(FIXTURE) && example.historyReplay() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("Shared history replay fixture is missing"));
        var replay = List.of(fixture.recommendation(), fixture.historyReplay().reply(), fixture.recommendation());
        boolean passed = true;
        try (var session = new ExtentReportSession(output, AtfReportConfigurationLoader.load().reportingSettings())) {
            for (String language : List.of("ro", "en")) {
                passed &= publishLocale(session, evidence, identity, language, historical, live, replay);
            }
        }
        System.out.println("J7 AI match Browser MCP Extent: " + output);
        assertTrue(passed, "Missing or inconsistent browser match evidence: " + output);
    }

    private static String token(String property, int length) {
        String value = System.getProperty(property, "");
        if (!value.matches("[a-f0-9]{" + length + "}")) throw new IllegalArgumentException("Supply a hex identity for -D" + property);
        return value;
    }

    private static String runtimeDigest() throws IOException {
        Path runtime = configuredPath("chess.web.match.runtime");
        String hash = WebEvidencePublication.bundleSha256(runtime);
        if (!hash.equals(runtime.getFileName().toString()) || !Files.isRegularFile(runtime.resolve("game-review.mjs"))
                || !Files.isRegularFile(runtime.resolve("engine-worker.mjs")) || !Files.isRegularFile(runtime.resolve("chess-engine.mjs"))) {
            throw new IOException("The immutable runtime directory must match its own full asset manifest");
        }
        return hash;
    }

    private static boolean publishLocale(ExtentReportSession session, Path evidence, Identity identity, String language,
                                         Position historical, Position live, List<String> replay) {
        String prefix = "compact-ai-" + language;
        try (var scenario = session.startScenario("AI match Browser MCP", "History, suggested line and reopen · " + language.toUpperCase(Locale.ROOT),
                List.of("e2e", "web", "browser-mcp", "compact", language)); var phase = scenario.startPhase("TEST BODY")) {
            boolean passed;
            try {
                var receipt = readObject(Files.readAllBytes(evidence.resolve(prefix + ".json")), "Match MCP receipt " + prefix);
                report(phase, "Browser evidence identity", List.of(new ReportField("Run ID", receipt.path("runId").asText()),
                        new ReportField("Source revision", receipt.path("sourceRevision").asText()),
                        new ReportField("Runtime SHA-256", receipt.path("runtimeDigest").asText()),
                        new ReportField("URL", receipt.path("url").asText()), new ReportField("Measured viewport", receipt.path("viewport").toString())));
                passed = assertions(phase, "Verify execution, measured profile and checkpoint inventory", validateIdentity(receipt, identity, language));
                if (passed) {
                    var first = receipt.path("checkpoints").get(0);
                    int slot = first.path("slot").asInt(-1);
                    var matchBoard = first.path("board");
                    double dpr = receipt.path("viewport").path("deviceScaleFactor").asDouble();
                    for (var checkpoint : receipt.path("checkpoints")) {
                        passed &= publishCheckpoint(phase, evidence, prefix, checkpoint, slot, matchBoard, language, historical, live, replay, dpr);
                    }
                }
            } catch (IOException | IllegalArgumentException failure) {
                passed = assertions(phase, "Read bound browser observations",
                        List.of(same("Valid Browser MCP receipt", "Readable and verified evidence", failure.getMessage())));
            }
            phase.complete(status(passed));
            scenario.complete(status(passed));
            return passed;
        }
    }

    static List<AtfAssertion> validateIdentity(JsonNode receipt, Identity expected, String language) {
        var viewport = receipt.path("viewport");
        double deviceScale = viewport.path("deviceScaleFactor").asDouble(Double.NaN);
        return List.of(
                same("Run matches the other locale's execution", expected.runId(), receipt.path("runId").asText()),
                same("Source revision matches the qualified input", expected.revision(), receipt.path("sourceRevision").asText()),
                same("Runtime matches the independently hashed immutable assets", expected.runtimeDigest(), receipt.path("runtimeDigest").asText()),
                same("Actual document locale", language, receipt.path("locale").asText()),
                same("URL identifies the active game after reload", true, matchUrl(receipt.path("url").asText())),
                same("Compact viewport in CSS px", List.of(PROFILE.width(), PROFILE.height()),
                        List.of(viewport.path("width").asInt(-1), viewport.path("height").asInt(-1))),
                same("Text measures 130% of the default 16 CSS px", true, ChessWebStudyPublication.expectedFont(viewport, PROFILE)),
                same("Browser records a positive DPR", true, Double.isFinite(deviceScale) && deviceScale > 0),
                same("All four checkpoints occur once and in order", JSON.valueToTree(STAGES),
                        JSON.valueToTree(identifiers(receipt.path("checkpoints")))));
    }

    private static List<String> identifiers(JsonNode checkpoints) {
        var identifiers = new ArrayList<String>();
        if (checkpoints.isArray()) {
            for (var checkpoint : checkpoints) identifiers.add(checkpoint.path("id").asText());
        }
        return identifiers;
    }

    private static boolean matchUrl(String text) {
        URI uri = URI.create(text);
        boolean pages = "https".equals(uri.getScheme()) && ChessPublicationRoutes.PAGES_HOST.equals(uri.getHost())
                && ChessPublicationRoutes.PAGES_PATH.equals(uri.getPath());
        boolean loopback = "http".equals(uri.getScheme()) && List.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
        return (pages || loopback) && "ai".equals(uri.getFragment());
    }

    private static boolean publishCheckpoint(Phase phase, Path evidence, String prefix, JsonNode checkpoint, int slot,
                                             JsonNode matchBoard, String language, Position historical, Position live, List<String> replay, double dpr) {
        String id = checkpoint.path("id").asText();
        var geometry = new ArrayList<>(List.of(new ReportField("Game checkpoint", checkpoint.path("checkpoint").asText()),
                new ReportField("Displayed position", checkpoint.path("fen").asText()),
                new ReportField("Board geometry", checkpoint.path("board").toString())));
        if (!"preview".equals(id)) {
            geometry.add(new ReportField("Evaluation bar geometry", checkpoint.path("meter").toString()));
            geometry.add(new ReportField("Bulb touch target", checkpoint.path("bulb").toString()));
        }
        if ("history".equals(id)) geometry.add(new ReportField("History arrow geometry", checkpoint.path("arrows").toString()));
        report(phase, "Observed " + id, geometry);
        boolean passed = assertions(phase, "Verify " + id, validateCheckpoint(checkpoint, slot, matchBoard, language, historical, live, replay));
        try {
            var capture = ChessWebStudyPublication.capture(evidence, prefix + "-" + id, checkpoint.path("captureSha256").asText(),
                    PROFILE.width(), PROFILE.height(), dpr);
            attach(phase, "Browser MCP · " + id, capture);
        } catch (IOException invalid) {
            passed &= assertions(phase, "Validate " + id + " capture", List.of(
                    same("Original compact capture associated with this checkpoint", "Readable, exact SHA-256", invalid.getMessage())));
        }
        return passed;
    }

    static List<AtfAssertion> validateCheckpoint(JsonNode checkpoint, int slot, JsonNode matchBoard, String language, Position historical,
                                                 Position live, List<String> replay) {
        String id = checkpoint.path("id").asText();
        boolean resumed = "resumed".equals(id);
        var assertions = new ArrayList<>(List.of(
                same("Checkpoint belongs to a valid active slot", true, slot >= 1 && slot <= 10),
                same("The active slot and saved moves stay the same", List.of(slot, "ai|" + FIXTURE),
                        List.of(checkpoint.path("slot").asInt(-1), checkpoint.path("checkpoint").asText())),
                same("The exact cursor and historical or live position are displayed",
                        List.of(resumed ? 2 : 1, resumed ? LIVE_FEN : HISTORICAL_FEN),
                        List.of(checkpoint.path("cursor").asInt(-1), checkpoint.path("fen").asText())),
                same("The board remains square and visible in the compact viewport", true,
                        ChessWebStudyPublication.boardFits(checkpoint.path("board"), PROFILE))));
        if ("preview".equals(id)) {
            assertions.add(same("Replay advances twice and can return one step", JSON.valueToTree(List.of(0, 1, 2, 1)),
                    checkpoint.path("previewSteps")));
            assertions.add(same("Replay uses the computed variation and the exact reply", JSON.valueToTree(replay),
                    checkpoint.path("previewMoves")));
            var frames = checkpoint.path("previewBoards");
            boolean fixed = frames.isArray() && frames.size() == 4;
            for (var frame : frames) fixed &= sameRect(checkpoint.path("board"), frame);
            assertions.add(same("The replay board does not stretch or shift between frames", true, fixed));
        } else {
            Position expected = resumed ? live : historical;
            assertions.add(same("Selection, return and reopen keep the match board in place",
                    true, sameRect(matchBoard, checkpoint.path("board"))));
            if ("history".equals(id)) {
                var arrows = checkpoint.path("arrows");
                assertions.add(same("Live, forward and backward keep the same board rectangle", true,
                        List.of("live", "forward", "backward").stream().allMatch(step -> sameRect(matchBoard, arrows.path(step)))));
            }
            assertions.add(same("The evaluation belongs to the visible position",
                    List.of(expected.meter(), expected.score(language)),
                    List.of(checkpoint.path("meterValue").asInt(-1), checkpoint.path("score").asText())));
            assertions.add(same("The evaluation bar is visible along the full left edge of the board",
                    true, leftBar(checkpoint.path("board"), checkpoint.path("meter"))));
            assertions.add(same("The compact board and its 8px meter with a 2px gap fill the viewport width",
                    true, fillsCompactWidth(checkpoint.path("board"), checkpoint.path("meter"))));
            assertions.add(same("The bulb's 48px touch target stays fully visible",
                    true, bulbFits(checkpoint.path("bulb"))));
            assertions.add(same("The bulb is pressed only while a hint is shown",
                    true, checkpoint.path("bulbPressed").isBoolean() && checkpoint.path("bulbPressed").asBoolean() != resumed));
            assertions.add(same(resumed ? "Reopen hides the previously revealed hint" : "The bulb marks the expected move",
                    resumed ? "" : expected.recommendation(), checkpoint.path("hint").asText("missing")));
            if (resumed) {
                assertions.add(same("The bulb recomputes the hint for the live position",
                        live.recommendation(), checkpoint.path("recomputedHint").asText()));
            }
        }
        return assertions;
    }

    private static boolean leftBar(JsonNode board, JsonNode meter) {
        double x = meter.path("x").asDouble(Double.NaN);
        double width = meter.path("width").asDouble(Double.NaN);
        return x >= 0 && width > 2 && x + width <= board.path("x").asDouble(Double.NaN) + 0.5
                && Math.abs(meter.path("y").asDouble(Double.NaN) - board.path("y").asDouble(Double.NaN)) <= 0.5
                && Math.abs(meter.path("height").asDouble(Double.NaN) - board.path("height").asDouble(Double.NaN)) <= 0.5;
    }

    private static boolean fillsCompactWidth(JsonNode board, JsonNode meter) {
        double boardLeft = board.path("x").asDouble(Double.NaN);
        double boardRight = boardLeft + board.path("width").asDouble(Double.NaN);
        return Math.abs(meter.path("x").asDouble(Double.NaN)) <= 0.5
                && Math.abs(meter.path("width").asDouble(Double.NaN) - 8) <= 0.5
                && Math.abs(boardLeft - 10) <= 0.5
                && Math.abs(boardRight - PROFILE.width()) <= 0.5;
    }

    private static boolean bulbFits(JsonNode bulb) {
        double x = bulb.path("x").asDouble(Double.NaN);
        double y = bulb.path("y").asDouble(Double.NaN);
        double width = bulb.path("width").asDouble(Double.NaN);
        double height = bulb.path("height").asDouble(Double.NaN);
        return x >= 0 && y >= 0 && width >= 48 && height >= 48
                && x + width <= PROFILE.width() + 0.5 && y + height <= PROFILE.height() + 0.5;
    }

    private static boolean sameRect(JsonNode left, JsonNode right) {
        return List.of("x", "y", "width", "height").stream().allMatch(component ->
                Math.abs(left.path(component).asDouble(Double.NaN) - right.path(component).asDouble(Double.NaN)) <= 0.5);
    }
}
