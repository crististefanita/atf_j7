package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Explicit publisher for a captured Android instrumentation run. */
@Tag("native-evidence")
class AndroidSanityPublication {
    private static final Pattern CHECK = Pattern.compile("^INSTRUMENTATION_RESULT: check_(\\d{2})=(.*)$");
    private static final List<String> OPENING_GEOMETRY_CHECKS = List.of(
            "Next preserves the board and its corner squares when Resume lesson appears",
            "Next preserves the board and its corner squares when Lesson hint returns");

    private record Frame(String file, String title) { }

    private static final List<Frame> FRAMES = List.of(new Frame("hint-piece.png", "Simple hint · piece only"),
            new Frame("hint-solution.png", "Complete hint · d8 to h4"), new Frame("board.png", "Restored solved position after Qh4#"),
            new Frame("milestone.png", "First milestone · five solved"), new Frame("mate-two.png", "Mate in two · after the defense"),
            new Frame("menu.png", "Main menu · four puzzle chapters and progress totals"), new Frame("mate-three.png", "Mate in three · after the second defense"),
            new Frame("tactics-streak.png", "Tactics · second solve, bonus and streak"),
            new Frame("match-resumed.png", "Computer game · last move after restart"),
            new Frame("match-history.png", "Computer game · reviewing an earlier move"));

