package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Reconciles the dedicated Surefire execution with its rendered Extent cases. */
final class ComponentQualification {
    private static final String PROFILE = "ChessComponentSuite";
    private static final String SUITE = "ro.atf.chess.suites." + PROFILE;
    private static final Pattern GROUP = Pattern.compile("<li\\s+class=\"test-item\"\\s+status=\"([^\"]+)\"[^>]*>");
    private static final Pattern OWNER = Pattern.compile("<p class=\"name\">([^<]+)</p>");
    private static final Pattern NODE = Pattern.compile("<a class=\"node [^\"]*\" id=\"[^\"]+\"><span>([^<]*)</span></a>");
    private static final Pattern SYSTEM = Pattern.compile("<tr>\\s*<td>([^<]+)</td>\\s*<td>([^<]+)</td>\\s*</tr>");
    private static final Pattern ASSERTIONS = Pattern.compile("(?s)<div class=\"atf-ui-assertions\"([^>]*)>.*?<tbody>(.*?)</tbody>");
    private static final Pattern ROW = Pattern.compile("(?s)<tr><td>.+?</td><td>.*?</td><td>.*?</td><td>([^<]*)</td></tr>");
    private static final Set<String> PHASES = Set.of("SETUP", "TEST BODY", "TEARDOWN");

    private record Case(String owner, String name) {
        String description() { return owner + "#" + name; }
    }

    private ComponentQualification() { }

    static int verify(ReleaseEvidence.Candidate candidate, String runId, Path receipts, Path extent) throws IOException {
        Set<Case> raw = rawCases(candidate.commit(), runId, receipts);
        require(Files.isRegularFile(extent, LinkOption.NOFOLLOW_LINKS), "Missing component Extent");
        String html = Files.readString(extent);
        require(systemValue(html, "Profile").equals(PROFILE) && systemValue(html, "Source/build").equals(candidate.commit())
                && systemValue(html, "Precondition").equals(runId), "Component Extent belongs to another source, suite or run");
        Set<Case> rendered = renderedCases(html);
        require(rendered.equals(raw), "Component Extent cases differ from Surefire (rendered " + rendered.size() + ", raw " + raw.size() + ")");
        return raw.size();
    }

