package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

final class TestChangeReceipt {
    private static final String SUITE = "ro.atf.chess.TestChangeReview";
    private static final String CASE = "verifiesReviewedChanges(Checks)";

    private TestChangeReceipt() { }

    static void verify(Path reports, GitTestChanges.Snapshot snapshot) throws IOException {
        Path receipt = reports.resolve("TEST-" + SUITE + ".xml");
        if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing TestChangeReview receipt");
        }
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element suite = factory.newDocumentBuilder().parse(receipt.toFile()).getDocumentElement();
            var cases = children(suite, "testcase");
            if (!suite.getTagName().equals("testsuite") || !suite.getAttribute("name").equals(SUITE)
                    || !suite.getAttribute("tests").equals("1") || cases.size() != 1
                    || !List.of("failures", "errors", "skipped").stream().allMatch(field -> suite.getAttribute(field).equals("0"))) {
                throw new IOException("TestChangeReview receipt did not pass exactly one case");
            }
            Element proof = cases.getFirst();
            String marker = "J7_TEST_CHANGES " + snapshot.base() + " " + snapshot.head();
            var markers = children(proof, "system-out").stream().flatMap(output -> output.getTextContent().lines())
                    .filter(line -> line.startsWith("J7_TEST_CHANGES ")).toList();
            if (!proof.getAttribute("name").equals(CASE) || !proof.getAttribute("classname").equals(SUITE)
                    || !List.of("failure", "error", "skipped").stream().allMatch(tag -> children(proof, tag).isEmpty())
                    || !markers.equals(List.of(marker))) {
                throw new IOException("TestChangeReview belongs to another Git base or candidate, or has no verified result");
            }
        } catch (ParserConfigurationException | SAXException invalid) {
            throw new IOException("Cannot read TestChangeReview receipt", invalid);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        var nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).filter(Element.class::isInstance)
                .map(Element.class::cast).filter(child -> child.getTagName().equals(tag)).toList();
    }
}
