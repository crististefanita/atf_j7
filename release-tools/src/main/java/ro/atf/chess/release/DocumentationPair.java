package ro.atf.chess.release;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static ro.atf.chess.release.GitTestChanges.command;
import static ro.atf.chess.release.GitTestChanges.fields;
import static ro.atf.chess.release.GitTestChanges.line;

/** Checks the documentation contract across two immutable Git trees. */
final class DocumentationPair {
    private static final Pattern EXTERNAL = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//)");
    private static final String README = "README.md";
    private static final String RELEASE_GUIDE = "docs/release/README.md";
    private static final Role ENGLISH = new Role("main", "## Play", "## Published Release", "## Qualification and Publication");
    private static final Role ROMANIAN = new Role("main_RO", "## Jocul", "## Ediția publicată", "## Calificare și publicare");

    record Result(String english, String romanian, int documents, int links, int sectionLinks) { }

    private record Role(String name, String play, String edition, String qualification) { }

    private record Entry(String mode, String type, String object) { }

    private record Document(String text, MarkdownOutline outline) { }

    private record Tree(String commit, Map<String, Entry> files, Set<String> directories, Map<String, Document> documents) {
        Document document(String path) throws IOException {
            var doc = documents.get(path);
            require(doc != null, "Missing required document " + path + " in " + commit);
            return doc;
        }
    }

    private record Destination(String path, String fragment, Integer heading) { }

    private DocumentationPair() { }

    static Result verify(Path root, String englishRef, String romanianRef) throws IOException {
        Path checkout = root.toRealPath();
        Path gitRoot = Path.of(line(command(checkout, "rev-parse", "--show-toplevel"))).toRealPath();
        require(checkout.equals(gitRoot), "Documentation pair scan must start at the Git checkout root");
        Tree english = read(checkout, englishRef);
        Tree romanian = read(checkout, romanianRef);
        require(!english.commit().equals(romanian.commit()), "EN and RO must identify distinct commits");
        require(english.documents().keySet().equals(romanian.documents().keySet()),
                "Tracked Markdown differs: only in main " + onlyIn(english.documents(), romanian.documents())
                        + "; only in main_RO " + onlyIn(romanian.documents(), english.documents()));
        validateRole(english, ENGLISH, ROMANIAN);
        validateRole(romanian, ROMANIAN, ENGLISH);
        require(ReleaseEvidence.gameLinks(english.document(README).text()).equals(ReleaseEvidence.gameLinks(romanian.document(README).text())),
                "English and Romanian README release routes differ");
        verifyReleaseFiles(english, romanian);

        int links = 0;
        int anchored = 0;
        for (String path : english.documents().keySet()) {
            var first = english.documents().get(path).outline();
            var second = romanian.documents().get(path).outline();
            verifyShape(path, first, second);
            require(first.links().size() == second.links().size(), "Markdown link count differs in " + path
                    + ": EN " + first.links().size() + ", RO " + second.links().size());
            for (int index = 0; index < first.links().size(); index++) {
                var left = first.links().get(index);
                var right = second.links().get(index);
                var enTarget = resolve(english, path, left);
                var roTarget = resolve(romanian, path, right);
                require(enTarget.equals(roTarget), "Markdown destination differs in " + path + " at EN:"
                        + left.line() + " / RO:" + right.line() + ": " + left.href() + " / " + right.href());
                links++;
                if (enTarget.heading() != null) anchored++;
            }
        }
        return new Result(english.commit(), romanian.commit(), english.documents().size(), links, anchored);
    }

    private static void verifyShape(String path, MarkdownOutline en, MarkdownOutline ro) throws IOException {
        int common = Math.min(en.blocks().size(), ro.blocks().size());
        for (int index = 0; index < common; index++) {
            var left = en.blocks().get(index);
            var right = ro.blocks().get(index);
            require(left.kind().equals(right.kind()), "Markdown structure differs in " + path + " at EN:" + left.line()
                    + " / RO:" + right.line() + ": " + left.kind() + " / " + right.kind());
        }
        require(en.blocks().size() == ro.blocks().size(), "Markdown block count differs in " + path + ": EN "
                + en.blocks().size() + ", RO " + ro.blocks().size());
    }

    private static void validateRole(Tree tree, Role expected, Role other) throws IOException {
        var root = tree.document(README).outline().headings();
        var release = tree.document(RELEASE_GUIDE).outline().headings();
        require(root.contains(expected.play()) && root.contains(expected.edition()) && release.contains(expected.qualification())
                        && !root.contains(other.play()) && !root.contains(other.edition()) && !release.contains(other.qualification()),
                expected.name() + " must use its localized README and release headings");
    }

