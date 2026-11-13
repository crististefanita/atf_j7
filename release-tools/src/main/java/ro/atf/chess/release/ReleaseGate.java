package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Command-line entry point for checks over receipts gathered outside the checkout. */
public final class ReleaseGate {
    private static final List<String> GOVERNANCE = List.of("DocumentationGovernanceTest", "ReleaseGovernanceTest", "ExtentGovernanceTest");

    private ReleaseGate() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && "links".equals(args[0])) {
            ReleaseEvidence.gameLinks(Files.readString(Path.of(args[1]))).forEach(System.out::println);
            return;
        }
        if (args.length == 2 && "preflight".equals(args[0])) {
            preflight(Path.of(args[1]));
            System.out.println("PASS preflight " + String.join(", ", GOVERNANCE));
            return;
        }
        if (args.length == 4 && "signing-continuity".equals(args[0])) {
            String status = ReleaseEvidence.signingContinuity(args[1], args[2], args[3]);
            System.out.println("PASS signing-continuity " + status);
            return;
        }
        if (args.length < 5) throw new IllegalArgumentException("Expected check, version, commit, APK and receipt values");
        Path apk = Path.of(args[3]);
        var candidate = new ReleaseEvidence.Candidate(args[1], args[2], Files.size(apk), ReleaseEvidence.digest(apk));
        switch (args[0]) {
            case "qualification" -> {
                if (args.length != 8) throw new IllegalArgumentException("Expected component Surefire directory, component Extent, Android run directory and Android Extent");
                ReleaseEvidence.apk(candidate, apk);
                var android = NativeQualification.verify(candidate, Path.of(args[6]), Path.of(args[7]));
                int components = ComponentQualification.verify(candidate, android.runId(), Path.of(args[4]), Path.of(args[5]));
                System.out.printf("QUALIFIED runId=%s components=%d android=%d%n", android.runId(), components, android.assertions());
            }
            case "publication" -> {
                if (args.length != 12) throw new IllegalArgumentException("Expected GitHub tag, README and download receipts");
                ReleaseEvidence.publication(candidate, new ReleaseEvidence.Download(args[4], args[5], Files.readString(Path.of(args[6])),
                        args[7], args[8], args[9], Integer.parseInt(args[10]), Path.of(args[11])));
            }
            default -> throw new IllegalArgumentException("Unknown release check: " + args[0]);
        }
        System.out.println("PASS " + args[0]);
    }

    public static void preflight(Path reports) throws IOException {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            var parser = factory.newDocumentBuilder();
            for (String check : GOVERNANCE) {
                String suite = "ro.atf.chess." + check;
                Path receipt = reports.resolve("TEST-" + suite + ".xml");
                if (!Files.isRegularFile(receipt)) throw new IllegalStateException("Missing governance receipt: " + check);
                var result = parser.parse(receipt.toFile()).getDocumentElement();
                if (!result.getTagName().equals("testsuite") || !result.getAttribute("name").equals(suite)
                        || !result.getAttribute("tests").matches("[1-9][0-9]*")
                        || !List.of("failures", "errors", "skipped").stream().allMatch(field -> result.getAttribute(field).equals("0"))) {
                    throw new IllegalStateException("Governance did not pass: " + check);
                }
            }
        } catch (ParserConfigurationException | SAXException invalid) {
            throw new IOException("Cannot read governance receipts", invalid);
        }
    }
}
