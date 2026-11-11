package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningCatalogTest {
    @Test void everyLessonReplaysFromBothSidesAndTeachesADistinctPosition(Checks check) {
        var catalog = OpeningCatalog.load();
        check.same("The catalog presents ten independently selectable lessons in order",
                List.of("italian", "ruy-lopez", "caro-kann", "queens-gambit", "sicilian", "scotch", "french", "scandinavian", "london", "kings-indian"),
                catalog.stream().map(OpeningCatalog.Opening::id).toList());
        for (var opening : catalog) for (Side side : Side.values()) {
            var practice = new OpeningPractice(opening, side, 0);
            int learnerMoves = 0;
            while (practice.phase() != OpeningPractice.Phase.COMPLETE) {
                if (practice.phase() == OpeningPractice.Phase.LEARNER) {
                    practice = practice.play(practice.next().move());
                    learnerMoves++;
                } else practice = practice.reply();
            }
            check.same(opening.id() + " is playable to completion as " + side, true,
                    practice.ply() == opening.steps().size() && learnerMoves >= 2);
        }
        var italian = catalog.getFirst();
        var finalPosition = italian.positionAt(italian.steps().size());
        check.same("The Italian line teaches real castling for both sides",
                List.of(new Piece(Side.WHITE, Kind.KING), new Piece(Side.BLACK, Kind.KING)),
                List.of(finalPosition.pieceAt(BoardSquare.parse("g1")), finalPosition.pieceAt(BoardSquare.parse("g8"))));
        var sicilian = catalog.get(4);
        check.same("The Najdorf reaches the defining a6 position", new Piece(Side.BLACK, Kind.PAWN),
                sicilian.positionAt(sicilian.steps().size()).pieceAt(BoardSquare.parse("a6")));
        check.same("The same lesson has a Romanian label", "Partida italiană", italian.title().in(Locale.forLanguageTag("ro")));
        var french = catalog.get(6).positionAt(9);
        check.same("The French lesson builds opposing pawn chains with the d4 base under pressure",
                List.of(new Piece(Side.WHITE, Kind.PAWN), new Piece(Side.BLACK, Kind.PAWN)),
                List.of(french.pieceAt(BoardSquare.parse("d4")), french.pieceAt(BoardSquare.parse("c5"))));
        var kingsIndian = catalog.getLast();
        var closedCenter = kingsIndian.positionAt(kingsIndian.steps().size());
        check.same("The King’s Indian ends with a closed center and a fianchettoed bishop",
                List.of(new Piece(Side.WHITE, Kind.PAWN), new Piece(Side.BLACK, Kind.PAWN), new Piece(Side.BLACK, Kind.BISHOP)),
                List.of(closedCenter.pieceAt(BoardSquare.parse("d5")), closedCenter.pieceAt(BoardSquare.parse("e5")),
                        closedCenter.pieceAt(BoardSquare.parse("g7"))));
    }

    @Test void legalAlternativesDoNotAdvanceTheBookAndAnalysisLeavesTheLessonIntact(Checks check) {
        var lesson = OpeningCatalog.load().getFirst();
        var practice = new OpeningPractice(lesson, Side.WHITE, 0);
        check.same("A legal alternative receives lesson feedback", OpeningPractice.Attempt.OFF_LINE, practice.check(Move.fromUci("d2d4")));
        check.same("An illegal move remains illegal", OpeningPractice.Attempt.ILLEGAL, practice.check(Move.fromUci("e2e5")));
        check.same("Only the authored move advances the lesson", 0, practice.ply());
        var reply = practice.play(Move.fromUci("e2e4"));
        check.same("The configured reply is queued on the opposing side", OpeningPractice.Phase.REPLY, reply.phase());
        var resumed = new OpeningPractice(lesson, Side.WHITE, reply.ply()).reply();
        check.same("The reply plays once when a pending lesson is restored", "e7e5", resumed.previous().uci());
        check.same("The restored lesson returns to the student", OpeningPractice.Phase.LEARNER, resumed.phase());
        var recommendations = TacticalAnalysis.recommendations(resumed.position());
        check.same("The position can be analyzed independently with legal, bounded recommendations", true,
                !recommendations.isEmpty() && recommendations.size() <= 5
                        && recommendations.stream().allMatch(line -> resumed.position().legalMoves().contains(line.move())));
        check.same("The book still expects the original continuation", "g1f3", resumed.next().move().uci());
    }

    @Test void aBlackLessonAppliesItsFirstReplyAndProtectsTurnBoundaries(Checks check) {
        var lesson = OpeningCatalog.load().getFirst();
        var black = new OpeningPractice(lesson, Side.BLACK, 0);
        var e4 = Move.fromUci("e2e4");
        var e5 = Move.fromUci("e7e5");
        check.same("Playing Black starts with a book reply", OpeningPractice.Phase.REPLY, black.phase());
        check.same("The learner cannot play a legal White move for the book", OpeningPractice.Attempt.ILLEGAL, black.check(e4));
        check.rejects("A player move during the book reply is refused", IllegalArgumentException.class, () -> black.play(e4));
        var blackTurn = black.reply();
        check.same("The first book reply is e4", e4, blackTurn.previous());
        check.same("Black can now answer e5", OpeningPractice.Attempt.ON_LINE, blackTurn.check(e5));
        check.rejects("The reply cannot be applied twice", IllegalStateException.class, blackTurn::reply);
        var completed = new OpeningPractice(lesson, Side.BLACK, lesson.steps().size());
        check.same("The last authored position ends the lesson", OpeningPractice.Phase.COMPLETE, completed.phase());
        check.rejects("A completed lesson cannot accept another player move", IllegalArgumentException.class, () -> completed.play(e5));
        check.rejects("A completed lesson cannot emit another book reply", IllegalStateException.class, completed::reply);
    }

    @Test void rejectsIllegalBookMovesAndDuplicateLessons(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("openings.yaml"));
        var entries = new ArrayList<Object>(ChessYaml.list(root, "openings"));
        var original = (Map<?, ?>) entries.getFirst();
        var bad = new HashMap<Object, Object>(original);
        var line = new ArrayList<Object>(ChessYaml.list(original, "line"));
        var step = new HashMap<Object, Object>((Map<?, ?>) line.getFirst());
        step.put("move", "e2e5");
        line.set(0, step);
        bad.put("line", line);
        root.put("openings", List.of(bad));
        check.same("An illegal move reports the offending lesson and ply", "italian ply 1 has illegal move e2e5",
                assertThrows(IllegalArgumentException.class, () -> OpeningCatalog.read(root)).getMessage());
        var duplicate = new HashMap<Object, Object>(original);
        duplicate.put("id", "another-italian");
        root.put("openings", List.of(original, duplicate));
        check.same("A duplicate move sequence is rejected even under a new ID", "Incomplete or duplicate opening line another-italian",
                assertThrows(IllegalArgumentException.class, () -> OpeningCatalog.read(root)).getMessage());
        var unsafe = new HashMap<Object, Object>(original);
        unsafe.put("source", "https://example.test@untrusted.test/italian");
        root.put("openings", List.of(unsafe));
        check.same("A deceptive authority in the source is rejected", "Opening source must use HTTPS: italian",
                assertThrows(IllegalArgumentException.class, () -> OpeningCatalog.read(root)).getMessage());
        root.put("openings", List.of());
        check.same("An empty catalog fails at the YAML boundary", "Expected nonempty list for openings",
                assertThrows(IllegalArgumentException.class, () -> OpeningCatalog.read(root)).getMessage());
    }
}
