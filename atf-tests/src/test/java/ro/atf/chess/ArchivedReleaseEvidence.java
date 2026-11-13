package ro.atf.chess;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import ro.atf.chess.release.ReleaseEvidence;

/** Revalidates the evidence retained in a strict release without requiring its APK or an emulator. */
final class ArchivedReleaseEvidence {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String HINT_RECEIPT = "hint-analysis-execution.json";
    private static final HintPhase PREPARE = new HintPhase("analysis-prepare", 5, "match-analysis.png", "android-match-analysis.html");
    private static final HintPhase RESTORE = new HintPhase("analysis-restore", 2, "", "");
    private static final HintPhase MANUAL = new HintPhase("manual-analysis", 5, "manual-analysis.png", "android-manual-analysis.html");
    private static final List<HintPhase> HINT_PHASES = List.of(PREPARE, RESTORE, MANUAL);
    private static final Set<String> LEGACY_HINT_EDITIONS = Set.of("0.13.5", "0.13.6");
    private static final String PNG_DATA = "data:image/png;base64,";

    private ArchivedReleaseEvidence() { }

    static ReleaseEvidence.Candidate verify(Path reports, String version) throws IOException {
        Path directory = reports.toRealPath();
        var qualified = read(directory, "qualification.json");
        require("PASS".equals(text(qualified, "qualificationGate")) && version.equals(text(qualified, "version")),
                "Archive does not identify a qualified release " + version);
        ReleaseEvidence.Candidate candidate;
        try {
            candidate = new ReleaseEvidence.Candidate(version, text(qualified, "sourceCommit"),
                    qualified.path("apkBytes").longValue(), text(qualified, "apkSha256"));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid archived candidate identity", invalid);
        }
        require(qualified.path("apkBytes").isIntegralNumber() && qualified.path("apkBytes").canConvertToLong(),
                "Invalid archived APK size");
        positiveCount(qualified.path("componentTests"), "component test count");
        verifyDigest(directory, "components.html", text(qualified, "componentExtentSha256"));

        var android = qualified.path("android");
        require(android.isObject() && !android.isEmpty(), "Archived Android evidence is missing");
        var usedFiles = new HashSet<>(Set.of("components.html", "qualification.json"));
        for (var entry : android.properties()) {
            verifyAndroid(directory, candidate, entry.getKey(), entry.getValue(), usedFiles);
        }
        if (qualified.has("hintAnalysisExecutionSha256") || LEGACY_HINT_EDITIONS.contains(version)
                || Files.exists(directory.resolve(HINT_RECEIPT), LinkOption.NOFOLLOW_LINKS)) {
            verifyHints(directory, candidate, qualified);
        }
        return candidate;
    }

    private record HintPhase(String scope, int assertions, String capture, String report) { }

