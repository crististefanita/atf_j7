package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
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
class ChessWebReviewGraphPublication {
    private static final String SCOPE = "review-graph";
    private static final String PLAY_URL = "https://crististefanita.github.io/atf_j7/#ai";
    private static final int TOTAL = 185;
    private static final int TARGET = 184;
    private static final int INSET = 2;
    private static final List<Visit> VISITS = List.of(new Visit("left-edge", 0, 390, 844), new Visit("middle-point", 93, 390, 844),
            new Visit("native-target", TARGET, 390, 844), new Visit("right-edge", TOTAL, 390, 844),
            new Visit("wide-target", TARGET, 1024, 768), new Visit("reload-cursor", TARGET, 390, 844));
    private static final Scenario GRAPH = new Scenario("ChessWebReviewGraphPublication",
            "Tap the evaluation graph and restore the exact recorded move", SCOPE, "web-review-graph.html",
            "Verify actual touch, selected rows, unchanged board geometry and reload", List.of(
                    new Frame("graph-left-edge.png", "Left edge selects the initial board and its row"),
                    new Frame("graph-native-target.png", "Compact display selects native reference ply 184"),
                    new Frame("graph-right-edge.png", "Right edge selects the final mate and its row"),
                    new Frame("graph-wide-target.png", "Wide display selects the same reference move", 1024, 768),
                    new Frame("graph-reload.png", "Reload restores the exact selected move")));

    private record Visit(String id, int ply, int viewportWidth, int viewportHeight) { }

    @Test void publishExactGraphSelection() throws Exception {
        Path fixturePath = configuredPath("chess.web.graph.fixture");
        boolean publicRun = Boolean.getBoolean("chess.web.graph.public");
        WebEvidencePublication.publish(configuredPath("chess.web.graph.receipt"), configuredPath("chess.web.bundle"),
                GRAPH, receipt -> inspect(receipt, fixturePath, publicRun));
    }

