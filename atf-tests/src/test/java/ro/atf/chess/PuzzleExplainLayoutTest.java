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
class PuzzleExplainLayoutTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final Path RES = ProjectRoot.locate().resolve("android/app/src/main/res");

    @Test void keepsTheFullWidthReplayBodyBetweenTheHeaderAndTheFixedControls(Checks check) throws Exception {
        var screen = read("layout/puzzle_explain.xml");
        var children = children(screen);
        check.same("The replay is a single vertical viewport", List.of("@id/quest_payoff_replay", "vertical"),
                List.of(android(screen, "id"), android(screen, "orientation")));
        check.same("Header, board and details, then controls have their own space",
                List.of("@+id/quest_payoff_header", "@+id/quest_payoff_body", "@+id/quest_payoff_actions"),
                children.stream().map(view -> android(view, "id")).toList());
        var header = children.getFirst();
        var close = children(header).getLast();
        check.same("The title can wrap and the dedicated close action keeps the localized OK and 48 dp target",
                List.of("2", "Button", "@android:string/ok", "48dp", "48dp"),
                List.of(android(children(header).getFirst(), "maxLines"), close.getTagName(),
                        android(close, "text"), android(close, "minWidth"), android(close, "minHeight")));
        var body = children.get(1);
        check.same("The replay body receives the full width and only the remaining height",
                List.of("FrameLayout", "match_parent", "0dp", "1"),
                List.of(body.getTagName(), android(body, "layout_width"), android(body, "layout_height"), android(body, "layout_weight")));
        check.same("The footer keeps one touch row", "48dp", android(children.getLast(), "layout_height"));
    }

    @Test void keepsDistinctMoveActionsAndReservesTheDangerSlot(Checks check) throws Exception {
        var footer = children(read("layout/puzzle_explain.xml")).getLast();
        var controls = children(footer);
        check.same("The controls stay in replay order",
                List.of("@id/quest_payoff_previous", "@id/quest_payoff_danger", "@id/quest_payoff_next"),
                controls.stream().map(view -> android(view, "id")).toList());
        var previous = controls.getFirst();
        var next = controls.getLast();
        check.same("Previous and next share the arrow but keep separate localized actions and 48 dp targets",
                List.of(List.of("ImageButton", "@drawable/match_chevron", "48dp", "@string/opening_previous_move"),
                        List.of("ImageButton", "@drawable/match_chevron", "48dp", "@string/opening_next_move")),
                List.of(arrow(previous), arrow(next)));
        check.same("Only the previous arrow turns backwards", List.of("180", ""),
                List.of(android(previous, "rotation"), android(next, "rotation")));
        var danger = controls.get(1);
        check.same("When there is no evidence, the danger slot retains its place without exposing an action",
                List.of("Button", "@string/quest_danger", "48dp", "invisible", "@color/puzzle_explain_danger_tint"),
                List.of(danger.getTagName(), android(danger, "text"), android(danger, "minWidth"),
                        android(danger, "visibility"), android(danger, "textColor")));
        var tint = children(read("color/puzzle_explain_danger_tint.xml"));
        check.same("The on-demand danger toggle has a distinct selected color", true,
                android(tint.getFirst(), "state_selected").equals("true")
                        && !android(tint.getFirst(), "color").equals(android(tint.getLast(), "color")));
    }

    private static List<String> arrow(Element arrow) {
        return List.of(arrow.getTagName(), android(arrow, "src"), android(arrow, "layout_width"),
                android(arrow, "contentDescription"));
    }

    private static String android(Element element, String attribute) {
        return element.getAttributeNS(ANDROID, attribute);
    }

    private static List<Element> children(Element element) {
        var children = new ArrayList<Element>();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element found) children.add(found);
        }
        return children;
    }

    private static Element read(String path) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(RES.resolve(path).toFile()).getDocumentElement();
    }
}
