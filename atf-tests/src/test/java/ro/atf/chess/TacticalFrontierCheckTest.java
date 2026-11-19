package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class TacticalFrontierCheckTest {
    @Test @org.junit.jupiter.api.Tag("benchmark") void aCheckedFrontierMustSearchItsLegalEvasion(Checks check)throws Exception {
        for(String fen:List.of("k7/8/8/8/8/8/4r3/4K3 w - - 0 1", "4k3/4R3/8/8/8/8/8/K7 b - - 0 1")) {
            var board=ChessPosition.fromFen(fen);var frontier=new Frontier(100);var result=frontier.search(board);
            var moves=moves(result);
            check.same("The checked side cannot stand pat at the configured frontier",false,moves.isEmpty());
            check.same("The legal king capture resolves the checking rook",ChessPosition.Move.fromUci(board.turn()==ChessPosition.Side.WHITE?"e1e2":"e8e7"),moves.getFirst());
            check.same("The evasion is charged to this same allowance",true,frontier.remaining()<100);
        }
    }
    @Test @org.junit.jupiter.api.Tag("benchmark") void quietKingEvasionsRemainAvailableWithoutCaptureFiltering(Checks check)throws Exception {
        var board=ChessPosition.fromFen("4r2k/8/8/8/8/8/8/4K3 w - - 0 1");var result=new Frontier(100).search(board);var path=moves(result);
        check.same("A check cannot be treated as a quiet frontier",false,path.isEmpty());
        check.same("The returned non-capture is a legal response",true,board.legalMoves().contains(path.getFirst()));
        check.same("The evasion preserves the original king",ChessPosition.Kind.KING,board.play(path.getFirst()).pieceAt(path.getFirst().to()).kind());
    }
    @Test void quietFrontiersAndActualMatesRemainTerminal(Checks check)throws Exception {
        var quiet=new Frontier(100);var result=quiet.search(ChessPosition.fromFen("7k/8/8/8/8/8/8/4K3 w - - 0 1"));
        check.same("No gratuitous quiet extension",List.of(),moves(result));check.same("No extra work for a quiet frontier",100,quiet.remaining());
        var mate=new Frontier(100);var m=mate.search(ChessPosition.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"));
        check.same("A real mate is not an unplayed evasion",List.of(),moves(m));check.same("The terminal result remains checkmate","CHECKMATE",field(m,"ending").toString());
    }
    @Test @org.junit.jupiter.api.Tag("benchmark") void anExhaustedEvasionCannotBecomeAnExactStaticScore(Checks check)throws Exception {
        var frontier=new Frontier(0);boolean exhausted=false;
        try {frontier.search(ChessPosition.fromFen("4r2k/8/8/8/8/8/8/4K3 w - - 0 1"));}
        catch(InvocationTargetException e){if(!e.getCause().getClass().getSimpleName().equals("BudgetExhausted"))throw e;exhausted=true;}
        check.same("An interrupted mandatory evasion propagates exhaustion",true,exhausted);check.same("The allowance never goes negative",0,frontier.remaining());
    }
    @SuppressWarnings("unchecked") private static List<ChessPosition.Move> moves(Object line)throws Exception {return (List<ChessPosition.Move>)field(line,"moves");}
    private static Object field(Object value,String name)throws Exception {var method=value.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);}
    private static final class Frontier {
        final Object budget;final Method settle;
        Frontier(int nodes)throws Exception {var type=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var ctor=type.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);budget=ctor.newInstance(nodes,2,0);settle=TacticalAnalysis.class.getDeclaredMethod("settle",ChessPosition.class,int.class,int.class,int.class,int.class,type,int.class,int.class);settle.setAccessible(true);}
        Object search(ChessPosition p)throws Exception{return settle.invoke(null,p,0,8,-100001,100001,budget,-1,-1);}
        int remaining()throws Exception{var f=budget.getClass().getDeclaredField("remaining");f.setAccessible(true);return f.getInt(budget);}
    }
}
