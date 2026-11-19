package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintArrowTest {
    private static final String[] MOVES = {"a1a2", "h8h7", "a1b1", "h1g1", "a1h8", "a8h1", "h8a1", "b1c3", "g8f6", "a4h4", "d1d8"};
    private static final int[] SIZES = {256, 320, 393, 720, 1080, 1081};
    @TempDir Path output;

    record Cell(double x, double y, double width, double height) {
        double centerX() { return x + width / 2; }
        double centerY() { return y + height / 2; }
    }

    record Example(String id, String from, String to, boolean blackBelow, int boardPixels, double ratio,
                   Cell source, Cell destination, HintArrow shape) { }

    @Test void tipAdvancesTowardsTheSquareCenterAtEveryScaleAndOrientation(Checks check) {
        for (var example : examples()) {
            var a = example.source();
            var b = example.destination();
            var arrow = example.shape();
            double x = a.centerX() + arrow.tip() * Math.cos(arrow.radians());
            double y = a.centerY() + arrow.tip() * Math.sin(arrow.radians());
            double dx = b.centerX() - a.centerX();
            double dy = b.centerY() - a.centerY();
            double distance = Math.hypot(dx, dy);
            double fraction = 1 - .20 * a.width() / distance;
            check.same(example.id() + " tip reaches the expected measured-cell position",
                    point(a.centerX() + fraction * dx, a.centerY() + fraction * dy), point(x, y));
            check.same(example.id() + " leaves one fifth of a cell before its destination center",
                    "0.200000", number(Math.hypot(b.centerX() - x, b.centerY() - y) / a.width()));
            check.same(example.id() + " keeps the origin contour clear", "0.310000", number(arrow.start() / a.width()));
            check.same(example.id() + " retains the head and stroke proportions", List.of("0.170000", "0.029000", "0.106000", "0.027000"),
                    List.of(number((arrow.tip() - arrow.head()) / a.width()), number(arrow.halfShaft() / a.width()),
                            number(arrow.halfHead() / a.width()), number(arrow.outline() / a.width())));
            check.same(example.id() + " has a forward-facing, nonoverlapping shaft", true, arrow.head() > arrow.start());
        }
    }

    @Test void webSvgAndReviewCanvasKeepTheNativeGeometry(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        var mapper = new ObjectMapper();
        Path input = output.resolve("hint-arrow-cases.json");
        Path log = output.resolve("hint-arrow-result.json");
        var examples = examples();
        mapper.writeValue(input.toFile(), examples);
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                root.resolve("web/tests/hint-arrow-probe.mjs").toString(), input.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The shared Web arrow probe completes without a browser or emulator", true, finished);
        check.same("The actual SVG and review Canvas adapters pass their geometric checks", "exit 0",
                process.exitValue() == 0 ? "exit 0" : Files.readString(log));
        var actual = mapper.readTree(log.toFile());
        check.same("Every measured native case is checked on the Web", examples.size(), actual.size());
        for (int index = 0; index < examples.size(); index++) {
            var example = examples.get(index);
            var row = actual.get(index);
            check.same(example.id() + " retains its shared fixture identity", example.id(), row.path("id").asText());
            check.same(example.id() + " Canvas tip matches native coordinates",
                    point(example.shape().tip() * Math.cos(example.shape().radians()), example.shape().tip() * Math.sin(example.shape().radians())),
                    List.of(row.path("canvasX").asText(), row.path("canvasY").asText()));
            check.same(example.id() + " SVG retains its destination-center clearance", "0.200000", row.path("svgGap").asText());
        }
    }

    @Test void invalidCellGeometryIsRejectedBeforeDrawing(Checks check) {
        for (double unit : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            check.rejects("A nonpositive or nonfinite cell width is rejected: " + unit, IllegalArgumentException.class,
                    () -> HintArrow.between(100, 0, unit));
        }
        for (double[] vector : new double[][]{{0, 0}, {10, 0}, {Double.NaN, 0}, {0, Double.POSITIVE_INFINITY}}) {
            check.rejects("A collapsed or nonfinite arrow is rejected: " + List.of(vector[0], vector[1]), IllegalArgumentException.class,
                    () -> HintArrow.between(vector[0], vector[1], 50));
        }
    }

    @Test void androidUsesTheTestedArrowModel(Checks check) throws Exception {
        String source = Files.readString(ProjectRoot.locate().resolve("android/app/src/main/java/ro/atf/chess/android/BoardView.java"));
        check.same("The Android adapter delegates measured cell centers to the tested geometry", true,
                source.matches("(?s).*HintArrow\\.between\\(\\s*dx\\s*,\\s*dy\\s*,\\s*unit\\s*\\).*"));
        check.same("Android draws the returned tip, head, width and outline", true,
                List.of(".tip()", ".head()", ".halfShaft()", ".halfHead()", ".outline()").stream().allMatch(source::contains));
    }

    private static List<Example> examples() {
        var result = new ArrayList<Example>();
        for (int size : SIZES) for (boolean black : new boolean[]{false, true}) for (String move : MOVES) {
            String from = move.substring(0, 2), to = move.substring(2);
            Cell a = cell(from, black, size), b = cell(to, black, size);
            double ratio = switch (size) { case 320 -> 1.25; case 393 -> 3; default -> 2; };
            result.add(new Example(move + "/" + (black ? "black" : "white") + "/" + size, from, to, black, size, ratio, a, b,
                    HintArrow.between(b.centerX() - a.centerX(), b.centerY() - a.centerY(), a.width())));
        }
        return result;
    }

    private static Cell cell(String square, boolean black, int size) {
        int column = square.charAt(0) - 'a', row = 8 - Character.digit(square.charAt(1), 10);
        if (black) { column = 7 - column; row = 7 - row; }
        int left = size * column / 8, top = size * row / 8;
        return new Cell(37 + left, 61 + top, size * (column + 1) / 8 - left, size * (row + 1) / 8 - top);
    }

    private static List<String> point(double x, double y) { return List.of(number(x), number(y)); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.6f", Math.abs(value) < 1e-10 ? 0 : value); }
}