    private static Set<Case> rawCases(String commit, String runId, Path directory) throws IOException {
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "Missing component Surefire directory");
        var cases = new HashSet<Case>();
        boolean marker = false;
        try (var files = Files.list(directory)) {
            var parser = parser();
            for (Path path : files.filter(file -> file.getFileName().toString().matches("TEST-.+\\.xml")).sorted().toList()) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Invalid Surefire receipt: " + path.getFileName());
                Element result = parser.parse(path.toFile()).getDocumentElement();
                String owner = result.getAttribute("name");
                require(result.getTagName().equals("testsuite") && owner.matches("ro\\.atf\\.chess\\.[A-Za-z0-9_.$]+")
                        && path.getFileName().toString().equals("TEST-" + owner + ".xml"), "Unexpected Surefire suite: " + path.getFileName());
                require(property(result, "test").equals(PROFILE) && property(result, "chess.qualification.commit").equals(commit)
                        && property(result, "chess.qualification.runId").equals(runId), "Surefire receipt belongs to another source, suite or run: " + owner);
                List<Element> tests = children(result, "testcase");
                require(result.getAttribute("tests").equals(Integer.toString(tests.size()))
                        && List.of("failures", "errors", "skipped").stream().allMatch(field -> result.getAttribute(field).equals("0")),
                        "Surefire receipt did not pass or has a wrong count: " + owner);
                if (owner.equals(SUITE)) {
                    require(tests.isEmpty(), "Unexpected testcase under the Surefire suite marker");
                    marker = true;
                    continue;
                }
                require(!tests.isEmpty(), "Empty component Surefire receipt: " + owner);
                for (Element test : tests) {
                    String name = test.getAttribute("name");
                    var key = new Case(owner.substring(owner.lastIndexOf('.') + 1), name);
                    require(test.getAttribute("classname").equals(owner) && !name.isBlank()
                            && List.of("failure", "error", "skipped").stream().allMatch(tag -> children(test, tag).isEmpty())
                            && cases.add(key), "Invalid or duplicate Surefire testcase: " + key.description());
                }
            }
        } catch (SAXException invalid) {
            throw new IOException("Cannot read component Surefire receipts", invalid);
        }
        require(marker && !cases.isEmpty(), "Missing component suite marker or passing Surefire testcases");
        return cases;
    }

    private static DocumentBuilder parser() throws IOException {
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException invalid) {
            throw new IOException("Cannot initialize the Surefire receipt reader", invalid);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        var nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).filter(Element.class::isInstance)
                .map(Element.class::cast).filter(child -> child.getTagName().equals(tag)).toList();
    }

    private static String property(Element suite, String name) throws IOException {
        var values = children(suite, "properties").stream().flatMap(node -> children(node, "property").stream())
                .filter(node -> node.getAttribute("name").equals(name)).map(node -> node.getAttribute("value")).toList();
        require(values.size() == 1, "Missing or duplicate Surefire property: " + name);
        return values.getFirst();
    }

    private static String systemValue(String html, String label) throws IOException {
        var values = SYSTEM.matcher(html).results().filter(row -> row.group(1).equals(label)).map(row -> row.group(2)).toList();
        require(values.size() == 1, "Missing or duplicate component Extent System field: " + label);
        return values.getFirst();
    }

    private static Set<Case> renderedCases(String html) throws IOException {
        var groups = GROUP.matcher(html).results().toList();
        var cases = new HashSet<Case>();
        for (int index = 0; index < groups.size(); index++) {
            var group = groups.get(index);
            require(group.group(1).equals("pass"), "Component Extent contains a nonpassing group");
            String section = html.substring(group.end(), index + 1 < groups.size() ? groups.get(index + 1).start() : html.length());
            var owner = OWNER.matcher(section);
            require(owner.find(), "Component Extent group has no test owner");
            readGroup(section, owner.group(1), cases);
        }
        require(!cases.isEmpty(), "Component Extent has no test results");
        return cases;
    }

    private static void readGroup(String section, String owner, Set<Case> cases) throws IOException {
        Case current = null;
        int bodyStart = -1;
        boolean verifiedBody = false;
        var nodes = NODE.matcher(section);
        while (nodes.find()) {
            String title = nodes.group(1);
            if (title.equals("Assertions")) continue;
            if (bodyStart >= 0) {
                verifyAssertions(section.substring(bodyStart, nodes.start()), current);
                verifiedBody = true;
                bodyStart = -1;
            }
            if (PHASES.contains(title)) {
                require(current != null, "Component Extent phase has no testcase: " + owner);
                if (title.equals("TEST BODY")) {
                    require(!verifiedBody, "Duplicate component Extent TEST BODY: " + current.description());
                    bodyStart = nodes.end();
                }
            } else {
                finish(cases, current, verifiedBody);
                current = new Case(owner, title);
                verifiedBody = false;
            }
        }
        require(current != null, "Component Extent group has no testcase: " + owner);
        if (bodyStart >= 0) {
            verifyAssertions(section.substring(bodyStart), current);
            verifiedBody = true;
        }
        finish(cases, current, verifiedBody);
    }

    private static void verifyAssertions(String body, Case test) throws IOException {
        var blocks = ASSERTIONS.matcher(body);
        require(blocks.find(), "Component Extent has no assertion table: " + test.description());
        do {
            var rows = ROW.matcher(blocks.group(2)).results().toList();
            require(blocks.group(1).contains("data-atf-evidence-status=\"pass\"") && !rows.isEmpty()
                            && ROW.matcher(blocks.group(2)).replaceAll("").isBlank()
                            && rows.stream().allMatch(row -> row.group(1).equals("✅")),
                    "Component Extent assertions are empty or nonpassing: " + test.description());
        } while (blocks.find());
    }

    private static void finish(Set<Case> cases, Case test, boolean verifiedBody) throws IOException {
        if (test != null) require(verifiedBody && cases.add(test), "Missing or duplicate component Extent result: " + test.description());
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }
}
