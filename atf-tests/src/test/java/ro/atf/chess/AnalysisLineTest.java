package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AnalysisLineTest {
    @Test void exploresBothColorsFromHistoryAndPreservesTheOriginalCheckpoint(Checks check) {
        var saved = play("e2e4", "e7e5", "g1f3", "b8c6");
        String checkpoint = saved.checkpoint();
        var entry = new AnalysisLine(saved, 1);
        check.same("Opening a historical ply keeps the remaining original moves available",
                List.of(1, 4, Side.BLACK), List.of(entry.ply(), entry.line().ply(), entry.position().turn()));
        check.same("Replaying the next recorded move preserves the original suffix", saved,
                entry.play(Move.fromUci("e7e5")).line());
        var line = entry.play(Move.fromUci("c7c5")).play(Move.fromUci("g1f3"));
        check.same("Both colors are playable and the divergent move replaces only the study suffix",
                List.of("e2e4", "c7c5", "g1f3"), moves(line.line()));
        check.same("Previous, next and reset address exact positions", List.of("c7c5", "g1f3", "e2e4"),
                List.of(line.seek(2).lastMove().uci(), line.seek(2).seek(3).lastMove().uci(), entry.lastMove().uci()));
        check.same("The checkpoint and result of the saved game are unaffected", checkpoint, saved.checkpoint());
        var restored = new AnalysisLine(ChessMatch.restore(line.line().checkpoint()), 2);
        check.same("Activity recreation restores the line and cursor independently", true,
                line.seek(2).position().sameForRepetition(restored.position()) && line.line().checkpoint().equals(restored.line().checkpoint()));
    }

    @Test void historyKeepsSpecialMoveRightsAndRejectsIllegalOrTerminalPlay(Checks check) {
        var enPassant = new AnalysisLine(play("e2e4", "a7a6", "e4e5", "d7d5", "g1f3"), 4);
        var captured = enPassant.play(Move.fromUci("e5d6")).position();
        check.same("An alternate en passant from history removes the pawn on d5", null, captured.pieceAt(BoardSquare.parse("d5")));
        check.same("The historical line retains the original d5 pawn", Kind.PAWN, enPassant.position().pieceAt(BoardSquare.parse("d5")).kind());
        check.rejects("An attempted move for the inactive color is rejected", IllegalArgumentException.class,
                () -> enPassant.play(Move.fromUci("a6a5")));
        var mate = play("f2f3", "e7e5", "g2g4", "d8h4");
        var ended = new AnalysisLine(mate, mate.ply());
        check.same("The actual terminal ply is read only", ChessMatch.Result.BLACK_WINS, ended.result());
        check.rejects("A new move at mate is rejected", IllegalStateException.class, () -> ended.play(Move.fromUci("a2a3")));
        var alternate = ended.seek(2).play(Move.fromUci("b2b3"));
        check.same("Earlier positions in a finished game can start a playable analysis branch", ChessMatch.Result.IN_PROGRESS, alternate.result());
        check.same("The original completed result is preserved", ChessMatch.Result.BLACK_WINS, mate.result());
    }

    @Test void promotionRemainsExplicitAndEachAlternatePromotionIsLegal(Checks check) {
        var line = new AnalysisLine(ChessMatch.fromPosition(ChessPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")), 0);
        check.rejects("Promotion without a piece is rejected", IllegalArgumentException.class, () -> line.play(Move.fromUci("a7a8")));
        var kinds = List.of("q", "r", "b", "n").stream().map(piece -> line.play(Move.fromUci("a7a8" + piece))
                .position().pieceAt(BoardSquare.parse("a8")).kind()).toList();
        check.same("Queen, rook, bishop and knight produce distinct branches", List.of(Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT), kinds);
    }

    @Test void continuingAStudyKeepsTheSelectedPrefixAndItsOwnAnalysisSetting(Checks check) {
        var source = ChessMatch.start(new MatchSetup(Side.BLACK, MatchSetup.Level.HARD, false));
        for (String move : List.of("e2e4", "e7e5", "g1f3", "b8c6")) source = source.play(Move.fromUci(move));
        String checkpoint = source.checkpoint();
        for (int ply = 0; ply <= source.ply(); ply++) {
            var study = new AnalysisLine(source, ply);
            var resumed = ChessMatch.restore(study.resumeMatch(true).checkpoint());
            check.same("The new slot keeps precisely the selected history prefix", moves(source).subList(0, ply), moves(resumed));
            check.same("The continuation keeps the source side and level, with the selected analysis setting",
                    new MatchSetup(Side.BLACK, MatchSetup.Level.HARD, true), resumed.setup());
            check.same("AI ownership follows the turn of the restored position", study.position().turn() == Side.WHITE, resumed.awaitingBot());
            check.same("Analysis can also remain disabled for the new slot", false, study.resumeMatch(false).analysisEnabled());
        }
        var mate = play("f2f3", "e7e5", "g2g4", "d8h4");
        check.rejects("A terminal position cannot start another active game", IllegalStateException.class,
                () -> new AnalysisLine(mate, mate.ply()).resumeMatch(true));
        check.same("The selected position before mate can still start a game", 3, new AnalysisLine(mate, 3).resumeMatch(true).ply());
        check.same("The source slot retains every original move and setting", checkpoint, source.checkpoint());
    }

    @Test void aSavedDecisionDoesNotEndTheIndependentStudyPosition(Checks check) {
        var played = play("e2e4", "e7e5");
        for (var finished : List.of(played.resign(), played.offerDraw())) {
            String checkpoint = finished.checkpoint();
            var study = new AnalysisLine(ChessMatch.restore(checkpoint), finished.ply());
            check.same(finished.result() + " keeps the recorded board playable in analysis", ChessMatch.Result.IN_PROGRESS, study.result());
            check.same("The next study move uses the final position", "g1f3", study.play(Move.fromUci("g1f3")).lastMove().uci());
            check.same("Exploring the final position preserves the match decision", checkpoint, finished.checkpoint());
        }
    }

    @Test void completedReviewUsesOnlyTheExactUneditedSourceRegardlessOfTheCursor(Checks check) {
        var match = play("e2e4", "e7e5", "g1f3", "b8c6").resign();
        var entry = new AnalysisLine(match, 1);
        check.same("A cursor in history still owns the entire completed source", true, entry.preservesCompletedGame(match));
        check.same("Following the original move preserves its completed review", true,
                entry.play(Move.fromUci("e7e5")).preservesCompletedGame(match));
        check.same("The exact line survives reconstruction from a saved study", true,
                new AnalysisLine(ChessMatch.restore(entry.line().checkpoint()), 2).preservesCompletedGame(match));
        check.same("A different suffix cannot inherit the source rating", false,
                entry.play(Move.fromUci("c7c5")).preservesCompletedGame(match));
        check.same("An unfinished source with identical moves cannot acquire a result", false,
                entry.preservesCompletedGame(match.forAnalysis()));
        var manualMate = new AnalysisLine(ChessMatch.start(), 0);
        for (String move : List.of("f2f3", "e7e5", "g2g4", "d8h4")) manualMate = manualMate.play(Move.fromUci(move));
        check.same("Reaching mate in a manual study still does not make its source a completed game", false,
                manualMate.preservesCompletedGame(ChessMatch.start()));
    }

    private static ChessMatch play(String... moves) {
        var match = ChessMatch.start();
        for (String move : moves) match = match.play(Move.fromUci(move));
        return match;
    }

    private static List<String> moves(ChessMatch match) {
        return java.util.stream.IntStream.rangeClosed(1, match.ply()).mapToObj(ply -> match.moveAt(ply).uci()).toList();
    }
}
