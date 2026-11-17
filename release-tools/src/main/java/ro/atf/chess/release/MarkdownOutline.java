package ro.atf.chess.release;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Language-independent outline of the Markdown features used by the tracked guides. */
record MarkdownOutline(List<Block> blocks, List<String> headings, Map<String, Integer> anchors, List<Link> links) {
    private static final Pattern FENCE = Pattern.compile("^\\h*([`]{3,}|~{3,})(.*)$");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\h+(.+)$");
    private static final Pattern LIST = Pattern.compile("^(\\h*)([-+*]|[0-9]{1,9}[.)])\\h+.*$");
    private static final Pattern INLINE_LINK = Pattern.compile("\\]\\(\\h*(?:<([^<>\\r\\n]+)>|([^\\s()<>]+))(?=\\h*[)\"'])");
    private static final Pattern LINK_DEFINITION = Pattern.compile("^ {0,3}\\[[^\\]^][^\\]]*]:\\h*(?:<([^<>]+)>|(\\S+))");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("\\|(?:\\h*:?-{3,}:?\\h*\\|)+");
    private static final Pattern TABLE_PIPE = Pattern.compile("(?<!\\\\)\\|");
    private static final Pattern OTHER_BLOCK = Pattern.compile("^<(?:/?details|/?summary)(?:\\h|>)");

    record Block(String kind, int line) { }
    record Link(String href, int line) { }

    private record Fence(String marker, int line) {
        boolean closes(String delimiter, String info) {
            return delimiter.startsWith(marker) && info.isBlank();
        }
    }

    static MarkdownOutline read(String path, String source) throws IOException {
        var scanner = new Scanner();
        var lines = source.lines().toList();
        for (int index = 0; index < lines.size(); index++) scanner.line(lines.get(index), index + 1);
        if (scanner.fence != null) throw new IOException(path + ":" + scanner.fence.line() + " has an unclosed Markdown fence");
        return new MarkdownOutline(List.copyOf(scanner.blocks), List.copyOf(scanner.headings),
                Map.copyOf(scanner.anchors), List.copyOf(scanner.links));
    }

    private static final class Scanner {
        private final List<Block> blocks = new ArrayList<>();
        private final List<String> headings = new ArrayList<>();
        private final Map<String, Integer> anchors = new LinkedHashMap<>();
        private final List<Link> links = new ArrayList<>();
        private Fence fence;

        private void line(String line, int number) {
            var delimiter = FENCE.matcher(line);
            if (fence != null) {
                if (delimiter.matches() && fence.closes(delimiter.group(1), delimiter.group(2))) {
                    blocks.add(new Block("end fence", number));
                    fence = null;
                }
                return;
            }
            if (delimiter.matches()) {
                fence = new Fence(delimiter.group(1), number);
                blocks.add(new Block("fence " + delimiter.group(2).strip(), number));
                return;
            }
            block(line, number);
            var definition = LINK_DEFINITION.matcher(line);
            if (definition.find()) addLink(definition.group(1), definition.group(2), number);
            var inline = INLINE_LINK.matcher(line);
            while (inline.find()) addLink(inline.group(1), inline.group(2), number);
        }

        private void block(String line, int number) {
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                var marker = heading.group(1);
                var title = heading.group(2).strip().replaceFirst("\\h+#+$", "");
                blocks.add(new Block("heading " + marker, number));
                headings.add(marker + " " + title);
                var stem = title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N} _-]", "").replace(' ', '-');
                var anchor = stem;
                for (int occurrence = 1; anchors.containsKey(anchor); occurrence++) anchor = stem + "-" + occurrence;
                anchors.put(anchor, headings.size());
                return;
            }
            var list = LIST.matcher(line);
            if (list.matches()) {
                String marker = list.group(2);
                blocks.add(new Block("list " + list.group(1).length() + " " + (marker.length() == 1 ? "bullet" : marker), number));
                return;
            }
            var content = line.strip();
            if (content.startsWith("|") && content.endsWith("|")) {
                var separator = TABLE_DIVIDER.matcher(content).matches();
                var columns = TABLE_PIPE.matcher(content).results().count() - 1;
                blocks.add(new Block((separator ? "table separator " : "table row ") + columns, number));
                return;
            }
            var html = OTHER_BLOCK.matcher(content);
            if (html.find()) blocks.add(new Block(html.group().substring(1).replace(">", "").strip(), number));
            else if (content.equals("---")) blocks.add(new Block("thematic break", number));
        }

        private void addLink(String delimited, String plain, int number) {
            links.add(new Link(delimited == null ? plain : delimited, number));
        }
    }
}