    private static Contract inspect(JsonNode receipt, Path path, boolean publicRun) throws IOException {
        byte[] fixtureBytes = Files.readAllBytes(path);
        var fixture = WebEvidencePublication.readObject(fixtureBytes, "Native review fixture");
        var source = ChessMatch.restore(fixture.path("checkpoint").asText());
        String encoded = fixture.path("review").asText();
        var review = GameReviewCodec.decode(source, encoded);
        var fixtureHash = WebEvidencePublication.sha256(fixtureBytes);
        String archiveId = receipt.path("archiveId").asText();
        String publicUrl = receipt.path("publicUrl").asText("");
        var checks = new ArrayList<>(List.of(same("Evidence belongs to Game Review graph", SCOPE, receipt.path("scope").asText()),
                same("Browser uses the expected deployment channel", publicRun ? PLAY_URL : "", publicUrl),
                same("The fixture is the exact native review used in the browser", fixtureHash, receipt.path("fixtureSha256").asText()),
                same("The source is the completed native 185-ply game", List.of(SCOPE, TOTAL, TOTAL, ChessMatch.Result.WHITE_WINS),
                        List.of(fixture.path("scope").asText(), fixture.path("expected").path("totalPlies").asInt(),
                                source.ply(), source.result())),
                same("The cached curve singles out the native reference move", List.of(500, List.of(TARGET)),
                        List.of(review.initialWhitePoints(), review.turns().stream().filter(turn -> turn.whitePoints() != 500)
                                .map(GameReview.Turn::ply).toList())),
                same("The reference move has the visible White advantage", 750, review.turns().get(TARGET - 1).whitePoints())));
        verifyVisits(receipt, fixture, source, checks);
        verifyReload(restoredLedger(receipt), source, encoded, archiveId, checks);
        var fields = List.of(new ReportField("Native review fixture SHA-256", fixtureHash), new ReportField("Archived game ID", archiveId),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl),
                new ReportField("Graph profiles", "390×844 and 1024×768 CSS pixels"));
        var observations = new LinkedHashMap<String, JsonNode>();
        observations.put("graph-long-fixture", BooleanNode.TRUE);
        VISITS.forEach(visit -> observations.put(visit.id(), BooleanNode.TRUE));
        observations.put("board-anchored", BooleanNode.TRUE);
        return new Contract(fields, checks, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static void verifyVisits(JsonNode receipt, JsonNode fixture, ChessMatch source, List<AtfAssertion> checks) {
        var samples = new LinkedHashMap<Integer, JsonNode>();
        var sampleRows = fixture.path("expected").path("samples");
        sampleRows.forEach(sample -> samples.put(sample.path("ply").asInt(), sample));
        checks.add(same("Independent native position witnesses occur exactly once", List.of(List.of(0, 93, TARGET, TOTAL), 4),
                List.of(List.copyOf(samples.keySet()), sampleRows.size())));
        var observed = new LinkedHashMap<String, JsonNode>();
        var entries = receipt.path("tapEvidence");
        entries.forEach(sample -> observed.put(sample.path("id").asText(), sample));
        checks.add(same("The compact taps, wide tap and reload each appear once",
                VISITS.stream().map(Visit::id).toList(), List.copyOf(observed.keySet())));
        checks.add(same("No touch observation is duplicated", VISITS.size(), entries.size()));
        for (var visit : VISITS) {
            JsonNode evidence = observed.get(visit.id());
            JsonNode sample = samples.get(visit.ply());
            if (evidence == null || sample == null) continue;
            var baseline = receipt.path(visit.viewportWidth() == 390 ? "compactBaselineBoard" : "wideBaselineBoard");
            verifyVisit(visit, source, sample, evidence, baseline, checks);
        }
        var compact = observed.get("native-target");
        var wide = observed.get("wide-target");
        var middle = observed.get("middle-point");
        checks.add(same("The wide and compact graphs actually use distinct plot widths", true, compact != null && wide != null
                && Math.abs(number(compact, "width") - number(wide, "width")) > 1));
        checks.add(same("The compact reference tap exposes the full-width rounding error", 183L, compact == null ? -1L
                : Math.round(TOTAL * number(compact, "touchX") / number(compact, "width"))));
        checks.add(same("The reference marker rises above the baseline score", true, compact != null && middle != null
                && number(compact, "markerY") < number(middle, "markerY") - 1));
    }

    private static void verifyVisit(Visit visit, ChessMatch source, JsonNode sample, JsonNode tap, JsonNode baseline,
                                    List<AtfAssertion> checks) {
        String id = visit.id() + " · ";
        var fen = sample.path("fen").asText();
        var expectedPosition = ChessPosition.fromFen(fen);
        var move = source.moveAt(visit.ply());
        String uci = move == null ? "" : move.uci();
        checks.add(same(id + "native replay agrees with the independent position and move", List.of(true, sample.path("lastMove").asText()),
                List.of(source.positionAt(visit.ply()).sameForRepetition(expectedPosition), uci)));
        checks.add(same(id + "browser board, side and last move match the exact recorded position",
                List.of(fen.split(" ", 2)[0], expectedPosition.turn().name(), uci),
                List.of(tap.path("fen").asText(), tap.path("turn").asText(), tap.path("lastMove").asText())));
        checks.add(same(id + "graph, history cursor and visible unique row select the same ply",
                List.of(visit.ply(), visit.ply(), visit.ply(), 1, true), List.of(tap.path("ply").asInt(-1),
                        tap.path("plottedPly").asInt(-1), tap.path("rowPly").asInt(-1), tap.path("rowCount").asInt(),
                        tap.path("rowVisible").asBoolean())));
        checks.add(same(id + "viewport, plot count and inset",
                List.of(visit.viewportWidth(), visit.viewportHeight(), TOTAL, INSET), List.of(tap.path("viewport").path(0).asInt(),
                        tap.path("viewport").path(1).asInt(), tap.path("totalPlies").asInt(), tap.path("inset").asInt())));
        checks.add(same(id + "actual workspace layout", visit.viewportWidth() == 390 ? "stacked" : "split", tap.path("layout").asText()));
        double width = number(tap, "width");
        double marker = INSET + (width - 2 * INSET) * visit.ply() / TOTAL;
        checks.add(same(id + "marker is centered at the expected coordinate", true, width > 2 * INSET
                && close(number(tap, "markerX"), marker)));
        verifyTouch(visit, tap, width, marker, checks);
        checks.add(same(id + "square board stays fully visible at its original viewport location", true,
                sameBoard(baseline, tap.path("board")) && withinViewport(tap.path("board"), visit)));
    }

    private static void verifyTouch(Visit visit, JsonNode tap, double width, double marker, List<AtfAssertion> checks) {
        if ("reload-cursor".equals(visit.id())) {
            checks.add(same("Reload restores without injecting a replacement graph tap", true, tap.path("touchX").isNull()));
            return;
        }
        double x = number(tap, "touchX");
        double expected = switch (visit.id()) {
            case "left-edge" -> 1;
            case "right-edge" -> width - 1;
            default -> marker;
        };
        checks.add(same(visit.id() + " · actual touch targets the intended graph coordinate", true, close(x, expected)));
        checks.add(same(visit.id() + " · shared projection maps the raw touch to the exact ply", visit.ply(),
                GameReviewGraphLayout.plyAtX(x, TOTAL, width, INSET)));
    }

    private static MatchStore restoredLedger(JsonNode receipt) throws IOException {
        try {
            return new MatchStore(new MatchLedgerPreferences(receipt.path("ledgerSnapshot").asText()), Clock.systemUTC());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Browser did not preserve a decodable match ledger", invalid);
        }
    }

    private static void verifyReload(MatchStore store, ChessMatch source, String encoded, String archiveId, List<AtfAssertion> checks) {
        var archived = store.readArchivedMatch(archiveId);
        checks.add(same("The browser ledger still has the exact finished source", source.checkpoint(),
                archived == null ? "missing" : archived.match().checkpoint()));
        var record = store.readReviewRecord(archiveId);
        checks.add(same("The browser did not overwrite the imported native analysis", true, record != null && encoded.equals(record.review())));
        checks.add(same("The archived bookmark independently restores exact recorded ply 184", new Snapshot(TARGET, Branch.HISTORY, 0, false),
                store.readReviewBookmark(archiveId, source)));
    }

    private static double number(JsonNode node, String key) { return node.path(key).asDouble(Double.NaN); }
    private static boolean close(double actual, double expected) { return Math.abs(actual - expected) < 1; }

    private static boolean sameBoard(JsonNode first, JsonNode second) {
        if (first.size() != 4 || second.size() != 4 || second.path(2).asDouble() <= 0
                || !close(second.path(2).asDouble(), second.path(3).asDouble())) return false;
        for (int index = 0; index < 4; index++) {
            if (!close(first.path(index).asDouble(Double.NaN), second.path(index).asDouble(Double.NaN))) return false;
        }
        return true;
    }

    private static boolean withinViewport(JsonNode board, Visit visit) {
        double x = board.path(0).asDouble(Double.NaN), y = board.path(1).asDouble(Double.NaN);
        return x >= -.5 && y >= -.5 && x + board.path(2).asDouble(Double.NaN) <= visit.viewportWidth() + .5
                && y + board.path(3).asDouble(Double.NaN) <= visit.viewportHeight() + .5;
    }
}
