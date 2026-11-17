package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardMoveAuditTest {
    @Test void retainsTheActualHardSearchOnBothSidesWithoutChangingTheMatch(Checks check) {
        for (var history : List.of("e2e4 c7c6 d2d4", "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4")) {
            var match = ChessMatch.restore("3|WHITE|HARD|1|" + history);
            var before = match.checkpoint();
            var audit = HardMoveAudit.capture(match);
            var ordinary = match.botDecision(MatchSetup.Level.HARD);
            check.same("Audit retains the ordinary production move", ordinary.move().uci(), audit.selected());
            check.same("Audit retains the actual completed horizon and charged nodes", List.of(ordinary.depth(), ordinary.extraNodes()),
                    List.of(audit.depth(), audit.extraNodes()));
            check.same("An audit cannot play a move or alter the full match history", before, match.checkpoint());
            check.same("Every normal decision has scored native root evidence", true, audit.selectedRank() > 0 && !audit.variations().isEmpty());
            check.same("Native scores cannot be presented as an external oracle verdict", true, audit.scoreKind().endsWith("NOT_ORACLE"));
        }
    }

    @Test void replayRejectsForeignPositionsMissingRootsAndBudgetInflation(Checks check) {
        var entry = HardMoveAudit.capture(ChessMatch.restore("3|WHITE|HARD|1|e2e4 c7c6 d2d4"));
        check.rejects("A native decision cannot carry a different clock", IllegalArgumentException.class,
                () -> HardMoveAudit.validate(copy(entry, entry.fen().replace("0 2", "1 2"), entry.extraNodes(), entry.variations())));
        check.rejects("A larger native allowance cannot be hidden in the receipt", IllegalArgumentException.class,
                () -> HardMoveAudit.validate(copy(entry, entry.fen(), 200_001, entry.variations())));
        check.rejects("A receipt without the scored roots cannot pass replay", IllegalArgumentException.class,
                () -> HardMoveAudit.validate(copy(entry, entry.fen(), entry.extraNodes(), List.of())));
    }

    @Test void repetitionEvidenceSurvivesAWholeHistoryReplay(Checks check) {
        var match = ChessMatch.restore("3|WHITE|HARD|1|g1f3 g8f6 f3g1 f6g8 g1f3 g8f6 f3g1");
        var entry = HardMoveAudit.capture(match);
        HardMoveAudit.validate(entry);
        check.same("Native analysis keeps the repeated full history", match.checkpoint(), entry.checkpoint());
        check.same("A replay does not silently start a fresh position-only search", match.ply(), entry.ply());
        check.same("The selected repeated-position move stays legal", true,
                match.position().legalMoves().contains(ChessPosition.Move.fromUci(entry.selected())));
    }

    private static HardMoveAudit.Entry copy(HardMoveAudit.Entry e, String fen, int nodes, List<TacticalAnalysis.Variation> lines) {
        return new HardMoveAudit.Entry(e.checkpoint(), fen, e.ply(), e.side(), e.selected(), e.depth(), nodes,
                e.theoryReference(), e.scoreKind(), e.selectedRank(), lines);
    }
}
