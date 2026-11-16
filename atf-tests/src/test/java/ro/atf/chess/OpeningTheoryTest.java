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

    @Test void recomputesFromAllTheoryAfterMovesOutsideTheVisiblePaths(Checks check) {
        var initial = line("");
        check.same("The initial three visible lines all start with e4", List.of("e2e4", "e2e4", "e2e4"),
                paths(initial).stream().map(path -> path.move().uci()).toList());
        var previousHint = initial.hint();
        previousHint.revealNext();

        var queensPawn = initial.play(Move.fromUci("d2d4"));
        check.same("Playing d4 outside the previous shortlist produces three lines from the new position",
                List.of("D63", "A48", "E05"), ecos(queensPawn));
        check.same("A hint from the old board cannot open a path on the new board", true,
                previousHint.theoryPlayback(queensPawn.position()).isEmpty());

        var indian = queensPawn.play(Move.fromUci("g8f6"));
        check.same("Nimzo-Indian can enter the next shortlist even though it was absent at d4",
                List.of("A48", "E05", "E32"), ecos(indian));
        var c4 = indian.play(Move.fromUci("c2c4"));
        check.same("The next move promotes the King's Indian into the three visible lines",
                List.of("E05", "E32", "E97"), ecos(c4));

        var kingsIndian = c4.play(Move.fromUci("g7g6"));
        var only = paths(kingsIndian);
        check.same("A forced entry into one documented line keeps just that line",
                List.of("E97"), only.stream().map(MatchHint.Theory::eco).toList());
        var lesson = OpeningCatalog.load().stream().filter(opening -> opening.id().equals("kings-indian")).findFirst().orElseThrow();
        check.same("The new hint keeps the exact suffix from the fourth ply",
                lesson.steps().subList(4, lesson.steps().size()), only.getFirst().steps());
        check.same("The same prior board restores its original shortlist from history", ecos(c4), ecos(kingsIndian.seek(3)));
    }

    @Test void rebranchesFromSicilianToFrenchAndHandsOffOutsideTheBook(Checks check) {
        var e4 = line("e2e4");
        check.same("The first three paths after e4 start with e5, e5 and c6",
                List.of("e7e5", "e7e5", "c7c6"), paths(e4).stream().map(path -> path.move().uci()).toList());
        var oldHint = e4.hint();
        oldHint.revealNext();

        var sicilian = e4.play(Move.fromUci("c7c5"));
        check.same("Entering the unlisted Sicilian offers Najdorf and Alapin",
                List.of("B90", "B22"), ecos(sicilian));
        var alapin = sicilian.play(Move.fromUci("c2c3"));
        var alapinLesson = authored("sicilian-alapin");
        check.same("Alapin keeps its entire authored continuation",
                alapinLesson.steps().subList(3, alapinLesson.steps().size()),
                paths(alapin).getFirst().steps());
        check.same("The old e5 remains legal but its hint cannot target the new board", List.of(true, true, true),
                List.of(alapin.position().legalMoves().contains(oldHint.revealedMove()),
                        oldHint.moveForTap(alapin.position(), -1, oldHint.revealedMove().to()) == null,
                        oldHint.theoryPlayback(alapin.position()).isEmpty()));

        var offBook = alapin.play(Move.fromUci("h7h5"));
        var engine = offBook.hint();
        engine.revealNext();
        check.same("An off-book black reply immediately exposes a legal engine suggestion", true,
                engine.revealedTheory() == null && engine.revealedMove() != null
                        && offBook.position().legalMoves().contains(engine.revealedMove()));
        check.same("Rewinding the off-book reply restores the Alapin hint", List.of("B22"), ecos(offBook.seek(3)));

        var french = offBook.seek(1).play(Move.fromUci("e7e6"));
        check.same("Branching from e4 into French regenerates the Advance line",
                List.of("C02"), ecos(french));
        check.same("The new line starts with d4 from the new board", "d2d4", paths(french).getFirst().move().uci());
    }

    @Test void transposesIntoTheCatalanFromAQueensGambitMoveOrder(Checks check) {
        var direct = line("d2d4 g8f6 c2c4 e7e6 g1f3 d7d5");
        var queensGambit = line("d2d4 d7d5 c2c4 e7e6");
        check.same("The Queen's Gambit order initially suggests the Orthodox line", List.of("D63"), ecos(queensGambit));
        var transposed = queensGambit.play(Move.fromUci("g1f3")).play(Move.fromUci("g8f6"));
        check.same("Trading move order reaches the same position and the same named paths",
                List.of(direct.position().searchKey(), paths(direct)), List.of(transposed.position().searchKey(), paths(transposed)));
        var catalan = paths(transposed).getFirst();
        var lesson = authored("catalan-open");
        check.same("The catalogue identifies the exact remaining Open Catalan path",
                List.of("E05", lesson.steps().subList(6, lesson.steps().size())), List.of(catalan.eco(), catalan.steps()));
        var replay = new OpeningReplay(transposed.position(), catalan);
        while (replay.hasNext()) replay.next();
        check.same("Replaying the transposed path reaches the authored position after dxc4",
                List.of(lesson.positionAt(lesson.steps().size()).searchKey(), Move.fromUci("d5c4")),
                List.of(replay.position().searchKey(), replay.lastMove()));
    }

    private static OpeningCatalog.Opening authored(String id) {
        return OpeningCatalog.load().stream().filter(opening -> opening.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<MatchHint.Theory> paths(AnalysisLine line) {
        var hint = line.hint();
        hint.revealNext();
        return hint.theoryPlayback(line.position()).orElseThrow().paths();
    }

    private static List<String> ecos(AnalysisLine line) { return paths(line).stream().map(MatchHint.Theory::eco).toList(); }

    private static AnalysisLine line(String moves) {
        var match = ChessMatch.restore("2|analysis|" + moves);
        return new AnalysisLine(match, match.ply());
    }
}
