package ro.atf.chess;

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
        var web = ChessWebRulesComponentTest.rows(runWorker(root, worker, inventoryFile));

        check.same("Worker exercised every source YAML resource", resources, ChessWebRulesComponentTest.resourceRows(web));
        check.same("Worker executes precisely the same fixture cases as JVM", jvm.keySet(), web.keySet());
        ChessWebRulesComponentTest.verifyExpectations(check, web);
        for (var entry : jvm.entrySet()) verifyRow(check, entry, web.get(entry.getKey()));

        check.same("Compiled TeaVM module SHA-256 stayed fixed throughout the run",
                bundleHash, sha256(Files.readAllBytes(engine)));
        check.same("Worker entry SHA-256 stayed fixed throughout the run",
                workerHash, sha256(Files.readAllBytes(worker)));
    }

    private String runWorker(Path root, Path worker, Path inventory) throws IOException, InterruptedException {
        var stdout = evidence.resolve("worker-result.txt");
        var stderr = evidence.resolve("worker-error.txt");
        var process = new ProcessBuilder("node", root.resolve("web/tests/run-parity-worker.mjs").toString(),
                worker.toString(), inventory.toString())
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
