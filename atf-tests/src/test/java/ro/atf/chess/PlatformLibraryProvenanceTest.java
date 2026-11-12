package ro.atf.chess;

import com.fasterxml.jackson.databind.ObjectMapper;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class PlatformLibraryProvenanceTest {
    @Test void publishedPairsKeepTheProducerBytes(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        Path libraries = root.resolve("lib");
        var provenance = new ObjectMapper().readTree(root.resolve("library-provenance.json").toFile());
        var files = new TreeMap<String, String>();
        for (var entry : provenance.required("files")) {
            String file = entry.required("file").textValue();
            String sha256 = entry.required("sha256").textValue().toLowerCase(Locale.ROOT);
            check.same("One provenance entry for " + file, null, files.putIfAbsent(file, sha256));
        }
        check.same("Declared JAR/POM population", 10, files.size());
        try (var paths = Files.list(libraries)) {
            var binaries = paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".jar") || name.endsWith(".pom")).sorted().toList();
            check.same("Physical library population", files.keySet().stream().toList(), binaries);
        }
        for (var item : files.entrySet()) {
            String name = item.getKey();
            Path file = libraries.resolve(name);
            check.same(name + " declared SHA-256", item.getValue(), digest(Files.readAllBytes(file)));
            if (!name.endsWith(".jar")) continue;
            String pom = name.substring(0, name.length() - 4) + ".pom";
            try (var archive = new JarFile(file.toFile())) {
                var embedded = archive.stream().filter(entry -> entry.getName().startsWith("META-INF/maven/atf.api/")
                        && entry.getName().endsWith("/pom.xml")).toList();
                check.same(name + " embedded POM count", 1, embedded.size());
                try (var stream = archive.getInputStream(embedded.getFirst())) {
                    check.same(pom + " embedded SHA-256", files.get(pom), digest(stream.readAllBytes()));
                }
            }
        }
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
