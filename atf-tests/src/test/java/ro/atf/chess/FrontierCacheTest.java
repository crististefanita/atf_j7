package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class FrontierCacheTest {
    private static final List<String> POSITIONS=List.of(
            "8/7k/8/3r4/3Q4/8/8/K7 w - - 0 1",
            "7k/8/8/3q4/3R4/8/8/K7 b - - 0 1",
            "4r2k/8/8/8/8/8/8/4K3 w - - 0 1");
    @Test void boundsAndOrderingPreserveIndependentMinimax(Checks check)throws Exception {
        for(String fen:POSITIONS)for(int depth:List.of(2,3))for(int capacity:List.of(0,8,22000)) {
            var board=ChessPosition.fromFen(fen);int expected=minimax(board,depth,0);var search=new Search(200000,capacity);
            search.call(board,depth,0,expected-2,expected-1);search.call(board,depth,0,expected+1,expected+2);
            var exact=search.call(board,depth,0,-100001,100001);
            check.same("Full-window minimax after both bounds; H"+depth+" cache="+capacity+" "+fen,expected,score(exact));
            check.same("The final value is exact",true,value(exact,"exact"));
            for(var line:search.map("transpositions").values())check.same("Only exact lines enter the exact table",true,value(line,"exact"));
            for(var bound:search.map("bounds").values())check.same("A bound remains explicitly inexact",false,value(value(bound,"line"),"exact"));
            check.same("Exact and bounded values share the configured slot limit",true,search.map("transpositions").size()+search.map("bounds").size()<=capacity);
            check.same("Ordering observations have their own finite limit",true,search.map("preferred").size()<=Math.min(4096,capacity));
        }
    }
    @Test void aNewHorizonOrMateDistanceCannotBorrowAnOldBound(Checks check)throws Exception {
        var board=ChessPosition.fromFen(POSITIONS.getFirst());var search=new Search(200000,22000);int shallow=minimax(board,2,0);
        search.call(board,2,0,shallow-2,shallow-1);
        check.same("The next ordinary horizon is independently searched",minimax(board,3,0),score(search.call(board,3,0,-100001,100001)));
        var mate=ChessPosition.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1");
        check.same("Terminal mate has the original distance",-100000,score(search.call(mate,2,0,-100001,100001)));
        check.same("A different distance cannot reuse the root mate score",-99993,score(search.call(mate,2,7,-100001,100001)));
    }
    @Test void rollingTheHorizonRetainsOnlyOrderingAndSpendsNoSearchNode(Checks check)throws Exception {
        var board=ChessPosition.fromFen(POSITIONS.getFirst());var search=new Search(200000,22000);search.call(board,3,0,-100001,100001);int before=search.remaining();var observations=new HashMap<>(search.map("preferred"));
        var method=budgetType.getDeclaredMethod("reuseFrontierWork");method.setAccessible(true);method.invoke(search.budget);
        check.same("Stale exact and bounded horizon entries are discarded",List.of(0,0),List.of(search.map("transpositions").size(),search.map("bounds").size()));
        check.same("Legal ordering observations remain available",observations,search.map("preferred"));check.same("Compaction does not manufacture or spend nodes",before,search.remaining());
        var remaining=budgetType.getDeclaredField("remaining");remaining.setAccessible(true);remaining.setInt(search.budget,0);boolean stopped=false;
        try{search.call(board,3,0,-100001,100001);}catch(InvocationTargetException e){if(!e.getCause().getClass().getSimpleName().equals("BudgetExhausted"))throw e;stopped=true;}
        check.same("An exhausted probe cannot become a static success",true,stopped);check.same("No negative allowance",0,search.remaining());
    }
    private static int minimax(ChessPosition p,int left,int distance)throws Exception {var moves=p.legalMoves();if(left==0||moves.isEmpty())return score(leaf.invoke(null,p,distance,!moves.isEmpty(),objective));int best=-100001;for(var m:moves)best=Math.max(best,-minimax(p.play(m),left-1,distance+1));return best;}
    private static final Class<?> budgetType,goalType;private static final Object objective;private static final Method run,leaf;
    static{try{budgetType=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");goalType=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");@SuppressWarnings({"rawtypes","unchecked"})var v=Enum.valueOf((Class)goalType,"POSITION");objective=v;run=TacticalAnalysis.class.getDeclaredMethod("search",ChessPosition.class,int.class,int.class,int.class,int.class,budgetType,goalType);run.setAccessible(true);leaf=TacticalAnalysis.class.getDeclaredMethod("leaf",ChessPosition.class,int.class,boolean.class,goalType);leaf.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static final class Search {
        final Object budget;
        Search(int nodes,int limit)throws Exception{var ctor=budgetType.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);budget=ctor.newInstance(nodes,0,limit);for(String name:List.of("principalVariationSearch","reuseFrontierWork")){var field=budgetType.getDeclaredField(name);field.setAccessible(true);field.setBoolean(budget,true);}}
        Object call(ChessPosition p,int left,int distance,int alpha,int beta)throws Exception{return run.invoke(null,p,left,distance,alpha,beta,budget,objective);}
        Map<?,?> map(String name)throws Exception{var f=budgetType.getDeclaredField(name);f.setAccessible(true);return (Map<?,?>)f.get(budget);}
        int remaining()throws Exception{var f=budgetType.getDeclaredField("remaining");f.setAccessible(true);return f.getInt(budget);}
    }
    private static int score(Object line)throws Exception{return (int)value(line,"score");}
    private static Object value(Object o,String name)throws Exception{var method=o.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(o);}
}
