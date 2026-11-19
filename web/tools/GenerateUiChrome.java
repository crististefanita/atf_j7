import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import ro.atf.chess.UiChrome;

/** Emits the same chrome policy consumed directly by the Android adapter. */
public final class GenerateUiChrome {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the generated module path");
        String backs = Arrays.stream(UiChrome.Page.values())
                .map(page -> "    " + page.name() + ": '" + page.back().name() + "'").collect(Collectors.joining(",\n"));
        String module = "// Generated from ro.atf.chess.UiChrome by web/tools/GenerateUiChrome.java.\n"
                + "export const chrome = Object.freeze({ target: " + UiChrome.TARGET + ", title: " + UiChrome.TITLE
                + ", gap: " + UiChrome.GAP + ", inset: " + UiChrome.INSET + ", navigationMax: " + UiChrome.NAVIGATION_MAX
                + ", compactWidth: " + UiChrome.COMPACT_WIDTH + " });\n"
                + "export const backTargets = Object.freeze({\n" + backs + "\n});\n";
        Files.writeString(Path.of(args[0]), module);
    }
}
