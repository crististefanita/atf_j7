package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Portable counterexamples from actual finished games; teacher scores are evidence, not runtime hints. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardContinuationWitnessTest {
    private static final String SHA="0a3bbda463aa4738171816ffab98d2b313e52fbdd111a3e620f7511ceaade352";
    @Test void finishedGameLossesRetainTheActualPrefixAndColdTeacher(Checks check)throws Exception {
        var cases=load();check.same("Two finished game counterexamples",2,cases.size());
        for(var w:cases) {
            var game=w.game();String checkpoint=game.checkpoint();
            check.same(w.id()+" original move is legal",true,game.position().legalMoves().contains(ChessPosition.Move.fromUci(w.originalMove())));
            check.same(w.id()+" both independent horizons confirm a large loss",true,w.loss(w.originalMove(),17)>=150&&w.loss(w.originalMove(),19)>=150);
            check.same("The exact teacher move has its own free search",0.0,w.loss(w.row("free",19).path("bestmove").asText(),19));
            check.same("Inspecting the evidence does not rewrite history",checkpoint,game.checkpoint());
        }
    }
    @Test void missingDepthOrForeignTeacherCannotRedefineTheLoss(Checks check)throws Exception {
        var w=load().getFirst();var missing=w.input().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)missing.path("free")).put("19","0".repeat(64));
        check.rejects("A missing H19 phase is not a confirmation",IllegalArgumentException.class,()->HardLearningWitnesses.read(missing));
        var foreign=w.input().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)foreign.path("teacherIdentity").path("source")).put("run","foreign-run");
        check.rejects("Another producer cannot inherit this first loss",IllegalArgumentException.class,()->HardLearningWitnesses.read(foreign));
    }
    @Test @Tag("benchmark") void englishChoiceNoLongerRepeatsTheConfirmedLoss(Checks check)throws Exception {quality("english-11",check);}
    @Test void theScandinavianDecisionAvoidsTheConfirmedPawnPush(Checks check)throws Exception {
        var w=load().stream().filter(x->x.id().equals("scandinavian-12")).findFirst().orElseThrow();var game=w.game();String checkpoint=game.checkpoint();var result=game.analyzeBot(MatchSetup.Level.HARD);var alternative=w.input().path("qualifiedAlternative");String move=result.decision().move().uci();
        HardQuestAudit.validate(game,result);
        check.same("The complete native decision chooses the independently graded knight move",alternative.path("move").asText(),move);
        for(var env:alternative.path("raw")) {
            var row=env.path("body");int depth=row.path("query").path("depth").asInt();
            check.same("The forced reply is a planned H17/H19 phase",true,(depth==17||depth==19)&&row.path("key").asText().equals(alternative.path("forced").path(""+depth).asText()));
            check.same("The raw alternative is intact",env.path("digest").asText(),HardLeagueAudit.digest(HardLeagueAudit.canonical(row).getBytes(StandardCharsets.UTF_8)));
            StockfishStudyAudit.validate(row.path("query"),row.path("identity"),row);
            check.same("The forced root is the move actually selected",move,row.path("query").path("root").asText());
            double loss=LearningReviewAudit.oracleLoss(w.row("free",depth),row);
            check.same("Independent large-loss floor remains unchanged at H"+depth,true,loss<150&&w.loss(w.originalMove(),depth)-loss>=150);
        }
        check.same("Both cold horizons are present",2,alternative.path("raw").size());
        check.same("Native work respects the actual adaptive YAML ceiling",true,result.decision().extraNodes()<=HardQuestAudit.maximumNodes(game.position()));
        check.same("Recalculation does not apply a move",checkpoint,game.checkpoint());
    }
    private static void quality(String id,Checks check)throws Exception {
        var w=load().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();var result=w.game().analyzeBot(MatchSetup.Level.HARD);String move=result.decision().move().uci();double h17=w.loss(move,17),h19=w.loss(move,19);
        check.same(id+" actual="+move+" H"+result.decision().depth()+" nodes="+result.decision().extraNodes()+" independent loss="+h17+"/"+h19,true,h17<150||h19<150);
    }
    private static List<HardLearningWitnesses.Witness> load()throws Exception {
        try(var in=HardContinuationWitnessTest.class.getResourceAsStream("/hard-continuation-witnesses.yaml")) {
            if(in==null)throw new IllegalArgumentException("Missing finished-game counterexamples");var data=LearningGameAudit.mapper().readTree(in.readAllBytes());
            if(!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals(SHA))throw new IllegalArgumentException("Changed first-loss fixture");
            var out=new ArrayList<HardLearningWitnesses.Witness>();for(var row:data.path("cases"))out.add(HardLearningWitnesses.read(row));return List.copyOf(out);
        }
    }
}
