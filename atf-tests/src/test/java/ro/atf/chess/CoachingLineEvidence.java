package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ro.atf.chess.ChessPosition.Move;

/** A correct first root does not qualify a scored continuation. No engine score is replaced here. */
final class CoachingLineEvidence {
    private CoachingLineEvidence() { }
    record Phase(int ply,Move move,double h17,double h19) {
        boolean large(){return h17>=150&&h19>=150;}
        boolean disputed(){return (h17>=150)!=(h19>=150);}
    }
    record Assessment(boolean rootQualified,List<Phase> phases) {
        Assessment {phases=List.copyOf(phases);if(phases.isEmpty())throw new IllegalArgumentException("No assessed continuation");}
        boolean qualified(){return rootQualified&&phases.stream().noneMatch(p->p.large()||p.disputed());}
    }

    static Assessment assess(JsonNode evidence,JsonNode trustedIdentity,String nativeDigest)throws Exception {
        require(evidence.isObject()&&evidence.path("schema").asInt()==1&&evidence.path("nativeRaw").isTextual(),"Unsupported coaching evidence schema");
        var plan=evidence.path("plan");var identity=plan.path("identity");
        require(plan.isObject()&&plan.path("queries").isArray()&&plan.path("phases").isArray()&&evidence.path("raw").isArray(),"Missing coaching evidence inventory");
        require(identity.equals(trustedIdentity),"Foreign teacher run or source");
        String nativeRaw=evidence.path("nativeRaw").asText();
        require(digest(nativeRaw).equals(nativeDigest)&&identity.path("source").path("nativeReceiptSha256").asText().equals(nativeDigest),"Changed native continuation");
        var nativeTree=LearningGameAudit.mapper().readTree(nativeRaw);
        require(nativeTree.path("work").path("status").asText().equals("COMPLETE"),"An incomplete native search cannot qualify");
        require(nativeTree.path("lines").isArray()&&!nativeTree.path("lines").isEmpty(),"Missing native continuation");
        var line=LearningGameAudit.mapper().treeToValue(nativeTree.path("lines").get(0),TacticalAnalysis.Variation.class);
        require(!line.line().isEmpty()&&plan.path("phases").size()==line.line().size(),"Incomplete continuation inventory");
        var rows=new HashMap<String,JsonNode>();
        for(var envelope:evidence.path("raw")) {
            var body=envelope.path("body");
            require(envelope.path("digest").asText().equals(digest(HardLeagueAudit.canonical(body)))&&rows.putIfAbsent(body.path("key").asText(),body)==null,"Changed or duplicated raw teacher phase");
        }
        var queries=new HashMap<String,JsonNode>();
        for(var item:plan.path("queries")) {
            String key=item.path("key").asText();require(queries.putIfAbsent(key,item.path("query"))==null,"Duplicated teacher query");
            StockfishStudyAudit.validate(item.path("query"),identity,rows.get(key));
        }
        require(!queries.isEmpty()&&queries.keySet().equals(rows.keySet()),"Missing or surplus teacher phase");
        var initial=plan.path("phases").get(0).path("history");var history=new ArrayList<String>();initial.forEach(m->history.add(m.asText()));
        String fen=queries.values().iterator().next().path("initialFen").asText();
        var board=ChessPosition.fromFen(fen);for(String m:history)board=board.play(Move.fromUci(m));
        var result=new ArrayList<Phase>();var used=new HashSet<String>();
        for(int i=0;i<line.line().size();i++) {
            var phase=plan.path("phases").get(i);var move=line.line().get(i);
            require(strings(phase.path("history")).equals(history)&&phase.path("move").asText().equals(move.uci()),"Foreign hypothetical prefix or move");
            double[] losses=new double[2];int index=0;
            for(int depth:List.of(17,19)) {
                var pair=new ArrayList<JsonNode>();
                for(String role:List.of("free","forced")) {
                    String key=phase.path(role).path(""+depth).asText();var q=queries.get(key);
                    require(q!=null&&q.path("initialFen").asText().equals(fen)&&strings(q.path("history")).equals(history)
                            &&q.path("depth").asInt()==depth&&(role.equals("free")?q.path("root").isNull():q.path("root").asText().equals(move.uci())),"Reassigned teacher phase");
                    used.add(key);pair.add(rows.get(key));
                }
                losses[index++]=StockfishStudyAudit.loss(pair.get(0),pair.get(1));
            }
            board=board.play(move);history.add(move.uci());result.add(new Phase(i+1,move,losses[0],losses[1]));
        }
        require(used.equals(queries.keySet()),"Unused or hidden teacher query");
        return new Assessment(result.getFirst().h17()<50&&result.getFirst().h19()<50,result);
    }
    private static List<String> strings(JsonNode node){require(node.isArray(),"Missing history");var result=new ArrayList<String>();for(var n:node){require(n.isTextual(),"Non-text move");result.add(n.asText());}return result;}
    private static String digest(String text){return HardLeagueAudit.digest(text.getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
