package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchHintTest {
    @Test void hidesThenCyclesAllTiedContinuationsWithoutPlayingThem(Checks check) {
        var match = ChessMatch.start(true);
        var lines = match.bestLinesAt(0);
        var hint = new MatchHint(lines);
        check.same("The starting board has more than one best line", true, lines.size() > 1);
        check.same("Evaluation is available while its hint is still hidden", true, hint.first() != null && hint.revealed() == null);
        var seen = new HashSet<ChessPosition.Move>();
        for (int index = 0; index < lines.size(); index++) {
            hint.revealNext();
            seen.add(hint.revealed().move());
            check.same("Line " + (index + 1) + " preserves the complete legal engine continuation", lines.get(index), hint.revealed());
        }
        check.same("There is one continuation per best first move", lines.size(), seen.size());
        hint.revealNext();
        check.same("One more press wraps to the first line", lines.getFirst(), hint.revealed());
        check.same("Showing a line leaves the match checkpoint untouched", "2|analysis|", match.checkpoint());
    }

    @Test void aSingleMateStaysVisibleAndAnotherFrameStartsHidden(Checks check) {
        var match = ChessMatch.restore("2|analysis|f2f3 e7e5 g2g4");
        var current = new MatchHint(match.bestLinesAt(3));
        check.same("The fastest mate is the only top continuation", 1, current.count());
        current.revealNext();
        current.revealNext();
        check.same("Repeated presses retain the same mate in one", "d8h4", current.revealed().move().uci());
        var earlier = new MatchHint(match.bestLinesAt(2));
        check.same("An earlier position starts with its variants hidden", null, earlier.revealed());
        check.same("An ended game has no lines for its final frame", List.of(), match.play(current.revealed().move()).bestLinesAt(4));
        var disabled = new MatchHint(ChessMatch.start().bestLinesAt(0));
        disabled.revealNext();
        check.same("A disabled analysis cannot reveal a line", null, disabled.revealed());
    }
}
