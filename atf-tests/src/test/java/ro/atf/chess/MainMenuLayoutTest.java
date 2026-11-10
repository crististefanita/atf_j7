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

    @Test void keepsPlayableChaptersOrderedAndTodoEntriesInactive(Checks check) throws Exception {
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
        check.same("The menu offers Mat in 1, 2 and 3, then the two planned modes",
                List.of("open_mate_one", "open_mate_two", "open_mate_three", "menu_chess_todo", "menu_analysis_todo"), entries);
        check.same("Only the three puzzle chapters can be opened", List.of(true, true, true, false, false), enabled);
        check.same("English labels identify the chapters and planned modes",
                List.of("Mate in 1", "Mate in 2", "Mate in 3", "Chess — ToDo", "Analysis — ToDo"), labels("values/strings.xml", entries));
        check.same("Romanian labels identify the chapters and planned modes",
                List.of("Mat în 1", "Mat în 2", "Mat în 3", "Șah — ToDo", "Analiză — ToDo"), labels("values-ro/strings.xml", entries));
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
