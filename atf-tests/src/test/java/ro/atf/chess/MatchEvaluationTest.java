package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.TacticalAnalysis.Ending;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchEvaluationTest {
    @Test void positionEstimateSettlesTheOpeningWithoutChangingTheSuggestion(Checks check) {
        var board = OpeningCatalog.load().getFirst().positionAt(2);
        var lines = TacticalAnalysis.recommendations(board);
        var best = lines.getFirst();
        var display = MatchEvaluation.forPosition(board, best);
        check.same("Nf3 remains the leading recommendation", "g1f3", best.move().uci());
        check.same("The White advantage after e4 e5 is smaller than one pawn", true,
                display.whiteCentipawns() > 0 && display.whiteCentipawns() < 70);
        check.same("Computing the displayed score leaves the recommended lines and scores unchanged", lines, TacticalAnalysis.recommendations(board));

        var mate = ChessMatch.restore("1|f2f3 e7e5 g2g4").position();
        var forced = TacticalAnalysis.recommendations(mate).getFirst();
        check.same("A proved Black mate still fills the Black side exactly", 0.0, MatchEvaluation.forPosition(mate, forced).whiteShare());
        var draw = new Variation(List.of(), 0, 0, Ending.STALEMATE);
        check.same("A stalemate keeps the bar centered", .5, MatchEvaluation.forPosition(board, draw).whiteShare());
    }

    @Test void givesDevelopmentAnOpeningValueWithAColorSymmetricPerspective(Checks check) {
        var board = ChessPosition.initial();
        check.same("Symmetric starting pieces have no positional advantage", 0, MatchPositionEvaluation.bonus(board));
        var whiteDevelops = board.play(ro.atf.chess.ChessPosition.Move.fromUci("g1f3"));
        var rim = board.play(ro.atf.chess.ChessPosition.Move.fromUci("g1h3"));
        check.same("Developing toward the center scores higher for White", true,
                MatchPositionEvaluation.bonus(whiteDevelops) < MatchPositionEvaluation.bonus(rim));
        var whiteAndBlackDevelop = whiteDevelops.play(ro.atf.chess.ChessPosition.Move.fromUci("g8f6"));
        check.same("Mirrored development cancels exactly", 0, MatchPositionEvaluation.bonus(whiteAndBlackDevelop));
        var italian = OpeningCatalog.load().getFirst().positionAt(5);
        var blocksPawn = italian.play(ro.atf.chess.ChessPosition.Move.fromUci("f8d6"));
        var clearsCastle = italian.play(ro.atf.chess.ChessPosition.Move.fromUci("g8f6"));
        check.same("Black scores better developing Nf6 than blocking d7 with Bd6", true,
                MatchPositionEvaluation.bonus(clearsCastle) < MatchPositionEvaluation.bonus(blocksPawn));
    }

    @Test void confirmsTheSignWhenTheNextHorizonDisagreesAtTheQueensGambit(Checks check) {
        var board = ChessMatch.restore("1|d2d4 d7d5 c2c4").position();
        var best = TacticalAnalysis.recommendations(board).getFirst();
        var display = MatchEvaluation.forPosition(board, best);
        check.same("The equal-turn confirmation keeps White's small edge after 2.c4", true,
                display.whiteCentipawns() > 0 && display.whiteCentipawns() <= 50 && display.whiteShare() > .5);
        check.same("The additional horizon only changes the display estimate", best, TacticalAnalysis.recommendations(board).getFirst());
    }

    @Test void mapsEvaluationToTheBarWithEqualAndTerminalPositions(Checks check) {
        var ending = TacticalAnalysis.Ending.HORIZON;
        check.same("Zero keeps the split at the center", .5, new MatchEvaluation(ending, 0, 0).whiteShare());
        check.same("White four pawns ahead gains three quarters of the track", .75, new MatchEvaluation(ending, 400, 0).whiteShare());
        check.same("A large Black lead fills its side", 0.0, new MatchEvaluation(ending, -900, 0).whiteShare());
        check.same("White mate and a finished Black win occupy opposite ends", java.util.List.of(1.0, 0.0),
                java.util.List.of(new MatchEvaluation(TacticalAnalysis.Ending.CHECKMATE, 0, 2).whiteShare(),
                        MatchEvaluation.terminalWhiteShare(ChessMatch.Result.BLACK_WINS)));
        check.same("An actual draw splits the bar", .5, MatchEvaluation.terminalWhiteShare(ChessMatch.Result.REPETITION_DRAW));
    }

    private static final Locale ROMANIAN = Locale.forLanguageTag("ro-RO");

    @Test void scoresKeepWhitesPerspectiveAcrossTurnsAndLocales(Checks check) {
        var whiteToMove = MatchEvaluation.from(Side.WHITE, new Variation(List.of(), 125, 0, Ending.HORIZON));
        var blackToMove = MatchEvaluation.from(Side.BLACK, new Variation(List.of(), -125, 0, Ending.HORIZON));
        check.same("The same White advantage is shown when Black moves next", whiteToMove, blackToMove);
        check.same("Romanian uses a signed decimal comma", "+1,25", blackToMove.pawnLabel(ROMANIAN));
        check.same("English uses a signed decimal point", "+1.25", whiteToMove.pawnLabel(Locale.US));
        var blackAhead = MatchEvaluation.from(Side.BLACK, new Variation(List.of(), 300, 0, Ending.HORIZON));
        check.same("A Black advantage is negative and preserves two decimal places", "−3,00", blackAhead.pawnLabel(ROMANIAN));
        var balanced = MatchEvaluation.from(Side.BLACK, new Variation(List.of(), 0, 0, Ending.HORIZON));
        check.same("A balanced position has no sign", "0,00", balanced.pawnLabel(ROMANIAN));
    }

    @Test void matesAndStalemateAreDistinctFromMaterialScores(Checks check) {
        var whiteMate = QuestCatalog.loadMateInTwo().getFirst().position();
        var best = TacticalAnalysis.analyze(whiteMate, 3).best().getFirst();
        check.same("Three individual moves mean mate in two for White", 2, MatchEvaluation.from(whiteMate.turn(), best).whiteMateMoves());
        var blackMate = QuestCatalog.load().stream().filter(quest -> quest.id().equals("mate-001")).findFirst().orElseThrow().position();
        var mate = TacticalAnalysis.analyze(blackMate, 1).best().getFirst();
        check.same("Black's immediate mate has a negative mate distance", -1, MatchEvaluation.from(blackMate.turn(), mate).whiteMateMoves());
        var pat = MatchEvaluation.from(Side.WHITE, new Variation(List.of(), 0, 0, Ending.STALEMATE));
        check.same("Stalemate keeps its own ending even though its score is zero", Ending.STALEMATE, pat.ending());
    }
}
