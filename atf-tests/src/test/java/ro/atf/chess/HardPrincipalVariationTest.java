package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Compare the PVS optimization with exhaustive minimax, independent of its move ordering. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardPrincipalVariationTest {
    private static final List<String> POSITIONS=List.of(
        "8/7k/8/3r4/3Q4/8/8/K7 w - - 0 1",
        "7k/8/8/3q4/3R4/8/8/K7 b - - 0 1",
        "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1",
        "4r2k/8/8/8/8/8/8/4K3 w - - 0 1",
        "7k/P7/8/8/8/8/7p/K7 w - - 0 1");
    @Test void fullWindowsAgreeWithIndependentExhaustiveMinimax(Checks check)throws Exception {
        for(String fen:POSITIONS)for(int depth:List.of(1,2,3)) {
            var board=ChessPosition.fromFen(fen);int expected=minimax(board,depth,0);
            for(boolean enabled:List.of(false,true))for(int cache:List.of(0,22000)) {
                var engine=new Search(enabled,cache);var line=engine.run(board,depth,-100001,100001);
                check.same("Exact H"+depth+" PVS="+enabled+" cache="+cache+" "+fen,expected,number(line,"score"));
                check.same("The independent full-window result is exact",true,field(line,"exact"));
                var replay=board;for(var move:path(line)){check.same("Every returned ply is legal",true,replay.legalMoves().contains(move));replay=replay.play(move);}
                check.same("A warm exact table retains the same full result",line,engine.run(board,depth,-100001,100001));
            }
        }
    }
    @Test void narrowBoundsCannotPoisonTheLaterExactScore(Checks check)throws Exception {
        for(String fen:POSITIONS.subList(0,2)) {
            var board=ChessPosition.fromFen(fen);int expected=minimax(board,3,0);var engine=new Search(true,22000);
            var lower=engine.run(board,3,expected-11,expected-10);var upper=engine.run(board,3,expected+10,expected+11);
            check.same("Fail-high remains a lower bound",true,number(lower,"score")>=expected-10&&number(lower,"score")<=expected);
            check.same("Fail-low remains an upper bound",true,number(upper,"score")<=expected+10&&number(upper,"score")>=expected);
            check.same("A narrow window never supplies an exact result",List.of(false,false),List.of(field(lower,"exact"),field(upper,"exact")));
            check.same("The later full window recovers the exhaustive value",expected,number(engine.run(board,3,-100001,100001),"score"));
        }
    }
    private static int minimax(ChessPosition board,int left,int distance)throws Exception {
        var moves=board.legalMoves();if(left==0||moves.isEmpty())return number(leaf.invoke(null,board,distance,!moves.isEmpty(),objective),"score");
        int best=-100001;for(var move:moves)best=Math.max(best,-minimax(board.play(move),left-1,distance+1));return best;
    }
    private static final Class<?> budgetType,goalType;private static final Object objective;private static final Method search,leaf;
    static {try{budgetType=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");goalType=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");@SuppressWarnings({"rawtypes","unchecked"})var value=Enum.valueOf((Class)goalType,"POSITION");objective=value;search=TacticalAnalysis.class.getDeclaredMethod("search",ChessPosition.class,int.class,int.class,int.class,int.class,budgetType,goalType);search.setAccessible(true);leaf=TacticalAnalysis.class.getDeclaredMethod("leaf",ChessPosition.class,int.class,boolean.class,goalType);leaf.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static final class Search {
        final Object budget;
        Search(boolean pvs,int cache)throws Exception {var ctor=budgetType.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);budget=ctor.newInstance(2000000,0,cache);var enabled=budgetType.getDeclaredField("principalVariationSearch");enabled.setAccessible(true);enabled.setBoolean(budget,pvs);}
        Object run(ChessPosition p,int depth,int alpha,int beta)throws Exception{return search.invoke(null,p,depth,0,alpha,beta,budget,objective);}
    }
    @SuppressWarnings("unchecked") private static List<ChessPosition.Move> path(Object line)throws Exception{return (List<ChessPosition.Move>)field(line,"moves");}
    private static int number(Object line,String name)throws Exception{return (int)field(line,name);}
    private static Object field(Object value,String name)throws Exception {var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);}
}
