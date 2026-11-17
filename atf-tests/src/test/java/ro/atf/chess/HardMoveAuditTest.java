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

    @Test void anInternalRepetitionNeedsBothThePriorGameAndTheSearchedPath(Checks check) {
        String history = "d2d4 g8f6 c2c4 e7e6 c1g5 f8b4 b1d2 h7h6 g5f6 d8f6 g1f3 c7c5 a2a3 b4a5 b2b4 c5b4 e2e3 b4a3 a1a3 a5b4 a3a2 b8c6 "
                + "d1b1 d7d6 f1d3 b4c3 e1g1 d6d5 b1b3 c6b4 a2a1 b4d3 b3c3 d3f2 g1f2 c8d7 c3a3 b7b5 c4b5 d7b5 f1b1 b5c6 a3d6 a8c8 a1a7 "
                + "h6h5 b1c1 h5h4 f2g1 g7g5 c1c3 g5g4 f3e5 g4g3 e5f3 h8h5 h2h3 c6b7 d6a3 c8c3 a3c3 f6e7 d2b3 h5f5 b3d2 e8f8 c3c5 e7c5 "
                + "d4c5 b7c6 a7a1 e6e5 d2b3 d5d4 e3d4 c6f3 g2f3 f5f3 a1b1 e5d4 c5c6 f3c3 b3d4 c3c4 d4f5 f8g8 f5d6 c4c6 d6f5 c6c4 b1b6 c4c1 g1g2 c1c2 g2g1";
        var match = ChessMatch.restore("3|WHITE|HARD|1|" + history);
        var entry = HardMoveAudit.capture(match);
        check.same("The real rook witness contains an early zero-score repetition line", true,
                entry.variations().stream().anyMatch(line -> line.ending() == TacticalAnalysis.Ending.HORIZON
                        && line.line().size() < entry.depth() && line.score() == 0));
        var altered = ChessMatch.restore("3|WHITE|HARD|1|" + history.replace("c4c1 g1g2 c1c2 g2g1", "c4c3 g1g2 c3c2 g2g1"));
        check.same("The counterexample has the same board, side, clocks and rights", entry.fen(), HintAuditSession.fen(altered));
        var unsupported = new HardMoveAudit.Entry(altered.checkpoint(), entry.fen(), entry.ply(), entry.side(), entry.selected(), entry.depth(),
                entry.extraNodes(), entry.theoryReference(), entry.scoreKind(), entry.selectedRank(), entry.variations());
        check.rejects("Matching FEN cannot replace the missing third occurrence in the full context", IllegalArgumentException.class,
                () -> HardMoveAudit.validate(unsupported));
    }

    private static HardMoveAudit.Entry copy(HardMoveAudit.Entry e, String fen, int nodes, List<TacticalAnalysis.Variation> lines) {
        return new HardMoveAudit.Entry(e.checkpoint(), fen, e.ply(), e.side(), e.selected(), e.depth(), nodes,
                e.theoryReference(), e.scoreKind(), e.selectedRank(), lines);
    }
}
