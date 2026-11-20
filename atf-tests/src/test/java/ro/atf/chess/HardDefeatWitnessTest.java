package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Portable failure contexts retain independent evidence without requiring a known bad live move. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardDefeatWitnessTest {
    static List<HardLearningWitnesses.Witness> load()throws Exception {
        try(var in=HardDefeatWitnessTest.class.getResourceAsStream("/hard-defeat-witnesses.yaml")) {
            if(in==null)throw new IllegalArgumentException("Missing retained defeat witnesses");
            var data=LearningGameAudit.mapper().readTree(in.readAllBytes());
            if(!hash(data).equals("c182cf8f6358fa5805431805f13156f6cce387433fc2606d2eb3b354da79cd23"))throw new IllegalArgumentException("Changed defeat witnesses");
            var result=new ArrayList<HardLearningWitnesses.Witness>();for(var item:data.path("cases"))result.add(HardLearningWitnesses.read(item));return List.copyOf(result);
        }
    }
    @Test void actualLossesKeepTheirWholeHistoryNativeWorkAndBothTeacherHorizons(Checks check)throws Exception {
        var cases=load();check.same("Five retained actual defeats, not five newly played independent families",5,cases.size());
        var ids=new HashSet<String>();
        for(var w:cases) {
            check.same("A defeat is not duplicated",true,ids.add(w.id()));
            var original=HardQuestAudit.read(w.input().path("originalAnalysis"));HardQuestAudit.validate(w.game(),original);
            check.same("Historical decision and native ranking agree",original.decision().move().uci(),w.originalMove());
            for(int d:List.of(17,19))check.same(w.id()+" H"+d+" historical "+w.originalMove()+" loss="+w.loss(w.originalMove(),d),true,w.loss(w.originalMove(),d)>=150);
        }
    }
    @Test void anotherHistoryRunOrTeacherScoreCannotRedefineTheHistoricalLoss(Checks check)throws Exception {
        var w=load().getFirst();var foreign=w.input().deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode)foreign.path("history")).remove(0);
        check.rejects("A shortened prefix cannot borrow an opening loss",IllegalArgumentException.class,()->HardLearningWitnesses.read(foreign));
        var relabeled=w.input().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)relabeled.path("teacherIdentity")).put("run","foreign-run");
        check.rejects("A different teacher run cannot corroborate this game",IllegalArgumentException.class,()->HardLearningWitnesses.read(relabeled));
        var changed=w.input().deepCopy();var body=(com.fasterxml.jackson.databind.node.ObjectNode)changed.path("raw").get(0).path("body");body.put("expectedPermille",1);
        check.rejects("An edited teacher result cannot make an old loss disappear",IllegalArgumentException.class,()->HardLearningWitnesses.read(changed));
    }
    private static String hash(JsonNode n){return HardLeagueAudit.digest(HardLeagueAudit.canonical(n).getBytes(StandardCharsets.UTF_8));}
}
