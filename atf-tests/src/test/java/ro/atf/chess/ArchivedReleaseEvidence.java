package ro.atf.chess;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import ro.atf.chess.release.ReleaseEvidence;

/** Revalidates the evidence retained in a strict release without requiring its APK or an emulator. */
final class ArchivedReleaseEvidence {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
        return candidate;
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
