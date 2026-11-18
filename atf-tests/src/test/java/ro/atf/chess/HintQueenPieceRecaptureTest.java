package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintQueenPieceRecaptureTest {
    private static final String PREFIX="e2e4 c7c6 d2d4 d7d5 b1c3";
    private static final List<Move> BAD=java.util.Arrays.stream("d5e4 c3e4 e7e5 c2c3 e5d4 c3d4 d8d4".split(" ")).map(Move::fromUci).toList();

    @Test void theActualCaroHintCannotEndAtTheUnansweredQueenCapture(Checks check) {
        var game=ChessMatch.restore("3|WHITE|HARD|1|"+PREFIX);String checkpoint=game.checkpoint();
        var search=TacticalAnalysis.verifiedHints(game.position(),200000);
        check.same("The defended theoretical pawn capture remains the primary suggestion",move("d5e4"),search.lines().getFirst().move());
        check.same("The old line ending with a lost queen no longer supplies the score",false,search.lines().stream().anyMatch(line->line.line().equals(BAD)));
        check.same("All additional recaptures share the public 200k ceiling",true,search.extraNodes()>0&&search.extraNodes()<=200000);
        var hint=game.hintAt(game.ply());hint.revealNext();
        check.same("The bulb and the retained scored line agree",search.lines().getFirst(),hint.revealed());
        check.same("Tapping the destination uses the same calculated move",move("d5e4"),hint.moveForTap(game.position(),-1,move("d5e4").to()));
        check.same("The source history remains intact",checkpoint,game.checkpoint());
    }

    @Test void theFrontierScoresTheQueensLegalRecaptureInBothColors(Checks check)throws Exception {
        for(String fen:List.of("rnbqkbnr/pp3ppp/2p5/8/3qN3/8/PP3PPP/R1BQKBNR w KQkq - 0 7",
                "r1bqkbnr/pp3ppp/8/3Qn3/8/2P5/PP3PPP/RNBQKBNR b KQkq - 0 7")) {
            var board=ChessPosition.fromFen(fen);int square=board.turn()==ChessPosition.Side.WHITE?move("d8d4").to():move("d1d5").to();
            var before=new Frontier(0);var ordinary=before.boundary(board,square);var enabled=new Frontier(0);enabled.enable();var resolved=enabled.boundary(board,square);
            check.same("The legal queen response is actually searched for "+board.turn(),List.of(board.turn()==ChessPosition.Side.WHITE?move("d1d4"):move("d8d5")),field(resolved,"moves"));
            check.same("Scoring changes before the line is displayed",true,(int)field(resolved,"score")>(int)field(ordinary,"score")+500);
            check.same("The reply consumes the same decision allowance",19999,enabled.remaining());
        }
    }

    @Test void selectiveClosureCannotManufactureAnEquivalentSicilianOffer(Checks check) {
        var board=ChessPosition.fromFen("rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3");
        var search=TacticalAnalysis.verifiedHints(board,200000);
        check.same("The sound exchange stays the public offer",List.of(move("c5d4")),search.lines().stream().map(TacticalAnalysis.Variation::move).toList());
        check.same("A selectively resolved frontier cannot certify a new equivalent move",false,search.lines().stream().anyMatch(line->line.move().equals(move("b8d7"))));
        check.same("Reconfirmation also pays for every attempted node",true,search.extraNodes()<=200000);
    }

    @Test void oldCacheBoundsAndSeedsCannotMasqueradeAsResolvedQueenExchanges(Checks check)throws Exception {
        var board=ChessPosition.fromFen("3q2k1/8/8/8/3p4/8/3R4/3Q2K1 w - - 0 1");
        var warm=new Frontier(64);var old=warm.search(board,-100001,100001);warm.enable();var actual=warm.search(board,-100001,100001);
        var cold=new Frontier(64);cold.enable();var expected=cold.search(board,-100001,100001);
        var disabled=new Frontier(0);disabled.enable();
        check.same("The ordinary and resolved modes have distinct cache identities",java.util.Set.of(0,1),warm.cacheModes());
        check.same("Warm and cold caches preserve the same value",field(expected,"score"),field(actual,"score"));
        check.same("Disabling the cache preserves the same value",field(expected,"score"),field(disabled.search(board,-100001,100001),"score"));
        var bounded=new Frontier(64);bounded.enable();int score=(int)field(expected,"score");
        check.same("A cutoff remains a bound",false,field(bounded.search(board,score-2,score-1),"exact"));
        check.same("The bound cannot poison the subsequent full window",score,field(bounded.search(board,-100001,100001),"score"));
    }

    @Test void pinnedPieceRecapturesAreIllegalAndSoundQueenSacrificesRemainMates(Checks check)throws Exception {
        var can=TacticalAnalysis.class.getDeclaredMethod("nonPawnCanRecaptureQueen",ChessPosition.class,int.class);can.setAccessible(true);
        var free=ChessPosition.fromFen("6k1/8/8/3q4/8/4N3/8/4K3 w - - 0 1");
        var pinned=ChessPosition.fromFen("4r1k1/8/8/3q4/8/4N3/8/4K3 w - - 0 1");
        check.same("A legal knight can close the queen exchange",true,can.invoke(null,free,move("e3d5").to()));
        check.same("A pinned knight cannot be invented as a recapture",false,can.invoke(null,pinned,move("e3d5").to()));
        var mate=ChessPosition.fromFen("4r2r/p1p2kp1/2p2p2/2Qb4/8/2B3Nq/PPP2R1P/5RK1 b - - 0 30");
        var search=TacticalAnalysis.verifiedHints(mate,200000);var first=search.lines().getFirst();
        check.same("The sound queen offer is still the mating recommendation",List.of(move("h3g3"),3),List.of(first.move(),first.signedMatePlies()));
        check.same("The actual pawn capture still leads to mate",true,mate.play(move("h3g3")).play(move("h2g3")).play(move("h8h1")).isCheckmate());
    }

    private static Move move(String uci){return Move.fromUci(uci);}
    private static Object field(Object value,String name)throws Exception{var method=value.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);}
    private static final class Frontier {
        final Object budget,objective;final Method search,settle;
        @SuppressWarnings({"rawtypes","unchecked"}) Frontier(int cache)throws Exception {
            var type=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var constructor=type.getDeclaredConstructor(int.class,int.class,int.class);constructor.setAccessible(true);budget=constructor.newInstance(20000,2,cache);
            var goal=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");objective=Enum.valueOf((Class)goal,"POSITION");
            search=TacticalAnalysis.class.getDeclaredMethod("search",ChessPosition.class,int.class,int.class,int.class,int.class,type,goal);search.setAccessible(true);
            settle=TacticalAnalysis.class.getDeclaredMethod("settle",ChessPosition.class,int.class,int.class,int.class,int.class,type,int.class,int.class,boolean.class);settle.setAccessible(true);
        }
        void enable()throws Exception{var f=budget.getClass().getDeclaredField("queenPieceRecaptureExtension");f.setAccessible(true);f.setInt(budget,1);}
        int remaining()throws Exception{var f=budget.getClass().getDeclaredField("remaining");f.setAccessible(true);return f.getInt(budget);}
        java.util.Set<Integer> cacheModes()throws Exception{var f=budget.getClass().getDeclaredField("transpositions");f.setAccessible(true);var result=new java.util.HashSet<Integer>();for(var key:((java.util.Map<?,?>)f.get(budget)).keySet())result.add((int)field(key,"queenPieceRecaptureExtension"));return result;}
        Object boundary(ChessPosition board,int square)throws Exception{return settle.invoke(null,board,0,8,-100001,100001,budget,square,-1,true);}
        Object search(ChessPosition board,int alpha,int beta)throws Exception{return search.invoke(null,board,0,0,alpha,beta,budget,objective);}
    }
}
