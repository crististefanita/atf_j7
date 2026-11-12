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
        check.same("All six chapters are grouped under Puzzles", List.of("open_mate_one", "open_mate_two", "open_mate_three", "open_tactics", "open_training", "open_intermezzo"), entries);
        check.same("English labels identify each mode",
                List.of("Mate in 1", "Mate in 2", "Mate in 3", "Tactics", "Tactical training", "In-between move"), labels("values/strings.xml", entries));
        check.same("Romanian labels identify each mode",
                List.of("Mat în 1", "Mat în 2", "Mat în 3", "Tactică", "Antrenament tactic", "Mutarea intermediară"), labels("values-ro/strings.xml", entries));
    }

    @Test void intermezzoExplainsTheMotifInBothLanguages(Checks check) throws Exception {
        var key = List.of("intermezzo_lesson");
        check.same("The chapter explains the forcing move before recapture", List.of("Find the forcing move before the recapture."),
                labels("values/strings.xml", key));
        check.same("The same lesson is localized", List.of("Găsește mutarea forțată înaintea recapturii."),
                labels("values-ro/strings.xml", key));
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
        var fields = row.getElementsByTagName("TextView");
        var title = (Element) fields.item(0);
        var summary = (Element) fields.item(1);
        var date = (Element) fields.item(2);
        check.same("The title and move summary precede the optional timestamp",
                List.of("@+id/match_row_label", "@+id/match_row_details", "@+id/match_row_date"),
                List.of(title.getAttribute("android:id"), summary.getAttribute("android:id"), date.getAttribute("android:id")));
        check.same("Only the title is bold and the type gets smaller down the row", true,
                title.getAttribute("android:textStyle").equals("bold") && !summary.hasAttribute("android:textStyle")
                        && !date.hasAttribute("android:textStyle") && fontSize(title) > fontSize(summary) && fontSize(summary) > fontSize(date));
        check.same("The date badge takes its natural width and is hidden when absent",
                List.of("wrap_content", "gone", "@drawable/match_date_badge"),
                List.of(date.getAttribute("android:layout_width"), date.getAttribute("android:visibility"), date.getAttribute("android:background")));
        check.same("The accessibility target is the row rather than three separate labels", List.of("no", "no", "no"),
                List.of(title.getAttribute("android:importantForAccessibility"), summary.getAttribute("android:importantForAccessibility"),
                        date.getAttribute("android:importantForAccessibility")));
        var badge = read("drawable/match_date_badge.xml");
        var badgeFill = ((Element) badge.getElementsByTagName("solid").item(0)).getAttribute("android:color");
        var groupFill = ((Element) read("drawable/home_group.xml").getElementsByTagName("solid").item(0)).getAttribute("android:color");
        double badgeSeparation = contrast(badgeFill, groupFill);
        double dateContrast = contrast(date.getAttribute("android:textColor"), badgeFill);
        check.same("The date is readable while the badge stays close to the row background", true,
                dateContrast >= 4.5 && badgeSeparation > 1 && badgeSeparation < 1.25
                        && dateContrast < contrast(summary.getAttribute("android:textColor"), groupFill));
        var localized = List.of("match_saved_games", "match_archive", "match_list_back");
        check.same("English titles and Back describe the list screens",
                List.of("Saved games", "Finished games", "Back"), labels("values/strings.xml", localized));
        check.same("Romanian titles and Back describe the list screens",
                List.of("Partide salvate", "Partide încheiate", "Înapoi"), labels("values-ro/strings.xml", localized));
    }

    @Test void gameTitlesAndMoveCountsHaveCompactLabelsInBothLanguages(Checks check) throws Exception {
        var titles = List.of("match_saved_title", "match_finished_title");
        var en = labels("values/strings.xml", titles);
        var ro = labels("values-ro/strings.xml", titles);
        check.same("The English titles identify the game or opponent", List.of("Game 3 · Mira", "vs. Atlas"),
                List.of(en.getFirst().formatted(3, "Mira"), en.getLast().formatted("Atlas")));
        check.same("The Romanian titles identify the game or opponent", List.of("Partida 3 · Mira", "Cu Atlas"),
                List.of(ro.getFirst().formatted(3, "Mira"), ro.getLast().formatted("Atlas")));
        check.same("English has distinct singular and plural move labels", List.of("1 move", "0 moves", "24 moves"),
                List.of(moveLabel("values/strings.xml", "one", 1), moveLabel("values/strings.xml", "other", 0),
                        moveLabel("values/strings.xml", "other", 24)));
        check.same("Romanian can show zero, one, a few and many moves naturally", List.of("0 mutări", "1 mutare", "4 mutări", "24 de mutări"),
                List.of(moveLabel("values-ro/strings.xml", "few", 0), moveLabel("values-ro/strings.xml", "one", 1),
                        moveLabel("values-ro/strings.xml", "few", 4), moveLabel("values-ro/strings.xml", "other", 24)));
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
                "@+id/chapter_two_totals", "@+id/chapter_three_totals", "@+id/chapter_tactics_totals", "@+id/chapter_training_totals", "@+id/chapter_intermezzo_totals"), totals);
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

    private static int fontSize(Element element) {
        return Integer.parseInt(element.getAttribute("android:textSize").replace("sp", ""));
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String color) {
        int rgb = Integer.parseInt(color.substring(1), 16);
        return 0.2126 * linear(rgb >> 16) + 0.7152 * linear(rgb >> 8) + 0.0722 * linear(rgb);
    }

    private static double linear(int component) {
        double value = (component & 255) / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static String moveLabel(String resource, String quantity, int count) throws Exception {
        var plurals = read(resource).getElementsByTagName("plurals");
        for (int index = 0; index < plurals.getLength(); index++) {
            var plural = (Element) plurals.item(index);
            if (!plural.getAttribute("name").equals("match_row_moves")) continue;
            var values = plural.getElementsByTagName("item");
            for (int item = 0; item < values.getLength(); item++) {
                var value = (Element) values.item(item);
                if (value.getAttribute("quantity").equals(quantity)) return value.getTextContent().formatted(count);
            }
        }
        throw new AssertionError("Missing move label for " + quantity + " in " + resource);
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
