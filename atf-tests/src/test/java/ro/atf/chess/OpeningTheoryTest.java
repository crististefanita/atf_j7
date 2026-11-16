package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningTheoryTest {
    @Test void cyclesDistinctBookMovesWithTheirOwnExplanationsAndAppliesTheMarker(Checks check) {
        var study = line("e2e4 c7c5");
        var position = study.position();
        var hint = study.hint();
        check.same("Theory starts hidden and preserves the configured variant ceiling", true,
                hint.revealedMove() == null && hint.count() <= MatchAiPolicy.CURRENT.hint.maxVariants());
        var seen = new HashSet<Move>();
        for (int index = 0; index < hint.count(); index++) {
            hint.revealNext();
            var theory = hint.revealedTheory();
            var move = theory.move();
            check.same("Each book choice is distinct, legal and plays the exact marked destination", true,
                    seen.add(move) && position.legalMoves().contains(move) && move.equals(hint.moveForTap(position, -1, move.to())));
            if (move.equals(Move.fromUci("c2c3"))) {
                check.same("Alapin has its own ECO and bilingual authored explanation", List.of("B22", true, true),
                        List.of(theory.eco(), theory.idea().en().contains("d4"), theory.idea().ro().contains("d4")));
            }
        }
        check.same("An Alapin and a Najdorf continuation are both reachable", true,
                seen.containsAll(List.of(Move.fromUci("c2c3"), Move.fromUci("g1f3"))));
        check.same("Opening playback carries source information instead of a calculated gain", true,
                hint.playback(position).isEmpty() && hint.evaluation(position) != null);
    }

    @Test void searchesTheRestOfTheIndexAfterAnAuthoredLessonEnds(Checks check) {
        var italian = line("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6 e1g1 e8g8");
        var hint = italian.hint();
        check.same("Two indexed C54 continuations remain after the Italian lesson", 2, hint.count());
        var moves = new HashSet<String>();
        for (int index = 0; index < hint.count(); index++) {
            hint.revealNext();
            var theory = hint.revealedTheory();
            moves.add(theory.move().uci());
            check.same("The continuation has genuine index provenance", List.of("C54", true),
                    List.of(theory.eco(), theory.idea() == null));
        }
        check.same("The controls reach both a4 and Re1", List.of("a2a4", "f1e1"), moves.stream().sorted().toList());
        var london = line("d2d4 g8f6 g1f3 g7g6 c1f4 f8g7 e2e3 d7d6 f1e2 e8g8").hint();
        london.revealNext();
        check.same("An authored continuation missing from the index still has its lesson explanation", List.of("e1g1", true),
                List.of(london.revealedTheory().move().uci(), london.revealedTheory().idea() != null));
    }

    @Test void findsTheoryByExactPositionWithCastlingRights(Checks check) {
        var direct = line("d2d4 d7d5 c2c4 e7e6");
        var reordered = line("d2d4 e7e6 c2c4 d7d5");
        check.same("Different move orders find the same book candidates", OpeningTheory.CURRENT.choices(direct.position()),
                OpeningTheory.CURRENT.choices(reordered.position()));
        check.same("The transposed position still offers Nc3", true, OpeningTheory.CURRENT.choices(reordered.position()).stream()
                .anyMatch(choice -> choice.move().equals(Move.fromUci("b1c3"))));
        var withoutCastling = line("e2e4 e7e5 e1e2 e8e7 e2e1 e7e8");
        check.same("The same arrangement of pieces without castling rights cannot use the earlier book position", List.of(),
                OpeningTheory.CURRENT.choices(withoutCastling.position()));
    }

    @Test void switchesToTheEngineAtARealExitAndRestoresTheoryFromHistory(Checks check) {
        var outside = line("e2e4 e7e5 g1f3 b8c6 f1c4 a7a5");
        var engine = outside.hint();
        engine.revealNext();
        check.same("A historic opening name does not prolong book hints outside the index", List.of(true, true),
                List.of(engine.revealedTheory() == null, engine.revealedMove() != null));
        var earlier = outside.seek(5);
        var restored = earlier.hint();
        restored.revealNext();
        var move = restored.revealedTheory().move();
        check.same("Seeking back restores the Italian choice without allowing it on the stale frame", List.of("f8c5", true),
                List.of(move.uci(), restored.moveForTap(outside.position(), -1, move.to()) == null));
        check.same("Playing the book move from history replaces only the analysis suffix", "f8c5", earlier.play(move).lastMove().uci());
        check.same("The completed board offers no theory or engine moves", 0, line("f2f3 e7e5 g2g4 d8h4").hint().count());
    }

    private static AnalysisLine line(String moves) {
        var match = ChessMatch.restore("2|analysis|" + moves);
        return new AnalysisLine(match, match.ply());
    }
}
