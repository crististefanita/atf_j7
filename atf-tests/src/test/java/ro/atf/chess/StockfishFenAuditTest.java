package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class StockfishFenAuditTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String FEN="7k/4Q3/5K2/8/8/8/8/8 w - - 0 1";

    @Test void supportsExplicitFenOriginsWithoutBorrowingAStartPositionPhase(Checks check) {
        var receipt=fixture(FEN,"e7g7");StockfishStudyAudit.validate(receipt.get("query"),receipt.get("identity"),receipt);
        var step=JSON.createObjectNode();step.putObject("queries").putObject("ROOT").put("19","fixture");var receipts=Map.<String,com.fasterxml.jackson.databind.JsonNode>of("fixture",receipt);
        check.same("The explicit puzzle origin participates in phase projection",receipt,StockfishStudyAudit.phase(step,"ROOT",19,List.of(),"e7g7",receipts,FEN));
        check.rejects("A FEN-origin phase cannot masquerade as a normal opening phase",IllegalArgumentException.class,()->StockfishStudyAudit.phase(step,"ROOT",19,List.of(),"e7g7",receipts));
        check.rejects("The correct move and empty history cannot excuse a different origin",IllegalArgumentException.class,()->StockfishStudyAudit.phase(step,"ROOT",19,List.of(),"e7g7",receipts,FEN.replace("0 1","1 1")));
    }

    @Test void initialFenClocksAndRawPositionCommandsRemainStrict(Checks check) {
        var original=fixture(FEN,"e7g7");
        for(String kind:List.of("origin","clock","command")) {
            var bad=original.deepCopy();
            if(kind.equals("origin"))bad.withObject("/query").put("initialFen",FEN.replace(" w "," b "));
            if(kind.equals("clock"))bad.put("fen",FEN.replace("0 1","4 1"));
            if(kind.equals("command"))bad.withObject("/uci").withArray("commands").set(6,JSON.getNodeFactory().textNode("position startpos"));
            check.rejects("The composed-position receipt rejects substituted "+kind,IllegalArgumentException.class,()->StockfishStudyAudit.validate(original.get("query"),original.get("identity"),bad));
        }
        var free=fixture(FEN,null);var other=fixture("7k/4Q3/6K1/8/8/8/8/8 w - - 7 42","e7g7");
        StockfishStudyAudit.validate(other.get("query"),other.get("identity"),other);
        check.rejects("Two different puzzle origins with an empty continuation cannot form a WDL delta",IllegalArgumentException.class,()->StockfishStudyAudit.loss(free,other));
    }

    @Test void replayPreservesBlackMoveNumbersQuietClocksAndTheInitialEnPassantRight(Checks check) {
        var start="4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 42";
        check.same("The explicit origin is not reset to quiet0/fullmove1",start,StudyFen.replay(start,List.of()));
        var path=List.of(ChessPosition.Move.fromUci("e5d6"),ChessPosition.Move.fromUci("e8d7"));
        check.same("En passant resets the quiet clock; a Black reply advances the fullmove number","8/3k4/3P4/8/8/8/8/4K3 w - - 1 43",StudyFen.replay(start,path));
    }

    private static ObjectNode fixture(String fen,String root) {
        var row=JSON.createObjectNode();var q=row.putObject("query");q.put("initialFen",fen);q.putArray("history");q.put("depth",19);q.put("binarySha256","a".repeat(64));
        if(root==null)q.putNull("root");else q.put("root",root);
        q.putObject("options").put("Threads",1).put("Hash",32).put("UCI_LimitStrength",false).put("UCI_ShowWDL",true);
        row.putObject("identity").put("run","mock-FEN-protocol-not-an-oracle").put("binarySha256","a".repeat(64));
        row.put("fen",fen).put("terminal",false).put("actualDepth",19).put("nodes",100).put("scoreType","mate").put("score",1).put("expectedPermille",1000).put("bestmove","e7g7");
        row.set("wdl",JSON.valueToTree(List.of(1000,0,0)));row.set("pv",JSON.valueToTree(List.of("e7g7")));
        var raw=row.putObject("uci");raw.set("commands",JSON.valueToTree(List.of("uci","setoption name Threads value 1","setoption name Hash value 32","setoption name UCI_LimitStrength value false","setoption name UCI_ShowWDL value true","isready","position fen "+fen,"go depth 19"+(root==null?"":" searchmoves "+root),"quit")));
        raw.set("stdout",JSON.valueToTree(List.of("id name Stockfish 19","uciok","readyok","info depth 19 score mate 1 wdl 1000 0 0 nodes 100 pv e7g7","bestmove e7g7")));return row;
    }
}
