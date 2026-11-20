package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable independent references; original oracle identities are never relabeled as a new search. */
final class QueenReplyWitnesses {
    static List<Witness> load()throws Exception {
        try(var in=QueenReplyWitnesses.class.getResourceAsStream("/queen-reply-witnesses.yaml")) {
            require(in!=null,"Missing queen reply references");var data=LearningGameAudit.mapper().readTree(in.readAllBytes());
            require(hash(data).equals("180eba08646b184cff714859e884885fe537f0270025d4ff0bcc6d76e2b9e994"),"Modified queen reply references");
            var result=new ArrayList<Witness>();for(var item:data.path("cases"))result.add(read(item));return List.copyOf(result);
        }
    }
    static Witness read(JsonNode item) {
        require(item.path("source").equals(item.path("teacherIdentity").path("source")),"Foreign native reference source");
        var rows=new HashMap<String,JsonNode>();var expected=new HashSet<String>();
        for(String role:List.of("free","rejected","selected"))for(int depth:List.of(17,19))require(expected.add(item.path("queries").path(role).path(""+depth).asText()),"Repeated oracle role");
        for(var env:item.path("raw")) {
            var body=env.path("body");require(hash(body).equals(env.path("digest").asText()),"Modified oracle receipt");
            require(hash(body.path("query")).equals(body.path("key").asText()),"Modified oracle query key");
            StockfishStudyAudit.validate(body.path("query"),item.path("teacherIdentity"),body);
            require(rows.putIfAbsent(body.path("key").asText(),body)==null,"Repeated oracle receipt");
        }
        require(rows.keySet().equals(expected),"Missing or foreign oracle phase");var witness=new Witness(item,Map.copyOf(rows));witness.game();
        for(String role:List.of("free","rejected","selected"))for(int depth:List.of(17,19)) {
            var q=witness.row(role,depth).path("query");
            require(q.path("initialFen").equals(item.path("initialFen"))&&q.path("history").equals(item.path("history"))&&q.path("depth").asInt()==depth,"Foreign reference history, origin or depth");
            require(role.equals("free")?q.path("root").isNull():q.path("root").asText().equals(witness.move(role)),"Foreign reference root");
        }
        return witness;
    }
    record Witness(JsonNode input,Map<String,JsonNode> rows) {
        String id(){return input.path("id").asText();}
        JsonNode row(String role,int depth){return rows.get(input.path("queries").path(role).path(""+depth).asText());}
        String move(String role){return row(role,19).path("query").path("root").asText();}
        double loss(String role,int depth){return StockfishStudyAudit.loss(row("free",depth),row(role,depth));}
        ChessMatch game() {
            var fields=input.path("checkpoint").asText().split("\\|",-1);require(fields.length==5&&fields[0].equals("3"),"Missing native setup");
            var game=ChessMatch.fromPosition(ChessPosition.fromFen(input.path("initialFen").asText()),new MatchSetup(ChessPosition.Side.valueOf(fields[1]),MatchSetup.Level.valueOf(fields[2]),fields[3].equals("1")));
            for(var move:input.path("history"))game=game.play(ChessPosition.Move.fromUci(move.asText()));
            require(game.checkpoint().equals(input.path("checkpoint").asText()),"Foreign native prefix");return game;
        }
    }
    private static String hash(JsonNode n){return HardLeagueAudit.digest(HardLeagueAudit.canonical(n).getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean value,String reason){if(!value)throw new IllegalArgumentException(reason);}
}