    private static void verifyReleaseFiles(Tree en, Tree ro) throws IOException {
        var paths = new TreeSet<String>();
        paths.addAll(en.files().keySet());
        paths.addAll(ro.files().keySet());
        for (String path : paths) {
            if (!path.startsWith("release/") || isMarkdown(path)) continue;
            Entry first = en.files().get(path);
            require(first != null && first.equals(ro.files().get(path)), "Release evidence differs between branches: " + path);
        }
    }

    private static Destination resolve(Tree tree, String source, MarkdownOutline.Link link) throws IOException {
        var href = link.href();
        if (EXTERNAL.matcher(href).find()) return new Destination(href, null, null);
        var parts = href.split("#", 2);
        try {
            String location = decodeLink(parts[0]);
            Path parent = Path.of(source).getParent();
            if (parent == null) parent = Path.of("");
            require(!location.startsWith("/") && !location.contains("\\"), "Expected a repository-relative Markdown path");
            Path target = location.isEmpty() ? Path.of(source) : parent.resolve(location).normalize();
            String path = target.toString().replace('\\', '/');
            require(!target.isAbsolute() && !target.startsWith("..") && (tree.files().containsKey(path) || tree.directories().contains(path)),
                    "Missing local Markdown destination " + path);
            String fragment = parts.length == 2 ? decodeLink(parts[1]) : null;
            if (fragment == null || fragment.isEmpty() || !isMarkdown(path)) return new Destination(path, fragment, null);
            var document = tree.document(path);
            Integer ordinal = document.outline().anchors().get(fragment);
            require(ordinal != null, "Missing local Markdown heading " + path + "#" + fragment);
            return new Destination(path, null, ordinal);
        } catch (IllegalArgumentException | IOException invalid) {
            throw new IOException(source + ":" + link.line() + " (" + href + ") at " + tree.commit() + ": " + invalid.getMessage(), invalid);
        }
    }

    private static String decodeLink(String path) {
        return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static Tree read(Path root, String reference) throws IOException {
        String commit = line(command(root, "rev-parse", "--verify", "--end-of-options", reference + "^{commit}"));
        var files = new TreeMap<String, Entry>();
        var directories = new HashSet<>(Set.of(""));
        for (String row : fields(command(root, "ls-tree", "-rz", "--full-tree", commit))) {
            int split = row.indexOf('\t');
            require(split >= 0, "Git tree entry has no path at " + commit);
            String[] entry = row.substring(0, split).split(" ", 3);
            require(entry.length == 3, "Invalid Git tree entry at " + commit);
            String path = row.substring(split + 1);
            files.put(path, new Entry(entry[0], entry[1], entry[2]));
            for (int index = path.indexOf('/'); index >= 0; index = path.indexOf('/', index + 1)) {
                directories.add(path.substring(0, index));
            }
        }
        var paths = files.keySet().stream().filter(DocumentationPair::isMarkdown).toList();
        require(!paths.isEmpty(), "No tracked Markdown at " + commit);
        var requested = new ArrayList<String>(paths.size());
        for (String path : paths) {
            var entry = files.get(path);
            require(entry.type().equals("blob") && Set.of("100644", "100755").contains(entry.mode()),
                    "Tracked Markdown must be a regular file: " + path);
            requested.add(entry.object());
        }
        byte[] batch = command(root, (String.join("\n", requested) + "\n").getBytes(StandardCharsets.US_ASCII), "cat-file", "--batch");
        var reader = new BlobReader(batch);
        var documents = new TreeMap<String, Document>();
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            String text = reader.next(requested.get(index));
            documents.put(path, new Document(text, MarkdownOutline.read(path, text)));
        }
        require(reader.offset == batch.length, "Git returned unrequested Markdown blobs at " + commit);
        return new Tree(commit, files, directories, documents);
    }

    private static final class BlobReader {
        private final byte[] bytes;
        private int offset;

        private BlobReader(byte[] bytes) { this.bytes = bytes; }

        private String next(String object) throws IOException {
            int end = offset;
            while (end < bytes.length && bytes[end] != '\n') end++;
            require(end < bytes.length, "Missing Markdown blob header for " + object);
            String[] header = new String(bytes, offset, end - offset, StandardCharsets.US_ASCII).split(" ");
            require(header.length == 3 && header[0].equals(object) && header[1].equals("blob"),
                    "Unexpected Markdown blob for " + object);
            offset = end + 1;
            int size;
            try {
                size = Integer.parseInt(header[2]);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid Markdown blob size for " + object, invalid);
            }
            require(size >= 0 && size < bytes.length - offset && bytes[offset + size] == '\n',
                    "Incomplete Markdown blob for " + object);
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, offset, size)).toString();
            offset += size + 1;
            return text;
        }
    }

    private static Set<String> onlyIn(Map<String, ?> left, Map<String, ?> right) {
        var paths = new TreeSet<>(left.keySet());
        paths.removeAll(right.keySet());
        return paths;
    }

    static boolean isMarkdown(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
