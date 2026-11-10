package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command-line entry point for checks over receipts gathered outside the checkout. */
public final class ReleaseGate {
    private ReleaseGate() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && "links".equals(args[0])) {
            ReleaseEvidence.gameLinks(Files.readString(Path.of(args[1]))).forEach(System.out::println);
            return;
        }
        if (args.length < 5) throw new IllegalArgumentException("Expected check, version, commit, APK and receipt values");
        Path apk = Path.of(args[3]);
        var candidate = new ReleaseEvidence.Candidate(args[1], args[2], Files.size(apk), ReleaseEvidence.digest(apk));
        switch (args[0]) {
            case "qualification" -> {
                if (args.length != 15) throw new IllegalArgumentException("Expected component and Android suite receipts");
                var components = new ReleaseEvidence.Suite(args[4], "", Integer.parseInt(args[5]), "PASS".equals(args[6]), args[7], Path.of(args[8]));
                var android = new ReleaseEvidence.Suite(args[9], args[10], Integer.parseInt(args[11]), "PASS".equals(args[12]), args[13], Path.of(args[14]));
                ReleaseEvidence.qualification(candidate, apk, components, android);
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
}