    private static void verifyHints(Path directory, ReleaseEvidence.Candidate candidate, JsonNode qualified) throws IOException {
        Path receipt = file(directory, HINT_RECEIPT);
        byte[] receiptBytes = Files.readAllBytes(receipt);
        if (!LEGACY_HINT_EDITIONS.contains(candidate.version()) || qualified.has("hintAnalysisExecutionSha256")) {
            require(sha256(receiptBytes).equals(text(qualified, "hintAnalysisExecutionSha256")),
                    "Archived hint receipt SHA-256 mismatch");
        }
        var hints = JSON.readTree(receiptBytes);
        require(hints != null && hints.isObject(), "Invalid archived receipt: " + HINT_RECEIPT);
        String runId = text(hints, "runId");
        require("PASS".equals(text(hints, "status")) && runId.matches("[a-f0-9]{32}")
                        && candidate.commit().equals(text(hints, "sourceCommit")) && candidate.apkSha256().equals(text(hints, "apkSha256")),
                "Archived hint receipt does not identify the qualified APK and source");
        var phases = hints.path("phases");
        require(phases.isObject() && phases.size() == HINT_PHASES.size(), "Archived hint phases are incomplete");
        int total = HINT_PHASES.stream().mapToInt(HintPhase::assertions).sum();
        require(positiveCount(hints.path("assertions"), "hint assertion count") == total, "Archived hint assertion total differs");
        var first = phases.path(PREPARE.scope()).path("execution");
        var digests = new HashSet<String>();
        for (var phase : HINT_PHASES) {
            var entry = phases.path(phase.scope());
            var execution = entry.path("execution");
            verifyExecution(execution, candidate, phase.scope());
            require(runId.equals(text(execution, "runId")) && !text(execution, "device").isBlank()
                            && text(execution, "device").equals(text(first, "device")) && text(execution, "api").matches("[0-9]+")
                            && text(execution, "api").equals(text(first, "api")),
                    "Archived hint phase belongs to another run, device or API: " + phase.scope());
            require(positiveCount(execution.path("assertions"), "hint phase assertion count") == phase.assertions()
                            && text(execution, "receiptSha256").matches("[a-f0-9]{64}"),
                    "Archived hint phase has invalid receipt evidence: " + phase.scope());
            var captures = execution.path("captureSha256");
            if (phase.capture().isEmpty()) {
                require(captures.isObject() && captures.isEmpty() && !entry.has("report") && !entry.has("extentSha256"),
                        "Archived hint restart has unexpected capture or report evidence");
                continue;
            }
            String digest = text(captures, phase.capture());
            require(captures.isObject() && captures.size() == 1 && digest.matches("[a-f0-9]{64}") && digests.add(digest),
                    "Archived hint capture is missing or repeated: " + phase.capture());
            require(phase.report().equals(text(entry, "report")), "Unexpected archived hint report: " + phase.scope());
            byte[] html = Files.readAllBytes(file(directory, phase.report()));
            require(sha256(html).equals(text(entry, "extentSha256")), "Archived Extent hash mismatch: " + phase.report());
            var report = Jsoup.parse(new String(html, java.nio.charset.StandardCharsets.UTF_8));
            verifyHintReport(report, phases, phase, digest);
        }
    }

    private static void verifyHintReport(Document report, JsonNode phases, HintPhase phase, String digest) throws IOException {
        var scenarios = report.select("li.test-item[status]");
        require(scenarios.size() == 1 && "pass".equals(scenarios.getFirst().attr("status"))
                        && scenarios.getFirst().select("> .test-detail > p.name").eachText().equals(List.of("BoardSanity")),
                "Archived hint Extent lacks the passing BoardSanity scenario: " + phase.report());
        var identities = new ArrayList<Map<String, String>>();
        for (var table : report.select("table.helm-evidence-table")) {
            var rows = table.select("tbody > tr");
            if (rows.isEmpty() || !rows.getFirst().select("td").eachText().stream().findFirst().orElse("").equals("PHASE")) continue;
            var identity = new LinkedHashMap<String, String>();
            for (var row : rows) {
                var cells = row.select("td").eachText();
                require(cells.size() == 2 && identity.putIfAbsent(cells.getFirst(), cells.getLast()) == null,
                        "Malformed archived hint identity: " + phase.report());
            }
            identities.add(identity);
        }
        var expected = phase.equals(PREPARE) ? List.of(PREPARE, RESTORE) : List.of(MANUAL);
        require(identities.size() == expected.size(), "Archived hint Extent has missing or repeated phases: " + phase.report());
        for (int i = 0; i < expected.size(); i++) {
            var execution = phases.path(expected.get(i).scope()).path("execution");
            var identity = identities.get(i);
            String pid = identity.getOrDefault("ANDROID PROCESS", "");
            require(pid.matches("[1-9][0-9]*") && identity.equals(Map.of("PHASE", expected.get(i).scope(),
                            "ANDROID API", text(execution, "api"), "DEVICE", text(execution, "device"), "VARIANT", "release",
                            "SOURCE COMMIT", text(execution, "sourceCommit"), "APK SHA-256", text(execution, "apkSha256"),
                            "RUN ID", text(execution, "runId"), "ANDROID PROCESS", pid)),
                    "Archived hint Extent phase identity differs: " + expected.get(i).scope());
        }
        if (phase.equals(PREPARE)) {
            require(!identities.getFirst().get("ANDROID PROCESS").equals(identities.getLast().get("ANDROID PROCESS")),
                    "Archived hint restore did not restart the process");
        }
        var captures = report.select("a.atf-ui-action-screenshot-link");
        var captions = report.select("tr > td:matchesOwn(^CAPTURE SHA-256$)");
        require(captures.size() == 1 && captions.size() == 1
                        && captions.getFirst().parent().children().eachText().equals(List.of("CAPTURE SHA-256", digest))
                        && digest.equals(pngDigest(captures.getFirst().attr("href"))),
                "Archived hint embedded PNG SHA-256 mismatch: " + phase.capture());
    }

