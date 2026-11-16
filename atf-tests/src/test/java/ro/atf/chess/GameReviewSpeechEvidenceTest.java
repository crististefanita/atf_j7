package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewSpeechEvidenceTest {
    private static final String RUN = "8b377c280f1e4c5690ff912679e69627";
    private static final String SCOPE = "talkback-review-ro";
    private static final String MOVE = "3. Cf3 · Poziția 5 din 8";

    @Test void usesOnlyAudioAcceptedInTheCurrentProbeWindow(Checks check) {
        String prior = RUN.replaceFirst("8", "1");
        var lines = List.of(marker(prior, "BEGIN"), accepted(MOVE, 120), marker(prior, "END"),
                marker(RUN, "BEGIN"), accepted("Altă mutare", 0), accepted(MOVE, 160),
                marker(RUN, "END"), accepted("În afara intervalului", 180));
        check.same("Only the current action contributes positive accepted PCM",
                List.of(new GameReviewSpeechEvidence.Utterance(MOVE, "ro-RO", 160)),
                GameReviewSpeechEvidence.speech(lines, RUN, SCOPE, "MOVE"));
    }

    @Test void rejectsIncompleteOrRepeatedActionWindows(Checks check) {
        check.rejects("A missing end cannot borrow audio from later phases", IllegalArgumentException.class,
                () -> GameReviewSpeechEvidence.speech(List.of(marker(RUN, "BEGIN"), accepted(MOVE, 120)), RUN, SCOPE, "MOVE"));
        check.rejects("A repeated probe action cannot be silently merged", IllegalArgumentException.class,
                () -> GameReviewSpeechEvidence.speech(List.of(marker(RUN, "BEGIN"), marker(RUN, "END"),
                        marker(RUN, "BEGIN"), marker(RUN, "END")), RUN, SCOPE, "MOVE"));
    }

    @Test void keepsTheMoveAndPositionTogetherInTheAnnouncement(Checks check) {
        check.same("The selected move is announced in order with its exact position", true,
                GameReviewSpeechEvidence.containsLabel(MOVE + ", selectat", MOVE));
        check.same("A nearby position does not satisfy the expected announcement", false,
                GameReviewSpeechEvidence.containsLabel(MOVE.replace("5", "6"), MOVE));
        check.same("Mentioning the control without the value cannot pass", false,
                GameReviewSpeechEvidence.containsLabel("Poziția selectată", MOVE));
    }

    private static String marker(String run, String end) {
        return "11-16 20:41:00.000  123  456 I J7ReviewProbe : " + run + ' ' + SCOPE + " MOVE_" + end;
    }

    private static String accepted(String message, int bytes) {
        String encoded = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        return "11-16 20:41:00.010  222  333 I J7ReviewSpeech: ACCEPTED 10029 ro-RO " + bytes + " " + encoded;
    }
}
