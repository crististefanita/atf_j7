package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebLedgerPublication {
    private static final String SCOPE = "match-ledger";
    private static final String LEGACY_LOCAL = "1|f2f3 e7e5 g2g4";
    private static final String LEGACY_AI = "ai|3|WHITE|ADVANCED|0|d2d4 e7e5";
    private static final List<String> OBSERVATIONS = List.of("legacy-migration", "saved-only-active", "mate-archived",
            "finished-only-complete", "archive-open", "history-fork", "fork-reloaded", "archive-unchanged");
    private static final Scenario LEDGER = new Scenario("ChessWebLedgerPublication",
            "Migrate saved games, archive a mate and continue from history", SCOPE, "web-match-ledger.html",
            "Verify the Saved and Finished menus and the reloaded continuation", List.of(
                    new Frame("saved-games.png", "Both migrated games are active"),
                    new Frame("finished-games.png", "The finished menu shows the recorded mate"),
                    new Frame("selected-history.png", "Review selects the position before the fork"),
                    new Frame("resumed-fork.png", "Reload restores the new branch")));

    @Test void publishBrowserLedger() throws Exception {
        boolean publicRun = Boolean.getBoolean("chess.web.ledger.public");
        WebEvidencePublication.publish(configuredPath("chess.web.ledger.receipt"), configuredPath("chess.web.bundle"), LEDGER,
                receipt -> inspect(receipt, publicRun));
    }

    private static Contract inspect(JsonNode receipt, boolean publicRun) {
        var local = ChessMatch.restore(LEGACY_LOCAL);
        var archived = local.play(Move.fromUci("d8h4"));
        var continuation = archived.continueFrom(2);
        var fork = continuation.play(Move.fromUci("d2d4"));

        String formerLocal = receipt.path("legacyLocalId").asText();
        String formerAi = receipt.path("legacyAiId").asText();
        String archiveId = receipt.path("archiveId").asText();
        String forkId = receipt.path("forkId").asText();
        String archiveCheckpoint = receipt.path("archiveCheckpoint").asText();
        String forkCheckpoint = receipt.path("forkCheckpoint").asText();
        String publicUrl = receipt.path("publicUrl").asText("");
        var fields = List.of(new ReportField("Archived game ID", archiveId), new ReportField("New game ID", forkId),
                new ReportField("Unchanged AI game ID", formerAi), new ReportField("Archived checkpoint", archiveCheckpoint),
                new ReportField("New game checkpoint", forkCheckpoint),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl));
        var inputs = new ArrayList<>(List.of(same("Evidence belongs to the ledger flow", SCOPE, receipt.path("scope").asText()),
                same("Browser uses the expected deployment channel", publicRun ? ChessPublicationRoutes.HOME_URL : "", publicUrl),
                same("The migrated manual game preserves its original three plies", LEGACY_LOCAL,
                        receipt.path("legacyLocalCheckpoint").asText()),
                same("The migrated AI game preserves its checkpoint and mode", LEGACY_AI, receipt.path("legacyAiCheckpoint").asText()),
                same("Finishing the manual game archives its existing ID", true, !formerLocal.isBlank() && formerLocal.equals(archiveId)),
                same("Archive output matches the JVM terminal checkpoint", archived.checkpoint(), archiveCheckpoint),
                same("The continuation selects recorded ply two", 2, receipt.path("forkPly").asInt()),
                same("The continuation starts at the same pre-move checkpoint as JVM", continuation.checkpoint(),
                        receipt.path("forkOriginCheckpoint").asText()),
                same("The new game has its own ID distinct from both original games", true,
                        !forkId.isBlank() && !formerAi.isBlank() && !archiveId.equals(formerAi)
                                && !forkId.equals(archiveId) && !forkId.equals(formerAi)),
                same("The resumed fork matches the JVM checkpoint after the alternate move", fork.checkpoint(), forkCheckpoint),
                same("The unrelated AI game retains its exact checkpoint", LEGACY_AI, receipt.path("separateAiCheckpoint").asText()),
                same("The final saved menu shows only the fresh fork and the conserved AI game", List.of(forkId, formerAi),
                        identities(receipt.path("savedAfterIds"))),
                same("The final finished menu shows only the original archived ID", List.of(archiveId),
                        identities(receipt.path("finishedAfterIds")))));
        inputs.add(restoredOutcomes(archiveCheckpoint, forkCheckpoint));
        var observations = new LinkedHashMap<String, JsonNode>();
        for (String id : OBSERVATIONS) observations.put(id, BooleanNode.TRUE);
        return new Contract(fields, inputs, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static List<String> identities(JsonNode values) {
        var ids = new ArrayList<String>();
        if (values.isArray()) for (JsonNode value : values) ids.add(value.asText());
        return ids;
    }

    private static AtfAssertion restoredOutcomes(String archive, String fork) {
        var title = "The archived checkpoint is terminal and the new checkpoint is playable";
        var expected = List.of(ChessMatch.Result.BLACK_WINS, ChessMatch.Result.IN_PROGRESS);
        try {
            return same(title, expected, List.of(ChessMatch.restore(archive).result(), ChessMatch.restore(fork).result()));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return same(title, expected, invalid.getMessage());
        }
    }
}
