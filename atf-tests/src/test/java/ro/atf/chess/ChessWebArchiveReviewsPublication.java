package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.nio.charset.StandardCharsets;
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
class ChessWebArchiveReviewsPublication {
    private static final String SCOPE = "archive-reviews";
    private static final String PLAY_URL = "https://crististefanita.github.io/atf_j7/#ai";
    private static final String LOCAL = "1|f2f3 e7e5 g2g4 d8h4";
    private static final String AI = "3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4";
    private static final List<String> OBSERVATIONS = List.of("two-archives", "analysis-a", "archive-a-bookmark", "analysis-b",
            "archive-b-bookmark", "archive-b-reloaded", "archive-a-reopened", "independent-bookmarks");
    private static final Scenario REVIEWS = new Scenario("ChessWebArchiveReviewsPublication",
            "Restore two analyses and their independent archived bookmarks", SCOPE, "web-archive-reviews.html",
            "Verify analysis, browser reload and independent review navigation", List.of(
                    new Frame("archive-a-why.png", "The first game shows its played mating reply and explanation"),
                    new Frame("archive-b-history.png", "The second game selects its first recorded move"),
                    new Frame("archive-b-reloaded.png", "Reload restores the second game's selected move"),
                    new Frame("archive-a-reopened.png", "The first game still restores the mating reply")));

    @Test void publishSeparateArchivedReviews() throws Exception {
        boolean publicRun = Boolean.getBoolean("chess.web.archive.reviews.public");
        WebEvidencePublication.publish(configuredPath("chess.web.archive.reviews.receipt"), configuredPath("chess.web.bundle"),
                REVIEWS, receipt -> inspect(receipt, publicRun));
    }

    private static Contract inspect(JsonNode receipt, boolean publicRun) {
        String first = receipt.path("archiveAId").asText();
        String second = receipt.path("archiveBId").asText();
        String firstCheckpoint = receipt.path("archiveACheckpoint").asText();
        String secondCheckpoint = receipt.path("archiveBCheckpoint").asText();
        String encoded = receipt.path("ledgerSnapshot").asText();
        String publicUrl = receipt.path("publicUrl").asText("");
        var checks = new ArrayList<>(List.of(same("Evidence belongs to the two archived reviews", SCOPE, receipt.path("scope").asText()),
                same("Browser uses the expected deployment channel", publicRun ? PLAY_URL : "", publicUrl),
                same("The archived games have two separate identities", true, !first.isBlank() && !second.isBlank() && !first.equals(second)),
                same("The local archive has the exact first checkpoint", LOCAL, firstCheckpoint),
                same("The AI archive has its own exact checkpoint", AI, secondCheckpoint)));
        verifySavedLedger(encoded, first, second, checks);
        var fields = List.of(new ReportField("Local archive ID", first), new ReportField("AI archive ID", second),
                new ReportField("Local checkpoint", firstCheckpoint), new ReportField("AI checkpoint", secondCheckpoint),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl),
                new ReportField("Final browser ledger SHA-256", WebEvidencePublication.sha256(encoded.getBytes(StandardCharsets.UTF_8))));
        var observations = new LinkedHashMap<String, JsonNode>();
        for (String id : OBSERVATIONS) observations.put(id, BooleanNode.TRUE);
        return new Contract(fields, checks, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static void verifySavedLedger(String encoded, String first, String second, List<AtfAssertion> checks) {
        try {
            var store = new MatchStore(new MatchLedgerPreferences(encoded), Clock.systemUTC());
            checks.add(same("The durable ledger has exactly both finished games", List.of(second, first),
                    store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList()));
            checks.add(same("The two completed games are absent from Saved", List.of(), store.savedMatchSlots()));
            verifyArchive(store, new ExpectedArchive(first, LOCAL, new Snapshot(3, Branch.PLAYED, 2, true), "d8h4"), checks);
            verifyArchive(store, new ExpectedArchive(second, AI, new Snapshot(1, Branch.HISTORY, 0, false), "f2f4"), checks);
        } catch (IllegalArgumentException invalid) {
            checks.add(same("The browser persisted a valid versioned match ledger", true, invalid.getMessage()));
        }
    }

    private static void verifyArchive(MatchStore store, ExpectedArchive expected, List<AtfAssertion> checks) {
        String label = expected.checkpoint().equals(LOCAL) ? "Local" : "AI";
        var archived = store.readArchivedMatch(expected.id());
        checks.add(same(label + " archive persists the agreed checkpoint and a generated review", List.of(expected.checkpoint(), true),
                archived == null ? List.of() : List.of(archived.match().checkpoint(), archived.reviewed())));
        if (archived == null || !expected.checkpoint().equals(archived.match().checkpoint())) return;
        var match = archived.match();
        var review = store.readReview(expected.id(), match);
        var expectedReview = archived.manualUsed() ? GameReview.analyzeLine(match, ignored -> { })
                : GameReview.analyze(match, ignored -> { });
        checks.add(same(label + " persisted analysis matches all turns from the same JVM checkpoint", true,
                expectedReview.equals(review)));
        var bookmark = store.readReviewBookmark(expected.id(), match);
        checks.add(same(label + " bookmark belongs to the exact archived game and review branch", expected.bookmark(), bookmark));
        if (review == null || bookmark == null) return;
        var resumed = new GameReviewSession(match, review, bookmark);
        checks.add(same(label + " review restores the expected visible move and cursor", List.of(expected.visibleMove(), expected.bookmark()),
                List.of(resumed.lastMove() == null ? "missing" : resumed.lastMove().uci(), resumed.snapshot())));
    }

    private record ExpectedArchive(String id, String checkpoint, Snapshot bookmark, String visibleMove) { }
}
