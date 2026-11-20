package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class MarkdownDiagramGovernanceTest {
    @Test void supportedDiagramsRetainTheirExplainedExecutableContent(Checks check) {
        for (var body : List.of(List.of("flowchart LR", "    A -->|validates| B"),
                List.of("sequenceDiagram", "    App->>Session: submit move"),
                List.of("stateDiagram-v2", "    Waiting --> Applied: current revision"),
                List.of("classDiagram", "    Session : +play(revision, move)"))) {
            check.same(body.getFirst() + " is an approved, explained diagram", List.of(), MarkdownVisualContract.inspect(diagram(body)));
        }
    }

    @Test void unknownAndMissingTypesCannotPassBecauseALegendExists(Checks check) {
        for (var body : List.of(List.of("notADiagram", "    A -->|validates| B"),
                List.of("sequenceDiagramTypo", "    App->>Session: submit move"),
                List.of("pie", "    \"Receipt\" : 1"), List.<String>of())) {
            var failures = MarkdownVisualContract.inspect(diagram(body));
            check.same("An unsupported or absent Mermaid kind is rejected: " + body, true,
                    failures.stream().anyMatch(message -> message.equals("1 diagram needs a supported type")));
        }
    }

    @Test void commentsAndInitializationDoNotReplaceDiagramContent(Checks check) {
        for (String type : List.of("flowchart LR", "sequenceDiagram", "stateDiagram-v2", "classDiagram")) {
            var body = List.of("%%{init: {\"theme\":\"neutral\"}}%%", type, "%% explanation without any node or relation");
            check.same(type + " needs content after its declaration", true,
                    MarkdownVisualContract.inspect(diagram(body)).contains("1 diagram needs executable content"));
        }
    }

    @Test void typeCheckingPreservesTheExistingEdgeAndLegendRequirements(Checks check) {
        var unlabelled = diagram(List.of("flowchart LR", "    A --> B"));
        check.same("A supported type still needs semantic edge labels", true,
                MarkdownVisualContract.inspect(unlabelled).contains("1 diagram edge needs a label"));
        var withoutLegend = List.of("```mermaid", "sequenceDiagram", "    A->>B: validate", "```");
        check.same("A supported sequence still needs its adjacent legend", true,
                MarkdownVisualContract.inspect(withoutLegend).contains("4 diagram needs an adjacent collapsed legend"));
    }

    private static List<String> diagram(List<String> body) {
        var lines = new ArrayList<String>(); lines.add("```mermaid"); lines.addAll(body);
        lines.addAll(List.of("```", "", "<details>", "<summary>Legend — ownership</summary>", "", "The session owns the decision.", "", "</details>"));
        return lines;
    }
}