    private static String pngDigest(String href) throws IOException {
        require(href.startsWith(PNG_DATA), "Archived hint screenshot is not an embedded PNG");
        try {
            return sha256(Base64.getDecoder().decode(href.substring(PNG_DATA.length())));
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Archived hint screenshot contains invalid base64", malformed);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void verifyAndroid(Path directory, ReleaseEvidence.Candidate candidate, String scope, JsonNode qualified,
                                      Set<String> usedFiles) throws IOException {
        String report = text(qualified, "report");
        String execution = text(qualified, "execution");
        require(report.endsWith(".html") && execution.endsWith(".json") && usedFiles.add(report) && usedFiles.add(execution),
                "Invalid or reused Android evidence file for " + scope);
        require(candidate.commit().equals(text(qualified, "sourceCommit")), "Wrong qualified source for " + scope);
        String reportHash = text(qualified, "extentSha256");
        verifyDigest(directory, report, reportHash);
        var receipt = read(directory, execution);
        String nativeScope = scope.equals("match-analysis") ? "analysis-only" : scope;
        verifyExecution(receipt, candidate, nativeScope);
        int expected = positiveCount(qualified.path("nativeAssertions"), "qualified Android assertion count");
        var assertions = receipt.path("assertions");
        require(assertionTotal(assertions) == expected, "Android assertion count differs for " + scope);
        if (receipt.has("extentSha256")) {
            require(reportHash.equals(text(receipt, "extentSha256")), "Android receipt links another report for " + scope);
        }
        if (receipt.has("phases")) verifyPhases(receipt, candidate, expected);
    }

    private static void verifyPhases(JsonNode receipt, ReleaseEvidence.Candidate candidate, int expected) throws IOException {
        var phases = receipt.path("phases");
        require(phases.isObject() && !phases.isEmpty(), "Android phases are missing");
        long total = 0;
        for (var phase : phases.properties()) {
            var data = phase.getValue();
            verifyExecution(data, candidate, phase.getKey());
            require(text(receipt, "device").equals(text(data, "device")) && text(receipt, "api").equals(text(data, "api")),
                    "Wrong Android device for " + phase.getKey());
            total += positiveCount(data.path("assertions"), "Android phase assertion count");
        }
        require(total == expected, "Android phase totals differ from qualification");
    }

    private static void verifyExecution(JsonNode receipt, ReleaseEvidence.Candidate candidate, String scope) throws IOException {
        require("PASS".equals(text(receipt, "status")) && "release".equals(text(receipt, "variant"))
                        && candidate.commit().equals(text(receipt, "sourceCommit"))
                        && candidate.apkSha256().equals(text(receipt, "apkSha256")) && scope.equals(text(receipt, "scope")),
                "Android execution does not match the qualified APK, source or scope: " + scope);
    }

    private static long assertionTotal(JsonNode assertions) throws IOException {
        if (!assertions.isObject()) return positiveCount(assertions, "Android assertion count");
        // Earlier analysis receipts store one count per phase.
        long total = 0;
        for (var entry : assertions.properties()) total += positiveCount(entry.getValue(), "Android phase assertion count");
        return total;
    }

    private static int positiveCount(JsonNode count, String label) throws IOException {
        require(count.isIntegralNumber() && count.canConvertToInt() && count.intValue() > 0, "Invalid " + label);
        return count.intValue();
    }

    private static void verifyDigest(Path directory, String name, String expected) throws IOException {
        require(ReleaseEvidence.digest(file(directory, name)).equals(expected), "Archived Extent hash mismatch: " + name);
    }

    private static JsonNode read(Path directory, String name) throws IOException {
        var value = JSON.readTree(file(directory, name).toFile());
        require(value != null && value.isObject(), "Invalid archived receipt: " + name);
        return value;
    }

    private static Path file(Path directory, String name) throws IOException {
        require(name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"), "Unsafe archived evidence path: " + name);
        Path file = directory.resolve(name);
        require(Files.isRegularFile(file) && file.toRealPath().getParent().equals(directory),
                "Archived evidence missing or outside the release: " + name);
        return file;
    }

    private static String text(JsonNode node, String name) { return node.path(name).asText(""); }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }
}
