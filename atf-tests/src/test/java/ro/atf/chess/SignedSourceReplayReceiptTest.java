package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.capture.ReplayViewportProfile;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class SignedSourceReplayReceiptTest {
    @TempDir Path folder;

    @Test void enlargedProfilesRequirePhysicalRotationAndEveryBackAssertion(Checks check) throws Exception {
        for (String profile : List.of("360x640", "740x420", "1280x900")) for (String locale : List.of("en", "ro")) {
            var display = ReplayViewportProfile.of(profile, true);
            String valid = receipt(display, locale);
            check.same(profile + " " + locale + " binds all 23 native checks, enlarged text and the actual turn", true, read(valid, profile, locale));
            check.same(profile + " cannot borrow a 1.0 font run", false, read(valid.replace("font=1.3", "font=1.0"), profile, locale));
            check.same(profile + " cannot borrow an opposite display rotation", false,
                    read(valid.replace("replayRotation=" + display.rotation(), "replayRotation=" + (1 - display.rotation())), profile, locale));
            check.same(profile + " rejects a missing rotation marker", false,
                    read(valid.replaceAll("(?m)^INSTRUMENTATION_RESULT: replayRotation=.*\\R", ""), profile, locale));
            check.same(profile + " rejects an unproven Back return despite PASS", false,
                    read(valid.replaceAll("(?m)^INSTRUMENTATION_RESULT: check_\\d+=calculated Back.*\\R", ""), profile, locale));
            check.same(profile + " cannot hide a failed Back return among the other successful checks", false,
                    read(valid.replace("calculated Back restores the source window and board\t316\t316\ttrue",
                            "calculated Back restores the source window and board\ttrue\tfalse\ttrue"), profile, locale));
        }
        check.rejects("Unknown profile cannot be silently resized", IllegalArgumentException.class,
                () -> ReplayViewportProfile.of("640x360", true));
        check.rejects("Unknown locale cannot borrow another translation", IllegalArgumentException.class,
                () -> ReplayViewportProfile.of("360x640", true).marker("fr"));
    }

    @Test void signedIdentityRequiresTheInstalledBytesAndMatchingRealSigner(Checks check) {
        var identity = new ObjectMapper().createObjectNode().put("variant", "release").put("sourceCommit", "a".repeat(40))
                .put("apkSha256", "b".repeat(64)).put("installedApkSha256", "b".repeat(64))
                .put("signingCertificateSha256", "c".repeat(64)).put("testCertificateSha256", "c".repeat(64));
        check.same("The signed app, test signer and installed bytes are bound", true, AndroidSanityPublication.signedSourceIdentity(identity));
        for (String field : List.of("variant", "sourceCommit", "apkSha256", "installedApkSha256", "signingCertificateSha256", "testCertificateSha256")) {
            var missing = identity.deepCopy();
            missing.remove(field);
            check.same("Signed qualification rejects missing " + field, false, AndroidSanityPublication.signedSourceIdentity(missing));
        }
        check.same("A sanity build cannot qualify the signed APK", false,
                AndroidSanityPublication.signedSourceIdentity(identity.deepCopy().put("variant", "sanity")));
        check.same("A different test certificate cannot qualify instrumentation", false,
                AndroidSanityPublication.signedSourceIdentity(identity.deepCopy().put("testCertificateSha256", "d".repeat(64))));
        check.same("A different installed APK cannot borrow the receipt", false,
                AndroidSanityPublication.signedSourceIdentity(identity.deepCopy().put("installedApkSha256", "d".repeat(64))));
    }

    @Test void sourceGeometryAndFixedTargetsRemainRequiredInTheEnlargedPhase(Checks check) throws Exception {
        var profile = ReplayViewportProfile.of("360x640", true);
        String valid = receipt(profile, "en");
        check.same("The source/replay side cannot drift at a larger font", false,
                read(valid.replace("\t316\t316\ttrue", "\t316\t300\ttrue"), profile.logical(), "en"));
        check.same("A passing but unrelated assertion cannot replace branch coverage", false,
                read(valid.replace("calculated branch keeps the board and control rectangles", "The app launched"), profile.logical(), "en"));
        check.same("Duplicate rotation evidence fails closed", false,
                read(valid + "INSTRUMENTATION_RESULT: replayRotation=0\n", profile.logical(), "en"));
    }

    @Test void projectSigningRecordIsRelativeAndContainsNoPlaintextPassword(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        Path directory = root.resolve(".atf-signing");
        var manifest = new ObjectMapper().readTree(directory.resolve("identity.json").toFile());
        check.same("The encrypted key remains relocatable between worktrees", "chesslab-release.p12", manifest.path("store").asText());
        check.same("Only the DPAPI-wrapped password is named by the manifest", "password.dpapi", manifest.path("credential").asText());
        String protectedPassword = Files.readString(directory.resolve("password.dpapi")).trim();
        check.same("The committed credential is the Windows DPAPI hex envelope", true,
                protectedPassword.startsWith("01000000d08c9ddf") && protectedPassword.matches("[a-fA-F0-9]+"));
        String certificate = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(directory.resolve("certificate.der"))));
        check.same("The public certificate binds the protected identity", certificate, manifest.path("certificateSha256").asText());
        check.same("The explicitly changed signer declares a clean installation", true, manifest.path("cleanInstallRequired").asBoolean());
        var initializer = Files.readString(root.resolve("scripts/Initialize-J7Signing.ps1"));
        check.same("Project storage is explicit; legacy external records remain supported", true,
                initializer.contains("[switch]$ProjectIdentity") && initializer.contains("infrastructure/signing")
                        && initializer.contains("IsPathRooted($record.store)") && initializer.contains("[switch]$StartNewIdentity"));
        String ignore = Files.readString(directory.resolve(".gitignore"));
        check.same("Only the named encrypted record is allowed through the directory ignore", true,
                ignore.lines().findFirst().orElse("").equals("*") && ignore.contains("!password.dpapi") && !ignore.contains("!*.log"));
    }

    private boolean read(String content, String profile, String locale) throws Exception {
        Path file = Files.createTempFile(folder, "receipt-", ".txt");
        Files.writeString(file, content);
        return AndroidSanityPublication.readSourceReplayPhase(file, profile, locale, true).passed();
    }

    private String receipt(ReplayViewportProfile profile, String locale) {
        var text = new StringBuilder("INSTRUMENTATION_RESULT: phase=source-sized-replay-enlarged\n"
                + "INSTRUMENTATION_RESULT: pid=123\nINSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n"
                + "INSTRUMENTATION_RESULT: replayProfile=" + profile.marker(locale) + "\n"
                + "INSTRUMENTATION_RESULT: replayRotation=" + profile.rotation() + "\n");
        var labels = AndroidSanityPublication.SIGNED_SOURCE_REPLAY_CHECKS;
        for (int id = 0; id < labels.size(); id++) text.append(String.format(Locale.ROOT,
                "INSTRUMENTATION_RESULT: check_%02d=%s\t316\t316\ttrue%n", id + 1, labels.get(id)));
        return text.toString();
    }
}
