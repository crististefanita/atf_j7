package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class LearningGameAuditTest {
    private static final String FEN="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final LearningGameAudit.Identity ID=new LearningGameAudit.Identity("component-audit","a".repeat(40),"b".repeat(64),"c".repeat(64));
    @Test void bothColorsRequireTheirOwnMaximumHintBeforePlaying(Checks check) {
        var journal=new ArrayList<LearningGameAudit.Envelope>();int[] nativeCalls={0},hintCalls={0},requested={0};
        var observer=fixture(journal,nativeCalls,hintCalls,requested);
        check.rejects("White cannot play without both calculations",IllegalArgumentException.class,()->observer.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null));
        var first=observer.observe();check.same("Repeated inspection reuses the same pre-move frame",first,observer.observe());
        check.same("The public hint is explicitly requested at its maximum",200000,requested[0]);
        check.same("Actual work below maximum is not misclassified as a smaller request",0,first.hint().extraNodes());
        observer.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null);
        check.rejects("Black cannot borrow White's hint",IllegalArgumentException.class,()->observer.play(move("e7e5"),LearningGameAudit.Actor.THEORY,null));
        observer.observe();observer.play(move("e7e5"),LearningGameAudit.Actor.THEORY,null);
        check.same("One native search per actual checkpoint",2,nativeCalls[0]);check.same("One maximum hint per actual checkpoint",2,hintCalls[0]);
        check.same("Both sides are retained",List.of("WHITE","BLACK"),journal.stream().map(r->r.body().before().side()).toList());
        check.same("Complete actual history is independently restored",observer.match().checkpoint(),LearningGameAudit.validate(journal,ID,"fixture",FEN).checkpoint());
    }
    @Test void persistenceFailureKeepsTheOldPositionAndPendingAnalysis(Checks check) {
        int[] writes={0};var events=new ArrayList<LearningGameAudit.Envelope>();
        var observer=new LearningGameAudit(ID,"fixture",FEN,start(),r->{if(++writes[0]==1)throw new IllegalStateException("write failed");events.add(r);},LearningGameAuditTest::nativeOne,(b,n)->hintOne(b));
        var pending=observer.observe();
        check.rejects("A failed journal write prevents the actual move",IllegalStateException.class,()->observer.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null));
        check.same("The game still precedes the move",0,observer.match().ply());check.same("The exact pending search remains reusable",pending,observer.observe());
        observer.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null);check.same("Retry commits exactly once",1,events.size());
    }
    @Test void sourcePrefixBudgetAndPhaseSubstitutionAreRejected(Checks check)throws Exception {
        var events=new ArrayList<LearningGameAudit.Envelope>();var o=fixture(events,new int[1],new int[1],new int[1]);o.observe();o.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null);o.observe();o.play(move("e7e5"),LearningGameAudit.Actor.THEORY,null);
        check.rejects("Missing White's record cannot become a complete Black-only game",IllegalArgumentException.class,()->LearningGameAudit.validate(events.subList(1,2),ID,"fixture",FEN));
        check.rejects("Foreign run cannot borrow maximum calculations",IllegalArgumentException.class,()->LearningGameAudit.validate(events,new LearningGameAudit.Identity("foreign",ID.revision(),ID.runtimeDigest(),ID.configurationDigest()),"fixture",FEN));
        var json=LearningGameAudit.mapper();var bad=json.valueToTree(events.getFirst().body());
        ((com.fasterxml.jackson.databind.node.ObjectNode)bad.get("before")).put("requestedHintNodes",50000);
        var body=json.treeToValue(bad,LearningGameAudit.Body.class);var altered=new LearningGameAudit.Envelope(body,LearningGameAudit.digest(body));
        check.rejects("Rehashed smaller requests still fail the admission contract",IllegalArgumentException.class,()->LearningGameAudit.validate(List.of(altered),ID,"fixture",FEN));
        var roundTrip=json.readValue(json.writeValueAsString(events.getFirst()),LearningGameAudit.Envelope.class);
        check.same("Typed native and hint collections survive JSON replay",events.getFirst(),roundTrip);
        check.rejects("A historical played prefix cannot be upgraded retrospectively",IllegalArgumentException.class,()->new LearningGameAudit(ID,"fixture",FEN,start().play(move("e2e4")),r->{}));
        check.rejects("An unrepresented FEN clock cannot earn complete audit",IllegalArgumentException.class,()->new LearningGameAudit(ID,"fixture",FEN.replace(" 0 1"," 99 1"),start(),r->{}));
    }
    @Test void missingCalculationsAndBorrowedSideCannotBeRehashedIntoAdmission(Checks check)throws Exception {
        var rows=new ArrayList<LearningGameAudit.Envelope>();var o=fixture(rows,new int[1],new int[1],new int[1]);o.observe();o.play(move("e2e4"),LearningGameAudit.Actor.THEORY,null);
        var json=LearningGameAudit.mapper();
        for(String field:List.of("hard","hint","estimate")) {
            var bad=json.valueToTree(rows.getFirst().body());((com.fasterxml.jackson.databind.node.ObjectNode)bad.get("before")).putNull(field);
            var body=json.treeToValue(bad,LearningGameAudit.Body.class);var receipt=new LearningGameAudit.Envelope(body,LearningGameAudit.digest(body));
            check.rejects("Rehashing cannot invent the missing "+field,IllegalArgumentException.class,()->LearningGameAudit.validate(List.of(receipt),ID,"fixture",FEN));
        }
        var bad=json.valueToTree(rows.getFirst().body());((com.fasterxml.jackson.databind.node.ObjectNode)bad.get("before")).put("side","BLACK");
        var body=json.treeToValue(bad,LearningGameAudit.Body.class);var receipt=new LearningGameAudit.Envelope(body,LearningGameAudit.digest(body));
        check.rejects("A rehashed after-side cannot replace the actual pre-move side",IllegalArgumentException.class,()->LearningGameAudit.validate(List.of(receipt),ID,"fixture",FEN));
    }
    private static LearningGameAudit fixture(List<LearningGameAudit.Envelope> rows,int[] nativeCalls,int[] hints,int[] requested) {
        return new LearningGameAudit(ID,"fixture",FEN,start(),rows::add,g->{nativeCalls[0]++;return nativeOne(g);},(b,n)->{hints[0]++;requested[0]=n;return hintOne(b);});
    }
    private static ChessMatch start(){return ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,true));}
    private static ChessPosition.Move move(String uci){return ChessPosition.Move.fromUci(uci);}
    private static ChessMatch.BotAnalysis nativeOne(ChessMatch game) {
        var move=game.position().legalMoves().getFirst();var end=game.position().play(move);
        var line=new TacticalAnalysis.Variation(List.of(move),0,HintAuditSession.materialChangeCp(game.position(),end),TacticalAnalysis.Ending.HORIZON);
        return new ChessMatch.BotAnalysis(new ChessMatch.BotDecision(move,1,0),new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of(line)),1,0));
    }
    private static TacticalAnalysis.HintSearch hintOne(ChessPosition b) {
        var move=b.legalMoves().getFirst();var line=new TacticalAnalysis.Variation(List.of(move),0,HintAuditSession.materialChangeCp(b,b.play(move)),TacticalAnalysis.Ending.HORIZON);
        return new TacticalAnalysis.HintSearch(List.of(line),null,0,1,1);
    }
}
