package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ScotchWitnessTest {
    @Test void thePawnGrabIsNotConfusedWithTheTeacherProvedBishopSacrifice(Checks check)throws Exception {
        var data=fixture();var rows=rows(data);var board=position(data);var path=moves(data.path("publicPath"));
        for(var move:path.subList(0,data.path("bad").path("pvIndex").asInt()))board=board.play(move);
        for(int d:List.of(17,19)) {
            var free=rows.get(data.path("bad").path("queries").path("free").path(""+d).asText());
            var forced=rows.get(data.path("bad").path("queries").path("forced").path(""+d).asText());
            check.same("The independent free search proves mate in three H"+d,List.of("mate",3),List.of(free.path("scoreType").asText(),free.path("score").asInt()));
            check.same("The bishop sacrifice is the forcing check, not the pawn grab", "d3h7",free.path("pv").get(0).asText());
            var end=board;for(var move:free.path("pv"))end=end.play(ChessPosition.Move.fromUci(move.asText()));
            check.same("The stored forcing line ends in legal checkmate",true,end.isCheck()&&end.legalMoves().isEmpty());
            check.same("Bxb2 misses mate but is not falsely described as a losing sacrifice",true,forced.path("expectedPermille").asDouble()>500&&StockfishStudyAudit.loss(free,forced)>150);
            var rootFree=rows.get(data.path("rootPair").path("free").path(""+d).asText());var rootForced=rows.get(data.path("rootPair").path("forced").path(""+d).asText());
            check.same("The original root has an independent winning check",data.path("rootPair").path("winning").asText(),rootFree.path("pv").get(0).asText());
            check.same("The root recommendation itself loses a substantial winning margin",true,StockfishStudyAudit.loss(rootFree,rootForced)>150);
        }
    }
    static JsonNode fixture()throws Exception {
        try(var in=ScotchWitnessTest.class.getResourceAsStream("/scotch-continuation-witness.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);
            if(!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals("5ee046834819445030ef48e425b94378be5af8a6f1532de22331f3212e431cd5"))throw new IllegalArgumentException("Changed Scotch witness");
            rows(data);return data;
        }
    }
    static Map<String,JsonNode> rows(JsonNode data) {
        var rows=new HashMap<String,JsonNode>();for(var env:data.path("raw")){var r=env.path("body");if(!env.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(r).getBytes(StandardCharsets.UTF_8)))||rows.putIfAbsent(r.path("key").asText(),r)!=null)throw new IllegalArgumentException("Changed or duplicated Scotch phase");}
        for(var q:data.path("plan").path("queries"))StockfishStudyAudit.validate(q.path("query"),data.path("plan").path("identity"),rows.get(q.path("key").asText()));
        if(rows.size()!=8)throw new IllegalArgumentException("Missing mate or pawn-grab phase");return rows;
    }
    static ChessPosition position(JsonNode data){var p=ChessPosition.fromFen(data.path("initialFen").asText());for(var m:moves(data.path("history")))p=p.play(m);return p;}
    static List<ChessPosition.Move> moves(JsonNode data){var result=new ArrayList<ChessPosition.Move>();for(var m:data)result.add(new ChessPosition.Move(m.path("from").asInt(),m.path("to").asInt(),m.path("promotion").isNull()?null:ChessPosition.Kind.valueOf(m.path("promotion").asText())));return List.copyOf(result);}
}
