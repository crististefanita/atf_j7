package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.w3c.dom.Element;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MainMenuLayoutTest {
    private static final Path RES = Path.of("android/app/src/main/res");

    @Test void offersChaptersAndLocalPlayInBothLanguages(Checks check) throws Exception {
        var home = read("layout/main_menu.xml");
        check.same("Short windows can scroll the menu", "ScrollView", home.getTagName());
        var main = home.getElementsByTagName("Button");
        var ids = new ArrayList<String>();
        for (int index = 0; index < main.getLength(); index++) ids.add(((Element) main.item(index)).getAttribute("android:id"));
        check.same("The home screen exposes games, puzzles, openings and the editable profile",
                List.of("@+id/new_match", "@+id/resume_match", "@+id/open_puzzles", "@+id/open_openings", "@+id/edit_player_name"), ids);
        check.same("Openings has an independent localized home entry", List.of("Openings", "Deschideri"),
                List.of(labels("values/strings.xml", List.of("openings_title")).getFirst(),
                        labels("values-ro/strings.xml", List.of("openings_title")).getFirst()));
        check.same("Resume is hidden until a game is saved", "gone", ((Element) main.item(1)).getAttribute("android:visibility"));
        var buttons = read("layout/puzzle_menu.xml").getElementsByTagName("Button");
        var entries = new ArrayList<String>();
        for (int index = 0; index < buttons.getLength() - 1; index++) {
            var button = (Element) buttons.item(index);
            entries.add(button.getAttribute("android:text").replace("@string/", ""));
        }
        check.same("All four chapters are grouped under Puzzles", List.of("open_mate_one", "open_mate_two", "open_mate_three", "open_tactics"), entries);
        check.same("English labels identify each mode",
                List.of("Mate in 1", "Mate in 2", "Mate in 3", "Tactics"), labels("values/strings.xml", entries));
        check.same("Romanian labels identify each mode",
                List.of("Mat în 1", "Mat în 2", "Mat în 3", "Tactică"), labels("values-ro/strings.xml", entries));
    }

    @Test void theTwoDropdownsPresentChoicesInTheOrderTheMatchConsumesThem(Checks check) throws Exception {
        var spinners = read("layout/match_setup.xml").getElementsByTagName("Spinner");
        check.same("The match has two dropdowns", 2, spinners.getLength());
        var arrays = read("values/match_arrays.xml").getElementsByTagName("string-array");
        check.same("Seat choices map in White, Black, Random order", List.of("@string/white", "@string/black", "@string/match_random_color"),
                items((Element) arrays.item(0)));
        check.same("AI choices map from Basic to Hard", List.of("@string/match_basic", "@string/match_advanced", "@string/match_hard"),
                items((Element) arrays.item(1)));
        for (int index = 0; index < spinners.getLength(); index++) {
            var spinner = (Element) spinners.item(index);
            check.same("Selector " + (index + 1) + " is a dropdown with the corresponding choices", List.of("dropdown", "@array/" + ((Element) arrays.item(index)).getAttribute("name")),
                    List.of(spinner.getAttribute("android:spinnerMode"), spinner.getAttribute("android:entries")));
        }
    }

    @Test void reservesSpaceForTotalsAndUsesAnAccessibleBulb(Checks check) throws Exception {
        var rows = read("layout/puzzle_menu.xml").getElementsByTagName("TextView");
        var totals = new ArrayList<String>();
        for (int index = 0; index < rows.getLength(); index++) {
            String id = ((Element) rows.item(index)).getAttribute("android:id");
            if (id.endsWith("_totals")) totals.add(id);
        }
        check.same("Overall and per-chapter totals each have a dedicated text view", List.of("@+id/quest_totals", "@+id/chapter_one_totals",
                "@+id/chapter_two_totals", "@+id/chapter_three_totals", "@+id/chapter_tactics_totals"), totals);
        var bulb = read("layout/quest_guidance.xml");
        check.same("Guidance is icon-only and retains its accessible label", List.of("ImageButton", "@drawable/quest_bulb", "@string/hint"),
                List.of(bulb.getTagName(), bulb.getAttribute("android:src"), bulb.getAttribute("android:contentDescription")));
        check.same("The icon keeps a 48 dp touch target", List.of("48dp", "48dp"),
                List.of(bulb.getAttribute("android:layout_width"), bulb.getAttribute("android:layout_height")));
        check.same("The flame glyph is compact", "16dp", read("drawable/quest_flame.xml").getAttribute("android:width"));
    }

    private static List<String> items(Element array) {
        var children = array.getElementsByTagName("item");
        var values = new ArrayList<String>();
        for (int index = 0; index < children.getLength(); index++) values.add(children.item(index).getTextContent());
        return values;
    }

    private static Element read(String path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(RES.resolve(path).toFile()).getDocumentElement();
    }

    private static List<String> labels(String path, List<String> names) throws Exception {
        var strings = read(path).getElementsByTagName("string");
        var labels = new java.util.HashMap<String, String>();
        for (int index = 0; index < strings.getLength(); index++) {
            var entry = (Element) strings.item(index);
            labels.put(entry.getAttribute("name"), entry.getTextContent());
        }
        return names.stream().map(labels::get).toList();
    }
}
