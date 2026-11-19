package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class CoachingProofOracleTest {
    private static final String SHA="5801d4eafca0f9e2ab5955cc9656ec18b031d4ef2365993806970ed06cee59b0";

    @Test void coldTeacherKeepsTheSoundMateAndTheStalemateCounterexample(Checks check)throws Exception {
        var data=load();var rows=validate(data);int cases=0;
        for(var c:data.path("cases")) {
            var board=ChessPosition.fromFen(c.path("fen").asText());var proof=CoachingProof.compare(board,List.of(ChessPosition.Move.fromUci(c.path("root").asText())),c.path("ordinary").asInt(),Set.of(),CoachingProof.CURRENT);
            check.same(c.path("id").asText()+" forcing transaction completed",true,proof.work().complete());
            var line=proof.lines().getFirst();
            for(int d:List.of(17,19)) {
                var free=rows.get(c.path("queries").path("free").path(""+d).asText());var forced=rows.get(c.path("queries").path("root").path(""+d).asText());
                double loss=StockfishStudyAudit.loss(free,forced);
                check.same(c.path("id").asText()+" H"+d+" teacher loss="+loss,true,c.path("id").asText().equals("promotion-stalemate")?loss==500:loss==0);
                if(c.path("id").asText().equals("sound-queen-offer"))check.same("A lost queen is not grounds to reject a proved mate",3,line.signedMatePlies());
                if(c.path("id").asText().equals("promotion-stalemate"))check.same("An apparently large promoted piece cannot replace the actual draw",TacticalAnalysis.Ending.STALEMATE,line.ending());
            }cases++;
        }
        check.same("All predeclared contexts were executed",7,cases);
    }

    @Test void teacherEvidenceRejectsAChangedScoreOrAChangedPosition(Checks check)throws Exception {
        var data=load();var changed=data.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("raw").get(0).path("body")).put("score",123456);
        check.rejects("A changed teacher score does not certify a completed exchange",IllegalArgumentException.class,()->validate(changed));
        var foreign=data.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)foreign.path("cases").get(0)).put("fen","rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        check.rejects("A teacher phase cannot be borrowed by another board",IllegalArgumentException.class,()->validate(foreign));
    }

    private static JsonNode load()throws Exception {
        try(var in=CoachingProofOracleTest.class.getResourceAsStream("/coaching-proof-witnesses.yaml")) {
            if(in==null)throw new IllegalArgumentException("Missing closure witnesses");var data=LearningGameAudit.mapper().readTree(in);
            require(hash(data).equals(SHA),"Changed portable teacher evidence");return data;
        }
    }
    private static Map<String,JsonNode> validate(JsonNode data) {
        var rows=new HashMap<String,JsonNode>();
        for(var e:data.path("raw")){var b=e.path("body");require(hash(b).equals(e.path("digest").asText()),"Modified raw receipt");StockfishStudyAudit.validate(b.path("query"),data.path("identity"),b);require(rows.putIfAbsent(b.path("key").asText(),b)==null,"Repeated cold phase");}
        var used=new HashSet<String>();
        for(var c:data.path("cases"))for(String role:List.of("free","root"))for(int d:List.of(17,19)) {
            String key=c.path("queries").path(role).path(""+d).asText();used.add(key);var r=rows.get(key);require(r!=null,"Missing cold phase");var q=r.path("query");
            require(q.path("initialFen").equals(c.path("fen"))&&q.path("history").isEmpty()&&q.path("depth").asInt()==d,"Foreign context or depth");
            require(role.equals("free")?q.path("root").isNull():q.path("root").asText().equals(c.path("root").asText()),"Foreign forced move");
        }
        require(used.equals(rows.keySet()),"Unplanned or omitted oracle phase");return rows;
    }
    private static String hash(JsonNode value){return HardLeagueAudit.digest(HardLeagueAudit.canonical(value).getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalArgumentException(reason);}
}
