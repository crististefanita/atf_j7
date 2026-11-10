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
        var layout = read("layout/main_menu.xml");
        check.same("Short windows can scroll the menu", "ScrollView", layout.getTagName());
        var buttons = layout.getElementsByTagName("Button");
        var entries = new ArrayList<String>();
        var enabled = new ArrayList<Boolean>();
        for (int index = 0; index < buttons.getLength(); index++) {
            var button = (Element) buttons.item(index);
            entries.add(button.getAttribute("android:text").replace("@string/", ""));
            enabled.add(!"false".equals(button.getAttribute("android:enabled")));
        }
        check.same("The four puzzle chapters precede new and saved local games",
                List.of("open_mate_one", "open_mate_two", "open_mate_three", "open_tactics", "new_match", "resume_match"), entries);
        check.same("Every menu entry is playable when shown", List.of(true, true, true, true, true, true), enabled);
        check.same("Resume is hidden until a game is saved", "gone", ((Element) buttons.item(5)).getAttribute("android:visibility"));
        check.same("English labels identify each mode",
                List.of("Mate in 1", "Mate in 2", "Mate in 3", "Tactics", "New game vs computer", "Resume game"), labels("values/strings.xml", entries));
        check.same("Romanian labels identify each mode",
                List.of("Mat în 1", "Mat în 2", "Mat în 3", "Tactică", "Partidă nouă cu calculatorul", "Reia partida"), labels("values-ro/strings.xml", entries));
    }

    @Test void reservesSpaceForTotalsAndUsesAnAccessibleBulb(Checks check) throws Exception {
        var rows = read("layout/main_menu.xml").getElementsByTagName("TextView");
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
