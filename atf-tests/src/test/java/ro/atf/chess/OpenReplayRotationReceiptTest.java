package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpenReplayRotationReceiptTest {
    @TempDir Path folder;

    @Test void requiresEveryRecreationAndTheExactEnlargedProfile(Checks check) throws Exception {
        check.same("The phase requires the complete 32 native observations", 32, AndroidSanityPublication.OPEN_REPLAY_ROTATION_CHECKS.size());
        for (String locale : List.of("en", "ro")) {
            String valid = receipt(locale);
            check.same("Complete rotation evidence passes in " + locale, true, read(valid, locale));
            for (int event = 1; event <= 8; event++) {
                String key = String.format(Locale.ROOT, "rotation_%02d=", event);
                check.same("Missing recreation " + event + " cannot borrow a PASS", false, read(valid.replace(key, "missing="), locale));
            }
            for (String label : AndroidSanityPublication.OPEN_REPLAY_ROTATION_CHECKS) {
                check.same("Missing native assertion fails: " + label, false, read(valid.replace(label, "App started"), locale));
            }
            check.same("A normal font cannot qualify 1.3", false, read(valid.replace("font=1.3", "font=1.0"), locale));
            check.same("Another source width cannot qualify", false, read(valid.replace("720x1280", "800x1280"), locale));
            check.same("Duplicate rotation markers fail closed", false,
                    read(valid + "INSTRUMENTATION_RESULT: rotation_01=ChessActivity recreated at 1\n", locale));
            check.same("A native FAIL remains FAIL", false, read(valid.replace("result=PASS", "result=FAIL"), locale));
        }
    }

    private boolean read(String text, String locale) throws Exception {
        Path file = Files.createTempFile(folder, "rotation-", ".txt");
        Files.writeString(file, text);
        return AndroidSanityPublication.readOpenReplayRotationPhase(file, locale).passed();
    }

    private String receipt(String locale) {
        var text = new StringBuilder("INSTRUMENTATION_RESULT: phase=open-replay-rotation\n"
                + "INSTRUMENTATION_RESULT: pid=123\nINSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n"
                + "INSTRUMENTATION_RESULT: rotationProfile=720x1280@320, font=1.3, language=" + locale + "\n");
        for (int event = 1; event <= 8; event++) text.append(String.format(Locale.ROOT,
                "INSTRUMENTATION_RESULT: rotation_%02d=%s recreated at %d%n", event,
                event == 5 || event == 6 ? "AnalysisActivity" : "ChessActivity", event % 2 == 1 ? 1 : 0));
        var labels = AndroidSanityPublication.OPEN_REPLAY_ROTATION_CHECKS;
        for (int index = 0; index < labels.size(); index++) text.append(String.format(Locale.ROOT,
                "INSTRUMENTATION_RESULT: check_%02d=%s\ttrue\ttrue\ttrue%n", index + 1, labels.get(index)));
        return text.toString();
    }
}
