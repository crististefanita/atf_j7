package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchToolbarResourcesTest {
    private static final Path RES = ProjectRoot.locate().resolve("android/app/src/main/res");
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test void sharesOneRowAndLetsLocalizedAutomationWrapInsideTheRemainingWidth(Checks check) throws Exception {
        Document toolbar = resource("layout/match_toolbar.xml");
        var row = toolbar.getDocumentElement();
        var menu = (Element) row.getElementsByTagName("ImageButton").item(0);
        var slot = (Element) row.getElementsByTagName("FrameLayout").item(0);
        var automatic = (Element) slot.getElementsByTagName("CheckBox").item(0);
        check.same("The hamburger and native checkbox share one horizontal header", "horizontal", attr(row, "orientation"));
        check.same("The menu keeps a 48 dp touch target and the common three-line glyph",
                List.of("48dp", "48dp", "@drawable/puzzle_menu"),
                List.of(attr(menu, "layout_width"), attr(menu, "layout_height"), attr(menu, "src")));
        check.same("The caption is constrained to the remaining width and the row can grow with the font",
                List.of("0dp", "1", "wrap_content", "wrap_content", "48dp", ""),
                List.of(attr(slot, "layout_width"), attr(slot, "layout_weight"), attr(row, "layout_height"),
                        attr(automatic, "layout_height"), attr(automatic, "minHeight"), attr(automatic, "maxLines")));
        check.same("The native controls expose localized accessible labels",
                List.of("@string/match_actions", "@string/match_automatic_ai"),
                List.of(attr(menu, "contentDescription"), attr(automatic, "text")));
        check.same("Romanian and English both name the checked action positively",
                List.of("Lasă AI-ul să mute", "Let AI play its moves"),
                List.of(string("values-ro/strings.xml", "match_automatic_ai"), string("values/strings.xml", "match_automatic_ai")));
    }

    private static String string(String file, String key) throws Exception {
        var labels = resource(file).getElementsByTagName("string");
        for (int index = 0; index < labels.getLength(); index++) {
            var label = (Element) labels.item(index);
            if (key.equals(label.getAttribute("name"))) return label.getTextContent();
        }
        throw new IllegalArgumentException("Missing " + key + " in " + file);
    }

    private static Document resource(String file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(RES.resolve(file).toFile());
    }

    private static String attr(Element view, String name) { return view.getAttributeNS(ANDROID, name); }
}
