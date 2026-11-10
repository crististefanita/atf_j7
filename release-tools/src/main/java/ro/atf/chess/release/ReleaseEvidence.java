package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Deterministic checks over the bytes supplied by independent build, test and download runs. */
public final class ReleaseEvidence {
    private static final Pattern LINKS = Pattern.compile("\\[[^\\]\\r\\n]+]\\(([^\\s)]+)\\)");
    private static final Pattern GAME = Pattern.compile("(?ms)^## Jocul[ \\t]*\\R(.*?)(?=^## |\\z)");

    public record Candidate(String version, String commit, long apkBytes, String apkSha256) {
        public Candidate {
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !commit.matches("[a-f0-9]{40}")
                    || apkBytes <= 0 || !apkSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid candidate identity");
            }
        }
    }

    public record Suite(String commit, String installedApkSha256, int assertions, boolean passed, String htmlSha256, Path html) { }

    public record Download(String tagCommit, String readmeCommit, String readme, String repository,
                           String assetUrl, String accessMode, int httpStatus, Path apk) { }

    private ReleaseEvidence() { }

    public static void qualification(Candidate candidate, Path apk, Suite components, Suite android) throws IOException {
        apk(candidate, apk);
        suite(candidate, components, "components", "");
        suite(candidate, android, "Android E2E", candidate.apkSha256());
    }

    private static void suite(Candidate candidate, Suite suite, String name, String installedApk) throws IOException {
        require(suite != null, "Missing " + name + " receipt");
        require(candidate.commit().equals(suite.commit()) && installedApk.equals(suite.installedApkSha256()), "Wrong candidate in " + name);
        require(suite.passed() && suite.assertions() > 0, "Missing passing assertions in " + name);
        require(Files.isRegularFile(suite.html()) && digest(suite.html()).equals(suite.htmlSha256()), "Extent hash mismatch in " + name);
    }

    public static void publication(Candidate candidate, Download download) throws IOException {
        require(download.tagCommit().equals(candidate.commit()), "Published tag points to another commit");
        require(download.readmeCommit().matches("[a-f0-9]{40}"), "Remote README commit is missing");
        require(download.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"), "Invalid GitHub repository");
        require(List.of("AUTHENTICATED", "ANONYMOUS").contains(download.accessMode()), "Unspecified access mode");
        String base = "https://github.com/" + download.repository() + "/releases/";
        String tag = "v" + candidate.version();
        String url = base + "download/" + tag + "/ChessLab-" + candidate.version() + ".apk";
        require(gameLinks(download.readme()).equals(List.of(base + "tag/" + tag, url)), "README does not lead to the qualified release and APK");
        require(url.equals(download.assetUrl()) && download.httpStatus() == 200, "Wrong download route or HTTP status");
        apk(candidate, download.apk());
    }

    public static List<String> gameLinks(String readme) throws IOException {
        var game = GAME.matcher(readme);
        require(game.find(), "Missing Jocul section");
        var links = LINKS.matcher(game.group(1)).results().map(match -> match.group(1)).toList();
        require(!game.find(), "Duplicate Jocul section");
        return links;
    }

    private static void apk(Candidate candidate, Path apk) throws IOException {
        require(Files.isRegularFile(apk) && Files.size(apk) == candidate.apkBytes()
                && digest(apk).equals(candidate.apkSha256()), "APK bytes or SHA-256 differ from qualification");
        try (var zip = new ZipFile(apk.toFile())) {
            require(zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null, "Missing APK entries");
            byte[] buffer = new byte[8192];
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var crc = new CRC32();
                try (var input = zip.getInputStream(entry)) {
                    for (int count; (count = input.read(buffer)) != -1;) crc.update(buffer, 0, count);
                }
                require(crc.getValue() == entry.getCrc(), "APK CRC mismatch: " + entry.getName());
            }
        }
    }

    public static String digest(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            var sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) sha.update(buffer, 0, count);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }
}
