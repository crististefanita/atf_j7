package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestDangerTest {
    @Test void identifiesOnlyTheMatedKingOnTheCurrentFrame(Checks check) {
        for (var quests : List.of(QuestCatalog.load(), QuestCatalog.loadMateInTwo(), QuestCatalog.loadMateInThree())) {
            var line = QuestPayoff.forMateLine(quests.get(0));
            check.same("The mark is missing before checkmate in the " + quests.get(0).id() + " line", true,
                    IntStream.range(0, line.plies()).mapToObj(line::dangerAt)
                            .noneMatch(mark -> mark != null && mark.kind() == QuestDanger.Kind.MATE));
            check.same("The last frame marks the mated king on its actual square in " + quests.get(0).id(),
                    new QuestDanger(QuestDanger.Kind.MATE, line.last().position().explainMate().king(), 0), line.dangerAt(line.plies()));
        }
    }

    @Test void confirmsTheWorstLegalReplyInBothColors(Checks check) {
        var white = replay("k3r3/4p3/8/8/3p4/8/4R3/K2Q4 w - - 0 1", "e2e7");
        var black = replay("k2q4/4r3/8/3P4/8/8/4P3/K3R3 b - - 0 1", "e7e2");
        for (var line : List.of(white, black)) {
            var frame = line.last();
            check.same("The first capture gains a pawn " + frame.move().uci(), 100, frame.materialChange());
            check.same("A real recapture can erase all of the pawn gain " + frame.move().uci(), null, line.dangerAt(1));
        }
        var safe = replay("5k2/p1p4p/8/8/8/8/R1R4R/K7 w - - 0 1", "a2a3 f8g8 a3a7");
        var reflected = replay("k7/r1r4r/8/8/8/8/P1P4P/5K2 b - - 0 1", "a7a6 f1g1 a6a2");
        check.same("A quiet move and the defender reply have no capture mark", true,
                safe.dangerAt(1) == null && safe.dangerAt(2) == null);
        check.same("The retained pawn gain is marked on a7", capture("a7", 100), safe.dangerAt(3));
        check.same("The same proof marks a2 with Black as solver", capture("a2", 100), reflected.dangerAt(3));
        var partial = replay("7k/8/8/8/3q3r/2P5/8/K2R4 w - - 0 1", "d1d4");
        check.same("A safe positive floor is lower than the initial capture when the opponent takes the rook", capture("d4", 400), partial.dangerAt(1));
    }

    @Test void rejectsStalemateAndUsesOnlyLegalRecaptures(Checks check) {
        var stale = replay("k7/8/1rK5/1Q6/8/8/8/8 w - - 0 1", "b5b6");
        check.same("Taking a rook into stalemate gives no danger", List.of(ChessPosition.Outcome.STALEMATE, true),
                List.of(stale.last().position().outcome(), stale.dangerAt(1) == null));
        var pinned = replay("4k3/4n3/2r5/8/8/8/8/K1Q1R3 w - - 0 1", "c1c6");
        check.same("The knight appears to defend c6 but is pinned", false, pinned.last().position().legalMoves().contains(Move.fromUci("e7c6")));
        check.same("A pinned recapture cannot disprove the capture", capture("c6", 500), pinned.dangerAt(1));
        check.rejects("A pinned attacker cannot manufacture a replay proof", IllegalArgumentException.class,
                () -> replay("4r2k/8/8/8/8/8/3qR3/4K3 w - - 0 1", "e2d2"));
    }

    @Test void marksTheVictimSquareForEnPassant(Checks check) {
        var line = replay("7k/8/8/3pP3/8/8/8/K7 w - d6 0 1", "e5d6");
        check.same("The capture mark points to the removed pawn on d5", capture("d5", 100), line.dangerAt(1));
    }

    private static QuestDanger capture(String square, int cp) {
        return new QuestDanger(QuestDanger.Kind.CAPTURE, BoardSquare.parse(square), cp);
    }

    private static QuestPayoff replay(String fen, String line) {
        return QuestPayoff.replay(ChessPosition.fromFen(fen), Arrays.stream(line.split(" ")).map(Move::fromUci).toList(), null, null);
    }
}
