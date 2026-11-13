package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Deterministic checks over the bytes supplied by independent build, test and download runs. */
public final class ReleaseEvidence {
    private static final Pattern LINK_BULLET = Pattern.compile("- \\[([^\\[\\]\\r\\n]+)]\\(([^\\s()<>]+)\\)");
    private static final Pattern GAME = Pattern.compile("(?ms)^## Jocul[ \\t]*\\R(.*?)(?=^#{1,2}[ \\t]+|\\z)");
    private static final Pattern SECTION = Pattern.compile("(?m)^##[ \\t]+");
    private static final Pattern INTEGRITY_LABEL = Pattern.compile("(?i)sha[- ]?256|checksum|\\bbytes?\\b|[a-f0-9]{64}");
    private static final Pattern APK_ROUTE = Pattern.compile("(?i)/releases/download/|[^\\s(<\\[\"'`=]+(?:\\.|%2e)apk(?=$|[\\s?#)>'\"`\\],;])");

    public record Candidate(String version, String commit, long apkBytes, String apkSha256) {
        public Candidate {
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !commit.matches("[a-f0-9]{40}")
                    || apkBytes <= 0 || !apkSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid candidate identity");
            }
        }
    }

    public record Download(String tagCommit, String readmeCommit, String readme, String repository,
                           String assetUrl, String accessMode, int httpStatus, Path apk) { }

    public record InstallationException(String status, String scope, String authorization, String reason,
                                        String candidateCommit, String candidateApkSha256,
                                        String supplementalApkSha256, int supplementalAssertions,
                                        boolean distributionInstalled, String signingStatus,
                                        String supplementalExtentSha256, Path supplementalExtent) { }

    private ReleaseEvidence() { }

    public static void installationException(Candidate candidate, InstallationException exception) throws IOException {
        require(exception != null, "Missing exact-install exception receipt");
        require("PUBLISHED_WITH_INSTALL_EXCEPTION".equals(exception.status())
                        && "exact-distribution-install".equals(exception.scope()),
                "Publication exception is outside its narrow scope");
        require("USER_DIRECTED_MAIN_README_PUBLICATION".equals(exception.authorization()),
                "Publication exception lacks the user's explicit publication direction");
        require(exception.reason() != null && !exception.reason().isBlank(), "Publication exception has no concrete reason");
        require(candidate.commit().equals(exception.candidateCommit()), "Publication exception belongs to another source commit");
        require(candidate.apkSha256().equals(exception.candidateApkSha256()), "Publication exception names another distribution APK");
        require(exception.supplementalApkSha256() != null
                        && exception.supplementalApkSha256().matches("[a-f0-9]{64}")
                        && !candidate.apkSha256().equals(exception.supplementalApkSha256()),
                "Supplemental sanity must identify a different APK");
        require(exception.supplementalAssertions() > 0 && !exception.distributionInstalled(),
                "Publication exception must disclose that only supplemental assertions ran");
        require("CLEAN_INSTALL_REQUIRED".equals(exception.signingStatus()),
                "Publication exception has an inconsistent signing transition");
        require(exception.supplementalExtent() != null && Files.isRegularFile(exception.supplementalExtent())
                        && digest(exception.supplementalExtent()).equals(exception.supplementalExtentSha256()),
                "Supplemental Extent evidence is missing or changed");
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

    public static String signingContinuity(String previousFingerprint, String candidateFingerprint, String installMode)
            throws IOException {
        String previous = fingerprint(previousFingerprint);
        String candidate = fingerprint(candidateFingerprint);
        require(List.of("UPGRADE", "CLEAN_INSTALL").contains(installMode), "Unknown signing transition mode");
        if (previous.equals(candidate)) return "UPGRADE_COMPATIBLE";
        require("CLEAN_INSTALL".equals(installMode),
                "Signing certificate differs; an in-place upgrade is blocked and requires an explicit clean-install mode");
        return "CLEAN_INSTALL_REQUIRED";
    }

    private static String fingerprint(String value) throws IOException {
        require(value != null && value.replace(":", "").matches("(?i)[0-9a-f]{64}"), "Invalid signing certificate fingerprint");
        return value.replace(":", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static List<String> gameLinks(String readme) throws IOException {
        var game = GAME.matcher(readme);
        require(game.find(), "Missing Jocul section");
        String before = readme.substring(0, game.start());
        String after = readme.substring(game.end());
        var rows = game.group(1).lines().filter(line -> !line.isBlank()).toList();
        require(!game.find(), "Duplicate Jocul section");
        require(!SECTION.matcher(before).find(), "Jocul must be the first section");
        require(rows.size() == 2, "Jocul must contain exactly two link bullets");
        var links = new ArrayList<String>(rows.size());
        for (String row : rows) {
            var link = LINK_BULLET.matcher(row);
            require(link.matches(), "Jocul must contain exactly two link bullets");
            require(!INTEGRITY_LABEL.matcher(link.group(1)).find(), "Integrity metadata belongs in the release evidence");
            links.add(link.group(2));
        }
        require(!APK_ROUTE.matcher(before).find() && !APK_ROUTE.matcher(after).find(), "APK route outside Jocul");
        return List.copyOf(links);
    }

    static void apk(Candidate candidate, Path apk) throws IOException {
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
