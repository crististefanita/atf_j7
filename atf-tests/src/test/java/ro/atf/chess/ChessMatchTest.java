package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessMatchTest {
    @Test void optionalAnalysisSurvivesRestartAndUsesTheSelectedFrame(Checks check) {
        var legacy = ChessMatch.restore("1|f2f3 e7e5 g2g4");
        check.same("Legacy games leave analysis off", false, legacy.analysisEnabled());
        check.same("Disabled analysis provides no recommendation", true, legacy.analysisAt(3).isEmpty());

        var match = ChessMatch.start(true).play(Move.fromUci("f2f3")).play(Move.fromUci("e7e5")).play(Move.fromUci("g2g4"));
        var restored = ChessMatch.restore(match.checkpoint());
        check.same("An enabled game retains its setting and saved history", true, restored.analysisEnabled() && restored.ply() == 3);
        var recommendation = restored.analysisAt(3).orElseThrow();
        check.same("On Black's turn the analysis recommends immediate mate", "d8h4", recommendation.move().uci());
        check.same("The recommendation reports mate at the next ply", 1, recommendation.signedMatePlies());
        var previous = restored.analysisAt(2).orElseThrow();
        check.same("Reviewing an earlier frame recommends a legal White move", true,
                restored.positionAt(2).legalMoves().contains(previous.move()) && !previous.move().equals(recommendation.move()));
        var finished = restored.play(recommendation.move());
        check.same("The final frame of a finished game offers no move", true, finished.analysisAt(4).isEmpty());
        check.same("Earlier frames can still be analyzed after game over", recommendation, finished.analysisAt(3).orElseThrow());
    }

    @Test void savedHistoryExposesTheStartAndEveryPositionInOrder(Checks check) {
        var match = ChessMatch.restore("1|e2e4 b8c6 g1f3");
        check.same("Position zero has no move and preserves the starting pawn", true,
                match.moveAt(0) == null && match.positionAt(0).pieceAt(BoardSquare.parse("e2")).kind() == Kind.PAWN);
        check.same("Move one and its board correspond to the human move", true,
                "e2e4".equals(match.moveAt(1).uci()) && new ChessPosition.Piece(Side.WHITE, Kind.PAWN)
                        .equals(match.positionAt(1).pieceAt(BoardSquare.parse("e4"))));
        check.same("The next position records the computer's reply", "b8c6", match.moveAt(2).uci());
        check.same("The latest position is still the current match", match.position(), match.positionAt(match.ply()));
        check.same("Reading prior positions leaves the checkpoint intact", "1|e2e4 b8c6 g1f3", match.checkpoint());
    }
    @Test void restoresEitherSideOfTheComputerReply(Checks check) {
        var human = ChessMatch.start().play(Move.fromUci("e2e4"));
        var pending = ChessMatch.restore(human.checkpoint());
        check.same("The saved human move remains highlighted and Black still owes a reply",
                "e2e4", pending.lastMove().uci());
        check.same("The bot is due exactly at the saved ply", true, pending.ply() == 1 && pending.awaitingBot());
        var reply = pending.chooseBotMove();
        check.same("The bot chooses a legal reply from the restored board", true, pending.position().legalMoves().contains(reply));
        check.same("Restoring the same checkpoint gives the same reply", human.chooseBotMove(), reply);
        var restored = ChessMatch.restore(pending.play(reply).checkpoint());
        check.same("A committed bot move restores at the next human turn", true,
                restored.ply() == 2 && !restored.awaitingBot() && restored.position().turn() == Side.WHITE);
        check.same("The saved last move is the actual bot reply", reply, restored.lastMove());
    }

    @Test void theBotFindsMateAndCannotPlayPastTheResult(Checks check) {
        var pending = ChessMatch.restore("1|f2f3 e7e5 g2g4");
        var move = pending.chooseBotMove();
        check.same("The computer chooses the forced mate", "d8h4", move.uci());
        var ended = ChessMatch.restore(pending.play(move).checkpoint());
        check.same("The mate and winning side survive a restart", ChessMatch.Result.BLACK_WINS, ended.result());
        assertThrows(IllegalStateException.class, ended::chooseBotMove);
        assertThrows(IllegalStateException.class, () -> ended.play(Move.fromUci("a2a3")));
    }

    @Test void historyPreservesCastlingAndEnPassant(Checks check) {
        var castled = ChessMatch.restore("1|e2e4 e7e5 g1f3 b8c6 f1e2 g8f6 e1g1");
        check.same("The castled king stays on g1", Kind.KING, castled.position().pieceAt(BoardSquare.parse("g1")).kind());
        check.same("Replaying castling also moves the rook to f1", Kind.ROOK, castled.position().pieceAt(BoardSquare.parse("f1")).kind());
        var ep = ChessMatch.restore("1|e2e4 a7a6 e4e5 d7d5");
        var capture = Move.fromUci("e5d6");
        check.same("The en passant right is available after restoring the double step", true, ep.position().legalMoves().contains(capture));
        check.same("The restored capture removes the pawn from d5", null, ep.play(capture).position().pieceAt(BoardSquare.parse("d5")));
        assertThrows(IllegalArgumentException.class, () -> ChessMatch.restore("1|e2e5"));
        assertThrows(IllegalArgumentException.class, () -> ChessMatch.restore("2|e2e4"));
    }

    @Test void automaticDrawsUseThePositionAndItsHistory(Checks check) {
        String cycle = "g1f3 g8f6 f3g1 f6g8";
        var fourfold = ChessMatch.restore("1|" + String.join(" ", cycle, cycle, cycle));
        check.same("Four occurrences keep the local match in progress", ChessMatch.Result.IN_PROGRESS, fourfold.result());
        var fivefold = ChessMatch.restore(fourfold.checkpoint() + " " + cycle);
        check.same("The fifth occurrence is an automatic draw", ChessMatch.Result.REPETITION_DRAW, fivefold.result());
        var pat = ChessMatch.fromPosition(ChessPosition.fromFen("k7/2Q5/2K5/8/8/8/8/8 b - - 0 1"));
        check.same("Stalemate ends the match without a winner", ChessMatch.Result.STALEMATE, pat.result());
        var loneBishop = ChessMatch.fromPosition(ChessPosition.fromFen("4k3/8/8/8/8/8/2B5/4K3 w - - 0 1"));
        check.same("A lone bishop against a king is a dead position", ChessMatch.Result.MATERIAL_DRAW, loneBishop.result());
    }

    @Test void repetitionIncludesOnlyAvailableEnPassantAndPreservesCastlingRights(Checks check) {
        String pinned = "k3r3/8/8/3pP3/8/8/8/4K3 w - ";
        String free = "k7/8/8/3pP3/8/8/8/4K3 w - ";
        String rook = "4k3/8/8/8/8/8/8/4K2R w ";
        check.same("A pinned pawn cannot distinguish an otherwise repeated position", true,
                ChessPosition.fromFen(pinned + "d6 0 1").sameForRepetition(ChessPosition.fromFen(pinned + "- 0 1")));
        check.same("An available en passant capture distinguishes two positions", false,
                ChessPosition.fromFen(free + "d6 0 1").sameForRepetition(ChessPosition.fromFen(free + "- 0 1")));
        check.same("A castling right distinguishes positions with identical pieces", false,
                ChessPosition.fromFen(rook + "K - 0 1").sameForRepetition(ChessPosition.fromFen(rook + "- - 0 1")));
    }
}
