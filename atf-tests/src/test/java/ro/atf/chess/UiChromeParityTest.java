package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class UiChromeParityTest {
    record Profile(String id, int width, int height, double density, double font, int railWidth,
                   List<Integer> anchor, List<Integer> panel, List<Integer> menu) { }
    record Cases(Map<String, String> pages, List<Profile> profiles) { }

    @Test void sharedProfilesKeepTheRailCenteredAndMenusAtTheirTrigger(Checks check) throws Exception {
        Cases cases;
        try (var input = getClass().getResourceAsStream("/ui-chrome-cases.json")) {
            cases = new ObjectMapper().readValue(input, Cases.class);
        }
        for (var entry : cases.pages().entrySet()) {
            check.same(entry.getKey() + " has a contextual Back destination", entry.getValue(), UiChrome.Page.valueOf(entry.getKey()).back().name());
        }
        check.same("Opening history Back returns to the played position", UiChrome.Back.SOURCE, UiChrome.back(UiChrome.Page.OPENING, true));
        check.same("Live opening Back returns to the catalog", UiChrome.Back.OPENINGS, UiChrome.back(UiChrome.Page.OPENING, false));
        check.same("Every screen is represented", UiChrome.Page.values().length, cases.pages().size());
        for (var p : cases.profiles()) {
            var rail = UiChrome.navigation(p.width());
            check.same(p.id() + " uses the shared width", p.railWidth(), rail.width());
            check.same(p.id() + " centers controls within one pixel", true, Math.abs(2 * rail.left() + rail.width() - p.width()) <= 1);
            var pixels = UiChrome.navigation((int) Math.round(p.width() * p.density()), p.density());
            check.same(p.id() + " Android density preserves logical rail width", true, Math.abs(pixels.width() / p.density() - rail.width()) <= 1);
            var a = p.anchor();
            var menu = UiChrome.options(p.width(), p.height(), a.get(0), a.get(1), a.get(2), a.get(3), p.panel().get(0), p.panel().get(1), 4, 8);
            check.same(p.id() + " options follow their own trigger and fit the viewport", p.menu(), List.of(menu.left(), menu.top(), menu.width(), menu.height()));
            check.same(p.id() + " panel never covers its trigger", true, menu.top() >= a.get(3) || menu.top() + menu.height() <= a.get(1));
        }
        check.rejects("Negative width is rejected", IllegalArgumentException.class, () -> UiChrome.navigation(-1));
        for (double density : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            check.rejects("Invalid density " + density + " is rejected", IllegalArgumentException.class, () -> UiChrome.navigation(360, density));
        }
    }

    @Test void generatedWebPolicyAndExecutableAdapterUseTheSameCases(Checks check, @TempDir Path scratch) throws Exception {
        Path root = ProjectRoot.locate();
        Path generated = scratch.resolve("chrome-contract.mjs");
        run(check, root, scratch.resolve("generator.log"), List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", Path.of(UiChrome.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                root.resolve("web/tools/GenerateUiChrome.java").toString(), generated.toString()));
        check.same("The Web contract is generated from Java without drift", Files.readString(generated).replace("\r\n", "\n"),
                Files.readString(root.resolve("web/src/main/web/chrome-contract.mjs")).replace("\r\n", "\n"));
        Path log = scratch.resolve("node.tap");
        run(check, root, log, List.of("node", "--test", "--test-reporter=tap", "web/tests/chrome-contract.test.mjs"));
        String tap = Files.readString(log);
        check.same("All five executable Web projections passed", true, tap.contains("# tests 5") && tap.contains("# pass 5") && tap.contains("# fail 0"));
    }

    private static void run(Checks check, Path root, Path log, List<String> command) throws Exception {
        var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean ended = process.waitFor(45, TimeUnit.SECONDS);
        if (!ended) process.destroyForcibly();
        check.same("Adapter command completed: " + command.get(0), true, ended);
        check.same("Adapter command succeeded: " + Files.readString(log), 0, process.exitValue());
    }
}
