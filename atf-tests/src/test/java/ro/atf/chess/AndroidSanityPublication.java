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
import org.junit.jupiter.api.Test;

/** Explicit publisher for a captured Android instrumentation run. */
class AndroidSanityPublication {
    private static final Pattern CHECK = Pattern.compile("^INSTRUMENTATION_RESULT: check_(\\d{2})=(.*)$");

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
        var evidence = readPhase(receipt, "openings", 15);
        var frames = List.of(new Frame("opening-catalog.png", "Opening catalog · first lessons"),
                new Frame("opening-catalog-end.png", "Opening catalog · last lessons"),
                new Frame("opening-hint.png", "Theory arrow and move explanation"),
                new Frame("opening-ai.png", "Independent AI analysis in an opening lesson"));
        boolean complete = evidence.passed() && frames.stream().allMatch(frame -> isPng(receipt.resolveSibling(frame.file())));
        var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Study the Italian opening on Android", List.of("e2e", "android", "openings"));
             var phase = scenario.startPhase("TEST BODY")) {
            publish(phase, "Choose a color, practise the line and consult the AI", evidence.rows(), evidence.passed());
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

    @Test void publishMatchAnalysisSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        Path report = receipt.resolveSibling("android-match-analysis.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var prepare = readPhase(receipt, "analysis-prepare", 14);
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

    private record PhaseEvidence(String pid, List<AtfAssertion> rows, boolean passed) { }

    private static PhaseEvidence readPhase(Path receipt, String phase, int expectedChecks) throws IOException {
        if (!Files.isRegularFile(receipt)) return new PhaseEvidence("", List.of(receiptFailure(phase, "Missing native receipt")), false);
        var input = Files.readAllLines(receipt);
        var rows = new ArrayList<AtfAssertion>();
        Set<Integer> ids = new HashSet<>();
        for (String line : input) {
            var match = CHECK.matcher(line);
            if (!match.matches()) continue;
            var fields = match.group(2).split("\t", -1);
            int id = Integer.parseInt(match.group(1));
            if (fields.length == 4 && ids.add(id) && (fields[3].equals("true") || fields[3].equals("false"))) {
                rows.add(new AtfAssertion(fields[0], fields[1], fields[2], Boolean.parseBoolean(fields[3]) && fields[1].equals(fields[2])));
            }
        }
        long rawChecks = input.stream().filter(line -> line.startsWith("INSTRUMENTATION_RESULT: check_")).count();
        String pidPrefix = "INSTRUMENTATION_RESULT: pid=";
        var pids = input.stream().filter(line -> line.startsWith(pidPrefix)).map(line -> line.substring(pidPrefix.length())).toList();
        String pid = pids.size() == 1 && pids.getFirst().matches("[1-9][0-9]*") ? pids.getFirst() : "";
        boolean passed = input.contains("INSTRUMENTATION_RESULT: phase=" + phase)
                && input.contains("INSTRUMENTATION_RESULT: result=PASS") && input.contains("INSTRUMENTATION_CODE: -1")
                && rawChecks == expectedChecks && ids.size() == expectedChecks
                && ids.stream().allMatch(id -> id >= 1 && id <= expectedChecks) && rows.stream().allMatch(AtfAssertion::passed);
        if (!passed) {
            String prefix = "INSTRUMENTATION_RESULT: diagnostic=";
            String diagnostic = input.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length()))
                    .findFirst().orElse("Expected " + expectedChecks + " passing checks and a successful result, received " + rawChecks + " checks");
            rows.add(receiptFailure(phase, diagnostic));
        }
        return new PhaseEvidence(pid, rows, passed);
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
