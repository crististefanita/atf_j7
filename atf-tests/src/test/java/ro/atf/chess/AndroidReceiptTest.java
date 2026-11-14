package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseEvidence;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidReceiptTest {
    private static final String TACTICS_RESTART = "A real process restart keeps the tactics chapter position and its two awards";
    private static final String TRAINING_RESTART = "Training reopens its second unsolved puzzle after process restart";
    private static final String PASSING = """
            INSTRUMENTATION_RESULT: check_01=The board is visible	true	true	true
            INSTRUMENTATION_RESULT: phase=openings
            INSTRUMENTATION_RESULT: pid=123
            INSTRUMENTATION_RESULT: result=PASS
            INSTRUMENTATION_CODE: -1
            """;

    @TempDir Path folder;

    @Test void reportsOnlyACompleteAndUnambiguousNativePass(Checks check) throws IOException {
        var passing = read("passing", PASSING);
        check.same("A complete native receipt passes", true, passing.passed());
        check.same("The recorded assertion survives import", "The board is visible", passing.rows().getFirst().name());
        var mixed = read("mixed", PASSING + "INSTRUMENTATION_RESULT: result=FAIL\n");
        check.same("Contradictory native verdicts fail", false, mixed.passed());
        check.same("The diagnostic shows both observed verdicts", true, mixed.rows().getLast().actual().contains("[PASS, FAIL]"));
        check.same("An opening receipt without a valid PID fails", false,
                read("pid", PASSING.replace("pid=123", "pid=0")).passed());
        check.same("An extra instrumentation code fails", false,
                read("code", PASSING + "INSTRUMENTATION_CODE: 0\n").passed());
        check.same("A claimed passing check with unequal values fails", false,
                read("wrong-value", PASSING.replace("\ttrue\ttrue\ttrue", "\ttrue\tfalse\ttrue")).passed());
        var invalid = read("bad-flag", PASSING.replace("\ttrue\ttrue\ttrue", "\ttrue\ttrue\tunknown"));
        check.same("A malformed assertion cannot count towards coverage", false, invalid.passed());
        check.same("The diagnostic separates valid assertions from raw lines", true,
                invalid.rows().getLast().actual().contains("valid checks 0/1 from 1 native rows"));
    }

    @Test void rejectsOpeningPassWhenAnUnrelatedCheckReplacesTheBoardGeometry(Checks check) throws IOException {
        String reply = "Next preserves the board and its corner squares when Resume lesson appears";
        String learner = "Next preserves the board and its corner squares when Lesson hint returns";
        var lines = new StringBuilder(PASSING.substring(PASSING.indexOf("INSTRUMENTATION_RESULT: phase=")));
        for (int id = 1; id <= 24; id++) {
            lines.append("INSTRUMENTATION_RESULT: check_%02d=Lesson step %d\ttrue\ttrue\ttrue%n".formatted(id, id));
        }
        lines.append("INSTRUMENTATION_RESULT: check_25=").append(reply).append("\ttrue\ttrue\ttrue\n");
        lines.append("INSTRUMENTATION_RESULT: check_26=").append(learner).append("\ttrue\ttrue\ttrue\n");
        var valid = AndroidSanityPublication.readOpeningPhase(Files.writeString(folder.resolve("opening-geometry.txt"), lines));
        check.same("Both Next transitions qualify the native opening receipt", true, valid.passed());
        var invalid = AndroidSanityPublication.readOpeningPhase(Files.writeString(folder.resolve("opening-missing-geometry.txt"),
                lines.toString().replace(learner, "An unrelated passing comparison")));
        check.same("The total alone cannot qualify the opening receipt", false, invalid.passed());
        check.same("The diagnostic names the missing Next transition", true, invalid.rows().getLast().actual().contains(learner));
    }

    @Test void requiresTheExactLongGameGraphComparisonAtTheExpectedCount(Checks check) throws IOException {
        String selection = "A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row";
        writePhase(folder, "game-review", 123, 23, List.of(selection));
        Path receipt = folder.resolve("game-review.txt");
        check.same("The long-game graph comparison qualifies a complete review receipt", true,
                AndroidSanityPublication.readGameReviewPhase(receipt).passed());
        writePhase(folder, "game-review", 123, 23, List.of("An unrelated passing comparison"));
        var unrelated = AndroidSanityPublication.readGameReviewPhase(receipt);
        check.same("The original assertion count cannot substitute for long-game selection", false, unrelated.passed());
        check.same("The rejection names the missing long-game graph comparison", true,
                unrelated.rows().getLast().actual().contains(selection));
        writePhase(folder, "game-review", 123, 23, List.of(selection, selection));
        check.same("A repeated long-game comparison also fails", false, AndroidSanityPublication.readGameReviewPhase(receipt).passed());
    }

    @Test void rendersChapterRestartEvidenceAndRejectsAnUnrelatedComparisonAtTheSameCount(Checks check) throws Exception {
        Path valid = boardReceipt(Files.createDirectory(folder.resolve("complete")), TRAINING_RESTART);
        AndroidSanityPublication.publishBoardSanity(valid);
        var resume = restoreStep(valid);
        check.same("The chapter restore step passes with both native checkpoints", "Pass", resume.selectFirst(".card-header .log").text());
        var table = resume.selectFirst(".atf-ui-assertions");
        check.same("Both comparisons are available in the collapsible Extent assertion table", true,
                table.closest(".collapse") != null && table.text().contains(TACTICS_RESTART) && table.text().contains(TRAINING_RESTART));

        Path incomplete = boardReceipt(Files.createDirectory(folder.resolve("wrong-check")), "Unrelated passing comparison");
        check.rejects("A passing line from an unrelated check cannot qualify the restart", AssertionError.class,
                () -> AndroidSanityPublication.publishBoardSanity(incomplete));
        resume = restoreStep(incomplete);
        check.same("The chapter restore step renders Fail despite the correct count", "Fail", resume.selectFirst(".card-header .log").text());
        check.same("The missing Training restart is named and the Tactics comparison is preserved", true,
                resume.text().contains("Missing or duplicate chapter restart checks: [" + TRAINING_RESTART + "]")
                        && resume.text().contains(TACTICS_RESTART));
    }

    @Test void bindsReviewCapturesToTheirPhasesAndRequiresARealProcessRestart(Checks check) throws Exception {
        var passing = reviewReceipt(Files.createDirectory(folder.resolve("review-valid")), 333);
        AndroidSanityPublication.publishGameReview(passing);
        var validReport = gameReviewReport(passing);
        check.same("The explanation and resumed position appear in Extent", true,
                validReport.contains("Why names the mating queen on h4") && validReport.contains("222 → 333"));

        var wrongCapture = reviewReceipt(Files.createDirectory(folder.resolve("review-wrong-frame")), 333);
        Path why = wrongCapture.resolveSibling("why");
        Files.copy(why.resolve("game-review-why-recommended.png"), why.resolve("game-review-why.png"), StandardCopyOption.REPLACE_EXISTING);
        check.rejects("A valid PNG from a different checkpoint cannot pass", AssertionError.class,
                () -> AndroidSanityPublication.publishGameReview(wrongCapture));
        check.same("Extent names the substituted capture", true,
                gameReviewReport(wrongCapture).contains("Capture SHA-256 mismatch: game-review-why.png"));

        var sameProcess = reviewReceipt(Files.createDirectory(folder.resolve("review-reused-process")), 222);
        check.rejects("A passing restore receipt cannot reuse the original process", AssertionError.class,
                () -> AndroidSanityPublication.publishGameReview(sameProcess));
        check.same("Extent exposes both identical process IDs", true, gameReviewReport(sameProcess).contains("222 → 222"));
    }

    @Test void rejectsAReviewPhaseFromAnotherRunOfTheSameBuild(Checks check) throws Exception {
        Path directory = Files.createDirectory(folder.resolve("review-mixed-run"));
        Path receipt = reviewReceipt(directory, 333, "2".repeat(32));
        check.rejects("A complete phase from another run of the same APK cannot pass", AssertionError.class,
                () -> AndroidSanityPublication.publishGameReview(receipt));
        String report = gameReviewReport(receipt);
        check.same("Extent exposes the mismatched run identity while retaining the restart PIDs", true,
                report.contains("Missing or mismatched phase run identity") && report.contains("222 → 333")
                        && report.contains("1".repeat(32)) && report.contains("2".repeat(32)));
    }

    private static String gameReviewReport(Path receipt) throws IOException {
        return Jsoup.parse(receipt.resolveSibling("android-game-review.html"), "UTF-8").text();
    }

    @Test void tacticsExplanationRequiresBothNativeCaptures(Checks check) throws Exception {
        var identity = new ReviewIdentity("", "sanity", "a".repeat(64), "1".repeat(32));
        var required = List.of("Tactic 002 opens the normal explanation on its final move",
                "The normal explanation rewinds and allows forward replay",
                "Closing the normal explanation preserves the solved tactic and points",
                "Tactic 043 opens the defense study on the accepted key",
                "The defense study replays Kg8 and shows the material on the board",
                "Closing the defense study preserves the solved tactic and points");
        for (boolean substituted : List.of(false, true)) {
            Path directory = Files.createDirectory(folder.resolve("tactics-" + substituted));
            var scope = "tactics-explanation";
            writeReviewRun(directory, scope, 111, required.size(), identity, required, List.of(scope, "tactics-defense"), 4);
            Path receipt = directory.resolve(scope + ".txt");
            if (substituted) {
                Path defense = directory.resolve("tactics-defense.png");
                Files.copy(directory.resolve(scope + ".png"), defense, StandardCopyOption.REPLACE_EXISTING);
                check.rejects("A passing receipt cannot qualify with the normal replay capture substituted for the defense",
                        AssertionError.class, () -> AndroidSanityPublication.publishTacticsExplanation(receipt));
            } else {
                AndroidSanityPublication.publishTacticsExplanation(receipt);
            }
            var report = analysisReport(receipt, scope);
            check.same("Extent renders the matching captures and actual status", List.of(1, substituted ? 1 : 2),
                    List.of(report.select(substituted ? "li.test-item[status=fail]" : "li.test-item[status=pass]").size(),
                            report.select("a[href^=data:image/png;base64]").size()));
            if (substituted) check.same("Extent identifies the substituted PNG", true,
                    report.text().contains("Capture SHA-256 mismatch: tactics-defense.png"));
        }
    }

    @Test void publishesBothAnalysisSurfacesWithBoundCapturesAndVisibleResults(Checks check) throws Exception {
        Path receipt = analysisFixture("analysis-valid", "1".repeat(32), "1".repeat(32));
        AndroidSanityPublication.publishMatchAnalysis(receipt);
        Path manual = receipt.resolveSibling("manual-analysis").resolve("manual-analysis.txt");
        AndroidSanityPublication.publishManualAnalysis(manual);
        for (var result : List.of(analysisReport(receipt, "match-analysis"), analysisReport(manual, "manual-analysis"))) {
            check.same("Extent renders a PASS scenario with the native comparison and one bound PNG", true,
                    result.select("li.test-item[status=pass]").size() == 1
                            && result.select("a[href^=data:image/png;base64]").size() == 1
                            && result.select(".atf-ui-assertions").text().contains("Next shows the calculated reply")
                            && result.text().contains("1".repeat(32)));
        }
    }

    @Test void archivedHintsRejectChangedReportsCapturesAndForeignPhases(Checks check) throws Exception {
        Path archive = Files.createDirectory(folder.resolve("archive"));
        String commit = "c".repeat(40);
        String apk = "a".repeat(64);
        var candidate = new ReleaseEvidence.Candidate("0.13.6", commit, 2048, apk);
        String run = "1".repeat(32);
        Path raw = Files.createDirectory(folder.resolve("hint-raw"));
        Path prepare = writeAnalysisFixture(raw, commit, "release", apk, run, run);
        Path manual = raw.resolve("manual-analysis/manual-analysis.txt");
        AndroidSanityPublication.publishMatchAnalysis(prepare);
        AndroidSanityPublication.publishManualAnalysis(manual);
        var json = new ObjectMapper();
        Files.writeString(archive.resolve("components.html"), "<html>Components</html>");
        Files.writeString(archive.resolve("android-game-review.html"), "<html>Game Review</html>");
        var review = json.createObjectNode().put("status", "PASS").put("scope", "game-review").put("variant", "release")
                .put("sourceCommit", commit).put("apkSha256", apk).put("assertions", 30);
        json.writeValue(archive.resolve("game-review-execution.json").toFile(), review);
        var qualification = json.createObjectNode().put("version", candidate.version()).put("qualificationGate", "PASS")
                .put("sourceCommit", commit).put("apkSha256", apk).put("apkBytes", candidate.apkBytes())
                .put("componentTests", 1).put("componentExtentSha256", ReleaseEvidence.digest(archive.resolve("components.html")));
        qualification.putObject("android").putObject("game-review").put("sourceCommit", commit).put("nativeAssertions", 30)
                .put("report", "android-game-review.html").put("execution", "game-review-execution.json")
                .put("extentSha256", ReleaseEvidence.digest(archive.resolve("android-game-review.html")));
        Path qualified = archive.resolve("qualification.json");
        json.writeValue(qualified.toFile(), qualification);
        var hint = json.createObjectNode().put("status", "PASS").put("runId", run).put("sourceCommit", commit)
                .put("apkSha256", apk).put("assertions", 12);
        var phases = hint.putObject("phases");
        archivePhase(json, phases.putObject("analysis-prepare"), raw, archive, "android-match-analysis.html");
        archivePhase(json, phases.putObject("analysis-restore"), raw.resolve("analysis-restore"), archive, "");
        var manualPhase = phases.putObject("manual-analysis");
        archivePhase(json, manualPhase, manual.getParent(), archive, "android-manual-analysis.html");
        Path receipt = archive.resolve("hint-analysis-execution.json");
        json.writeValue(receipt.toFile(), hint);
        check.same("The publisher's archived HTML and captures identify the qualified APK", candidate,
                ArchivedReleaseEvidence.verify(archive, candidate.version()));

        Path manualHtml = archive.resolve("android-manual-analysis.html");
        String original = Files.readString(manualHtml);
        Files.writeString(manualHtml, original + "<!-- changed -->");
        assertArchiveRejection(check, archive, candidate, "A byte change in the manual HTML fails",
                "Archived Extent hash mismatch: android-manual-analysis.html");

        Path matchHtml = archive.resolve("android-match-analysis.html");
        String originalMatch = Files.readString(matchHtml);
        Files.writeString(matchHtml, originalMatch + "<!-- changed -->");
        assertArchiveRejection(check, archive, candidate, "A byte change in the match HTML fails",
                "Archived Extent hash mismatch: android-match-analysis.html");
        Files.writeString(matchHtml, originalMatch);

        String originalDigest = manualPhase.path("extentSha256").asText();
        var rendered = Jsoup.parse(original);
        String otherCapture = Jsoup.parse(matchHtml, "UTF-8").selectFirst("a.atf-ui-action-screenshot-link").attr("href");
        rendered.selectFirst("a.atf-ui-action-screenshot-link").attr("href", otherCapture);
        Files.writeString(manualHtml, rendered.outerHtml());
        manualPhase.put("extentSha256", ReleaseEvidence.digest(manualHtml));
        json.writeValue(receipt.toFile(), hint);
        assertArchiveRejection(check, archive, candidate, "A substituted PNG fails even with a recomputed HTML hash",
                "Archived hint embedded PNG SHA-256 mismatch: manual-analysis.png");

        Files.writeString(manualHtml, original);
        manualPhase.put("extentSha256", originalDigest);
        var manualExecution = (com.fasterxml.jackson.databind.node.ObjectNode) manualPhase.path("execution");
        manualExecution.put("runId", "2".repeat(32));
        json.writeValue(receipt.toFile(), hint);
        assertArchiveRejection(check, archive, candidate, "A phase from another run of the same signed APK fails",
                "Archived hint phase belongs to another run, device or API: manual-analysis");

        String foreignRun = "2".repeat(32);
        hint.put("runId", foreignRun);
        phases.properties().forEach(phase -> ((com.fasterxml.jackson.databind.node.ObjectNode) phase.getValue().path("execution"))
                .put("runId", foreignRun));
        json.writeValue(receipt.toFile(), hint);
        assertArchiveRejection(check, archive, candidate, "A rewritten run ID must match the archived Extent observations",
                "Archived hint Extent phase identity differs: analysis-prepare");

        hint.put("runId", run);
        phases.properties().forEach(phase -> ((com.fasterxml.jackson.databind.node.ObjectNode) phase.getValue().path("execution"))
                .put("runId", run));
        json.writeValue(receipt.toFile(), hint);
        qualification.put("hintAnalysisExecutionSha256", ReleaseEvidence.digest(receipt));
        json.writeValue(qualified.toFile(), qualification);
        check.same("The optional qualification digest binds the archived hint receipt", candidate,
                ArchivedReleaseEvidence.verify(archive, candidate.version()));
        var next = new ReleaseEvidence.Candidate("0.13.7", commit, candidate.apkBytes(), apk);
        qualification.put("version", next.version());
        json.writeValue(qualified.toFile(), qualification);
        check.same("New releases can qualify the same two reports through the receipt digest", next,
                ArchivedReleaseEvidence.verify(archive, next.version()));
        String receiptDigest = qualification.remove("hintAnalysisExecutionSha256").asText();
        json.writeValue(qualified.toFile(), qualification);
        assertArchiveRejection(check, archive, next, "A new hint archive requires the qualification digest",
                "Archived hint receipt SHA-256 mismatch");
        qualification.put("version", candidate.version()).put("hintAnalysisExecutionSha256", receiptDigest);
        json.writeValue(qualified.toFile(), qualification);
        Files.writeString(receipt, Files.readString(receipt) + "\n");
        assertArchiveRejection(check, archive, candidate, "Editing a receipt bound by qualification fails",
                "Archived hint receipt SHA-256 mismatch");
        Files.delete(receipt);
        qualification.remove("hintAnalysisExecutionSha256");
        json.writeValue(qualified.toFile(), qualification);
        check.rejects("A published edition with this evidence cannot omit the whole receipt", IOException.class,
                () -> ArchivedReleaseEvidence.verify(archive, candidate.version()));
        qualification.put("version", "0.13.4");
        json.writeValue(qualified.toFile(), qualification);
        check.same("A legacy archive without the hint receipt remains valid", "0.13.4",
                ArchivedReleaseEvidence.verify(archive, "0.13.4").version());
    }

    private static void archivePhase(ObjectMapper json, com.fasterxml.jackson.databind.node.ObjectNode phase, Path raw,
                                     Path archive, String report) throws IOException {
        phase.set("execution", json.readTree(raw.resolve("execution.json").toFile()));
        if (report.isEmpty()) return;
        Path copy = Files.copy(raw.resolve(report), archive.resolve(report));
        phase.put("report", report).put("extentSha256", ReleaseEvidence.digest(copy));
    }

    private static void assertArchiveRejection(Checks check, Path archive, ReleaseEvidence.Candidate candidate, String label,
                                               String expected) {
        var failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> ArchivedReleaseEvidence.verify(archive, candidate.version()));
        check.same(label, expected, failure.getMessage());
    }

    @Test void rejectsSubstitutedReadablePngOnBothAnalysisSurfaces(Checks check) throws Exception {
        Path receipt = analysisFixture("analysis-foreign-frame", "1".repeat(32), "1".repeat(32));
        Path manual = receipt.resolveSibling("manual-analysis").resolve("manual-analysis.txt");
        Path matchPng = receipt.resolveSibling("match-analysis.png");
        Path manualPng = manual.resolveSibling("manual-analysis.png");
        byte[] originalMatch = Files.readAllBytes(matchPng);
        Files.copy(manualPng, matchPng, StandardCopyOption.REPLACE_EXISTING);
        Files.write(manualPng, originalMatch);
        // Even a rewritten metadata hash cannot replace the digest recorded in instrumentation.
        var metadata = manual.resolveSibling("execution.json");
        var json = new ObjectMapper().readTree(metadata.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.path("captureSha256")).put("manual-analysis.png", sha256(manualPng));
        Files.writeString(metadata, json.toString());
        check.rejects("A different but readable match PNG fails", AssertionError.class, () -> AndroidSanityPublication.publishMatchAnalysis(receipt));
        check.rejects("A substituted manual PNG fails even when its metadata hash was updated",
                AssertionError.class, () -> AndroidSanityPublication.publishManualAnalysis(manual));
        assertRejectedCapture(check, receipt, "match-analysis");
        assertRejectedCapture(check, manual, "manual-analysis");
    }

    @Test void rejectsAnalysisPhasesFromAnotherRunOfTheSameApk(Checks check) throws Exception {
        Path receipt = analysisFixture("analysis-mixed-run", "2".repeat(32), "3".repeat(32));
        check.rejects("A complete restore with the same APK but a different run fails",
                AssertionError.class, () -> AndroidSanityPublication.publishMatchAnalysis(receipt));
        var result = analysisReport(receipt, "match-analysis");
        check.same("Extent identifies the two different runs and does not attach the unqualified PNG", true,
                result.select("li.test-item[status=fail]").size() == 1 && result.select("a[href^=data:image/png;base64]").isEmpty()
                        && result.text().contains("Missing or mismatched phase run identity")
                        && result.text().contains("1".repeat(32)) && result.text().contains("2".repeat(32)));
        Path manual = receipt.resolveSibling("manual-analysis").resolve("manual-analysis.txt");
        check.rejects("A self-consistent manual phase from a different combined run fails",
                AssertionError.class, () -> AndroidSanityPublication.publishManualAnalysis(manual));
        check.same("The manual Extent records the run mismatch", true,
                analysisReport(manual, "manual-analysis").text().contains("Missing or mismatched phase run identity"));
    }

    private static void assertRejectedCapture(Checks check, Path receipt, String scope) throws IOException {
        var result = analysisReport(receipt, scope);
        check.same("Extent fails without embedding the substituted " + scope + " capture", true,
                result.select("li.test-item[status=fail]").size() == 1 && result.select("a[href^=data:image/png;base64]").isEmpty()
                        && result.text().contains("Capture SHA-256 mismatch: " + scope + ".png"));
    }

    private static org.jsoup.nodes.Document analysisReport(Path receipt, String scope) throws IOException {
        return Jsoup.parse(receipt.resolveSibling("android-" + scope + ".html"), "UTF-8");
    }

    private Path analysisFixture(String name, String restoreRun, String manualRun) throws Exception {
        Path directory = Files.createDirectory(folder.resolve(name));
        return writeAnalysisFixture(directory, "", "sanity", "a".repeat(64), restoreRun, manualRun);
    }

    static Path writeAnalysisFixture(Path directory, String commit, String variant, String apk, String restoreRun, String manualRun) throws Exception {
        var identity = new ReviewIdentity(commit, variant, apk, "1".repeat(32));
        var preview = List.of("Next shows the calculated reply and Previous returns to the first move",
                "The suggested line and its navigation are visible with a fixed preview board");
        var prepare = new java.util.ArrayList<>(preview);
        prepare.add("Returning to history preserves the selected move and the saved game");
        var manual = new java.util.ArrayList<>(preview);
        manual.add("Returning to the study lets the player apply the hint without saving a match");
        writeReviewRun(directory, "analysis-prepare", 111, 5, identity, prepare, List.of("match-analysis"), 4);
        writeReviewRun(Files.createDirectory(directory.resolve("analysis-restore")), "analysis-restore", 222, 2, identity.withRunId(restoreRun),
                List.of("The reopened match analyzes its live position with the hint hidden"), List.of(), 5);
        writeReviewRun(Files.createDirectory(directory.resolve("manual-analysis")), "manual-analysis", 333, 5, identity.withRunId(manualRun),
                manual, List.of("manual-analysis"), 6);
        return directory.resolve("analysis-prepare.txt");
    }

    private static Path reviewReceipt(Path directory, int restorePid) throws Exception {
        return reviewReceipt(directory, restorePid, "1".repeat(32));
    }

    private static Path reviewReceipt(Path directory, int restorePid, String restoreRunId) throws Exception {
        var identity = new ReviewIdentity("", "sanity", "a".repeat(64), "1".repeat(32));
        return reviewReceipt(directory, restorePid, identity, restoreRunId);
    }

    static Path releaseReviewReceipt(Path directory, String commit, String apkSha256, String runId) throws Exception {
        return reviewReceipt(directory, 333, new ReviewIdentity(commit, "release", apkSha256, runId), runId);
    }

    private record ReviewIdentity(String commit, String variant, String apkSha256, String runId) {
        ReviewIdentity withRunId(String replacement) {
            return new ReviewIdentity(commit, variant, apkSha256, replacement);
        }
    }

    private static Path reviewReceipt(Path directory, int restorePid, ReviewIdentity identity, String restoreRunId) throws Exception {
        writeReviewRun(directory, "game-review", 111, 23, identity,
                List.of("A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row"),
                List.of("game-review", "game-review-navigation", "game-review-line", "game-review-comparison", "game-review-actions"), 1);
        writeReviewRun(Files.createDirectory(directory.resolve("why")), "game-review-why", 222, 5, identity,
                List.of("Why shows both expected scores and search depth when the line proves no concrete reason",
                        "Why names the mating queen on h4 for the archived g4 blunder",
                        "The recommended branch starts before g4 and replays its first legal move",
                        "The played branch replays g4 and the mating reply from the same starting position",
                        "Cancelling Try again keeps the played line and the original review open"),
                List.of("game-review-why", "game-review-why-recommended", "game-review-why-score"), 6);
        writeReviewRun(Files.createDirectory(directory.resolve("why-restore")), "game-review-why-restore", restorePid, 2, identity.withRunId(restoreRunId),
                List.of("A restarted process reopens the archived analysis on the played mating reply",
                        "Confirming from the reopened review creates a separate game and keeps the archived analysis"),
                List.of("game-review-why-restore"), 9);
        return directory.resolve("game-review.txt");
    }

    private static void writeReviewRun(Path directory, String phase, int pid, int checks, ReviewIdentity identity, List<String> required,
                                       List<String> frames, int firstColor) throws Exception {
        writePhase(directory, phase, pid, checks, required);
        Path receipt = directory.resolve(phase + ".txt");
        Files.writeString(receipt, "INSTRUMENTATION_RESULT: runId=" + identity.runId() + "\n", java.nio.file.StandardOpenOption.APPEND);
        var execution = new ObjectMapper().createObjectNode();
        execution.put("status", "PASS").put("scope", phase).put("variant", identity.variant()).put("sourceCommit", identity.commit()).put("runId", identity.runId())
                .put("device", "fixture").put("api", "28").put("apkSha256", identity.apkSha256()).put("assertions", checks);
        var hashes = execution.putObject("captureSha256");
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        int color = firstColor;
        for (String frame : frames) {
            Path png = directory.resolve(frame + ".png");
            image.setRGB(0, 0, color++);
            ImageIO.write(image, "png", png.toFile());
            String digest = sha256(png);
            hashes.put(png.getFileName().toString(), digest);
            Files.writeString(receipt, "INSTRUMENTATION_RESULT: capture_" + frame + "=" + digest + "\n", java.nio.file.StandardOpenOption.APPEND);
        }
        execution.put("receiptSha256", sha256(receipt));
        Files.writeString(directory.resolve("execution.json"), execution.toString());
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Path boardReceipt(Path directory, String training) throws IOException {
        writePhase(directory, "prepare", 111, 46, List.of());
        writePhase(directory, "restore", 222, 26, List.of(TACTICS_RESTART, training));
        writePhase(directory, "match-prepare", 333, 3, List.of());
        writePhase(directory, "match-restore", 444, 15, List.of());
        writePhase(directory, "match-settled", 555, 4, List.of());
        var pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        for (String frame : List.of("hint-piece", "hint-solution", "board", "milestone", "mate-two", "menu",
                "mate-three", "tactics-streak", "match-resumed", "match-history")) {
            ImageIO.write(pixel, "png", directory.resolve(frame + ".png").toFile());
        }
        return directory.resolve("prepare.txt");
    }

    private static void writePhase(Path directory, String phase, int pid, int checks, List<String> required) throws IOException {
        var text = new StringBuilder();
        text.append("INSTRUMENTATION_RESULT: phase=").append(phase).append('\n');
        text.append("INSTRUMENTATION_RESULT: pid=").append(pid).append('\n');
        for (int index = 0; index < checks; index++) {
            String name = index < required.size() ? required.get(index) : "Fixture comparison " + (index + 1);
            text.append("INSTRUMENTATION_RESULT: check_%02d=%s\ttrue\ttrue\ttrue%n".formatted(index + 1, name));
        }
        text.append("INSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n");
        Files.writeString(directory.resolve(phase + ".txt"), text);
    }

    private static Element restoreStep(Path receipt) throws IOException {
        var report = Jsoup.parse(receipt.resolveSibling("android-board-sanity.html"), "UTF-8");
        return report.select("a.node").stream().filter(node -> node.text().equals("Resume pending quests and restore tactics, training and mate progress"))
                .findFirst().orElseThrow().closest(".card");
    }

    private AndroidSanityPublication.PhaseEvidence read(String file, String contents) throws IOException {
        return AndroidSanityPublication.readPhase(Files.writeString(folder.resolve(file + ".txt"), contents), "openings", 1);
    }
}
