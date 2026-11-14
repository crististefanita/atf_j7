package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
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
    private static final String LONG_GRAPH_CHECK = "A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row";
    private static final List<String> REVIEW_EXPLANATION_CHECKS = List.of(
            "Why shows both expected scores and search depth when the line proves no concrete reason",
            "Why names the mating queen on h4 for the archived g4 blunder",
            "The recommended branch starts before g4 and replays its first legal move",
            "The played branch replays g4 and the mating reply from the same starting position",
            "Cancelling Try again keeps the played line and the original review open");
    private static final List<String> REVIEW_RESTORE_CHECKS = List.of(
            "A restarted process reopens the archived analysis on the played mating reply",
            "Confirming from the reopened review creates a separate game and keeps the archived analysis");
    private static final List<String> OPENING_GEOMETRY_CHECKS = List.of(
            "Next preserves the board and its corner squares when Resume lesson appears",
            "Next preserves the board and its corner squares when Lesson hint returns");
    private static final List<String> QUEST_RESTART_CHECKS = List.of(
            "A real process restart keeps the tactics chapter position and its two awards",
            "Training reopens its second unsolved puzzle after process restart");

    private record Frame(String file, String title) { }
    private record MenuCheckpoint(String action, String assertion, String frame) { }
    private record BoundRun(String scope, String title, Path receipt, PhaseEvidence evidence,
                            JsonNode execution, List<ReportField> identity, List<Frame> frames, List<String> nativeMarkers) { }
    private static final String DATED_LAST_ROW = "The last game shows its title, moves and local time with Back visible";

    private static final List<Frame> FRAMES = List.of(new Frame("hint-piece.png", "Simple hint · piece only"),
            new Frame("hint-solution.png", "Complete hint · d8 to h4"), new Frame("board.png", "Restored solved position after Qh4#"),
            new Frame("milestone.png", "First milestone · five solved"), new Frame("mate-two.png", "Mate in two · after the defense"),
            new Frame("menu.png", "Puzzle menu · opening viewport"), new Frame("mate-three.png", "Mate in three · after the second defense"),
            new Frame("tactics-streak.png", "Tactics · second solve and streak"),
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
                    missingChecks(geometry, OPENING_GEOMETRY_CHECKS).isEmpty() && geometry.stream().allMatch(AtfAssertion::passed));
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
        publishGameReview(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishGameReview(Path receipt) throws Exception {
        Path report = receipt.resolveSibling("android-game-review.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        Path why = receipt.resolveSibling("why").resolve("game-review-why.txt");
        Path restored = receipt.resolveSibling("why-restore").resolve("game-review-why-restore.txt");
        var reviewRun = reviewRun("game-review", "Select the exact plotted move and review the completed game", receipt, 23,
                List.of(LONG_GRAPH_CHECK), List.of(new Frame("game-review.png", "Game review summary"),
                        new Frame("game-review-navigation.png", "Ply 184 of 185 · synchronized board and highlighted row"),
                        new Frame("game-review-line.png", "Recommended line marked on the board"),
                        new Frame("game-review-comparison.png", "Move counts by type · player and AI"),
                        new Frame("game-review-actions.png", "In-game options · offer draw and resign")));
        var whyRun = reviewRun("game-review-why", "Compare both scores and explore the blunder before restart", why, 5,
                REVIEW_EXPLANATION_CHECKS, List.of(new Frame("game-review-why-score.png", "Expected scores and depth after f3"),
                        new Frame("game-review-why.png", "Why g4 allows mate on h4"),
                        new Frame("game-review-why-recommended.png", "Recommended branch · first move")));
        var restoredRun = reviewRun("game-review-why-restore", "Resume the archived analysis and confirm a separate continuation", restored, 2,
                REVIEW_RESTORE_CHECKS, List.of(new Frame("game-review-why-restore.png", "Reopened played branch · mating reply")));
        var runs = List.of(reviewRun, whyRun, restoredRun);
        boolean restarted = !whyRun.evidence().pid().isEmpty() && !restoredRun.evidence().pid().isEmpty()
                && !whyRun.evidence().pid().equals(restoredRun.evidence().pid());
        boolean sameExecution = sameExecution(runs);
        boolean complete = restarted && sameExecution;
        var acceptedCaptures = new HashSet<String>();
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Understand a blunder and resume its archived analysis",
                     List.of("e2e", "android", "game-review"));
             var phase = scenario.startPhase("TEST BODY")) {
            publish(phase, "Android process restart and execution identity", List.of(
                    new AtfAssertion("Explanation and restore process IDs", "Two distinct Android PIDs",
                            whyRun.evidence().pid() + " → " + restoredRun.evidence().pid(), restarted),
                    new AtfAssertion("All phases belong to the same execution", "Matching run ID, APK, source, variant, device and API",
                            sameExecution ? "Matching run ID, APK, source, variant, device and API" : "Missing or mismatched phase run identity", sameExecution)),
                    complete);
            for (var run : runs) {
                complete &= publishBoundRun(phase, run, acceptedCaptures, false, true);
            }
            var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android Game Review evidence: " + receipt);
    }

    private static BoundRun reviewRun(String scope, String title, Path receipt, int checks, List<String> required,
                                       List<Frame> frames) throws IOException, java.security.NoSuchAlgorithmException {
        return boundRun(scope, title, receipt, checks, required, "Game Review", frames);
    }

    private static BoundRun boundRun(String scope, String title, Path receipt, int checks, List<String> required,
                                     String contract, List<Frame> frames) throws IOException, java.security.NoSuchAlgorithmException {
        var evidence = requireChecks(readPhase(receipt, scope, checks), scope, required, contract);
        var execution = readExecution(receipt.resolveSibling("execution.json"));
        boolean hasReceipt = Files.isRegularFile(receipt);
        byte[] raw = hasReceipt ? Files.readAllBytes(receipt) : new byte[0];
        String receiptHash = hasReceipt ? sha256(raw) : "";
        var markers = new String(raw, java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        String runId = execution.path("runId").asText();
        boolean bound = runId.matches("[a-f0-9]{32}")
                && markerValues(markers, "INSTRUMENTATION_RESULT: runId=").equals(List.of(runId));
        var identity = bound ? menuIdentity(execution, scope, receiptHash, checks) : List.<ReportField>of();
        return new BoundRun(scope, title, receipt, evidence, execution, identity, frames, markers);
    }

    private static boolean sameExecution(List<BoundRun> runs) {
        var first = runs.getFirst().execution();
        return runs.stream().allMatch(run -> !run.identity().isEmpty()
                && List.of("runId", "apkSha256", "sourceCommit", "variant", "device", "api").stream()
                        .allMatch(field -> run.execution().path(field).equals(first.path(field))));
    }

    private static boolean publishBoundRun(atf.reporting.contract.AtfReportPorts.Phase phase, BoundRun run,
                                           Set<String> captures, boolean nativeDigest, boolean attach)
            throws IOException, java.security.NoSuchAlgorithmException {
        if (!run.identity().isEmpty()) {
            var audit = new ArrayList<>(run.identity());
            audit.add(new ReportField("Run ID", run.execution().path("runId").asText()));
            audit.add(new ReportField("Android process", run.evidence().pid()));
            phase.observation(new ReportObservation(run.scope() + " execution identity", audit, List.of(), List.of()));
        }
        publish(phase, run.title(), run.evidence().rows(), run.evidence().passed());
        boolean passed = run.evidence().passed();
        for (var frame : run.frames()) passed &= publishBoundFrame(phase, run, frame, captures, nativeDigest, attach);
        return passed;
    }

    private static boolean publishBoundFrame(atf.reporting.contract.AtfReportPorts.Phase phase, BoundRun run, Frame frame,
                                             Set<String> acceptedCaptures, boolean nativeDigest, boolean attach)
            throws IOException, java.security.NoSuchAlgorithmException {
        Path path = run.receipt().resolveSibling(frame.file());
        byte[] image = isPng(path) ? Files.readAllBytes(path) : null;
        String captureHash = image == null ? "" : sha256(image);
        String nativeKey = frame.file().substring(0, frame.file().length() - ".png".length());
        boolean matchesNative = !nativeDigest || markerValues(run.nativeMarkers(), "INSTRUMENTATION_RESULT: capture_" + nativeKey + "=")
                .equals(List.of(captureHash));
        boolean matchesCapture = image != null && matchesNative && captureHash.equals(run.execution().path("captureSha256").path(frame.file()).asText());
        if (!matchesCapture || !acceptedCaptures.add(captureHash)) {
            String reason = image == null ? "Missing or invalid PNG: " : matchesCapture ? "Capture repeated: " : "Capture SHA-256 mismatch: ";
            publish(phase, frame.title(), List.of(receiptFailure(run.scope(), reason + frame.file())), false);
            return false;
        }
        if (!attach) return false;
        try (var action = phase.startRequest("Android instrumentation", "UI", frame.title())) {
            action.detail("Capture SHA-256", captureHash);
            action.screenshot(frame.title(), Base64.getEncoder().encodeToString(image));
            action.complete(AtfReportStatus.PASS);
        }
        return true;
    }

    @Test void publishManualAnalysisSanity() throws Exception {
        publishManualAnalysis(Path.of(System.getProperty("chess.android.receipt")));
    }

    @Test void publishMatchSlotsSanity() throws Exception {
        publishMatchSlots(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishMatchSlots(Path receipt) throws Exception {
        publishMatchMenu(receipt, "match-slots", "Find and resume a saved game", List.of(
                new MenuCheckpoint("WHEN the player scrolls to the last saved game", DATED_LAST_ROW, "saved-games.png"),
                new MenuCheckpoint("THEN selecting it restores the recorded position",
                        "Selecting a saved row restores the recorded position", "match-slots.png")));
    }

    @Test void publishMatchArchiveSanity() throws Exception {
        publishMatchMenu(Path.of(System.getProperty("chess.android.receipt")), "match-archive", "Navigate and reopen finished games", List.of(
                new MenuCheckpoint("WHEN the player scrolls to the last finished game", DATED_LAST_ROW, "finished-games.png"),
                new MenuCheckpoint("THEN selecting it opens the result",
                        "Selecting a finished row opens its result and review entry point", "match-archive.png")));
    }

    private static void publishMatchMenu(Path receipt, String scope, String scenarioTitle, List<MenuCheckpoint> checkpoints) throws Exception {
        Path report = receipt.resolveSibling("android-" + scope + ".html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var evidence = readPhase(receipt, scope, checkpoints.size());
        String receiptHash = Files.isRegularFile(receipt) ? sha256(Files.readAllBytes(receipt)) : "";
        var execution = readExecution(receipt.resolveSibling("execution.json"));
        var identity = menuIdentity(execution, scope, receiptHash, checkpoints.size());
        boolean complete = evidence.passed() && !identity.isEmpty();
        var acceptedCaptures = new HashSet<String>();
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", scenarioTitle, List.of("e2e", "android", scope));
             var phase = scenario.startPhase("TEST BODY")) {
            var audit = new ArrayList<>(identity);
            audit.add(new ReportField("Android process", evidence.pid()));
            audit.add(new ReportField("Receipt SHA-256", receiptHash));
            phase.observation(new ReportObservation("Android execution identity", audit, List.of(), List.of()));
            if (!complete) publish(phase, "Native execution evidence", evidence.passed()
                    ? List.of(receiptFailure(scope, "Missing or mismatched execution identity")) : evidence.rows(), false);
            for (var checkpoint : checkpoints) {
                var row = evidence.rows().stream().filter(assertion -> assertion.name().equals(checkpoint.assertion())).findFirst()
                        .orElseGet(() -> receiptFailure(scope, "Missing check: " + checkpoint.assertion()));
                Path frame = receipt.resolveSibling(checkpoint.frame());
                byte[] image = isPng(frame) ? Files.readAllBytes(frame) : null;
                String captureHash = image == null ? "" : sha256(image);
                boolean matchesCapture = image != null && captureHash.equals(execution.path("captureSha256").path(checkpoint.frame()).asText());
                boolean uniqueCapture = matchesCapture && acceptedCaptures.add(captureHash);
                var assertions = new ArrayList<>(List.of(row));
                if (image == null) assertions.add(receiptFailure(scope, "Missing or invalid PNG: " + checkpoint.frame()));
                else if (!matchesCapture) assertions.add(receiptFailure(scope, "Capture SHA-256 mismatch: " + checkpoint.frame()));
                else if (!uniqueCapture) assertions.add(receiptFailure(scope, "Capture repeated across checkpoints: " + checkpoint.frame()));
                var status = assertions.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
                try (var action = phase.startRequest(checkpoint.action(), "ANDROID", scope);
                     var validation = action.startValidation("Native assertions")) {
                    if (uniqueCapture) {
                        action.detail("Capture SHA-256", captureHash);
                        validation.screenshot(checkpoint.frame(), Base64.getEncoder().encodeToString(image));
                    }
                    validation.assertions(assertions);
                    validation.complete(status);
                    action.complete(status);
                }
                complete &= status == AtfReportStatus.PASS;
            }
            phase.complete(complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            scenario.complete(complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android " + scope + " evidence: " + receipt);
    }

    private static JsonNode readExecution(Path execution) {
        try {
            var identity = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(execution.toFile());
            return identity == null ? MissingNode.getInstance() : identity;
        } catch (IOException invalid) {
            return MissingNode.getInstance();
        }
    }

    private static List<ReportField> menuIdentity(JsonNode identity, String scope, String receiptHash, int count) {
        String variant = identity.path("variant").asText();
        String commit = identity.path("sourceCommit").asText();
        String apk = identity.path("apkSha256").asText();
        String api = identity.path("api").asText();
        String device = identity.path("device").asText();
        if (!identity.path("status").asText().equals("PASS") || !identity.path("scope").asText().equals(scope)
                || !receiptHash.matches("[a-f0-9]{64}") || !receiptHash.equals(identity.path("receiptSha256").asText())
                || !identity.path("assertions").isIntegralNumber() || identity.path("assertions").asLong() != count || !apk.matches("[a-f0-9]{64}")
                || !api.matches("[0-9]+") || device.isBlank()
                || !(variant.equals("sanity") || variant.equals("release") && commit.matches("[a-f0-9]{40}"))) return List.of();
        return List.of(new ReportField("Phase", scope), new ReportField("Android API", api), new ReportField("Device", device),
                new ReportField("Variant", variant), new ReportField("Source commit", commit), new ReportField("APK SHA-256", apk));
    }

    private static String sha256(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final String PREVIEW_REPLY = "Next shows the calculated reply and Previous returns to the first move";
    private static final String PREVIEW_FRAME = "The suggested line and its navigation are visible with a fixed preview board";

    @Test void publishTacticsExplanationSanity() throws Exception {
        publishTacticsExplanation(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishTacticsExplanation(Path receipt) throws Exception {
        var normal = "Tactic 002 opens the normal explanation on its final move";
        var defense = "The defense study replays Kg8 and shows the material on the board";
        var run = boundRun("tactics-explanation", "THEN both explanations show the recorded moves and keep the awards", receipt, 6,
                List.of(normal, "The normal explanation rewinds and allows forward replay",
                        "Closing the normal explanation preserves the solved tactic and points",
                        "Tactic 043 opens the defense study on the accepted key", defense,
                        "Closing the defense study preserves the solved tactic and points"), "tactics explanation",
                List.of(new Frame("tactics-explanation.png", normal), new Frame("tactics-defense.png", defense)));
        publishNativeRuns("tactics-explanation", "Open both tactic explanations from the puzzle board", receipt, List.of(run), null);
    }

    @Test void publishTrainingFinishSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        var explanation = "The puzzle explains Qxb2 and the delayed capture after Rd8+";
        var replay = "The replay shows Qxb2 on its third ply and +5 material";
        var run = boundRun("training-finish", "THEN the replay preserves stage six and only Qxb2 earns points", receipt, 7,
                List.of("Stage six shows the immediate material objective", "Bxf7+ leaves the exercise open", explanation, replay,
                        "Studying the alternative preserves stage six and its unawarded score",
                        "Playing Qxb2 solves the puzzle and earns exactly ten points",
                        "The explanation remains available after reopening the solved puzzle"), "training finish",
                List.of(new Frame("training-finish.png", explanation), new Frame("training-finish-line.png", replay)));
        publishNativeRuns("training-finish", "Explain the last move of training-069", receipt, List.of(run), null);
    }

    @Test void publishPuzzleMateReplaySanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        var label = "The Romanian bulb names its next action at each step and fits in a short window";
        var two = "Mate in two opens after the saved defense and reaches the actual mating square";
        var three = "Mate in three replays the second defense and can step back without moving the board";
        var run = boundRun("puzzle-mate-replay", "THEN both replays preserve the puzzle and rewards", receipt, 5,
                List.of(label, two, "Closing the two-move replay preserves the unsolved puzzle, stage and rewards",
                        three, "Closing the three-move replay keeps the solved puzzle and its award"), "mate replay",
                List.of(new Frame("puzzle-mate-replay.png", label), new Frame("puzzle-mate-two.png", two),
                        new Frame("puzzle-mate-three.png", three)));
        publishNativeRuns("puzzle-mate-replay", "Follow the puzzle guidance and replay both mating lines", receipt, List.of(run), null);
    }

    @Test void publishPuzzleDangerSanity() throws Exception {
        Path receipt = Path.of(System.getProperty("chess.android.receipt"));
        var mate = "Requesting danger marks the mated king and announces its square";
        var capture = "The retained capture marks h1 with five pawn units after any reply";
        var run = boundRun("puzzle-danger", "THEN the board uses the available width and the mark follows only the requested evidence", receipt, 7,
                List.of("Puzzle and replay use the full content width on the same screen as a normal game",
                        mate, "Rewinding clears the marker, reason and control",
                        "Returning to mate keeps the marker off until requested again",
                        "Requesting and navigating danger preserves the solved ID and award", capture,
                        "Tapping the control again hides the capture reason"), "puzzle danger",
                List.of(new Frame("puzzle-danger-mate.png", mate), new Frame("puzzle-danger-capture.png", capture)));
        publishNativeRuns("puzzle-danger", "Reveal danger in the puzzle replay", receipt, List.of(run), null);
    }

    @Test void publishMatchAnalysisSanity() throws Exception {
        publishMatchAnalysis(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishMatchAnalysis(Path receipt) throws Exception {
        var prepare = matchPreparation(receipt);
        var restore = boundRun("analysis-restore", "THEN restart at the live position with the hint hidden",
                receipt.resolveSibling("analysis-restore").resolve("analysis-restore.txt"), 2,
                List.of("The reopened match analyzes its live position with the hint hidden"), "match analysis", List.of());
        publishNativeRuns("match-analysis", "Explore a recommendation and reopen the saved game", receipt, List.of(prepare, restore), null);
    }

    private static BoundRun matchPreparation(Path receipt) throws Exception {
        return boundRun("analysis-prepare", "GIVEN a saved game, explore the recommendation from history", receipt, 5,
                List.of(PREVIEW_REPLY, PREVIEW_FRAME, "Returning to history preserves the selected move and the saved game"),
                "match analysis", List.of(new Frame("match-analysis.png", "Suggested reply in match history")));
    }

    static void publishManualAnalysis(Path receipt) throws Exception {
        var manual = boundRun("manual-analysis", "WHEN exploring the recommended line on the manual board", receipt, 5,
                List.of(PREVIEW_REPLY, PREVIEW_FRAME, "Returning to the study lets the player apply the hint without saving a match"),
                "manual analysis", List.of(new Frame("manual-analysis.png", "Suggested reply on the manual board")));
        Path combined = receipt.toAbsolutePath().getParent().resolveSibling("analysis-prepare.txt");
        var anchor = Files.exists(combined) ? matchPreparation(combined) : null;
        publishNativeRuns("manual-analysis", "Explore a line and play the hint in a manual study", receipt, List.of(manual), anchor);
    }

    private static void publishNativeRuns(String scope, String scenarioTitle, Path receipt, List<BoundRun> runs, BoundRun anchor) throws Exception {
        Path report = receipt.resolveSibling("android-" + scope + ".html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        boolean sameExecution = sameExecution(runs) && (anchor == null || anchor.evidence().passed() && sameExecution(List.of(anchor, runs.getFirst())));
        var pids = runs.stream().map(run -> run.evidence().pid()).toList();
        boolean restarted = runs.size() == 1 || !pids.contains("") && pids.stream().distinct().count() == runs.size();
        boolean complete = sameExecution && restarted;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", scenarioTitle, List.of("e2e", "android", scope));
             var phase = scenario.startPhase("TEST BODY")) {
            var checks = new ArrayList<AtfAssertion>();
            checks.add(new AtfAssertion("Native run identity", "Matching native run ID, source, APK, device and receipt hash",
                    sameExecution ? "Matching native run ID, source, APK, device and receipt hash" : "Missing or mismatched phase run identity"
                            + (anchor == null ? "" : " · match run " + anchor.execution().path("runId").asText()),
                    sameExecution));
            if (runs.size() > 1) {
                checks.add(new AtfAssertion("Instrumentation process IDs", "Two distinct Android PIDs", String.join(" → ", pids), restarted));
            }
            publish(phase, "Verify native provenance", checks, complete);
            var acceptedCaptures = new HashSet<String>();
            for (var run : runs) complete &= publishBoundRun(phase, run, acceptedCaptures, true, sameExecution);
            var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android " + scope + " evidence: " + receipt);
    }
    @Test void publishBoardSanity() throws Exception {
        publishBoardSanity(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishBoardSanity(Path receipt) throws Exception {
        Path report = receipt.resolveSibling("android-board-sanity.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var prepare = readPhase(receipt, "prepare", 46);
        var restore = readQuestRestore(receipt.resolveSibling("restore.txt"));
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
            publish(phase, "Resume pending quests and restore tactics, training and mate progress", restore.rows(), restore.passed());
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

    static PhaseEvidence readGameReviewPhase(Path receipt) throws IOException {
        return requireChecks(readPhase(receipt, "game-review", 23), "game-review", List.of(LONG_GRAPH_CHECK), "long-game graph");
    }

    static PhaseEvidence readOpeningPhase(Path receipt) throws IOException {
        return requireChecks(readPhase(receipt, "openings", 26), "openings", OPENING_GEOMETRY_CHECKS, "board geometry");
    }

    static PhaseEvidence readQuestRestore(Path receipt) throws IOException {
        return requireChecks(readPhase(receipt, "restore", 26), "restore", QUEST_RESTART_CHECKS, "chapter restart");
    }

    private static PhaseEvidence requireChecks(PhaseEvidence evidence, String phase, List<String> names, String subject) {
        if (!evidence.passed()) return evidence;
        var missing = missingChecks(evidence.rows(), names);
        if (missing.isEmpty()) return evidence;
        var rows = new ArrayList<>(evidence.rows());
        rows.add(receiptFailure(phase, "Missing or duplicate " + subject + " checks: " + missing));
        return new PhaseEvidence(evidence.pid(), rows, false);
    }

    private static List<String> missingChecks(List<AtfAssertion> rows, List<String> names) {
        return names.stream().filter(name -> rows.stream().filter(row -> row.name().equals(name)).count() != 1).toList();
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
