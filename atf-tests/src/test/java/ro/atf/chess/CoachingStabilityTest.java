package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class CoachingStabilityTest {
    @Test void theCompletedRecaptureCannotBypassTheQualifiedEqualTurnGuard(Checks check)throws Exception {
        var data=load();var rows=validate(data);
        for(var c:data.path("cases")) {
            var game=ChessMatch.restore("2|analysis|"+c.path("history").asText());String checkpoint=game.checkpoint();
            var hint=TacticalAnalysis.verifiedHints(game.position(),200000);String move=hint.lines().getFirst().move().uci();
            check.same("Retain the independently lower-loss root in "+c.path("id").asText(),c.path("expected").asText(),move);
            for(int d:List.of(17,19)) {
                var free=rows.get(c.path("queries").path("free").path(""+d).asText());var retained=rows.get(c.path("queries").path("previous").path(""+d).asText());
                check.same("Retained H"+d+" root loss="+StockfishStudyAudit.loss(free,retained),true,StockfishStudyAudit.loss(free,retained)<50);
            }
            if(hint.proof()!=null) {
                var proof=hint.proof();check.same("The closure is complete and fully charged: "+proof,true,proof.complete()&&hint.totalNodes()==proof.totalNodes()&&proof.nodes()<=proof.ceiling());
                var choice=proof.confirmation();check.same("H6 cannot corroborate its own new leader",4,choice.plies());
                check.same("The actual H4 comparison retains the incumbent",true,choice.earlierIncumbentScore()>=choice.earlierChallengerScore());
                check.same("The guard does not restore the old unclosed score",choice.incumbentScore(),hint.lines().getFirst().score());
                check.same("An unsupported challenger cannot become an equivalent public comparison",null,hint.comparison());
            }
            check.same("Analysis preserves the actual game",checkpoint,game.checkpoint());
        }
    }

    @Test void anInterruptedEqualTurnConfirmationDoesNotPublishTheAlreadyClosedLine(Checks check)throws Exception {
        var c=load().path("cases").get(0);var board=ChessMatch.restore("2|analysis|"+c.path("history").asText()).position();
        var roots=List.of(Move.fromUci("e5d7"),Move.fromUci("d3e4"));
        var full=CoachingProof.compare(board,roots,6,Set.of(),CoachingProof.CURRENT,Set.of(roots.getFirst()));
        var limited=new CoachingProof.Policy(full.work().nodes()+1,1,48);
        var confirmed=CoachingProof.confirmLeader(board,full,roots.getFirst(),limited);
        check.same("One shared ceiling includes the unfinished confirmation",limited.maxNodes(),confirmed.work().nodes());
        check.same("The tactical comparison cannot borrow a missing confirmation",CoachingProof.Status.NODE_LIMIT,confirmed.work().status());
        check.same("No fallback scored line survives incomplete evidence",List.of(),confirmed.lines());
    }

    private static JsonNode load()throws Exception {
        try(var in=CoachingStabilityTest.class.getResourceAsStream("/coaching-stability-witnesses.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);require(hash(data).equals("d7d11943b6b090ef70c44eb27833d81bdf3d69bdb7831234936238f79f3c2b72"),"Changed stability witnesses");return data;
        }
    }
    private static Map<String,JsonNode> validate(JsonNode data) {
        var rows=new HashMap<String,JsonNode>();
        for(var e:data.path("raw")){var b=e.path("body");require(hash(b).equals(e.path("digest").asText()),"Changed teacher row");StockfishStudyAudit.validate(b.path("query"),data.path("identity"),b);require(rows.putIfAbsent(b.path("key").asText(),b)==null,"Repeated phase");}
        for(var c:data.path("cases"))for(String role:List.of("free","previous","current"))for(int d:List.of(17,19)) {
            var row=rows.get(c.path("queries").path(role).path(""+d).asText());require(row!=null,"Missing phase");var q=row.path("query");
            require(q.path("history").equals(LearningGameAudit.mapper().valueToTree(Arrays.asList(c.path("history").asText().split(" "))))&&q.path("depth").asInt()==d,"Foreign phase history");
            require(!role.equals("previous")||q.path("root").asText().equals(c.path("expected").asText()),"Foreign protected move");
        }return rows;
    }
    private static String hash(JsonNode value){return HardLeagueAudit.digest(HardLeagueAudit.canonical(value).getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalArgumentException(reason);}
}
