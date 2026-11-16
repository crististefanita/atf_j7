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
                hint.revealedMove() == null && hint.count() <= 3);
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

    @Test void keepsThreeFullYamlPathsEvenWhenTheyStartWithTheSameMove(Checks check) {
        var study = line("e2e4");
        var hint = study.hint();
        check.same("Exactly three complete paths are offered", 3, hint.count());
        check.same("Playback stays hidden until the first path is revealed", true, hint.theoryPlayback(study.position()).isEmpty());
        hint.revealNext();
        var italian = hint.revealedTheory();
        hint.revealNext();
        var span = hint.theoryPlayback(study.position()).orElseThrow();
        var spanish = span.paths().get(span.selected());
        check.same("Italian and Spanish paths share the first move but diverge later", true,
                italian.move().equals(spanish.move()) && !italian.steps().equals(spanish.steps()) && span.selected() == 1);
        var lesson = OpeningCatalog.load().get(0);
        check.same("The Italian path retains every authored move and explanation from this board",
                lesson.steps().subList(1, lesson.steps().size()), italian.steps());
        var replay = new OpeningReplay(study.position(), italian);
        while (replay.hasNext()) replay.next();
        check.same("Replay ends at the YAML board and does not invent another reply",
                List.of(lesson.positionAt(lesson.steps().size()).searchKey(), Move.fromUci("e8g8"), 11),
                List.of(replay.position().searchKey(), replay.lastMove(), replay.length()));
        replay.previous();
        check.same("Stepping back restores the previous authored frame and its explanation",
                List.of(lesson.positionAt(11).searchKey(), lesson.steps().get(10)),
                List.of(replay.position().searchKey(), replay.explainedStep()));
        check.same("Previewing never advances the study and stale hints cannot open paths", true,
                study.ply() == 1 && hint.theoryPlayback(replay.position()).isEmpty());
        hint.revealNext();
        hint.revealNext();
        check.same("Cycling all three branches returns to the first path", italian, hint.revealedTheory());
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
