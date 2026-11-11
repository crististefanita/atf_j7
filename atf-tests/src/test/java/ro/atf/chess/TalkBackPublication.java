package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Publishes an explicit emulator probe without changing the release qualification reports. */
@Tag("native-evidence")
class TalkBackPublication {
    private static final Pattern SPEECH = Pattern.compile(".* I/J7TtsProbe\\(\\s*\\d+\\): (REQUEST|SPOKEN) (.*)");
    private static final Pattern FOCUS = Pattern.compile(".* I/J7FocusProbe\\(\\s*\\d+\\): FOCUS \\[(.*)] (.*)");
    private static final Pattern GESTURE_MARKER = Pattern.compile(".* I/J7ProbeMarker\\(\\s*\\d+\\): ((?:BLACK|WHITE)_(?:ANCHOR|PIECE|DESTINATION)_(?:START|END))");
    private static final List<String> ORIENTATIONS = List.of("BLACK", "WHITE");
    private static final String HINT = "Black to move. Move the Black Queen on d8. Destination h4.";
    private static final String MILESTONE = "5 reached · next 15";
    private static final String CHECKMATE = "Checkmate · Solved";

    @Test void publishTalkBackSpeech() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.talkback.receipt"));
        Path report = receipt.resolveSibling("android-talkback.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh TalkBack evidence folder");
        var lines = Files.readAllLines(receipt);
        var baseline = new Trace(lines, "RUN5_MILESTONE");
        var hint = new Trace(lines, "FINAL_HINT");
        var milestone = new Trace(lines, "FINAL_MILESTONE");
        var redraw = new Trace(lines, "FINAL_REDRAW");
        var hintChecks = List.of(
                assertion("TalkBack requests the color, piece and destination in order", HINT, hint.utterance("REQUEST", "Destination")),
                assertion("The TTS service returns audio for the same ordered hint", HINT, hint.utterance("SPOKEN", "Destination")));
        var milestoneChecks = List.of(
                assertion("Released 0.3.0 requests the checkmate result", CHECKMATE, baseline.utterance("REQUEST", "Checkmate")),
                assertion("Released 0.3.0 omits the milestone from speech", 0L, baseline.milestones("REQUEST")),
                assertion("The new checkmate announcement includes the milestone", CHECKMATE + ". " + MILESTONE, milestone.utterance("SPOKEN", "Checkmate")),
                assertion("The fifth solve requests the milestone exactly once", 1L, milestone.milestones("REQUEST")),
                assertion("The TTS service returns audio for the milestone exactly once", 1L, milestone.milestones("SPOKEN")),
                assertion("Both flips and the return through the menu were accepted", 4L, redraw.acceptedActions()),
                assertion("Redrawing and reopening do not request the milestone again", 0L, redraw.milestones("REQUEST")));
        var result = status(java.util.stream.Stream.concat(hintChecks.stream(), milestoneChecks.stream()).toList());
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("TalkBack", "Hint traversal and milestone announcement", List.of("e2e", "android", "accessibility"));
             var phase = scenario.startPhase("TEST BODY")) {
            phase.detail("Speech source", "TalkBack 14.2 on Android API 33. The temporary TTS probe forwards Google's synthesized PCM and records accepted output.");
            try (var step = phase.startAssertionStep("Hint traversal")) {
                step.assertions(hintChecks);
                step.complete(status(hintChecks));
            }
            try (var step = phase.startAssertionStep("Milestone announcement")) {
                step.assertions(milestoneChecks);
                step.complete(status(milestoneChecks));
            }
            for (String checkpoint : List.of("final-hint", "final-milestone", "final-reopen")) {
                Path image = receipt.resolveSibling(checkpoint + ".png");
                if (Files.isRegularFile(image)) try (var request = phase.startRequest("Android emulator", "UI", checkpoint)) {
                    request.screenshot(checkpoint, Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
                    request.complete(AtfReportStatus.PASS);
                }
            }
            phase.complete(result);
            scenario.complete(result);
        }
        org.junit.jupiter.api.Assertions.assertEquals(AtfReportStatus.PASS, result, "See " + report);
    }

    @Test void publishTalkBackGestures() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.talkback.gestures"));
        Path report = receipt.resolveSibling("talkback-gestures-extent.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh TalkBack evidence folder");
        var lines = Files.readAllLines(receipt);
        String provenance = Files.readString(receipt.resolveSibling("emulator.json"));
        validateGestureMarkers(lines);
        var black = gestures(lines, "BLACK");
        var white = gestures(lines, "WHITE");
        var result = status(java.util.stream.Stream.concat(black.stream(), white.stream()).toList());
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("TalkBack gestures", "Focus follows the complete hint in both orientations", List.of("e2e", "android", "accessibility"));
             var phase = scenario.startPhase("TEST BODY")) {
            try (var request = phase.startRequest("Android emulator", "UI", "Touchscreen provenance")) {
                request.json("Emulator and APK", provenance);
                request.detail("Input and oracle", "Two right swipes through the emulator touchscreen RPC, verified with Android accessibility focus events.");
                request.complete(AtfReportStatus.PASS);
            }
            for (String orientation : ORIENTATIONS) {
                var assertions = orientation.equals("BLACK") ? black : white;
                var anchor = new Trace(lines, orientation + "_ANCHOR");
                String marker = orientation + "_PROGRAMMATIC_ANCHOR";
                try (var step = phase.startAssertionStep(orientation.equals("BLACK") ? "Black below" : "White below")) {
                    step.assertions(assertions);
                    step.complete(status(assertions));
                }
                for (String step : List.of("color", "PIECE", "DESTINATION")) {
                    String checkpoint = orientation + "-" + step;
                    try (var request = phase.startRequest("Android emulator", "UI", checkpoint)) {
                        if (step.equals("color")) request.detail("Focus starting point", anchor.lines().stream().anyMatch(line -> line.endsWith(marker))
                                ? "Placed on the color label by the observer before the gestures" : "Reached by touch exploration");
                        byte[] png = Files.readAllBytes(receipt.resolveSibling(checkpoint + ".png"));
                        request.screenshot(checkpoint, Base64.getEncoder().encodeToString(png));
                        request.complete(AtfReportStatus.PASS);
                    }
                }
            }
            phase.complete(result);
            scenario.complete(result);
        }
        org.junit.jupiter.api.Assertions.assertEquals(AtfReportStatus.PASS, result, "See " + report);
    }

    private static void validateGestureMarkers(List<String> lines) {
        var expected = ORIENTATIONS.stream().flatMap(orientation -> List.of("ANCHOR", "PIECE", "DESTINATION").stream()
                .flatMap(step -> List.of(orientation + "_" + step + "_START", orientation + "_" + step + "_END").stream())).toList();
        var actual = lines.stream().map(GESTURE_MARKER::matcher).filter(java.util.regex.Matcher::matches).map(match -> match.group(1)).toList();
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, "Use the trace from one complete emulator probe");
    }

    private static List<AtfAssertion> gestures(List<String> lines, String orientation) {
        var anchor = new Trace(lines, orientation + "_ANCHOR").focus();
        return List.of(
                assertion("Focus starts on the active color", "Black to move", anchor.isEmpty() ? "No focus event" : anchor.getLast()),
                assertion("First right swipe focuses the hinted piece", List.of("d8, Black Queen, hinted piece"),
                        new Trace(lines, orientation + "_PIECE").focus()),
                assertion("Second right swipe focuses the solution destination", List.of("h4, solution destination"),
                        new Trace(lines, orientation + "_DESTINATION").focus()));
    }

    private static AtfAssertion assertion(String name, Object expected, Object actual) {
        return new AtfAssertion(name, expected.toString(), actual.toString(), expected.equals(actual));
    }

    private static AtfReportStatus status(List<AtfAssertion> checks) {
        return checks.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
    }

    private record Trace(List<String> lines) {
        Trace(List<String> all, String phase) {
            this(between(all, phase));
        }

        private static List<String> between(List<String> all, String phase) {
            int start = -1;
            for (int i = 0; i < all.size(); i++) {
                String line = all.get(i);
                if (line.contains("J7ProbeMarker") && line.endsWith(phase + "_START")) start = i;
                if (start >= 0 && line.contains("J7ProbeMarker") && line.endsWith(phase + "_END")) return all.subList(start, i + 1);
            }
            throw new IllegalArgumentException("Missing TalkBack evidence for " + phase);
        }

        private List<String> speech(String type) {
            return lines.stream().map(SPEECH::matcher).filter(java.util.regex.Matcher::matches)
                    .filter(match -> type.equals(match.group(1))).map(match -> match.group(2).strip()).toList();
        }

        List<String> focus() {
            return lines.stream().map(FOCUS::matcher).filter(java.util.regex.Matcher::matches)
                    .map(match -> match.group(2).equals("null") ? match.group(1) : match.group(2)).toList();
        }

        String utterance(String type, String fragment) { return String.join(" / ", speech(type).stream().filter(text -> text.contains(fragment)).toList()); }
        long milestones(String type) {
            return speech(type).stream().mapToLong(text -> text.split(Pattern.quote(MILESTONE), -1).length - 1).sum();
        }

        long acceptedActions() { return lines.stream().filter(line -> line.contains("J7FocusProbe") && line.endsWith("Action result=true")).count(); }
    }
}
