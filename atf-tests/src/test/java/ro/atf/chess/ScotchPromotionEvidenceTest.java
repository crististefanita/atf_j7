package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ScotchPromotionEvidenceTest {
    static final String NATIVE="0573ba3e456423d415b3cad409434443a6ac0c9428e61b0bdc7e514635d59f8a";
    @Test void aCorrectRe8RootCannotPromoteTwoBadFreeReplyDecisions(Checks check)throws Exception {
        var data=fixture();var result=assess(data);
        check.same("Re8+ is independently sound at both H17 and H19",true,result.rootQualified());
        check.same("Every hypothetical White and Black move is covered",7,result.phases().size());
        var expected=List.of(List.of(7.5,8.0),List.of(4.0,0.0),List.of(450.5,450.0),List.of(32.5,200.5),List.of(536.0,668.5),List.of(0.0,10.5),List.of(0.0,12.5));
        for(int i=0;i<result.phases().size();i++) {
            var p=result.phases().get(i);
            check.same("Rejected experiment, PV ply "+p.ply()+" "+p.move().uci()+": independently measured H17/H19 loss in permille",expected.get(i),List.of(p.h17(),p.h19()));
        }
        check.same("The complete line exposes both failed Black decisions",List.of("d4c3","e8e3"),result.phases().stream().filter(CoachingLineEvidence.Phase::large).map(p->p.move().uci()).toList());
        check.same("The unstable b3 grade remains disputed rather than silently passing",List.of("b2b3"),result.phases().stream().filter(CoachingLineEvidence.Phase::disputed).map(p->p.move().uci()).toList());
        check.same("A correct first move and a COMPLETE native receipt are insufficient",false,result.qualified());
    }
    @Test void rawHorizonScoreAndQuerySubstitutionAreRejected(Checks check)throws Exception {
        var original=fixture();var changed=original.deepCopy();var body=(ObjectNode)changed.path("raw").get(0).path("body");
        body.put("score",body.path("score").asInt()+1);reseal(changed,0);
        check.rejects("Rehashing a changed summary cannot change the raw UCI score",IllegalArgumentException.class,()->assess(changed));
        var foreign=original.deepCopy();((ObjectNode)foreign.path("raw").get(0).path("body").path("query")).put("depth",18);reseal(foreign,0);
        check.rejects("A phase from another depth cannot be substituted",IllegalArgumentException.class,()->assess(foreign));
    }
    @Test void missingDuplicatedAndRelabeledRepliesCannotQualify(Checks check)throws Exception {
        var missing=fixture().deepCopy();((ArrayNode)missing.path("raw")).remove(0);
        check.rejects("No missing raw phase",IllegalArgumentException.class,()->assess(missing));
        var duplicate=fixture().deepCopy();((ArrayNode)duplicate.path("raw")).add(duplicate.path("raw").get(0).deepCopy());
        check.rejects("Duplicating an existing receipt does not fill a gap",IllegalArgumentException.class,()->assess(duplicate));
        var reassigned=fixture().deepCopy();((ObjectNode)reassigned.path("plan").path("phases").get(1)).put("move","d3h7");
        check.rejects("An otherwise valid teacher phase belongs to its exact hypothetical prefix",IllegalArgumentException.class,()->assess(reassigned));
    }
    @Test void theWholeTeacherRunAndNativeBodyRemainBoundToTheirOwnSource(Checks check)throws Exception {
        var original=fixture();var foreign=original.deepCopy();((ObjectNode)foreign.path("plan").path("identity")).put("run","foreign-run");
        for(int i=0;i<foreign.path("raw").size();i++){((ObjectNode)foreign.path("raw").get(i).path("body").path("identity")).put("run","foreign-run");reseal(foreign,i);}
        check.rejects("Even a consistently relabeled run is not the trusted observation",IllegalArgumentException.class,
                ()->CoachingLineEvidence.assess(foreign,original.path("plan").path("identity"),NATIVE));
        var edited=original.deepCopy();((ObjectNode)edited).put("nativeRaw",edited.path("nativeRaw").asText()+" ");
        check.rejects("Native bytes may not be rewritten after oracle admission",IllegalArgumentException.class,()->assess(edited));
        var shortened=original.deepCopy();((ArrayNode)shortened.path("plan").path("phases")).remove(6);
        check.rejects("The last hypothetical move is mandatory",IllegalArgumentException.class,()->assess(shortened));
    }
    @Test void anIncompleteOrRootOnlyRunCannotGainAdmissionByResealingItsNativeBody(Checks check)throws Exception {
        check.rejects("No empty assessment can manufacture PASS",IllegalArgumentException.class,()->new CoachingLineEvidence.Assessment(true,List.of()));
        var unsupported=fixture().deepCopy();((ObjectNode)unsupported).put("schema",2);
        check.rejects("An unknown schema cannot silently reuse current admission",IllegalArgumentException.class,()->assess(unsupported));
        for(String mode:List.of("incomplete","root-only")) {
            var data=fixture().deepCopy();var nativeTree=(ObjectNode)LearningGameAudit.mapper().readTree(data.path("nativeRaw").asText());
            if(mode.equals("incomplete"))((ObjectNode)nativeTree.path("work")).put("status","NODE_LIMIT");
            else {
                var moves=(ArrayNode)nativeTree.path("lines").get(0).path("line");while(moves.size()>1)moves.remove(moves.size()-1);
            }
            String raw=LearningGameAudit.mapper().writeValueAsString(nativeTree),digest=HardLeagueAudit.digest(raw.getBytes(StandardCharsets.UTF_8));
            ((ObjectNode)data).put("nativeRaw",raw);((ObjectNode)data.path("plan").path("identity").path("source")).put("nativeReceiptSha256",digest);
            check.rejects("Freshly pinned "+mode+" evidence still fails its logical admission",IllegalArgumentException.class,
                    ()->CoachingLineEvidence.assess(data,data.path("plan").path("identity"),digest));
        }
    }
    static JsonNode fixture()throws Exception {
        try(var in=ScotchPromotionEvidenceTest.class.getResourceAsStream("/scotch-rejected-repair.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);
            if(!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals("ab40ef221bbda7d848883271775f423b3913140e2e9ca80df36f2b53294af57f"))throw new IllegalArgumentException("Changed rejected-repair witness");
            return data;
        }
    }
    private static CoachingLineEvidence.Assessment assess(JsonNode data)throws Exception{return CoachingLineEvidence.assess(data,fixture().path("plan").path("identity"),NATIVE);}
    private static void reseal(JsonNode data,int index){var envelope=(ObjectNode)data.path("raw").get(index);envelope.put("digest",HardLeagueAudit.digest(HardLeagueAudit.canonical(envelope.path("body")).getBytes(StandardCharsets.UTF_8)));}
}
