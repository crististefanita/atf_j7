package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The shared evaluator has one bounded structural rule, not an exercise-key override. */
@AtfComponentTest @ExtendWith(ExtentAudit.class)
class PawnBlockadeTest {
    private static final String BLACK="3r4/5k2/p4Pp1/2K3Pp/2R5/P7/8/8 b - - 0 51";
    private static final String WHITE="8/8/p7/2r5/2k3pP/P4pP1/5K2/3R4 w - - 0 51";
    @Test void onlyARealPassedPawnDirectlyBlockedByTheOpposingKingQualifies(Checks check) {
        var position=ChessPosition.fromFen("8/5k2/p4Pp1/6Pp/2K5/P7/8/8 b - - 0 53");
        int pawn=BoardSquare.parse("f6");
        check.same("f6 is passed and its direct advance is blocked by Kf7",true,PawnRace.kingBlocksPassedPawn(position,pawn,position.pieceAt(pawn)));
        var guarded=ChessPosition.fromFen("8/4pk2/p4Pp1/6Pp/2K5/P7/8/8 b - - 0 53");
        check.same("An opposing pawn on an adjacent forward file makes this a different structure",false,PawnRace.kingBlocksPassedPawn(guarded,pawn,guarded.pieceAt(pawn)));
        var free=ChessPosition.fromFen("8/4k3/p4Pp1/6Pp/2K5/P7/8/8 b - - 0 53");
        check.same("A king beside the advance is not a direct blockade",false,PawnRace.kingBlocksPassedPawn(free,pawn,free.pieceAt(pawn)));
    }
    @Test void theDiscountRemovesOnlyTheExistingAdvanceBonusWithConsistentColorAndTempo(Checks check) {
        var enabled=new PawnBlockadePolicy(true,1000);var disabled=new PawnBlockadePolicy(false,1000);var half=new PawnBlockadePolicy(true,500);
        for(var row:List.of(Map.entry("8/5k2/p4Pp1/6Pp/2K5/P7/8/8 b - - 0 53",48),Map.entry("8/5k2/p4Pp1/6Pp/2K5/P7/8/8 w - - 0 53",0),Map.entry("8/8/p7/2k5/6pP/P4pP1/5K2/8 w - - 0 53",48))) {
            var p=ChessPosition.fromFen(row.getKey());int before=MatchPositionEvaluation.bonus(p,disabled);
            check.same("The material ledger and other position terms stay outside the adjustment: "+row.getKey(),row.getValue(),MatchPositionEvaluation.bonus(p,enabled)-before);
            check.same("The configured fraction scales only that advancement",row.getValue()/2,MatchPositionEvaluation.bonus(p,half)-before);
        }
        var rooks=ChessPosition.fromFen(BLACK);
        check.same("The adjustment is absent while rooks remain",MatchPositionEvaluation.bonus(rooks,disabled),MatchPositionEvaluation.bonus(rooks,enabled));
    }
    @Test void anAdjacentPasserOrAReachablePromotionCannotJustifyDiscountingTheBlockadedPawn(Checks check) {
        var close=ChessPosition.fromFen("8/5k2/p4Pp1/6Pp/4K3/P7/8/8 b - - 0 53");int f6=BoardSquare.parse("f6");
        check.same("A king already inside the square can stop the outside pawn",false,PawnRace.uncatchableOutsidePasser(close,f6,close.pieceAt(f6)));
        var counterexample=ChessPosition.fromFen("8/8/2p1k1p1/p1KpP3/P4PP1/8/8/8 b - - 0 39");int e5=BoardSquare.parse("e5");
        check.same("A neighboring-file passer is not an outside racing pawn",false,PawnRace.uncatchableOutsidePasser(counterexample,e5,counterexample.pieceAt(e5)));
    }
    @Test void ordinaryPlayAndMaximumHintChooseTheWinningCheckInBothColors(Checks check) {
        for(var entry:List.of(Map.entry(BLACK,"d8c8"),Map.entry(WHITE,"d1c1"))) {
            var p=ChessPosition.fromFen(entry.getKey());var game=ChessMatch.fromPosition(p,new MatchSetup(p.turn().other(),MatchSetup.Level.HARD,true));String checkpoint=game.checkpoint();
            var actual=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,actual);
            check.same("Native H"+actual.decision().depth()+" nodes="+actual.decision().extraNodes()+" "+entry.getKey(),entry.getValue(),actual.decision().move().uci());
            var hint=TacticalAnalysis.verifiedHints(p,200000);
            check.same("The maximum public hint independently agrees",entry.getValue(),hint.lines().getFirst().move().uci());
            check.same("The root correction does not silently raise the ordinary allowance",true,actual.decision().extraNodes()<=200000&&hint.extraNodes()<=200000);
            var review=GameReview.analyzeLine(game.play(ChessPosition.Move.fromUci(entry.getValue())),n->{});
            check.same("Game Review retains its own search and the same winning recommendation",entry.getValue(),review.turns().getFirst().bestLine().getFirst().uci());
            check.same("Analysis cannot mutate the actual checkpoint",checkpoint,game.checkpoint());
        }
    }
    @Test void liquidationKeepsTheSupportedPawnRaceInsteadOfTheCooperativeKingCapture(Checks check) {
        for(var entry:List.of(Map.entry(BLACK,"d8c8 c5d4 c8c4 d4c4 h5h4"),Map.entry(WHITE,"d1c1 c4d5 c1c5 d5c5 h4h5"))) {
            var p=ChessPosition.fromFen(entry.getKey());var game=ChessMatch.fromPosition(p,new MatchSetup(p.turn().other(),MatchSetup.Level.HARD,true));var moves=entry.getValue().split(" ");
            for(int i=0;i<4;i++)game=game.play(ChessPosition.Move.fromUci(moves[i]));String checkpoint=game.checkpoint();
            var actual=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,actual);
            check.same("The two exact even-horizon comparisons support the pawn advance",moves[4],actual.decision().move().uci());
            check.same("The completed H6 and confirmation share the ordinary ceiling",true,actual.decision().depth()==6&&actual.decision().extraNodes()<=200000);
            check.same("The maximum hint independently agrees after the exchange",moves[4],TacticalAnalysis.verifiedHints(game.position(),200000).lines().getFirst().move().uci());
            check.same("Only the real outside racing pawn can be confirmed",true,PawnBlockadePolicy.CURRENT.confirms(game.position(),actual.decision().move()));
            var kingMove=ChessPosition.Move.fromUci(p.turn()==ChessPosition.Side.BLACK?"f7e6":"f2e3");
            check.same("A king move cannot borrow the pawn-race exception",false,PawnBlockadePolicy.CURRENT.confirms(game.position(),kingMove));
            check.same("Disabling the feature disables its confirmation too",false,new PawnBlockadePolicy(false,1000).confirms(game.position(),actual.decision().move()));
            check.same("A zero coefficient is also a neutral feature",false,new PawnBlockadePolicy(true,0).confirms(game.position(),actual.decision().move()));
            check.same("The complete actual history is conserved",checkpoint,game.checkpoint());
        }
    }
    @Test void theInspectedOppositionCounterexampleKeepsItsWinningPawnBreak(Checks check) {
        var p=ChessPosition.fromFen("8/8/2p1k1p1/p2pP3/P2K1PP1/8/8/8 w - - 0 39");
        var game=ChessMatch.fromPosition(p,new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,false)).play(ChessPosition.Move.fromUci("d4c5"));
        for(var level:List.of(MatchSetup.Level.ADVANCED,MatchSetup.Level.HARD)) {
            var actual=game.analyzeBot(level);HardQuestAudit.validate(game,actual);
            check.same("021cc retains g5 instead of the drawing king retreat: "+level,"g6g5",actual.decision().move().uci());
        }
    }
    @Test void anInterruptedEvenHorizonCheckCannotApproveTheLeaderChange(Checks check)throws Exception {
        var p=ChessPosition.fromFen("8/5k2/p4Pp1/6Pp/2K5/P7/8/8 b - - 0 53");
        var game=ChessMatch.fromPosition(p,new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,false));var actual=game.analyzeBot(MatchSetup.Level.HARD);
        var rootType=Class.forName("ro.atf.chess.TacticalAnalysis$RootSearch");var rc=rootType.getDeclaredConstructor(ChessPosition.class,Set.class);rc.setAccessible(true);
        var budgetType=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var bc=budgetType.getDeclaredConstructor(int.class,int.class,int.class);bc.setAccessible(true);var budget=bc.newInstance(0,0,50000);
        var confirm=TacticalAnalysis.class.getDeclaredMethod("corroboratedEvenLeader",rootType,ChessPosition.Move.class,TacticalAnalysis.Result.class,int.class,budgetType);confirm.setAccessible(true);
        check.same("A completed deeper ranking cannot borrow an unfinished H4 comparison",false,confirm.invoke(null,rc.newInstance(p,Set.of()),ChessPosition.Move.fromUci("f7e6"),actual.search().result(),6,budget));
        var remaining=budgetType.getDeclaredField("remaining");remaining.setAccessible(true);
        check.same("No proof nodes exist beyond the allowance",0,remaining.getInt(budget));
    }
    @Test void unknownKeysOutOfRangeWeightsAndUntypedSwitchesAreRejected(Checks check) {
        var row=new HashMap<Object,Object>(ChessYaml.resource("pawn-blockade.yaml"));
        check.same("The active policy is the typed file",PawnBlockadePolicy.CURRENT,PawnBlockadePolicy.read(row));
        for(Object bad:List.of(-1,1001,"1000")){var changed=new HashMap<>(row);changed.put("advance-discount-per-mille",bad);check.rejects("Invalid discount "+bad,IllegalArgumentException.class,()->PawnBlockadePolicy.read(changed));}
        for(String key:List.of("fen","winning-move","oracle-score","extra-nodes")){var changed=new HashMap<>(row);changed.put(key,"forbidden");check.rejects("No hidden context/key override "+key,IllegalArgumentException.class,()->PawnBlockadePolicy.read(changed));}
        row.put("enabled","true");check.rejects("The switch is a boolean",IllegalArgumentException.class,()->PawnBlockadePolicy.read(row));
    }
}
