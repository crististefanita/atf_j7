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
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebArchiveContinuationPublication {
    private static final String SCOPE = "review-continue";
    private static final String SOURCE = "3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4";
    private static final int SELECTED_PLY = 1;
    private static final Snapshot CURSOR = new Snapshot(SELECTED_PLY, Branch.HISTORY, 0, false);
    private static final List<String> OBSERVATIONS = List.of("actual-history", "variant-unavailable", "confirmation",
            "cancel-preserves-ledger", "forked-exact-position", "manual-branch", "fork-reloaded", "origin-review-preserved");
    private static final Scenario CONTINUATION = new Scenario("ChessWebArchiveContinuationPublication",
            "Continue from an archived Game Review without changing the archive", SCOPE, "web-review-continuation.html",
            "Verify recorded history, confirmation, continuation and reload", List.of(
                    new Frame("review-selected-history.png", "An earlier recorded move is selected in Game Review"),
                    new Frame("review-continue-confirmation.png", "Confirmation selects manual play for the fork"),
                    new Frame("review-continued-game.png", "The separate game starts at the selected historical position"),
                    new Frame("review-origin-restored.png", "The original archived analysis and cursor are still available")));

    @Test void publishArchivedReviewContinuation() throws Exception {
        boolean publicRun = Boolean.getBoolean("chess.web.archive.continuation.public");
        WebEvidencePublication.publish(configuredPath("chess.web.archive.continuation.receipt"), configuredPath("chess.web.bundle"),
                CONTINUATION, receipt -> inspect(receipt, publicRun));
    }

    private static Contract inspect(JsonNode receipt, boolean publicRun) {
        var source = ChessMatch.restore(SOURCE);
        var start = source.continueFrom(SELECTED_PLY);
        var played = start.play(Move.fromUci("d7d5"));
        String origin = receipt.path("originArchiveId").asText();
        String fork = receipt.path("forkId").asText();
        String before = receipt.path("ledgerBefore").asText();
        String after = receipt.path("ledgerAfter").asText();
        String publicUrl = receipt.path("publicUrl").asText("");
        var checks = new ArrayList<>(List.of(same("Evidence belongs to archived Game Review continuation", SCOPE, receipt.path("scope").asText()),
                same("Browser uses the expected deployment channel", publicRun ? ChessPublicationRoutes.HOME_URL : "", publicUrl),
                same("The finished original has the exact archived checkpoint", SOURCE, receipt.path("archiveCheckpoint").asText()),
                same("The user selects the exact recorded move", SELECTED_PLY, receipt.path("selectedPly").asInt()),
                same("The fork starts at the JVM history checkpoint", start.checkpoint(), receipt.path("forkStartCheckpoint").asText()),
                same("The persisted fork contains the expected manual move", played.checkpoint(), receipt.path("forkCheckpoint").asText()),
                same("The fork has a new identity distinct from the finished source", true,
                        !origin.isBlank() && !fork.isBlank() && !origin.equals(fork))));
        verifyLedgers(before, after, origin, fork, played, checks);

        var fields = List.of(new ReportField("Original archive ID", origin), new ReportField("New saved game ID", fork),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl),
                new ReportField("Pre-fork ledger SHA-256", digest(before)), new ReportField("Reloaded ledger SHA-256", digest(after)));
        var observations = new LinkedHashMap<String, JsonNode>();
        for (String id : OBSERVATIONS) observations.put(id, BooleanNode.TRUE);
        return new Contract(fields, checks, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static void verifyLedgers(String before, String after, String origin, String fork, ChessMatch expected,
                                      List<AtfAssertion> checks) {
        try {
            var prior = new MatchStore(new MatchLedgerPreferences(before), Clock.systemUTC());
            var loaded = new MatchStore(new MatchLedgerPreferences(after), Clock.systemUTC());
            var beforeArchive = prior.readArchivedMatch(origin);
            var afterArchive = loaded.readArchivedMatch(origin);
            var beforeReview = prior.readReviewRecord(origin);
            var afterReview = loaded.readReviewRecord(origin);
            checks.add(same("Original result and archive metadata stay identical", result(beforeArchive), result(afterArchive)));
            checks.add(same("Original archived result remains the expected outcome and position",
                    List.of(SOURCE, ChessMatch.Result.BLACK_WINS, true, false, true), result(afterArchive).stream().limit(5).toList()));
            checks.add(same("The original cached analysis and bookmark exist before and after the fork", true,
                    beforeReview != null && beforeReview.review() != null && beforeReview.bookmark() != null && afterReview != null));
            checks.add(same("The original encoded analysis and bookmark remain byte-identical", beforeReview, afterReview));
            if (afterArchive != null) {
                var review = loaded.readReview(origin, afterArchive.match());
                var cursor = loaded.readReviewBookmark(origin, afterArchive.match());
                checks.add(same("The original analysis still decodes and restores the exact history bookmark", List.of(true, CURSOR),
                        List.of(review != null, cursor == null ? "missing" : cursor)));
            }
            checks.add(same("The finished menu retains all original archived IDs", archivedIds(prior), archivedIds(loaded)));
            checks.add(same("The new identity was absent before the continuation", false, prior.savedMatchSlots().stream()
                    .anyMatch(slot -> fork.equals(prior.archivedId(slot)))));
            checks.add(same("The continuation adds just one saved game", prior.savedMatchSlots().size() + 1, loaded.savedMatchSlots().size()));
            checks.add(same("All previously saved games retain their checkpoints and order", savedFacts(prior, ""), savedFacts(loaded, fork)));
            var forkMatches = loaded.savedMatchSlots().stream().filter(slot -> fork.equals(loaded.archivedId(slot))).toList();
            checks.add(same("Exactly one saved slot owns the new game identity", 1, forkMatches.size()));
            if (forkMatches.size() == 1) {
                int slot = forkMatches.getFirst();
                checks.add(same("Reload restored a playable Advanced fork with manual input selected",
                        List.of(expected.checkpoint(), expected.setup(), true), List.of(loaded.readSavedMatch(slot).checkpoint(),
                                loaded.readSavedMatch(slot).setup(), loaded.matchManualAi(slot))));
                checks.add(same("No archived analysis is erroneously attached to the active fork", null, loaded.readReviewRecord(fork)));
            }
        } catch (IllegalArgumentException invalid) {
            checks.add(same("Both browser values decode to valid saved match ledgers", true, invalid.getMessage()));
        }
    }

    private static List<Object> result(MatchStore.ArchivedMatch archive) {
        if (archive == null) return List.of();
        return List.of(archive.match().checkpoint(), archive.match().result(), archive.blackBelow(), archive.manualUsed(),
                archive.reviewed(), archive.finishedAt());
    }

    private static List<String> archivedIds(MatchStore store) {
        return store.archivedMatches().stream().map(MatchStore.ArchivedMatch::id).toList();
    }

    private static List<List<Object>> savedFacts(MatchStore store, String excluded) {
        return store.savedMatchSlots().stream().filter(slot -> !excluded.equals(store.archivedId(slot)))
                .map(slot -> List.<Object>of(store.archivedId(slot), store.readSavedMatch(slot).checkpoint(), store.matchManualAi(slot),
                        store.startedAt(slot))).toList();
    }

    private static String digest(String encoded) { return WebEvidencePublication.sha256(encoded.getBytes(StandardCharsets.UTF_8)); }
}
