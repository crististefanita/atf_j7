package ro.atf.chess;

import java.util.List;
import ro.atf.chess.ChessPosition.Move;

/** Preserves a composed origin's clocks and en-passant state during independent study replay. */
final class StudyFen {
    private StudyFen() { }

    static String replay(String initialFen, List<Move> history) {
        var board=ChessPosition.fromFen(initialFen);var fields=initialFen.trim().split("\\s+");
        int quiet=Integer.parseInt(fields[4]),full=Integer.parseInt(fields[5]);String ep=fields[3];
        for(var move:history) {
            boolean pawn=board.pieceAt(move.from()).kind()==ChessPosition.Kind.PAWN;
            quiet=pawn||MatchMaterial.victim(board,move)!=null?0:quiet+1;
            ep=pawn&&Math.abs(move.to()-move.from())==16?BoardSquare.name((move.to()+move.from())/2):"-";
            if(board.turn()==ChessPosition.Side.BLACK)full++;
            board=board.play(move);
        }
        var placement=HintAuditSession.fen(ChessMatch.fromPosition(board)).split(" ");
        return placement[0]+" "+placement[1]+" "+placement[2]+" "+ep+" "+quiet+" "+full;
    }
}
