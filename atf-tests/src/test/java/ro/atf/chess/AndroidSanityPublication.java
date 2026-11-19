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
    private static final List<String> COMPACT_REPLAY_CHECKS = List.of(
            "Compact replay uses the requested locale and enlarged Android display profile",
            "Opening Theory shows three readable choices, a square board and fixed navigation",
            "Opening Theory replays the full path, exposes its last explanation and returns to the same position",
            "Game Review shows both readable branches, a square board and fixed navigation",
            "Game Review replays both branches and exposes the complete explanation",
            "Closing the enlarged replay returns to the archived move without changing the game");
    private static final List<String> OPENING_GEOMETRY_CHECKS = List.of(
            "Next preserves the board and its corner squares when Resume lesson appears",
            "Next preserves the board and its corner squares when Lesson hint returns");
    private static final List<String> BOARD_PREPARE_CHECKS = List.of(
            "Home exposes play and profile, and Puzzles groups six playable chapters",
            "The opening viewport renders the puzzle chapters",
            "The compact probe runs with Romanian resources and font scale 1.3",
            "The last Romanian menu entry is reachable in a short window",
            "The Romanian board, active color and controls fit with the enlarged move hint",
            "Accessibility traversal follows color, piece and destination after flipping, with old links cleared",
            "The accessible bulb fits below the board and Next starts locked",
            "The authored tactical defense advances once without solving",
            "The authored tactical defense advances once without solving",
            "A tactics solve renders its result and unlocks Next",
            "Tactics renders the streak badge with the localized accessible name",
            "The tactic explanation opens on the final board and announces the actual net gain",
            "The explanation lets the player rewind to the previous position",
            "The previous position enables forward replay",
            "The defense study starts just after the key and leaves the playable result separate",
            "The read-only defense reply is available",
            "The replay advances to the actual defensive move and announces the board material",
            "The individual lesson is displayed in the app language",
            "A separate Stockfish estimate is identified as analysis after the key",
            "The end of the study announces both the current loss and the worse immediate reply",
            "The puzzle menu displays the saved chapter and overall totals",
            "The authored tactical defense advances once without solving",
            "64 visible cells",
            "Square board",
            "The active color stays visible above the board",
            "A compact layout keeps the turn, square board and puzzle controls visible",
            "The first of 500 quests is shown",
            "Next is disabled on the first unsolved quest",
            "Black is to move with Black's side at the bottom",
            "Flipping rotates the board without changing the active color or moving its label",
            "Fresh storage shows zero solved quests",
            "The collection opens with a zero meter and goal of five",
            "The simple hint exposes only the black queen on d8 and keeps the board and controls visible",
            "The simple hint renders a mint outline and no arrow",
            "The full hint reveals d8 to h4 and leaves the position playable",
            "The full hint renders an arrow across the board",
            "The solution names the checking queen and king square",
            "The solution explains why the king cannot escape to f2",
            "Revealing the solution does not mark a solve",
            "Wrong legal move keeps the puzzle open",
            "Rejected attempt leaves the pawn on e5",
            "The first mating move solves the quest",
            "Flipped lower-right cell",
            "The second quest is shown",
            "A second quest can be solved independently",
            "The application shows two solves",
            "The third quest is the current selection",
            "The selected third quest is unsolved",
            "Mate in two opens with its own 300-quest counter",
            "A wrong opening keeps White to move and asks for forced mate in two",
            "The opening hint shows the unique first move",
            "The player's move leaves the opponent to move and locks input during the pause",
            "The rendered defense follows the player's move by at least 650 ms",
            "The opponent replies and the first move does not count as a solve",
            "The mate-in-two board highlights the opponent's defense",
            "Mate in three opens with 200 puzzles and White to move",
            "The first defense leaves two moves and keeps White to move",
            "The second defense is pending when the board closes");
    private static final List<String> QUEST_RESTART_CHECKS = List.of(
            "A real process restart keeps the tactics chapter position and its two awards",
            "Training reopens its second unsolved puzzle after process restart");

    private record Frame(String file, String title) { }
    static final List<String> SOURCE_REPLAY_CHECKS = sourceReplayChecks();
    static final List<String> SIGNED_SOURCE_REPLAY_CHECKS = SOURCE_REPLAY_CHECKS.stream().flatMap(label -> {
        if (!label.endsWith("return restores the source rectangle")) return java.util.stream.Stream.of(label);
        return java.util.stream.Stream.of(label, label.substring(0, label.indexOf(' ')) + " Back restores the source window and board");
    }).toList();

    private static List<String> sourceReplayChecks() {
        var checks = new ArrayList<String>();
        checks.add("AI hint reveal preserves the source board rectangle");
        checks.add("The AI board and fixed controls fit without page scrolling");
        for (String owner : List.of("calculated", "theory")) {
            if (owner.equals("theory")) checks.add("Study hint reveal preserves the source board rectangle");
            checks.add(owner + " replay keeps the measured source side");
            checks.add(owner + " replay renders all ranks with accessible fixed controls");
            for (String action : List.of("Next", "Previous", "branch")) checks.add(owner + " " + action + " keeps the board and control rectangles");
            checks.add(owner + " return restores the source rectangle");
        }
        checks.add("Mating replay keeps the measured source side");
        checks.add("Mating replay renders all ranks with accessible fixed controls");
        checks.add("Mating Next keeps the board and control rectangles");
        checks.add("Mating Previous keeps the board and control rectangles");
        checks.add("Mating return restores the source rectangle");
        return List.copyOf(checks);
    }

    static PhaseEvidence readSourceReplayPhase(Path receipt, String profile, String locale) throws IOException {
        return readSourceReplayPhase(receipt, profile, locale, false);
    }

    static PhaseEvidence readSourceReplayPhase(Path receipt, String profile, String locale, boolean signed) throws IOException {
        var required = signed ? SIGNED_SOURCE_REPLAY_CHECKS : SOURCE_REPLAY_CHECKS;
        var evidence = readPhase(receipt, signed ? "source-sized-replay-enlarged" : "source-sized-replay", required.size(), required, null);
        var markers = Files.isRegularFile(receipt) ? Files.readAllLines(receipt) : List.<String>of();
        var observed = markerValues(markers, "INSTRUMENTATION_RESULT: replayProfile=");
        var display = ro.atf.chess.capture.ReplayViewportProfile.of(profile, signed);
        var expected = List.of(display.marker(locale));
        var rotation = markerValues(markers, "INSTRUMENTATION_RESULT: replayRotation=");
        boolean rotated = !signed || rotation.equals(List.of(Integer.toString(display.rotation())));
        if (observed.equals(expected) && rotated) return evidence;
        var rows = new ArrayList<>(evidence.rows());
        rows.add(new AtfAssertion("Native source-sized profile", expected + (signed ? " rotation=" + display.rotation() : ""),
                observed + (signed ? " rotation=" + rotation : ""), false));
        return new PhaseEvidence(evidence.pid(), rows, false);
    }

    @Test void publishSourceReplaySanity() throws Exception {
        publishSourceReplaySanity(false, false);
    }

    @Test void publishSignedSourceReplaySanity() throws Exception {
        publishSourceReplaySanity(true, true);
    }

    @Test void publishEnlargedSourceReplaySanity() throws Exception {
        publishSourceReplaySanity(true, false);
    }

    private void publishSourceReplaySanity(boolean enlarged, boolean signed) throws Exception {
        Path root = Path.of(System.getProperty("chess.android.receipt"));
        String scope = enlarged ? "source-sized-replay-enlarged" : "source-sized-replay";
        var required = enlarged ? SIGNED_SOURCE_REPLAY_CHECKS : SOURCE_REPLAY_CHECKS;
        var runs = new ArrayList<BoundRun>();
        for (String profile : List.of("360x640", "740x420", "1280x900")) for (String locale : List.of("en", "ro")) {
            Path receipt = root.resolve(profile + "-" + locale).resolve(scope + ".txt");
            var run = boundRun(scope, "THEN source-sized replay fits " + profile + " in " + locale,
                    receipt, required.size(), required, "source-sized replay", List.of(
                            new Frame("source-ai.png", "AI hint with fixed source board"),
                            new Frame("source-calculated.png", "Calculated line"), new Frame("source-theory.png", "Analysis hint"),
                            new Frame("source-mating.png", "Mating line")));
            var evidence = readSourceReplayPhase(receipt, profile, locale, enlarged);
            var identity = new ArrayList<>(run.identity());
            identity.add(new ReportField("Logical profile", profile + " dp · " + locale));
            identity.add(new ReportField("Native profile", markerValues(run.nativeMarkers(), "INSTRUMENTATION_RESULT: replayProfile=").toString()));
            identity.add(new ReportField("Physical rotation", markerValues(run.nativeMarkers(), "INSTRUMENTATION_RESULT: replayRotation=").toString()));
            identity.add(new ReportField("Qualification scope", signed ? "Signed release APK with matching instrumentation signer and installed bytes"
                    : "Development sanity only; not a signed release qualification"));
            identity.add(new ReportField("Timing scope", "Report durations measure receipt validation/publication; native action latency was not measured by this probe."));
            var projection = markerValues(run.nativeMarkers(), "INSTRUMENTATION_RESULT: framebufferProjection=");
            if (projection.size() > 1 || run.execution().path("api").asText().equals("28") && projection.size() != 1) identity.clear();
            if (!identity.isEmpty() && !projection.isEmpty()) identity.add(new ReportField("Verified compositor projection", projection.toString()));
            if (run.identity().isEmpty()) identity.clear();
            if (signed && !signedSourceIdentity(run.execution())) identity.clear();
            runs.add(new BoundRun(run.scope(), run.title(), receipt, evidence, run.execution(), identity, run.frames(), run.nativeMarkers()));
        }
        publishNativeRuns(scope, "Keep source-sized native boards and fixed controls across replay navigation",
                Path.of(System.getProperty("chess.extent", root.resolve("android-" + scope + ".html").toString())), runs, null, true);
    }

    static boolean signedSourceIdentity(JsonNode execution) {
        String certificate = execution.path("signingCertificateSha256").asText();
        return execution.path("variant").asText().equals("release") && certificate.matches("[a-f0-9]{64}")
                && execution.path("apkSha256").asText().matches("[a-f0-9]{64}")
                && execution.path("sourceCommit").asText().matches("[a-f0-9]{40}")
                && execution.path("testCertificateSha256").asText().equals(certificate)
                && execution.path("installedApkSha256").asText().equals(execution.path("apkSha256").asText());
    }

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
                complete &= publishBoundRun(phase, run, acceptedCaptures, false, true, true);
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
        if ("puzzle-ux".equals(scope) && !identity.isEmpty()) {
            var display = markerValues(markers, "INSTRUMENTATION_RESULT: puzzleDisplay=");
            if (display.size() != 1 || !display.getFirst().matches("[1-9][0-9]*x[1-9][0-9]*, [1-9][0-9]* dpi, font 1\\.3, language (ro|en)")) {
                identity = List.of();
            } else {
                identity = new ArrayList<>(identity);
                identity.add(new ReportField("Puzzle display profile", display.getFirst()));
            }
        }
        return new BoundRun(scope, title, receipt, evidence, execution, identity, frames, markers);
    }

    private static boolean sameExecution(List<BoundRun> runs) {
        var first = runs.getFirst().execution();
        return runs.stream().allMatch(run -> !run.identity().isEmpty()
                && List.of("runId", "apkSha256", "sourceCommit", "variant", "device", "api").stream()
                        .allMatch(field -> run.execution().path(field).equals(first.path(field))));
    }

    private static boolean publishBoundRun(atf.reporting.contract.AtfReportPorts.Phase phase, BoundRun run,
                                           Set<String> captures, boolean nativeDigest, boolean attach, boolean showRunId)
            throws IOException, java.security.NoSuchAlgorithmException {
        if (!run.identity().isEmpty()) {
            var audit = new ArrayList<>(run.identity());
            if (showRunId) audit.add(new ReportField("Run ID", run.execution().path("runId").asText()));
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

    @Test void publishActiveMatchSanity() throws Exception {
        publishActiveMatch(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishActiveMatch(Path receipt) throws Exception {
        var checkpoint = "The active slot and both checkpoints match the saved fixtures";
        var initialPosition = "The native board renders the saved position · rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2";
        var prepare = boundRun("active-match-prepare", "GIVEN two saved matches and the active match visible", receipt, 2,
                List.of(checkpoint, initialPosition), "active match", List.of());
        var restored = boundRun("active-match-restore", "WHEN the player relaunches and plays the next legal move",
                receipt.resolveSibling("active-match-restore").resolve("active-match-restore.txt"), 4,
                List.of(checkpoint, initialPosition, "Playing Nf3 saves one move to the active slot and leaves the other slot intact",
                        "The native board renders the new position · rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"),
                "active match", List.of(new Frame("active-match-restore.png", "Slot 3 restored after process death"),
                        new Frame("active-match-next.png", "Slot 3 after the player moves Nf3")));
        publishNativeRuns("active-match", "Restore the same active match and continue it", receipt, List.of(prepare, restored), null);
    }

    @Test void publishPendingBotSanity() throws Exception {
        publishPendingBot(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishPendingBot(Path receipt) throws Exception {
        var pending = "The pending checkpoint and both slots match before launch";
        var prepare = boundRun("pending-bot-prepare", "GIVEN the active match has one bot reply due", receipt, 2,
                List.of("The human move leaves one automatic bot reply due", pending), "pending bot", List.of());
        var restored = boundRun("pending-bot-restore", "WHEN the app restarts and handles the pending reply",
                receipt.resolveSibling("pending-bot-restore").resolve("pending-bot-restore.txt"), 5,
                List.of(pending, "Exactly one legal bot response extends the original history",
                        "The original level, human side, orientation and automatic play are restored",
                        "The settled checkpoint remains unchanged after the bot delay",
                        "The native board renders the bot reply and the restored human turn"),
                "pending bot", List.of(new Frame("pending-bot-restore.png", "The original match after one bot reply")));
        publishNativeRuns("pending-bot", "Resume a pending computer turn after process death", receipt, List.of(prepare, restored), null);
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

    @Test void publishPuzzleUxSanity() throws Exception {
        publishPuzzleUx(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishPuzzleUx(Path receipt) throws Exception {
        var frames = List.of(
                new Frame("puzzle-ux-mate-one.png", "Mate in 1 keeps the board fixed with puzzle options open"),
                new Frame("puzzle-ux-mate-two.png", "Mate in 2 keeps the board fixed with puzzle options open"),
                new Frame("puzzle-ux-mate-three.png", "Mate in 3 keeps the board fixed with puzzle options open"),
                new Frame("puzzle-ux-tactics.png", "Tactics keeps the board fixed with puzzle options open"),
                new Frame("puzzle-ux-training.png", "Tactical training keeps the board fixed with puzzle options open"),
                new Frame("puzzle-ux-intermezzo.png", "Intermezzo keeps the board fixed with puzzle options open"));
        var run = boundRun("puzzle-ux", "THEN each chapter keeps the same board with puzzle options open", receipt, frames.size(),
                frames.stream().map(Frame::title).toList(), "puzzle navigation", frames);
        publishNativeRuns("puzzle-ux", "Open and close the shared puzzle controls in six chapters", receipt, List.of(run), null);
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

    private static final String COMPACT_GEOMETRY = "INSTRUMENTATION_RESULT: compactAiLayoutVersion=";
    private static final String COMPACT_MAX_BOARD = "The compact game uses the largest available square with the meter and history controls visible";
    private static final String COMPACT_ARROWS = "Back and Next preserve the board, evaluation bar and bulb while selecting the correct move";
    private static final String COMPACT_OPPONENT = "The opponent name and level are fully visible before any captures";

    @Test void publishCompactMatchAnalysisSanity() throws Exception {
        Path romanianReceipt = Path.of(System.getProperty("chess.android.receipt")).toAbsolutePath();
        Path root = romanianReceipt.getParent().getParent();
        var geometryVersion = markerValues(Files.readAllLines(romanianReceipt), COMPACT_GEOMETRY);
        var runs = new ArrayList<BoundRun>();
        for (String locale : List.of("ro", "en")) {
            String prepare = "analysis-compact-prepare-" + locale;
            String restore = "analysis-compact-restore-" + locale;
            var prepared = compactMatchAnalysisRun(root.resolve(prepare).resolve(prepare + ".txt"), prepare, locale, null, geometryVersion);
            runs.add(prepared);
            runs.add(compactMatchAnalysisRun(root.resolve(restore).resolve(restore + ".txt"), restore, locale, prepared, geometryVersion));
        }
        publishNativeRuns("analysis-compact", "Replay the historical recommendation and resume the live game in RO and EN",
                root.resolve("android-analysis-compact.html"), runs, null, true);
    }

    private static BoundRun compactMatchAnalysisRun(Path receipt, String scope, String locale, BoundRun prepared,
                                                    List<String> geometryVersion) throws Exception {
        boolean restore = prepared != null;
        boolean measuresOpponent = geometryVersion.equals(List.of("2"));
        boolean measuresGeometry = measuresOpponent || geometryVersion.equals(List.of("1"));
        String display = "Compact replay uses the requested locale and enlarged Android display profile";
        String analysis = "The localized score, left evaluation bar and bulb are rendered at the displayed position";
        String stage = restore ? "Restart at the live position with the hint hidden" : "Open and step the historical recommendation";
        var required = new ArrayList<>(restore
                ? List.of(display, "The enabled setting and two moves survive a process restart",
                        "The reopened match analyzes its live position with the hint hidden", analysis)
                : List.of(display, "The saved game opens analysis at the selected historical position",
                        "The historical hint marks its own legal recommendation", analysis, PREVIEW_REPLY, PREVIEW_FRAME,
                        "Returning to history preserves the selected move and the saved game"));
        if (measuresGeometry) {
            required.add(COMPACT_MAX_BOARD);
            if (!restore) required.add(COMPACT_ARROWS);
        }
        if (measuresOpponent) required.add(COMPACT_OPPONENT);
        var frames = restore ? List.of(new Frame("match-restored.png", "Restored live board and hidden bulb"))
                : List.of(new Frame("match-history-analysis.png", "Historical board, score, meter and bulb"),
                        new Frame("match-analysis.png", "Calculated variation from the historical position"));
        var run = boundRun(scope, locale.toUpperCase(java.util.Locale.ROOT) + " · " + stage, receipt, required.size(),
                required, "compact match analysis", frames);
        var markers = run.nativeMarkers();
        var observed = List.of(replayValues(markers, "Locale"), replayValues(markers, "FontScale"), replayValues(markers, "Width"),
                replayValues(markers, "Height"), replayValues(markers, "DensityDpi"));
        var expected = List.of(List.of(locale), List.of("1.3"), List.of("720"), List.of("1280"), List.of("320"));
        var cursor = markerValues(markers, "INSTRUMENTATION_RESULT: matchPly=");
        var checkpoint = markerValues(markers, "INSTRUMENTATION_RESULT: matchCheckpoint=");
        String expectedPly = restore ? "2" : "1";
        boolean match = observed.equals(expected) && cursor.equals(List.of(expectedPly))
                && checkpoint.equals(List.of("2|analysis|e2e4 e7e5"));
        var rows = new ArrayList<>(run.evidence().rows());
        var versions = markerValues(markers, COMPACT_GEOMETRY);
        boolean schema = (geometryVersion.isEmpty() || measuresGeometry) && versions.equals(geometryVersion);
        rows.add(new AtfAssertion("Match analysis measurement format", geometryVersion.toString(), versions.toString(), schema));
        boolean geometry = schema && (!measuresGeometry || appendCompactMatchGeometry(rows, markers, prepared));
        if (measuresOpponent) {
            String expectedName = (restore ? "" : "●  ") + "Alex · AI Basic";
            var expectedLabel = List.of(expectedName, "0");
            var observedLabel = List.of(compactMarker(markers, "compactOpponentText"), compactMarker(markers, "compactOpponentEllipsis"));
            boolean label = expectedLabel.equals(observedLabel);
            rows.add(new AtfAssertion("Opponent label without ellipsis", expectedLabel.toString(), observedLabel.toString(), label));
            geometry &= label;
        }
        rows.add(new AtfAssertion("Native display and cursor",
                "720×1280 · 320 dpi · font 1.3 · " + locale + " · ply " + expectedPly + " · e2e4 e7e5",
                observed + " · ply " + cursor + " · " + checkpoint, match));
        var identity = new ArrayList<>(run.identity());
        for (String field : List.of("matchScore", "evaluationCp", "evaluationWhiteShare", "hintMove", "previewLine",
                "matchBoardBounds", "evaluationBarBounds", "bulbBounds", "previewBoardBounds",
                "compactBoardBudgetPx", "compactBoardSquarePx", "compactArrowGeometry", "compactOpponentText", "compactOpponentEllipsis")) {
            var values = markerValues(markers, "INSTRUMENTATION_RESULT: " + field + "=");
            if (values.size() == 1) identity.add(new ReportField(field, values.getFirst()));
        }
        var evidence = new PhaseEvidence(run.evidence().pid(), rows, run.evidence().passed() && match && geometry);
        return new BoundRun(run.scope(), run.title(), receipt, evidence, run.execution(), identity, run.frames(), markers);
    }

    private static boolean appendCompactMatchGeometry(List<AtfAssertion> rows, List<String> markers, BoundRun prepared) {
        var budget = compactMarker(markers, "compactBoardBudgetPx");
        var board = compactMarker(markers, "compactBoardSquarePx");
        var bounds = compactMarker(markers, "compactMatchGeometry");
        var available = budget.split(" ");
        boolean square = budget.matches("[0-9]{1,4} [0-9]{1,4}") && board.matches("[0-9]{1,4}")
                && Integer.parseInt(board) > 524
                && Integer.parseInt(board) == Math.min(Integer.parseInt(available[0]), Integer.parseInt(available[1]));
        rows.add(new AtfAssertion("Board fits the limiting measured dimension",
                "Square equals min(usable width, usable height) and exceeds 524 px", board + " px within " + budget, square));
        boolean same;
        if (prepared == null) {
            var steps = compactMarker(markers, "compactArrowGeometry");
            same = !bounds.isBlank() && steps.equals(String.join(" / ", bounds, bounds, bounds));
            rows.add(new AtfAssertion("Board, meter and controls at Back, Next, Back", bounds, steps, same));
        } else {
            var before = compactMarker(prepared.nativeMarkers(), "compactMatchGeometry");
            same = !before.isBlank() && before.equals(bounds);
            rows.add(new AtfAssertion("Board, meter and controls retain their coordinates after restart", before, bounds, same));
        }
        return square && same;
    }

    private static String compactMarker(List<String> markers, String name) {
        var values = markerValues(markers, "INSTRUMENTATION_RESULT: " + name + "=");
        return values.size() == 1 ? values.getFirst() : "";
    }

    private static BoundRun matchPreparation(Path receipt) throws Exception {
        return boundRun("analysis-prepare", "GIVEN a saved game, explore the recommendation from history", receipt, 5,
                List.of(PREVIEW_REPLY, PREVIEW_FRAME, "Returning to history preserves the selected move and the saved game"),
                "match analysis", List.of(new Frame("match-analysis.png", "Suggested reply in match history")));
    }

    static void publishManualAnalysis(Path receipt) throws Exception {
        var manual = boundRun("manual-analysis", "WHEN entering, leaving and returning to opening theory", receipt, 5,
                List.of("The manual hint marks a book move and explains its named opening",
                        "An off-book position shows the engine hint after theory ends",
                        "Returning to theory shows the opening label below the same board",
                        "Returning to the study lets the player apply the hint without saving a match"),
                "opening theory on the manual board", List.of(new Frame("manual-analysis.png", "Opening source below the manual board")));
        Path combined = receipt.toAbsolutePath().getParent().resolveSibling("analysis-prepare.txt");
        var anchor = Files.exists(combined) ? matchPreparation(combined) : null;
        publishNativeRuns("manual-analysis", "Study an opening and resume engine hints outside theory", receipt, List.of(manual), anchor);
    }

    @Test void publishReplayCompactSanity() throws Exception {
        publishReplayCompact(Path.of(System.getProperty("chess.android.receipt")));
    }

    static void publishReplayCompact(Path romanianReceipt) throws Exception {
        Path root = romanianReceipt.toAbsolutePath().getParent().getParent();
        var romanian = compactReplayRun(romanianReceipt, "ro", "Romanian");
        var english = compactReplayRun(root.resolve("replay-compact-en/replay-compact-en.txt"), "en", "English");
        publishNativeRuns("replay-compact", "Read and navigate both replay screens at enlarged font in RO and EN",
                root.resolve("android-replay-compact.html"), List.of(romanian, english), null, true);
    }

    private static BoundRun compactReplayRun(Path receipt, String locale, String language) throws Exception {
        String scope = "replay-compact-" + locale;
        var run = boundRun(scope, "Read and replay in " + language, receipt, COMPACT_REPLAY_CHECKS.size(), COMPACT_REPLAY_CHECKS,
                "compact replay", List.of(new Frame("opening-theory.png", language + " · Opening Theory fullscreen"),
                        new Frame("review-why.png", language + " · Game Review explanation fullscreen")));
        var markers = run.nativeMarkers();
        var observed = List.of(replayValues(markers, "Locale"), replayValues(markers, "FontScale"), replayValues(markers, "Width"),
                replayValues(markers, "Height"), replayValues(markers, "DensityDpi"));
        var expected = List.of(List.of(locale), List.of("1.3"), List.of("720"), List.of("1280"), List.of("320"));
        boolean profile = observed.equals(expected) && run.execution().path("variant").asText().equals("release");
        var rows = new ArrayList<>(run.evidence().rows());
        var identity = new ArrayList<>(run.identity());
        String display = "720x1280, 320 dpi, font 1.3, language " + locale;
        if (profile && !identity.isEmpty()) {
            identity.add(new ReportField("Replay display profile", display));
        } else if (!profile) {
            rows.add(new AtfAssertion("Native compact replay profile", display + " on a signed release",
                    "Locale, font, width, height and dpi " + observed + " · " + run.execution().path("variant").asText(), false));
            identity.clear();
        }
        var evidence = new PhaseEvidence(run.evidence().pid(), rows, run.evidence().passed() && profile);
        return new BoundRun(run.scope(), run.title(), receipt, evidence, run.execution(), identity, run.frames(), markers);
    }

    private static List<String> replayValues(List<String> markers, String parameter) {
        return markerValues(markers, "INSTRUMENTATION_RESULT: replay" + parameter + "=");
    }

    private static void publishNativeRuns(String scope, String scenarioTitle, Path receipt, List<BoundRun> runs, BoundRun anchor) throws Exception {
        publishNativeRuns(scope, scenarioTitle, receipt.resolveSibling("android-" + scope + ".html"), runs, anchor, false);
    }

    private static void publishNativeRuns(String scope, String scenarioTitle, Path report, List<BoundRun> runs, BoundRun anchor,
                                          boolean sharedRunId) throws Exception {
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
                checks.add(new AtfAssertion("Instrumentation process IDs", runs.size() + " distinct Android PIDs", String.join(" → ", pids), restarted));
            }
            publish(phase, "Verify native provenance", checks, complete);
            if (sameExecution && sharedRunId) {
                var identity = new ReportField("Run ID", runs.getFirst().execution().path("runId").asText());
                phase.observation(new ReportObservation("Shared native execution", List.of(identity), List.of(), List.of()));
            }
            var acceptedCaptures = new HashSet<String>();
            for (var run : runs) complete &= publishBoundRun(phase, run, acceptedCaptures, true, sameExecution, !sharedRunId);
            var status = complete ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Android Extent: " + report);
        org.junit.jupiter.api.Assertions.assertTrue(complete, "Missing or failed Android " + scope + " evidence: " + report);
    }
    @Test void publishBoardSanity() throws Exception {
        publishBoardSanity(Path.of(System.getProperty("chess.android.receipt")), System.getProperty("chess.android.runId", ""));
    }

    static void publishBoardSanity(Path receipt, String runId) throws Exception {
        Path report = receipt.resolveSibling("android-board-sanity.html");
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Android evidence folder");
        var prepare = readPhase(receipt, "prepare", BOARD_PREPARE_CHECKS.size(), BOARD_PREPARE_CHECKS, runId);
        var restore = requireChecks(readPhase(receipt.resolveSibling("restore.txt"), "restore", 26, List.of(), runId),
                "restore", QUEST_RESTART_CHECKS, "chapter restart");
        var matchPrepare = readPhase(receipt.resolveSibling("match-prepare.txt"), "match-prepare", 3, List.of(), runId);
        var matchRestore = readPhase(receipt.resolveSibling("match-restore.txt"), "match-restore", 15, List.of(), runId);
        var matchSettled = readPhase(receipt.resolveSibling("match-settled.txt"), "match-settled", 4, List.of(), runId);
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
            phase.observation(new ReportObservation("Android run identity", List.of(new ReportField("Expected run ID", runId)), List.of(), List.of()));
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
        return readPhase(receipt, phase, expectedChecks, List.of(), null);
    }

    private static PhaseEvidence readPhase(Path receipt, String phase, int expectedChecks, List<String> expectedTitles, String runId) throws IOException {
        if (!Files.isRegularFile(receipt)) return new PhaseEvidence("", List.of(receiptFailure(phase, "Missing native receipt")), false);
        var input = Files.readAllLines(receipt);
        var rows = new ArrayList<AtfAssertion>();
        var substitutions = new ArrayList<String>();
        Set<Integer> ids = new HashSet<>();
        for (String line : input) {
            var match = CHECK.matcher(line);
            if (!match.matches()) continue;
            var fields = match.group(2).split("\t", -1);
            int id = Integer.parseInt(match.group(1));
            if (fields.length == 4 && !fields[0].isBlank() && (fields[3].equals("true") || fields[3].equals("false")) && ids.add(id)) {
                rows.add(new AtfAssertion(fields[0], fields[1], fields[2], Boolean.parseBoolean(fields[3]) && fields[1].equals(fields[2])));
                if (!expectedTitles.isEmpty() && id >= 1 && id <= expectedTitles.size() && !expectedTitles.get(id - 1).equals(fields[0])) {
                    substitutions.add("check_" + match.group(1) + " expected " + expectedTitles.get(id - 1) + " but found " + fields[0]);
                }
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
        if (!substitutions.isEmpty()) {
            rows.add(receiptFailure(phase, "Unexpected assertion titles: " + String.join(" · ", substitutions)));
        }
        var observedRunIds = markerValues(input, "INSTRUMENTATION_RESULT: runId=");
        boolean sameRun = runId == null || (runId.matches("[a-f0-9]{32}") && observedRunIds.equals(List.of(runId)));
        if (!sameRun) {
            rows.add(receiptFailure(phase, "Expected native run ID " + runId + ", observed " + observedRunIds));
        }
        return new PhaseEvidence(pid, rows, passed && substitutions.isEmpty() && sameRun);
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
