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
    private static final Path RES = ProjectRoot.locate().resolve("android/app/src/main/res");

    @Test void offersChaptersAndLocalPlayInBothLanguages(Checks check) throws Exception {
        var home = read("layout/main_menu.xml");
        check.same("Short windows can scroll the menu", "ScrollView", home.getTagName());
        var main = home.getElementsByTagName("Button");
        var ids = new ArrayList<String>();
        for (int index = 0; index < main.getLength(); index++) ids.add(((Element) main.item(index)).getAttribute("android:id"));
        check.same("The home screen exposes active games, the archive, puzzles, openings, manual analysis and the editable profile",
                List.of("@+id/new_match", "@+id/resume_match", "@+id/match_archive", "@+id/open_puzzles", "@+id/open_openings",
                        "@+id/open_analysis", "@+id/edit_player_name"), ids);
        var groups = new java.util.LinkedHashMap<String, List<String>>();
        for (int index = 0; index < main.getLength(); index++) {
            var button = (Element) main.item(index);
            var parent = (Element) button.getParentNode();
            groups.computeIfAbsent(parent.getAttribute("android:id"), ignored -> new ArrayList<>()).add(button.getAttribute("android:id"));
        }
        check.same("Games, study and profile have separate visual and structural groups", java.util.Map.of(
                "@+id/home_games", List.of("@+id/new_match", "@+id/resume_match", "@+id/match_archive"),
                "@+id/home_study", List.of("@+id/open_puzzles", "@+id/open_openings", "@+id/open_analysis"),
                "@+id/home_profile", List.of("@+id/edit_player_name")), groups);
        check.same("Only creating a game uses the primary action style", List.of("@+id/new_match"),
                java.util.stream.IntStream.range(0, main.getLength()).mapToObj(index -> (Element) main.item(index))
                        .filter(button -> button.getAttribute("style").equals("@style/HomePrimaryAction"))
                        .map(button -> button.getAttribute("android:id")).toList());
        check.same("Study has a localized section heading", List.of("Study", "Studiu"),
                List.of(labels("values/strings.xml", List.of("menu_study")).getFirst(),
                        labels("values-ro/strings.xml", List.of("menu_study")).getFirst()));
        check.same("The archive is independently accessible in both languages", List.of("Finished games", "Partide încheiate"),
                List.of(labels("values/strings.xml", List.of("match_archive")).getFirst(),
                        labels("values-ro/strings.xml", List.of("match_archive")).getFirst()));
        check.same("Openings has an independent localized home entry", List.of("Openings", "Deschideri"),
                List.of(labels("values/strings.xml", List.of("openings_title")).getFirst(),
                        labels("values-ro/strings.xml", List.of("openings_title")).getFirst()));
        check.same("Resume is hidden until a game is saved", "gone", ((Element) main.item(1)).getAttribute("android:visibility"));
        check.same("Saved games has one stable menu label", "@string/match_saved_games", ((Element) main.item(1)).getAttribute("android:text"));
        check.same("Manual analysis is available independently of a saved game in both languages", List.of("Analysis board", "Tablă de analiză"),
                List.of(labels("values/strings.xml", List.of("manual_analysis")).getFirst(),
                        labels("values-ro/strings.xml", List.of("manual_analysis")).getFirst()));
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

    @Test void gameListsFollowThePuzzleMenuAndKeepBackOutsideTheScroll(Checks check) throws Exception {
        var page = read("layout/match_list.xml");
        var heading = (Element) page.getElementsByTagName("TextView").item(0);
        var puzzleHeading = (Element) read("layout/puzzle_menu.xml").getElementsByTagName("TextView").item(0);
        check.same("Game lists use the Puzzles title alignment and size",
                List.of(puzzleHeading.getAttribute("android:gravity"), puzzleHeading.getAttribute("android:textSize")),
                List.of(heading.getAttribute("android:gravity"), heading.getAttribute("android:textSize")));
        check.same("The page exposes an accessibility heading", "true", heading.getAttribute("android:accessibilityHeading"));
        var scroll = (Element) page.getElementsByTagName("ScrollView").item(0);
        var back = (Element) page.getElementsByTagName("Button").item(0);
        check.same("Back is outside the weighted scroll area", List.of(true, "1", "@style/ChapterEntry"),
                List.of(scroll.getParentNode().isSameNode(back.getParentNode()), scroll.getAttribute("android:layout_weight"), back.getAttribute("style")));
        var row = read("layout/match_list_row.xml");
        check.same("Rows retain one focusable target and a vertical layout", List.of("@style/MatchListRow", "true", "vertical"),
                List.of(row.getAttribute("style"), row.getAttribute("android:focusable"), row.getAttribute("android:orientation")));
        var labels = row.getElementsByTagName("TextView");
        var game = (Element) labels.item(0);
        var date = (Element) labels.item(1);
        check.same("The date uses a smaller badge that is hidden for legacy saves", List.of("16sp", "13sp", "wrap_content", "gone", "@drawable/match_date_badge"),
                List.of(game.getAttribute("android:textSize"), date.getAttribute("android:textSize"), date.getAttribute("android:layout_width"),
                        date.getAttribute("android:visibility"), date.getAttribute("android:background")));
        var badge = read("drawable/match_date_badge.xml");
        var badgeFill = ((Element) badge.getElementsByTagName("solid").item(0)).getAttribute("android:color");
        var groupFill = ((Element) read("drawable/home_group.xml").getElementsByTagName("solid").item(0)).getAttribute("android:color");
        check.same("The date has a distinct background and rounded corners", List.of(true, "8dp"),
                List.of(!badgeFill.equals(groupFill), ((Element) badge.getElementsByTagName("corners").item(0)).getAttribute("android:radius")));
        var localized = List.of("match_saved_games", "match_archive", "match_list_back");
        check.same("English titles and Back describe the list screens",
                List.of("Saved games", "Finished games", "Back"), labels("values/strings.xml", localized));
        check.same("Romanian titles and Back describe the list screens",
                List.of("Partide salvate", "Partide încheiate", "Înapoi"), labels("values-ro/strings.xml", localized));
    }

    @Test void theTwoDropdownsPresentChoicesInTheOrderTheMatchConsumesThem(Checks check) throws Exception {
        var setup = read("layout/match_setup.xml");
        var spinners = setup.getElementsByTagName("Spinner");
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
        var notes = setup.getElementsByTagName("TextView");
        var replacement = (Element) notes.item(notes.getLength() - 1);
        check.same("The replacement notice has a localized label and is hidden by default",
                List.of("@+id/match_replacement_note", "@string/match_replacement_note", "gone"),
                List.of(replacement.getAttribute("android:id"), replacement.getAttribute("android:text"), replacement.getAttribute("android:visibility")));
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
