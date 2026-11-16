package ro.atf.chess;

import static ro.atf.chess.WebEvidencePublication.configuredPath;
import static ro.atf.chess.WebEvidencePublication.same;

import atf.reporting.contract.AtfAssertion;
import atf.reporting.model.ReportField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ro.atf.chess.WebEvidencePublication.Contract;
import ro.atf.chess.WebEvidencePublication.Frame;
import ro.atf.chess.WebEvidencePublication.Scenario;

@Tag("web-evidence")
class ChessWebReviewAccessibilityPublication {
    private static final String SCOPE = "review-accessibility";
    private static final String PROTOCOL = "Chromium CDP Accessibility";
    private static final Scenario ACCESSIBILITY = new Scenario("ChessWebReviewAccessibilityPublication",
            "Review chess moves through the browser accessibility tree", SCOPE, "web-review-accessibility.html",
            "Verify real Edge accessibility nodes and updates for focused controls", List.of(
                    new Frame("focus-graph.png", "Keyboard focus remains on the evaluated move in the graph"),
                    new Frame("focus-history.png", "The selected move is in focus"),
                    new Frame("focus-why.png", "Why keeps the selected move while reviewing the alternative"),
                    new Frame("focus-return.png", "Both returns restore the Why launcher and move")));
    private static final List<String> MILESTONES = List.of("before-toolbar", "after-toolbar", "graph-before", "graph-left", "graph-right",
            "selected-row", "why-entry", "why-recommended", "why-escape", "why-back");

    private record SpeechContext(String slider, String earlier, String selected, String why, String back, String reason,
                                 String recommended, String board) {
        static SpeechContext forLocale(boolean english) {
            return english
                    ? new SpeechContext("Select a move by position", "1… e7 to e5. Book. Position 2 of 4.", "2. g2 to g4. Blunder. Position 3 of 4.",
                            "Why?", "Close explanation", "The reply mates with the queen on h4.", "Recommended", "Chess board · Black to move")
                    : new SpeechContext("Alege poziția din istoric", "1… e7 la e5. De carte. Poziția 2 din 4.",
                            "2. g2 la g4. Greșeală gravă. Poziția 3 din 4.", "De ce?", "Închide explicația",
                            "Replica dă mat cu dama pe h4.", "Recomandată", "Tabla de șah · Negrul la mutare");
        }
    }

    @Test void publishBrowserAccessibilityTreeAndEvents() throws Exception {
        Path fixture = configuredPath("chess.web.accessibility.fixture");
        boolean publicRun = Boolean.getBoolean("chess.web.accessibility.public");
        WebEvidencePublication.publish(configuredPath("chess.web.accessibility.receipt"), configuredPath("chess.web.bundle"),
                ACCESSIBILITY, receipt -> inspect(receipt, fixture, publicRun));
    }

    private static Contract inspect(JsonNode receipt, Path fixturePath, boolean publicRun) throws IOException {
        var focus = ChessWebReviewFocusPublication.inspect(receipt, fixturePath, publicRun, SCOPE);
        JsonNode fixture = WebEvidencePublication.readObject(Files.readAllBytes(fixturePath), "Archived accessibility fixture");
        var source = ChessMatch.restore(fixture.path("checkpoint").asText());
        var review = GameReviewCodec.decode(source, fixture.path("review").asText());
        var context = SpeechContext.forLocale(publicRun);
        var evidence = receipt.path("accessibility");
        var checks = new ArrayList<>(focus.identities());
        checks.add(same("Evidence is provider data without recorded screen-reader audio", List.of(PROTOCOL, BooleanNode.FALSE),
                List.of(evidence.path("protocol").asText(), evidence.path("spokenAudioRecorded"))));
        var visits = indexVisits(evidence.path("witnesses"), checks);
        var left = graphDescription(review, context.earlier(), 2, publicRun);
        var right = graphDescription(review, context.selected(), 3, publicRun);
        verifyNavigation(visits, context, left, right, evidence.path("updates"), checks);
        verifyExplanation(visits, context, checks);
        int length = fixture.path("expected").path("recommendedLength").asInt();
        String branchAnnouncement = context.selected() + (publicRun ? " Recommended line. Step 0 of " + length + "."
                : " Linia recomandată. Pasul 0 din " + length + ".");
        verifyLiveStatus(visits, context.selected(), branchAnnouncement, checks);
        var fields = new ArrayList<>(focus.fields());
        fields.addAll(List.of(new ReportField("Accessibility source", PROTOCOL),
                new ReportField("Graph at ply 2", left), new ReportField("Graph at ply 3", right),
                new ReportField("Back from Why", context.why() + " " + context.selected() + " " + context.reason())));
        return new Contract(fields, checks, focus.observations(), focus.locale());
    }

