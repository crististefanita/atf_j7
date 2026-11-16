package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebRulesComponentTest {
    @Test void referenceRulesAndCataloguesMeetTheirIndependentExpectations(Checks check) throws IOException {
        var resources = resources(ProjectRoot.locate());
        var observed = rows(ChessWebParity.snapshot(String.join("\n", resources)));
        check.same("The JVM exercised every source YAML resource", resources, resourceRows(observed));
        verifyExpectations(check, observed);
    }

    static List<String> resources(Path root) throws IOException {
        try (var paths = Files.list(root.resolve("chess-core/src/main/resources"))) {
            return paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml")).sorted().toList();
        }
    }

    static List<String> resourceRows(Map<String, String> rows) {
        return rows.keySet().stream().filter(key -> key.startsWith("yaml:"))
                .map(key -> key.substring("yaml:".length())).toList();
    }

    static Map<String, String> rows(String snapshot) {
        var rows = new LinkedHashMap<String, String>();
        for (String line : snapshot.split("\n", -1)) {
            int separator = line.indexOf('\t');
            if (separator < 1 || separator == line.length() - 1) {
                throw new IllegalArgumentException("Invalid snapshot row " + (rows.size() + 1));
            }
            String prior = rows.putIfAbsent(line.substring(0, separator), line.substring(separator + 1));
            if (prior != null) throw new IllegalArgumentException("Duplicate snapshot row " + (rows.size() + 1));
        }
        return rows;
    }

    static void verifyExpectations(Checks check, Map<String, String> observed) {
        check.same("Initial board has 20 legal moves", "20", observed.get("initial-legal-moves"));
        check.same("Castling moves both kings and rooks to their destination squares",
                "WHITE-KING/WHITE-ROOK/WHITE-KING/WHITE-ROOK", observed.get("castling-moves-king-and-rook"));
        check.same("Castling through check is rejected", "false", observed.get("castle-through-attacked-square"));
        check.same("En passant removes the pawn and expires after the immediate reply",
                "-/WHITE-PAWN/false", observed.get("en-passant-capture-and-expiry"));
        check.same("Pinned en passant is rejected", "false", observed.get("en-passant-cannot-expose-own-king"));
        check.same("Promotion offers precisely the four legal choices", "a7a8b,a7a8n,a7a8q,a7a8r", observed.get("promotion-choices"));
        check.same("Knight underpromotion places a white knight", "WHITE-KNIGHT", observed.get("underpromotion"));
        check.same("A move leaving the moving king in check throws", "true", observed.get("move-that-leaves-king-in-check"));
        check.same("Blocking the check is legal", "true", observed.get("blocking-a-check"));
        check.same("Fool's mate terminates with no legal reply", "CHECKMATE/0", observed.get("fools-mate"));
        check.same("Stalemate has a different outcome", "STALEMATE", observed.get("stalemate"));
        check.same("Checkpoint restores the winner and four plies", "BLACK_WINS/4", observed.get("checkpoint-restores-result"));
        check.same("Sicilian branches retain distinct legal moves and ECO names", "B90/g1f3,B22/c2c3",
                observed.get("sicilian-authored-choices"));
        var continuations = observed.getOrDefault("italian-eco-continuations", "").split(",");
        check.same("The book continues the Italian lesson from the ECO index", List.of("C54/a2a4", "C54/f1e1"),
                java.util.Arrays.stream(continuations).sorted().toList());
        var lessonIds = List.of(observed.getOrDefault("authored-openings", "").split(","));
        check.same("The authored Italian and Alapin lessons both appear", true,
                lessonIds.containsAll(List.of("italian", "sicilian-alapin")));
        check.same("One-move mate catalogue retains the authored tutorial", "d8h4", observed.get("anchor:mate-in-one"));
        check.same("Two-move mate retains both moves and the reply", "d2d8,b6d8,d1d8", observed.get("anchor:mate-in-two"));
        check.same("Three-move mate retains its complete solution", "c4c8,b7c8,c1c8,f6d8,c8d8", observed.get("anchor:mate-in-three"));
        check.same("Tactic retains the playable payoff sequence", "c3e4,h6g5,f4e5", observed.get("anchor:tactics"));
        check.same("Exact mate chapter counts", List.of("500", "300", "200"),
                List.of(observed.get("count:mate-in-one"), observed.get("count:mate-in-two"), observed.get("count:mate-in-three")));
        check.same("Training retains the authored rook move", "h1h6", observed.get("anchor:training"));
        check.same("Intermezzo retains the payoff sequence", "a8f8,g8f8,b1b5", observed.get("anchor:intermezzo"));
    }
}
