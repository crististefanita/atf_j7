package ro.atf.chess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@Tag("web-parity")
@ExtendWith(ExtentAudit.class)
class ChessWebParityPublication {
    @TempDir Path evidence;

    @Test void workerAndJvmShareTheRulesAndData(Checks check) throws Exception {
        var configured = System.getProperty("chess.web.bundle", "");
        check.same("A compiled Web bundle was supplied explicitly", true, !configured.isBlank());
        var site = Path.of(configured).toAbsolutePath().normalize();
        var worker = site.resolve("engine-worker.mjs");
        var engine = site.resolve("chess-engine.mjs");
        check.same("Bundle includes the Worker entry and compiled TeaVM module", true,
                Files.isRegularFile(worker) && Files.isRegularFile(engine));

        String bundleHash = sha256(Files.readAllBytes(engine));
        String workerHash = sha256(Files.readAllBytes(worker));
        var root = ProjectRoot.locate();
        var resources = ChessWebRulesComponentTest.resources(root);
        var inventory = String.join("\n", resources);
        var inventoryFile = Files.writeString(evidence.resolve("yaml-files.txt"), inventory);
        var jvm = ChessWebRulesComponentTest.rows(ChessWebParity.snapshot(inventory));
        var web = ChessWebRulesComponentTest.rows(runWorker(root, "run-parity-worker.mjs", worker, inventoryFile));

        check.same("Worker exercised every source YAML resource", resources, ChessWebRulesComponentTest.resourceRows(web));
        check.same("Worker executes precisely the same fixture cases as JVM", jvm.keySet(), web.keySet());
        ChessWebRulesComponentTest.verifyExpectations(check, web);
        for (var entry : jvm.entrySet()) verifyRow(check, entry, web.get(entry.getKey()));

        check.same("Compiled TeaVM module SHA-256 stayed fixed throughout the run",
                bundleHash, sha256(Files.readAllBytes(engine)));
        check.same("Worker entry SHA-256 stayed fixed throughout the run",
                workerHash, sha256(Files.readAllBytes(worker)));
    }

    @Test void packagedWorkerResolvesAndSavesTheSharedColorChoices(Checks check) throws Exception {
        var configured = System.getProperty("chess.web.bundle", "");
        check.same("The packaged worker is supplied for the color case", true, !configured.isBlank());
        var root = ProjectRoot.locate();
        var fixture = root.resolve("atf-tests/src/test/resources/match-color-choices.json");
        var worker = Path.of(configured).toAbsolutePath().normalize().resolve("engine-worker.mjs");
        var rows = ChessWebRulesComponentTest.rows(runWorker(root, "match-color-worker.mjs", worker, fixture).strip());
        var cases = new ObjectMapper().readTree(fixture.toFile());
        for (var color : cases) {
            String id = color.get("id").asText();
            String expected = String.join("/", color.get("side").asText(), color.get("level").asText(),
                    color.get("blackBelow").asText(), color.get("awaitingBot").asText(), color.get("draws").asText());
            check.same(id + " resolves color, level, orientation, opening turn and draw count across reload", expected, rows.get(id));
        }
        check.same("A restored Black game applies one opening reply and rejects replay at the old revision",
                "APPLIED/1/BLACK/STALE/1/1", rows.get("random-black-single-reply"));
        check.same("An unsupported color is rejected without changing the saved game",
                "TypeError: Invalid chess worker request", rows.get("invalid-choice"));
        check.same("The packaged worker reports every shared case and both negative or replay observations",
                cases.size() + 2, rows.size());
    }

    private String runWorker(Path root, String script, Path worker, Path input) throws IOException, InterruptedException {
        var stdout = evidence.resolve("worker-result.txt");
        var stderr = evidence.resolve("worker-error.txt");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                root.resolve("web/tests").resolve(script).toString(), worker.toString(), input.toString())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            if (!process.waitFor(55, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for the TeaVM worker");
            }
            if (process.exitValue() != 0) {
                throw new IOException("TeaVM worker exited " + process.exitValue() + ": " + Files.readString(stderr).strip());
            }
            return Files.readString(stdout, StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void verifyRow(Checks check, Map.Entry<String, String> jvm, String web) throws NoSuchAlgorithmException {
        String key = jvm.getKey();
        String expected = jvm.getValue();
        if (expected.length() < 220) {
            check.same("JVM and generated Web worker match " + key, expected, web);
            return;
        }
        // Compare complete data first. Compact digests make the Extent table readable.
        String reference = fingerprint(expected);
        String actual = fingerprint(web);
        if (!Objects.equals(expected, web)) actual += " [content differs]";
        check.same("JVM and generated Web worker match " + key, reference, actual);
    }

    private static String fingerprint(String value) throws NoSuchAlgorithmException {
        if (value == null) return "(missing)";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length + " bytes, SHA-256 " + sha256(bytes);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