    private static Map<String, JsonNode> indexVisits(JsonNode entries, List<AtfAssertion> checks) {
        var visits = new LinkedHashMap<String, JsonNode>();
        entries.forEach(entry -> visits.put(entry.path("id").asText(), entry.path("nodes")));
        checks.add(same("Each accessibility milestone is recorded once and in order", List.of(MILESTONES, MILESTONES.size()),
                List.of(List.copyOf(visits.keySet()), entries.size())));
        return visits;
    }

    private static void verifyNavigation(Map<String, JsonNode> visits, SpeechContext context, String left, String right, JsonNode updates,
                                         List<AtfAssertion> checks) {
        JsonNode initial = target(visits, "graph-before", "slider");
        verifyControl("The board exposes the exact side to move", "image", context.board(), target(visits, "graph-before", "board"), false, checks);
        verifySlider("Initially", initial, context.slider(), right, 3, checks);
        verifySlider("ArrowLeft", target(visits, "graph-left", "slider"), context.slider(), left, 2, checks);
        verifySlider("ArrowRight", target(visits, "graph-right", "slider"), context.slider(), right, 3, checks);
        for (var phase : List.of("graph-left", "graph-right")) {
            int ply = phase.equals("graph-left") ? 2 : 3;
            String description = ply == 2 ? left : right;
            var matching = new ArrayList<JsonNode>();
            updates.forEach(update -> {
                if (phase.equals(update.path("phase").asText()) && "slider".equals(update.path("target").asText())) matching.add(update);
            });
            checks.add(same(phase + " · real AX event reports the committed value and localized description on the same focused node", true,
                    matching.stream().anyMatch(update -> sameNode(initial, update)
                            && update.path("value").isNumber() && update.path("value").asInt() == ply && isFocused(update)
                            && description.equals(update.path("description").asText()))));
        }
        verifyControl("Tab reaches the selected move with its grade and position", "button", context.selected(),
                target(visits, "selected-row", "row"), true, checks);
    }

    private static void verifyExplanation(Map<String, JsonNode> visits, SpeechContext context, List<AtfAssertion> checks) {
        var back = target(visits, "why-entry", "back");
        verifyControl("Why focuses its Back button", "button", context.back(), back, true, checks);
        checks.add(same("The focused Back button describes the selected move then the decisive mate", List.of("review-why-context",
                context.why() + " " + context.selected() + " " + context.reason()),
                List.of(back.path("properties").path("describedby").asText(), back.path("description").asText())));
        checks.add(same("Why removes graph and history from the accessible tree", true,
                BooleanNode.TRUE.equals(target(visits, "why-entry", "slider").path("ignored"))
                        && BooleanNode.TRUE.equals(target(visits, "why-entry", "row").path("ignored"))));
        var recommended = target(visits, "why-recommended", "recommended");
        verifyControl("The recommended branch is reachable by keyboard", "button", context.recommended(), recommended, true, checks);
        checks.add(same("The recommended branch identifies its pressed state", "true", recommended.path("properties").path("pressed").asText()));
        for (String phase : List.of("why-escape", "why-back")) {
            var launcher = target(visits, phase, "why");
            verifyControl(phase + " · the original Why button regains focus", "button", context.why(), launcher, true, checks);
            checks.add(same(phase + " · the focused launcher retains the exact position and is collapsed",
                    List.of("review-selection-context", context.selected(), BooleanNode.FALSE), List.of(
                            launcher.path("properties").path("describedby").asText(), launcher.path("description").asText(),
                            launcher.path("properties").path("expanded"))));
        }
    }

