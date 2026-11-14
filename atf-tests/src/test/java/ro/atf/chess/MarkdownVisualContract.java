package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Renderer-neutral documentation checks derived from the ATF J0 style and diagram rules. */
final class MarkdownVisualContract {
    private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Pattern PRESENTATION = Pattern.compile("(?i)<(?:font|h[1-6]|span|div)\\b|\\bstyle\\s*=");
    private static final Pattern RELATION = Pattern.compile("-->|-\\.->");
    private static final Pattern LABELLED = Pattern.compile("(?:-->|-\\.->)\\s*\\|[^|]+\\|");

    private MarkdownVisualContract() { }

    static List<String> inspect(List<String> lines) {
        var failures = new ArrayList<String>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            var opening = FENCE.matcher(line);
            if (opening.matches()) {
                String language = opening.group(2).strip();
                int closing = endOfFence(lines, index + 1, opening.group(1));
                if (!LANGUAGE.matcher(language).matches()) failures.add((index + 1) + " fence needs a language");
                if (closing == lines.size()) {
                    failures.add((index + 1) + " fence must close");
                    break;
                }
                if (language.equals("mermaid")) inspectDiagram(lines, index, closing, failures);
                index = closing;
            } else if (PRESENTATION.matcher(line).find()) failures.add((index + 1) + " use Markdown for presentation");
        }
        return failures;
    }

    private static int endOfFence(List<String> lines, int start, String delimiter) {
        for (int index = start; index < lines.size(); index++) {
            var fence = FENCE.matcher(lines.get(index).strip());
            if (fence.matches() && fence.group(1).startsWith(delimiter) && fence.group(2).isBlank()) return index;
        }
        return lines.size();
    }

    private static void inspectDiagram(List<String> lines, int opening, int closing, List<String> failures) {
        var statements = lines.subList(opening + 1, closing).stream().map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("%%")).toList();
        String type = statements.isEmpty() ? "" : statements.getFirst();
        if (type.startsWith("flowchart")) {
            if (!type.equals("flowchart LR")) failures.add((opening + 1) + " flowchart must use LR");
            if (statements.stream().anyMatch(line -> RELATION.matcher(line).results().count()
                    > LABELLED.matcher(line).results().count())) failures.add((opening + 1) + " diagram edge needs a label");
        }
        var next = lines.subList(closing + 1, lines.size()).stream().map(String::strip).filter(line -> !line.isBlank()).limit(2).toList();
        if (next.size() != 2 || !next.getFirst().equals("<details>") || !next.get(1).startsWith("<summary>Legendă")) {
            failures.add((closing + 1) + " diagram needs an adjacent collapsed legend");
        }
    }
}
