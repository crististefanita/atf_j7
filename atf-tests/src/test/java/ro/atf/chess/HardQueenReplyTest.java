package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardQueenReplyTest {
    @Test void phaseAndConfigurationKeepSparseEndgamesOutsideThisVerification(Checks check) {
        var policy=QueenReplyPolicy.CURRENT;
        check.same("The active phase threshold and aspiration window are explicit",List.of(true,16,200),List.of(policy.enabled(),policy.minimumPhase(),policy.aspirationWindowCp()));
        var middle=ChessPosition.fromFen("r2q2kr/8/8/8/8/8/8/R2Q2KR w - - 0 1");
        var sparse=ChessPosition.fromFen("r2q2k1/8/8/8/8/8/8/R2Q2KR w - - 0 1");
        check.same("The boundary uses the same phase weights as the position evaluator",List.of(16,14),List.of(MatchPositionEvaluation.phase(middle),MatchPositionEvaluation.phase(sparse)));
        check.same("The configured boundary is inclusive",true,policy.applies(middle));
        check.same("The neighboring lower phase keeps the ordinary horizon guard",false,policy.applies(sparse));
        check.same("An explicit disabled policy never activates",false,new QueenReplyPolicy(false,0,200).applies(ChessPosition.initial()));
    }
    @Test void malformedOrAmbiguousPoliciesAreRejected(Checks check) {
        var row=new HashMap<String,Object>(Map.of("schema",1,"enabled",true,"minimum-phase",16,"aspiration-window-cp",200));
        for(var entry:List.of(Map.entry("minimum-phase",-1),Map.entry("minimum-phase",25),Map.entry("aspiration-window-cp",0),Map.entry("aspiration-window-cp",1001),Map.entry("schema",2))) {
            var changed=new HashMap<String,Object>(row);changed.put(entry.getKey(),entry.getValue());
            check.rejects("Unsupported policy value "+entry,IllegalArgumentException.class,()->QueenReplyPolicy.read(changed));
        }
        var unknown=new HashMap<String,Object>(row);unknown.put("hidden-budget",500000);
        check.rejects("Unknown keys cannot silently change the budget",IllegalArgumentException.class,()->QueenReplyPolicy.read(unknown));
        var untyped=new HashMap<String,Object>(row);untyped.put("enabled","true");
        check.rejects("The switch must be a boolean",IllegalArgumentException.class,()->QueenReplyPolicy.read(untyped));
    }
    @Test void theFourHistoricalQueenFrontiersIncludeChecksAndNonPawnVictims(Checks check)throws Exception {
        for(var witness:HardDefeatWitnessTest.load()) {
            var analysis=HardQuestAudit.read(witness.input().path("originalAnalysis"));
            var line=analysis.search().result().ranked().getFirst();
            check.same(witness.id()+" actual legal recapture, not just a geometric attack",!witness.id().equals("petroff-hard-white"),
                    TacticalAnalysis.unresolvedQueenRecapture(witness.game().position(),line,analysis.decision().depth()));
        }
    }
    @Test void aPinnedRecapturerOrQuietQueenMoveCannotActivateTheReserve(Checks check) {
        String prefix="a1b1 h8g8 b1a1 g8h8 ";
        var free=ChessPosition.fromFen("3r3k/8/8/3p4/8/3Q4/8/K7 w - - 0 1");
        var pinned=ChessPosition.fromFen("R2r3k/8/8/3p4/8/3Q4/8/K7 w - - 0 1");
        var capture=line(prefix+"d3d5");
        check.same("The free rook can legally take the capturing queen",true,TacticalAnalysis.unresolvedQueenRecapture(free,capture,5));
        check.same("The pinned rook cannot legally take that queen",false,TacticalAnalysis.unresolvedQueenRecapture(pinned,capture,5));
        check.same("A queen move without a capture is not this risk",false,TacticalAnalysis.unresolvedQueenRecapture(free,line(prefix+"d3e3"),5));
        check.same("A mismatched reported horizon cannot activate this branch",false,TacticalAnalysis.unresolvedQueenRecapture(free,capture,4));
        var mate=new TacticalAnalysis.Variation(capture.line(),99995,0,TacticalAnalysis.Ending.CHECKMATE);
        check.same("A proved mate cannot be replaced by a frontier fallback",false,TacticalAnalysis.unresolvedQueenRecapture(free,mate,5));
    }
    private static TacticalAnalysis.Variation line(String moves) {
        return new TacticalAnalysis.Variation(Arrays.stream(moves.split(" ")).map(ChessPosition.Move::fromUci).toList(),0,0,TacticalAnalysis.Ending.HORIZON);
    }

    @Test void anAspirationMissMustRecoverEveryExactRootScoreAndTie(Checks check)throws Exception {
        for(String fen:List.of("8/7k/8/3r4/3Q4/8/8/K7 w - - 0 1","7k/8/8/3q4/3R4/8/8/K7 b - - 0 1")) {
            var p=ChessPosition.fromFen(fen);
            for(int depth:List.of(2,3)) {
                var rootType=Class.forName("ro.atf.chess.TacticalAnalysis$RootSearch");var ctor=rootType.getDeclaredConstructor(ChessPosition.class,Set.class);ctor.setAccessible(true);
                var goal=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");@SuppressWarnings({"rawtypes","unchecked"})var objective=Enum.valueOf((Class)goal,"POSITION");
                var analyze=TacticalAnalysis.class.getDeclaredMethod("analyze",rootType,int.class,List.class,goal);analyze.setAccessible(true);
                var expected=(TacticalAnalysis.Result)analyze.invoke(null,ctor.newInstance(p,Set.of()),depth,p.legalMoves(),objective);
                var best=expected.best().stream().map(v->List.of(v.move(),v.score())).toList();
                for(int prediction:List.of(-2000,0,2000)) {
                    var bt=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var bc=bt.getDeclaredConstructor(int.class,int.class,int.class);bc.setAccessible(true);var budget=bc.newInstance(2000000,0,22000);
                    var field=bt.getDeclaredField("rootPrediction");field.setAccessible(true);field.set(budget,prediction);
                    var reuse=bt.getDeclaredMethod("reuseFrontierWork");reuse.setAccessible(true);reuse.invoke(budget);
                    var deepen=TacticalAnalysis.class.getDeclaredMethod("deepen",rootType,List.class,int.class,bt,int.class,Map.class);deepen.setAccessible(true);
                    var moves=new ArrayList<>(p.legalMoves());Collections.reverse(moves);
                    var actual=(TacticalAnalysis.Result)deepen.invoke(null,ctor.newInstance(p,Set.of()),moves,depth,budget,0,Map.of());
                    check.same("A bad prediction is not a final bound: H"+depth+" center="+prediction+" "+fen,best,
                            actual.ranked().stream().map(v->List.of(v.move(),v.score())).toList());
                    for(var v:actual.ranked()){var end=p;for(var m:v.line())end=end.play(m);}
                }
            }
        }
    }
}
