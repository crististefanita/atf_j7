package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class TacticalAnalysisTest {
    @Test void hardRootOrderingPromotesChecksAndPreservesTheEvaluatedOrderWithinEachGroup(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1");
        var moves = new ArrayList<>(List.of(Move.fromUci("e1d2"), Move.fromUci("d1h5"), Move.fromUci("d1b3")));
        check.same("The fixture starts with one forcing check and two quiet alternatives", List.of(false, true, false),
                moves.stream().map(move -> position.play(move).isCheck()).toList());
        TacticalAnalysis.orderCheckingRootMovesFirst(position, moves);
        check.same("Checking moves move first while evaluated order remains stable inside each group",
                List.of("d1h5", "e1d2", "d1b3"), moves.stream().map(Move::uci).toList());
    }

    @Test void hardAcceptsTheExtraHorizonOnlyWhenItsLeaderIsStable(Checks check) {
        var previous = Move.fromUci("e2e4");
        var changed = new TacticalAnalysis.Result(List.of(new Variation(List.of(Move.fromUci("d2d4")), 20, 0, Ending.HORIZON)));
        var stable = new TacticalAnalysis.Result(List.of(new Variation(List.of(previous), 10, 0, Ending.HORIZON)));
        check.same("A changed depth-six leader falls back to the completed depth-five choice", false,
                TacticalAnalysis.stableHardLeader(previous, changed));
        check.same("A stable depth-six leader may replace the completed choice", true,
                TacticalAnalysis.stableHardLeader(previous, stable));
    }

    @Test void hardQuietHistoryLearnsCutoffsWithoutPromotingChecksOrCaptures(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1");
        var quiet = Move.fromUci("d1b3");
        var checking = Move.fromUci("d1h5");
        var ordering = new TacticalAnalysis.QuietMoveOrdering();
        check.same("A new search has no inherited history", 0, ordering.bonus(position, quiet, false));
        ordering.recordCutoff(position, quiet, 4, false);
        check.same("A quiet cutoff gains depth-squared history", 512, ordering.bonus(position, quiet, false));
        for (int cutoff = 0; cutoff < 7; cutoff++) ordering.recordCutoff(position, quiet, 5, false);
        check.same("Quiet history stays within its ordering cap", 6_000, ordering.bonus(position, quiet, false));
        ordering.recordCutoff(position, checking, 5, true);
        check.same("A checking move does not enter the quiet history", 0, ordering.bonus(position, checking, true));

        var capturePosition = ChessPosition.fromFen("3rk3/8/8/8/8/8/8/3QK3 w - - 0 1");
        var capture = Move.fromUci("d1d8");
        check.same("The capture fixture is a legal tactical move", true, capturePosition.legalMoves().contains(capture));
        ordering.recordCutoff(capturePosition, capture, 5, true);
        check.same("A capture does not enter the quiet history", 0, ordering.bonus(capturePosition, capture, true));
    }

    @Test void aReplyChangesAnApparentGainIntoALoss(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/3p4/4r3/3Q4/8/8/4K3 w - - 0 1");
        var shallow = variation(position, 1, "d4e5");
        var answered = variation(position, 2, "d4e5");
        check.same("Taking the rook appears to gain five pawn units after one ply", 500, shallow.materialChange());
        check.same("The opponent can take the exposed queen on the next ply", List.of("d4e5", "d6e5"), uci(answered));
        check.same("The best recapture turns the exchange into a net loss", -400, answered.materialChange());
        check.same("After the reply, keeping the queen scores higher", false,
                TacticalAnalysis.analyze(position, 2).best().contains(answered));
    }

    @Test void twoDistinctCapturesKeepTheirEqualRank(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/8/3p1p2/4B3/8/8/4K3 w - - 0 1");
        var best = TacticalAnalysis.analyze(position, 2).best();
        check.same("Both bishop captures survive ranking in deterministic order",
                List.of("e4d5", "e4f5"), best.stream().map(entry -> entry.move().uci()).toList());
        check.same("Each of the equal replies gains exactly one pawn",
                List.of(100, 100), best.stream().map(Variation::materialChange).toList());
        check.same("Both variations include the opponent's move", List.of(2, 2), best.stream().map(entry -> entry.line().size()).toList());
    }

    @Test void theThirdPlyProvesTheCatalogMateAndPatScoresZero(Checks check) {
        var quest = QuestCatalog.loadMateInTwo().getFirst();
        var shortLine = variation(quest.position(), 2, "d2d8");
        var mate = TacticalAnalysis.analyze(quest.position(), 3).best().getFirst();
        check.same("The queen sacrifice has no proven mate within two plies", 0, shortLine.signedMatePlies());
        check.same("The third ply proves the published mate after the defense", List.of("d2d8", "b6d8", "d1d8"), uci(mate));
        check.same("The mate is three plies away for the side to move", 3, mate.signedMatePlies());
        var ending = ChessPosition.fromFen("k7/8/1QK5/8/8/8/8/8 w - - 0 1");
        var pat = variation(ending, 1, "b6c7");
        check.same("Stalemate scores zero despite a queen advantage", List.of(Ending.STALEMATE, 0), List.of(pat.ending(), pat.score()));
        check.same("The other queen move delivers checkmate", Ending.CHECKMATE, variation(ending, 1, "b6b7").ending());
    }

    @Test void thePuzzleExplainsTheBestLegalReplyWithoutAdvancing(Checks check) {
        String fen = "r2qk2r/ppp2ppp/1bnp4/1B2p3/4P1bP/2N2N2/PPPPQ1P1/R1B1K2n w kq - 2 12";
        var position = ChessPosition.fromFen(fen);
        var attempted = Move.fromUci("b5c6");
        var quest = new QuestCatalog.Quest("italian-training", fen, attempted, "TACTIC", position);
        var result = quest.assess(attempted);
        check.same("The attempted move gives check without advancing the quest", ChessPosition.Outcome.CHECK, result.outcome());
        check.same("The board still belongs to the same quest stage", false, result.continues());
        check.same("The first generated defense would give away the bishop", "g4d7", result.position().legalMoves().getFirst().uci());
        check.same("The opponent instead captures the checking bishop with the b-pawn", "b7c6", result.defense().uci());
        var reply = result.replyAnalysis().best().getFirst();
        check.same("The line accounts for White's strongest material reply", List.of("b7c6", "f3e5"), uci(reply));
        check.same("Black gains a bishop and gives up a pawn", 230, reply.materialChange());
    }

    @Test void aLosingSidePrefersAReplyThatAvoidsImmediateMate(Checks check) {
        var position = ChessPosition.fromFen("8/7k/4r1p1/7p/KP5P/6P1/2q5/8 w - - 6 57");
        var ranked = TacticalAnalysis.analyze(position, 2);
        var losing = variation(position, 2, "a4a3");
        check.same("The tempting move allows Black to mate on its very next turn", List.of("a4a3", "e6e3"), uci(losing));
        check.same("A mate against the side to move has a negative distance", -2, losing.signedMatePlies());
        check.same("Both alternatives avoid that mate within the horizon",
                List.of("a4a5", "a4b5"), ranked.best().stream().map(entry -> entry.move().uci()).toList());
    }

    private static Variation variation(ChessPosition position, int plies, String move) {
        return TacticalAnalysis.analyze(position, plies).ranked().stream()
                .filter(entry -> entry.move().uci().equals(move)).findFirst().orElseThrow();
    }

    private static List<String> uci(Variation variation) {
        return variation.line().stream().map(Move::uci).toList();
    }
}
