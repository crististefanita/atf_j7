package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.w3c.dom.Element;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PuzzleChromeResourcesTest {
    private static final Path RES = ProjectRoot.locate().resolve("android/app/src/main/res");
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test void exposesStableLocalizedPuzzleOptionsAndNavigation(Checks check) throws Exception {
        var expectedIds = List.of("puzzle_options", "puzzle_options_panel", "puzzle_flip_board", "puzzle_main_menu",
                "puzzle_chapter", "puzzle_rank", "puzzle_turn", "puzzle_points", "puzzle_streak", "quest_previous", "quest_next");
        var ids = elements(read("values/puzzle_chrome_ids.xml"), "item")
                .filter(element -> element.getAttribute("type").equals("id"))
                .map(element -> element.getAttribute("name"))
                .filter(expectedIds::contains).sorted().toList();
        check.same("The header, rewards, options and arrows each expose a distinct Android ID",
                expectedIds.stream().sorted().toList(), ids);

        var english = read("values/strings.xml");
        var romanian = read("values-ro/strings.xml");
        var englishChrome = read("values/puzzle_chrome.xml");
        var romanianChrome = read("values-ro/puzzle_chrome.xml");
        check.same("The English options name both actions clearly", List.of("Puzzle options", "Flip board", "Main menu"),
                List.of(label(englishChrome, "puzzle_options"), label(english, "flip"), label(englishChrome, "puzzle_main_menu")));
        check.same("The Romanian options name both actions clearly", List.of("Opțiuni puzzle", "Întoarce tabla", "Meniu principal"),
                List.of(label(romanianChrome, "puzzle_options"), label(romanian, "flip"), label(romanianChrome, "puzzle_main_menu")));
        var arrowLabels = List.of("previous_quest", "next_quest", "first_unsolved_quest");
        check.same("English distinguishes advancing from returning to the first unsolved puzzle",
                List.of("Previous", "Next", "First unsolved"), arrowLabels.stream().map(name -> label(english, name)).toList());
        check.same("Romanian distinguishes advancing from returning to the first unsolved puzzle",
                List.of("Precedentă", "Următoarea", "Prima nerezolvată"), arrowLabels.stream().map(name -> label(romanian, name)).toList());
        check.same("The rank has a spoken form that distinguishes the ordinal and chapter size",
                List.of("7 / 300", "Puzzle 7 of 300", "Problema 7 din 300"),
                List.of(label(englishChrome, "puzzle_rank").formatted(7, 300),
                        label(englishChrome, "puzzle_rank_spoken").formatted(7, 300),
                        label(romanianChrome, "puzzle_rank_spoken").formatted(7, 300)));
    }

    @Test void keepsEachChapterActionLinkedToItsLocalizedName(Checks check) throws Exception {
        var entries = List.of(
                new Chapter("chapter_one", "open_mate_one", "Mate in 1", "Mat în 1"),
                new Chapter("chapter_two", "open_mate_two", "Mate in 2", "Mat în 2"),
                new Chapter("chapter_three", "open_mate_three", "Mate in 3", "Mat în 3"),
                new Chapter("chapter_tactics", "open_tactics", "Tactics", "Tactică"),
                new Chapter("chapter_training", "open_training", "Tactical training", "Antrenament tactic"),
                new Chapter("chapter_intermezzo", "open_intermezzo", "In-between move", "Mutarea intermediară"));
        var english = read("values/strings.xml");
        var romanian = read("values-ro/strings.xml");
        var actual = elements(read("layout/puzzle_menu.xml"), "Button")
                .filter(button -> button.getAttributeNS(ANDROID, "id").startsWith("@+id/chapter_"))
                .map(button -> {
                    String name = button.getAttributeNS(ANDROID, "text").replace("@string/", "");
                    return new Chapter(button.getAttributeNS(ANDROID, "id").replace("@+id/", ""), name,
                            label(english, name), label(romanian, name));
                }).toList();
        check.same("All six entries keep their existing IDs, order and chapter-specific translations", entries, actual);
    }

    @Test void keepsNavigationGlyphsCompactAndTheHintTouchTargetAccessible(Checks check) throws Exception {
        var chevron = read("drawable/match_chevron.xml");
        check.same("The shared chevron is a compact 18 dp vector", List.of("vector", "18dp", "18dp"),
                List.of(chevron.getTagName(), chevron.getAttributeNS(ANDROID, "width"), chevron.getAttributeNS(ANDROID, "height")));
        var menu = read("drawable/puzzle_menu.xml");
        check.same("The menu glyph fits within its 48 dp control", List.of("vector", "24dp", "24dp"),
                List.of(menu.getTagName(), menu.getAttributeNS(ANDROID, "width"), menu.getAttributeNS(ANDROID, "height")));
        var lamp = read("layout/quest_guidance.xml");
        check.same("The lamp has one 48 dp image control with a localized accessible name",
                List.of("ImageButton", "@+id/quest_guidance", "48dp", "48dp", "@drawable/quest_bulb", "@string/hint"),
                List.of(lamp.getTagName(), lamp.getAttributeNS(ANDROID, "id"), lamp.getAttributeNS(ANDROID, "layout_width"),
                        lamp.getAttributeNS(ANDROID, "layout_height"), lamp.getAttributeNS(ANDROID, "src"),
                        lamp.getAttributeNS(ANDROID, "contentDescription")));
    }

    private record Chapter(String id, String resource, String english, String romanian) { }

    private static Element read(String path) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(RES.resolve(path).toFile()).getDocumentElement();
    }

    private static Stream<Element> elements(Element root, String tag) {
        var found = root.getElementsByTagName(tag);
        return IntStream.range(0, found.getLength()).mapToObj(index -> (Element) found.item(index)).filter(Objects::nonNull);
    }

    private static String label(Element root, String name) {
        return elements(root, "string").filter(element -> element.getAttribute("name").equals(name))
                .map(Element::getTextContent).findFirst().orElseThrow(() -> new AssertionError("Missing string " + name));
    }
}
