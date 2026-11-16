package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebReviewFocusPublication {
    private static final String SCOPE = "review-focus";
    private static final String PLAY_URL = "https://crististefanita.github.io/atf_j7/#ai";
    private static final String CHECKPOINT = "3|WHITE|ADVANCED|0|f2f3 e7e5 g2g4 d8h4";
    private static final Scenario FOCUS = new Scenario("ChessWebReviewFocusPublication",
            "Use the Game Review keyboard at 130% text", SCOPE, "web-review-focus.html",
            "Verify real Edge focus, recorded moves, enlarged text, and returning from Why", List.of(
                    new Frame("focus-graph.png", "Arrow keys move the graph cursor while focus stays on the slider"),
                    new Frame("focus-history.png", "Tab enters the visible selected historical move"),
                    new Frame("focus-why.png", "The recommended branch is reachable in Why"),
                    new Frame("focus-return.png", "Closing Why restores the launcher and the historical position")));
    private static final List<String> POSITION = List.of(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "rnbqkbnr/pppppppp/8/8/8/5P2/PPPPP1PP/RNBQKBNR",
            "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR", "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR",
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR");
    private static final List<String> MOVES = List.of("", "f2f3", "e7e5", "g2g4", "d8h4");
    private static final List<Visit> VISITS = List.of(
            new Visit("graph-left", "seek", 2), new Visit("graph-right", "seek", 3),
            row("selected-row", 3), row("row-up", 2), row("row-end", 4), row("row-home", 0), row("row-down", 1),
            row("row-end-again", 4), row("row-return", 3),
            new Visit("why-entry", "close-why", 3, Branch.HISTORY, true),
            new Visit("why-recommended", "recommended", 3, Branch.RECOMMENDED, true),
            new Visit("why-escape", "why", 3), new Visit("why-reopen", "close-why", 3, Branch.HISTORY, true),
            new Visit("why-back", "why", 3));

    private record Visit(String id, String focus, int ply, Branch branch, boolean why) {
        Visit(String id, String focus, int ply) { this(id, focus, ply, Branch.HISTORY, false); }
        int boardPly() { return branch == Branch.HISTORY ? ply : ply - 1; }
        String lastMove() { return branch == Branch.HISTORY ? MOVES.get(ply) : ""; }
    }

    private static Visit row(String id, int ply) { return new Visit(id, "row-" + ply, ply); }

    @Test void publishKeyboardFocusAndHistoricalCursor() throws Exception {
        Path fixture = configuredPath("chess.web.focus.fixture");
        boolean publicRun = Boolean.getBoolean("chess.web.focus.public");
        WebEvidencePublication.publish(configuredPath("chess.web.focus.receipt"), configuredPath("chess.web.bundle"),
                FOCUS, receipt -> inspect(receipt, fixture, publicRun));
    }

    private static Contract inspect(JsonNode receipt, Path fixturePath, boolean publicRun) throws IOException {
        byte[] fixtureBytes = Files.readAllBytes(fixturePath);
        JsonNode fixture = WebEvidencePublication.readObject(fixtureBytes, "Archived Fool's Mate review fixture");
        String fixtureHash = WebEvidencePublication.sha256(fixtureBytes);
        String publicUrl = receipt.path("publicUrl").asText("");
        String archiveId = receipt.path("archiveId").asText();
        var source = ChessMatch.restore(CHECKPOINT);
        String encoded = fixture.path("review").asText();
        var review = GameReviewCodec.decode(source, encoded);
        var checks = new ArrayList<>(List.of(
                same("Focus evidence matches the native scenario and exact archived fixture",
                        List.of(SCOPE, SCOPE, CHECKPOINT, fixtureHash), List.of(receipt.path("scope").asText(),
                                fixture.path("scope").asText(), fixture.path("checkpoint").asText(), receipt.path("fixtureSha256").asText())),
                same("Browser uses the expected deployment channel", publicRun ? PLAY_URL : "", publicUrl),
                same("Native game ends at the fourth recorded ply", 4, review.turns().size()),
                same("Enlargement changes computed fonts for the page, Why and history rows by 30%", true, enlarged(receipt)),
                same("The browser consumes the cached review without new engine requests", IntNode.valueOf(0), receipt.path("analysisRequests"))));
        verifyOrder(receipt, source, review, checks);
        verifyVisits(receipt.path("focusEvidence"), checks);
        verifyLedger(receipt, archiveId, source, encoded, checks);
        var fields = List.of(new ReportField("Review fixture SHA-256", fixtureHash), new ReportField("Archived game ID", archiveId),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl),
                new ReportField("Font sizes before and after", receipt.path("baseFonts") + " → " + receipt.path("scaledFonts")),
                new ReportField("Keyboard actions", "Tab, Shift+Tab, arrows, Home, End, Enter and Escape"));
        var observations = new LinkedHashMap<String, JsonNode>();
        for (String id : List.of("font-enlarged", "review-tab-order", "graph-retains-focus", "selected-row-only-tab-stop",
                "history-navigation-keys", "why-entry-order", "escape-restores-launcher", "back-restores-launcher", "square-board-stable")) {
            observations.put(id, BooleanNode.TRUE);
        }
        observations.put("cached-review-used", IntNode.valueOf(0));
        return new Contract(fields, checks, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static void verifyOrder(JsonNode receipt, ChessMatch source, GameReview review, List<AtfAssertion> checks) {
        var timeline = new GameReviewTimeline(source, review);
        timeline.seek(3);
        var normal = new ArrayList<>(List.of("archive-review-back", "board", "previous", "best-line", "next"));
        if (timeline.nextKeyMomentPly().isPresent()) normal.add("next-key");
        normal.addAll(List.of("why", "review-actions", "seek"));
        checks.add(same("Tab follows the board, enabled Review controls and graph slider", normal, strings(receipt.path("normalOrder"))));
        checks.add(same("Shift+Tab returns from the selected row to the original Why button",
                List.of("row-3", "seek", "review-actions", "why"), strings(receipt.path("reverseOrder"))));
        checks.add(same("Why starts at Back and Tab reaches both variations without entering hidden history",
                List.of("close-why", "why-actions", "board", "played", "recommended"), strings(receipt.path("whyOrder"))));
    }

    private static void verifyVisits(JsonNode entries, List<AtfAssertion> checks) {
        var reports = new LinkedHashMap<String, JsonNode>();
        entries.forEach(entry -> reports.put(entry.path("id").asText(), entry));
        checks.add(same("Each keyboard transition occurs exactly once and in order",
                List.of(VISITS.stream().map(Visit::id).toList(), VISITS.size()), List.of(List.copyOf(reports.keySet()), entries.size())));
        JsonNode baseline = entries.path(0).path("board");
        for (Visit visit : VISITS) {
            JsonNode entry = reports.get(visit.id());
            if (entry == null) continue;
            String label = visit.id() + " · ";
            checks.add(same(label + "focus is expected, visible and marked for keyboard input", List.of(visit.focus(), true, true),
                    List.of(entry.path("focus").asText(), isTrue(entry, "focusVisible"), isTrue(entry, "keyboardFocus"))));
            checks.add(same(label + "the board and the selected recorded move are exact",
                    List.of(visit.ply(), visit.branch().name(), POSITION.get(visit.boardPly()), visit.lastMove(), turn(visit.boardPly())),
                    List.of(entry.path("ply").asInt(-1), entry.path("branch").asText(), entry.path("fen").asText(),
                            entry.path("lastMove").asText(), entry.path("turn").asText())));
            checks.add(same(label + "the active screen exposes only its own controls",
                    List.of(visit.why(), visit.why(), !visit.why()), List.of(isTrue(entry, "why"), isTrue(entry, "whyExpanded"),
                            isTrue(entry, "seekVisible"))));
            checks.add(same(label + "the square board is fully inside the compact viewport", true, boardVisible(entry)));
            if (visit.why()) {
                checks.add(same(label + "the hidden history has no tab stop", true,
                        entry.path("rowTabStops").isArray() && entry.path("rowTabStops").isEmpty() && !isTrue(entry, "rowVisible")));
            } else {
                checks.add(same(label + "graph and sole history tab stop stay on the same ply",
                        List.of(visit.ply(), visit.ply(), visit.ply(), 1, List.of(visit.ply())),
                        List.of(entry.path("plotPly").asInt(-1), entry.path("seekValue").asInt(-1), entry.path("rowPly").asInt(-1),
                                entry.path("rowCount").asInt(), integers(entry.path("rowTabStops")))));
                checks.add(same(label + "the archived board does not shift", true, sameBoard(baseline, entry.path("board"))));
                if (visit.focus().startsWith("row-")) {
                    checks.add(same(label + "the focused move is visible inside the history pane", true, isTrue(entry, "rowVisible")));
                } else if ("seek".equals(visit.focus())) {
                    checks.add(same(label + "the graph slider announces the current move", true,
                            entry.path("seekLabel").asText().matches(".*\\b" + visit.ply() + "\\b.*")));
                }
            }
        }
    }

    private static void verifyLedger(JsonNode receipt, String id, ChessMatch source, String encoded, List<AtfAssertion> checks) {
        var store = new MatchStore(new MatchLedgerPreferences(receipt.path("ledgerSnapshot").asText()), Clock.systemUTC());
        var archived = store.readArchivedMatch(id);
        var record = store.readReviewRecord(id);
        checks.add(same("The exact archived game and cached analysis remain unchanged", true,
                archived != null && source.checkpoint().equals(archived.match().checkpoint()) && record != null && encoded.equals(record.review())));
        checks.add(same("The persistent bookmark returns to recorded ply 3 with Why closed", new Snapshot(3, Branch.HISTORY, 0, false),
                store.readReviewBookmark(id, source)));
    }

    private static String turn(int ply) { return ply % 2 == 0 ? "WHITE" : "BLACK"; }
    private static boolean isTrue(JsonNode node, String key) { return BooleanNode.TRUE.equals(node.path(key)); }

    private static List<String> strings(JsonNode array) {
        var items = new ArrayList<String>();
        array.forEach(value -> items.add(value.asText()));
        return List.copyOf(items);
    }

    private static List<Integer> integers(JsonNode array) {
        var items = new ArrayList<Integer>();
        array.forEach(value -> items.add(value.isIntegralNumber() ? value.asInt() : -1));
        return List.copyOf(items);
    }

    private static boolean enlarged(JsonNode receipt) {
        var baseline = receipt.path("baseFonts");
        var scaled = receipt.path("scaledFonts");
        if (!baseline.isArray() || !scaled.isArray() || baseline.size() != 3 || scaled.size() != 3) return false;
        for (int index = 0; index < 3; index++) {
            double original = baseline.path(index).asDouble(Double.NaN);
            if (!(original >= 12 && Math.abs(scaled.path(index).asDouble(Double.NaN) - original * 1.3) < .1)) return false;
        }
        return true;
    }

    private static boolean boardVisible(JsonNode entry) {
        JsonNode board = entry.path("board");
        double x = board.path(0).asDouble(Double.NaN), y = board.path(1).asDouble(Double.NaN);
        double width = board.path(2).asDouble(Double.NaN), height = board.path(3).asDouble(Double.NaN);
        return isTrue(entry, "boardVisible") && integers(entry.path("viewport")).equals(List.of(390, 844)) && board.size() == 4
                && x >= -.5 && y >= -.5 && width >= 300 && x + width <= 390.5 && y + height <= 844.5 && Math.abs(width - height) < 1;
    }

    private static boolean sameBoard(JsonNode first, JsonNode second) {
        if (first.size() != 4 || second.size() != 4) return false;
        for (int index = 0; index < 4; index++) {
            if (!(Math.abs(first.path(index).asDouble(Double.NaN) - second.path(index).asDouble(Double.NaN)) < 1)) return false;
        }
        return true;
    }
}
