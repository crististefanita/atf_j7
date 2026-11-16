package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebBotPublication {
    private static final String SCOPE = "ai-bot-reload";
    private static final String HOST_PATH = "/atf_j7/";
    private static final String PLAY_URL = "https://crististefanita.github.io/atf_j7/#ai";
    private static final String AI_PREFIX = "ai|";
    private static final String PENDING = "3|BLACK|HARD|0|";
    private static final List<String> OBSERVATIONS = List.of("ai-setup", "ai-black-orientation", "bot-pending",
            "bot-reloaded", "bot-reply", "bot-applied-once", "manual-independent");
    private static final Scenario BOT = new Scenario("ChessWebBotPublication",
            "Resume a pending computer move after browser reload", SCOPE, "web-ai-game.html",
            "Verify the computer turn, cold reload and single reply", List.of(
                    new Frame("ai-pending.png", "Hard game at White's pending opening move"),
                    new Frame("ai-resumed.png", "Reload restores the computer turn before the reply"),
                    new Frame("ai-replied.png", "The computer replies once and gives control to Black")));

    @Test void publishBrowserBotRecovery() throws Exception {
        boolean publicRun = Boolean.getBoolean("chess.web.bot.public");
        WebEvidencePublication.publish(configuredPath("chess.web.bot.receipt"), configuredPath("chess.web.bundle"), BOT,
                receipt -> inspect(receipt, publicRun));
    }

    private static Contract inspect(JsonNode receipt, boolean publicRun) {
        var pending = ChessMatch.restore(PENDING);
        var reference = pending.chooseBotMove();
        var after = pending.play(reference);
        var pendingStored = receipt.path("pendingCheckpoint").asText();
        var completedStored = receipt.path("completedCheckpoint").asText();
        var pendingRaw = receipt.path("pendingMatchCheckpoint").asText();
        var completedRaw = receipt.path("completedMatchCheckpoint").asText();
        var botUci = receipt.path("botMove").asText();
        String publicUrl = receipt.path("publicUrl").asText("");
        var fields = List.of(new ReportField("Hosting path", receipt.path("hostPath").asText()),
                new ReportField("Browser origin", publicUrl.isEmpty() ? "Loopback" : publicUrl),
                new ReportField("Pending game checkpoint", pendingStored), new ReportField("Committed reply checkpoint", completedStored),
                new ReportField("JVM bot reference UCI", reference.uci()), new ReportField("Browser bot UCI", botUci));
        var checks = new ArrayList<>(List.of(same("Evidence belongs to the AI game flow", SCOPE, receipt.path("scope").asText()),
                same("Browser serves the production-shaped subpath", HOST_PATH, receipt.path("hostPath").asText()),
                same("Browser uses the expected deployment channel", publicRun ? PLAY_URL : "", publicUrl),
                same("Pending checkpoint retains Black's seat and the Hard profile", PENDING, pendingRaw),
                same("Browser persists the pending game in AI mode", AI_PREFIX + pendingRaw, pendingStored),
                same("The real worker replies with the same move as the JVM bot", reference.uci(), botUci),
                same("The saved game contains only the expected computer move", after.checkpoint(), completedRaw),
                same("Browser keeps the completed reply in AI mode", AI_PREFIX + completedRaw, completedStored)));
        checks.add(restoredReply(completedRaw, reference.uci()));
        var observations = new LinkedHashMap<String, JsonNode>();
        for (String id : OBSERVATIONS) observations.put(id, BooleanNode.TRUE);
        observations.put("bot-reply", TextNode.valueOf(reference.uci()));
        return new Contract(fields, checks, observations, publicRun ? "en-US" : "ro-RO");
    }

    private static AtfAssertion restoredReply(String checkpoint, String expectedMove) {
        var title = "A new session restores one reply, Black to move and the Hard profile";
        var expected = List.of(1, Side.BLACK, Level.HARD, false, expectedMove);
        try {
            var session = new ChessGameSession(ChessMatch.restore(checkpoint), true);
            var lastMove = session.match().lastMove();
            return same(title, expected, List.of(session.match().ply(), session.match().position().turn(),
                    session.match().setup().level(), session.awaitingBot(), lastMove == null ? "missing" : lastMove.uci()));
        } catch (IllegalArgumentException invalid) {
            return same(title, expected, invalid.getMessage());
        }
    }
}