    @Test void publishOpeningSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        Path report = receipt.resolveSibling("android-openings.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var evidence = readOpeningPhase(receipt);
        var frames = List.of(new Frame("opening-catalog.png", "Opening catalog · first lessons"),
                new Frame("opening-catalog-end.png", "Opening catalog · last lessons"),
                new Frame("opening-hint.png", "Theory arrow and move explanation"),
                new Frame("opening-ai.png", "Independent AI analysis in an opening lesson"),
                new Frame("opening-replay.png", "Review with previous, next and play from here"));
        boolean complete = evidence.passed() && frames.stream().allMatch(frame -> isPng(receipt.resolveSibling(frame.file())));
        var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Study the Italian opening on Android", List.of("e2e", "android", "openings"));
             var phase = scenario.startPhase("TEST BODY")) {
            var geometry = evidence.rows().stream().filter(row -> OPENING_GEOMETRY_CHECKS.contains(row.name())).toList();
            var lesson = evidence.rows().stream().filter(row -> !OPENING_GEOMETRY_CHECKS.contains(row.name())).toList();
            publish(phase, "Choose a color, use the hints, step through the line and replay", lesson, evidence.passed());
            publish(phase, "Keep board coordinates stable across Next at three viewport sizes", geometry,
                    missingOpeningGeometry(geometry).isEmpty() && geometry.stream().allMatch(AtfAssertion::passed));
            for (var frame : frames) {
                Path image = receipt.resolveSibling(frame.file());
                if (isPng(image)) {
                    try (var action = phase.startRequest("Android instrumentation", "UI", frame.title())) {
                        action.screenshot(frame.title(), Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
                        action.complete(AtfReportStatus.PASS);
                    }
                } else publish(phase, frame.title(), List.of(receiptFailure("openings", "Missing PNG: " + frame.file())), false);
            }
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android opening evidence: " + receipt);
    }

    @Test void publishGameReviewSanity() throws Exception {
        publishFocusedSanity("game-review", "Review a completed match and continue from an earlier move",
                "Review the completed game, change the line and verify match actions", 15, List.of(
                        new Frame("game-review.png", "Game review summary"),
                        new Frame("game-review-comparison.png", "Move counts by type · player and AI"),
                        new Frame("game-review-actions.png", "In-game options · offer draw and resign")));
    }

    @Test void publishManualAnalysisSanity() throws Exception {
        publishFocusedSanity("manual-analysis", "Explore a line without changing the saved game",
                "Play the hinted move and continue from history in a separate game", 15,
                List.of(new Frame("manual-analysis.png", "Manual analysis board")));
    }

    @Test void publishMatchSlotsSanity() throws Exception {
        publishFocusedSanity("match-slots", "Keep separate games and take over the AI side",
                "Play, switch games and verify the tenth slot", 5, List.of(
                        new Frame("match-home.png", "Grouped home menu"), new Frame("match-slots.png", "Manual AI control above the board")));
    }

    @Test void publishMatchArchiveSanity() throws Exception {
        publishFocusedSanity("match-archive", "Keep the latest completed games and their reviews",
                "Review from history, reopen the result and verify retention", 4, List.of(new Frame("match-archive.png", "Completed games with saved review")));
    }

    private static void publishFocusedSanity(String scope, String scenarioTitle, String stepTitle, int checks, List<Frame> frames) throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        Path report = receipt.resolveSibling("android-" + scope + ".html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var evidence = readPhase(receipt, scope, checks);
        boolean rendered = frames.stream().allMatch(frame -> isPng(receipt.resolveSibling(frame.file())));
        boolean complete = evidence.passed() && rendered;
        var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", scenarioTitle, List.of("e2e", "android", scope));
             var phase = scenario.startPhase("TEST BODY")) {
            publish(phase, stepTitle, evidence.rows(), evidence.passed());
            for (var frame : frames) {
                Path image = receipt.resolveSibling(frame.file());
                if (isPng(image)) {
                    try (var action = phase.startRequest("Android instrumentation", "UI", frame.title())) {
                        action.screenshot(frame.title(), Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
                        action.complete(AtfReportStatus.PASS);
                    }
                } else publish(phase, frame.title(), List.of(receiptFailure(scope, "Missing " + frame.file())), false);
            }
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android " + scope + " evidence: " + receipt);
    }

    @Test void publishMatchAnalysisSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        Path report = receipt.resolveSibling("android-match-analysis.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var prepare = readPhase(receipt, "analysis-prepare", 17);
        var restore = readPhase(receipt.resolveSibling("analysis-restore.txt"), "analysis-restore", 9);
        boolean restarted = !prepare.pid().isEmpty() && !restore.pid().isEmpty() && !prepare.pid().equals(restore.pid());
        var frames = List.of(new Frame("match-analysis.png", "Evaluation and suggestion below the board"),
                new Frame("match-captures.png", "Captured pawn and material advantage beneath the player"),
                new Frame("match-setup.png", "Color, AI level and analysis setup"), new Frame("match-black.png", "Named Black seat and evaluation bar"));
        boolean rendered = frames.stream().allMatch(frame -> isPng(receipt.resolveSibling(frame.file())));
        boolean complete = prepare.passed() && restore.passed() && restarted && rendered;
        var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Enable optional AI analysis and restore the game across a process restart", List.of("e2e", "android", "analysis"));
             var phase = scenario.startPhase("TEST BODY")) {
            publish(phase, "Navigate puzzles and game history with an anchored board", prepare.rows(), prepare.passed());
            publish(phase, "Restart the Android process", List.of(new AtfAssertion("Instrumentation process IDs", "Two distinct Android PIDs",
                    prepare.pid() + " → " + restore.pid(), restarted)), restarted);
            publish(phase, "Resume the game, edit the profile and start a Black match", restore.rows(), restore.passed());
            for (var frame : frames) {
                Path file = receipt.resolveSibling(frame.file());
                if (isPng(file)) {
                    try (var action = phase.startRequest("Android instrumentation", "UI", frame.title())) {
                        action.screenshot(frame.title(), Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
                        action.complete(AtfReportStatus.PASS);
                    }
                } else {
                    publish(phase, frame.title(), List.of(new AtfAssertion("PNG evidence", "Readable PNG", "Missing or invalid " + frame.file(), false)), false);
                }
            }
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android analysis evidence: " + receipt);
    }

    @Test void publishBoardSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        Path report = receipt.resolveSibling("android-board-sanity.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var prepare = readPhase(receipt, "prepare", 46);
        var restore = readPhase(receipt.resolveSibling("restore.txt"), "restore", 28);
        var matchPrepare = readPhase(receipt.resolveSibling("match-prepare.txt"), "match-prepare", 3);
        var matchRestore = readPhase(receipt.resolveSibling("match-restore.txt"), "match-restore", 15);
        var matchSettled = readPhase(receipt.resolveSibling("match-settled.txt"), "match-settled", 4);
        var phases = List.of(prepare, restore, matchPrepare, matchRestore, matchSettled);
        var pids = phases.stream().map(PhaseEvidence::pid).toList();
        boolean newProcess = !pids.contains("") && pids.stream().distinct().count() == phases.size();
        Set<Frame> available = new HashSet<>();
        for (var frame : FRAMES) if (isPng(receipt.resolveSibling(frame.file()))) available.add(frame);
        boolean complete = phases.stream().allMatch(PhaseEvidence::passed) && newProcess && available.size() == FRAMES.size();
        var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Play the puzzle chapters and review the computer match across restarts", List.of("e2e", "android"));
             var phase = scenario.startPhase("TEST BODY")) {
            publish(phase, "Open the menu, earn a tactics bonus and play up to the pending second defense", prepare.rows(), prepare.passed());
            publish(phase, "Android process restart", List.of(new AtfAssertion("Instrumentation process IDs", "Five distinct Android PIDs",
                    String.join(" → ", pids), newProcess)), newProcess);
            publish(phase, "Resume pending quests and restore tactics points and mate progress", restore.rows(), restore.passed());
            publish(phase, "Save a computer game before the reply", matchPrepare.rows(), matchPrepare.passed());
            publish(phase, "Resume the computer game and navigate its history", matchRestore.rows(), matchRestore.passed());
            publish(phase, "Reopen after the saved reply", matchSettled.rows(), matchSettled.passed());
            for (var frame : FRAMES) {
                if (available.contains(frame)) {
                    try (var action = phase.startRequest("Android instrumentation", "UI", frame.title())) {
                        var png = Files.readAllBytes(receipt.resolveSibling(frame.file()));
                        action.screenshot(frame.title(), Base64.getEncoder().encodeToString(png));
                        action.complete(AtfReportStatus.PASS);
                    }
                } else {
                    publish(phase, frame.title(), List.of(new AtfAssertion("PNG evidence", "Readable PNG", "Missing or invalid " + frame.file(), false)), false);
                }
            }
            Path failure = receipt.resolveSibling("failure.png");
            if (!complete && isPng(failure)) {
                try (var action = phase.startRequest("Android instrumentation", "UI", "Android failure frame")) {
                    action.screenshot("Screen at the failing checkpoint", Base64.getEncoder().encodeToString(Files.readAllBytes(failure)));
                    action.complete(AtfReportStatus.FAIL);
                }
            }
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android restart evidence: " + receipt);
    }

    private static void publish(atf.reporting.contract.AtfReportPorts.Phase phase, String title, List<AtfAssertion> rows, boolean passed) {
        try (var step = phase.startAssertionStep(title)) {
            if (!rows.isEmpty()) step.assertions(rows);
            step.complete(passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
        }
    }

    record PhaseEvidence(String pid, List<AtfAssertion> rows, boolean passed) {
        PhaseEvidence { rows = List.copyOf(rows); }
    }

    static PhaseEvidence readOpeningPhase(Path receipt) throws IOException {
        var evidence = readPhase(receipt, "openings", 26);
        if (!evidence.passed()) return evidence;
        var missing = missingOpeningGeometry(evidence.rows());
        if (missing.isEmpty()) return evidence;
        var rows = new ArrayList<>(evidence.rows());
        rows.add(receiptFailure("openings", "Missing or duplicate board geometry checks: " + missing));
        return new PhaseEvidence(evidence.pid(), rows, false);
    }

    private static List<String> missingOpeningGeometry(List<AtfAssertion> rows) {
        return OPENING_GEOMETRY_CHECKS.stream().filter(name -> rows.stream().filter(row -> row.name().equals(name)).count() != 1).toList();
    }

    static PhaseEvidence readPhase(Path receipt, String phase, int expectedChecks) throws IOException {
        if (!Files.isRegularFile(receipt)) return new PhaseEvidence("", List.of(receiptFailure(phase, "Missing native receipt")), false);
        var input = Files.readAllLines(receipt);
        var rows = new ArrayList<AtfAssertion>();
        Set<Integer> ids = new HashSet<>();
        for (String line : input) {
            var match = CHECK.matcher(line);
            if (!match.matches()) continue;
            var fields = match.group(2).split("\t", -1);
            int id = Integer.parseInt(match.group(1));
            if (fields.length == 4 && !fields[0].isBlank() && (fields[3].equals("true") || fields[3].equals("false")) && ids.add(id)) {
                rows.add(new AtfAssertion(fields[0], fields[1], fields[2], Boolean.parseBoolean(fields[3]) && fields[1].equals(fields[2])));
            }
        }
        long rawChecks = input.stream().filter(line -> line.startsWith("INSTRUMENTATION_RESULT: check_")).count();
        var pids = markerValues(input, "INSTRUMENTATION_RESULT: pid=");
        var phases = markerValues(input, "INSTRUMENTATION_RESULT: phase=");
        var results = markerValues(input, "INSTRUMENTATION_RESULT: result=");
        var codes = markerValues(input, "INSTRUMENTATION_CODE: ");
        String pid = pids.size() == 1 && pids.getFirst().matches("[1-9][0-9]*") ? pids.getFirst() : "";
        boolean passed = !pid.isEmpty() && phases.equals(List.of(phase)) && results.equals(List.of("PASS")) && codes.equals(List.of("-1"))
                && rawChecks == expectedChecks && rows.size() == expectedChecks
                && ids.stream().allMatch(id -> id >= 1 && id <= expectedChecks) && rows.stream().allMatch(AtfAssertion::passed);
        if (!passed) {
            var diagnostics = markerValues(input, "INSTRUMENTATION_RESULT: diagnostic=");
            String diagnostic = diagnostics.isEmpty()
                    ? "Phase " + phases + ", result " + results + ", code " + codes + ", PID " + pids
                            + ", valid checks " + rows.size() + "/" + expectedChecks + " from " + rawChecks + " native rows"
                    : String.join(" · ", diagnostics);
            rows.add(receiptFailure(phase, diagnostic));
        }
        return new PhaseEvidence(pid, rows, passed);
    }

    private static List<String> markerValues(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length())).toList();
    }

    private static AtfAssertion receiptFailure(String phase, String diagnostic) {
        return new AtfAssertion("Native " + phase + " receipt", "Complete passing receipt", diagnostic, false);
    }

    private static boolean isPng(Path image) {
        if (!Files.isRegularFile(image)) return false;
        try (var stream = ImageIO.createImageInputStream(image.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                reader.setInput(stream);
                return reader.getFormatName().equalsIgnoreCase("PNG") && reader.read(0) != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException invalidCapture) {
            return false;
        }
    }
}