    private static void verifyLiveStatus(Map<String, JsonNode> visits, String selected, String branch, List<AtfAssertion> checks) {
        var before = target(visits, "before-toolbar", "announcement");
        var after = target(visits, "after-toolbar", "announcement");
        var visiblePosition = target(visits, "after-toolbar", "position");
        checks.add(same("The visible position avoids a second live announcement", false,
                visiblePosition.path("properties").has("live") || "status".equals(visiblePosition.path("role").asText())));
        checks.add(same("The one toolbar status is polite, atomic and empty before navigation", List.of("status", "polite", BooleanNode.TRUE, List.of()),
                List.of(before.path("role").asText(), before.path("properties").path("live").asText(),
                        before.path("properties").path("atomic"), liveText(before))));
        checks.add(same("Toolbar navigation announces only the selected move", List.of(selected), liveText(after)));
        verifyUnchangedStatus(visits, after, MILESTONES.subList(2, 7), checks);
        var recommended = target(visits, "why-recommended", "announcement");
        checks.add(same("Activating Recommended announces the start of that line", List.of(branch), liveText(recommended)));
        verifyUnchangedStatus(visits, recommended, List.of("why-escape", "why-back"), checks);
    }

    private static void verifyUnchangedStatus(Map<String, JsonNode> visits, JsonNode reference, List<String> phases,
                                              List<AtfAssertion> checks) {
        JsonNode expectedText = reference.path("children").path(0);
        for (String phase : phases) {
            var status = target(visits, phase, "announcement");
            checks.add(same(phase + " · focus or range changes do not republish the live status", true,
                    sameNode(reference, status) && sameNode(expectedText, status.path("children").path(0))
                            && liveText(reference).equals(liveText(status))));
        }
    }

    private static List<String> liveText(JsonNode node) {
        var text = new ArrayList<String>();
        node.path("children").forEach(child -> text.add(child.path("name").asText()));
        return text;
    }

    private static void verifySlider(String phase, JsonNode node, String name, String description, int ply, List<AtfAssertion> checks) {
        verifyControl(phase + " · evaluation is a focused native slider", "slider", name, node, true, checks);
        checks.add(same(phase + " · provider exposes the numerical position and localized context",
                List.of(ply, "review-graph-context", description), List.of(node.path("value").asInt(-1),
                        node.path("properties").path("describedby").asText(), node.path("description").asText())));
    }

    private static void verifyControl(String label, String role, String name, JsonNode node, boolean focused, List<AtfAssertion> checks) {
        checks.add(same(label, List.of(role, name, BooleanNode.FALSE, focused),
                List.of(node.path("role").asText(), node.path("name").asText(), node.path("ignored"), isFocused(node))));
    }

    private static boolean isFocused(JsonNode node) { return BooleanNode.TRUE.equals(node.path("properties").path("focused")); }
    private static boolean sameNode(JsonNode first, JsonNode second) {
        return first.path("backend").isIntegralNumber() && first.path("backend").equals(second.path("backend"))
                && first.path("node").isTextual() && first.path("node").equals(second.path("node"));
    }

    private static JsonNode target(Map<String, JsonNode> visits, String phase, String name) {
        return visits.getOrDefault(phase, com.fasterxml.jackson.databind.node.MissingNode.getInstance()).path(name);
    }

    private static String graphDescription(GameReview review, String position, int ply, boolean english) {
        var number = NumberFormat.getNumberInstance(Locale.forLanguageTag(english ? "en-US" : "ro-RO"));
        number.setMinimumFractionDigits(1);
        number.setMaximumFractionDigits(1);
        String score = number.format(review.turns().get(ply - 1).whitePoints() / 10.0);
        return position + (english ? " White score " + score + " percent." : " Scorul albului " + score + " la sută.");
    }
}
