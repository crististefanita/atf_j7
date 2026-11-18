package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import ro.atf.chess.ChessPosition.Move;

/** Small permanent counterexamples; full games remain in the external learning corpus. */
final class HardLearningWitnesses {
    private static final String SHA="8d160a38908611686454016c4e510faa0b7c06929944b585baba7d4e7b84a809";
    private HardLearningWitnesses() { }
    static List<Witness> load()throws Exception {
        try(var stream=HardLearningWitnesses.class.getResourceAsStream("/hard-learning-witnesses.yaml")) {
            require(stream!=null,"Missing permanent learning witnesses");var bytes=stream.readAllBytes();
            var data=LearningGameAudit.mapper().readTree(bytes);
            require(HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(SHA),"Changed witness fixture");
            var cases=data.path("cases");var result=new ArrayList<Witness>();
            for(var item:cases)result.add(read(item));return List.copyOf(result);
        }
    }
    static Witness read(JsonNode item) {
        require(item.path("source").equals(item.path("teacherIdentity").path("source")),"Foreign witness producer");
        var rows=new HashMap<String,JsonNode>();var expected=new HashSet<String>();
        for(String role:List.of("free","forced"))for(int d:List.of(17,19))require(expected.add(item.path(role).path(""+d).asText()),"Repeated witness phase");
        for(var envelope:item.path("raw")) {
            var row=envelope.path("body");String key=row.path("key").asText();
            require(HardLeagueAudit.digest(HardLeagueAudit.canonical(row.path("query")).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(key),"Rewritten witness query key");
            require(HardLeagueAudit.digest(HardLeagueAudit.canonical(row).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(envelope.path("digest").asText()),"Rewritten witness oracle");
            StockfishStudyAudit.validate(row.path("query"),item.path("teacherIdentity"),row);
            require(rows.putIfAbsent(key,row)==null,"Repeated raw witness");
        }
        require(rows.keySet().equals(expected),"Missing witness phase");
        var witness=new Witness(item,Map.copyOf(rows));witness.game();
        for(String role:List.of("free","forced"))for(int d:List.of(17,19)) {
            var q=witness.row(role,d).path("query");
            require(q.path("initialFen").equals(item.path("initialFen"))&&q.path("depth").asInt()==d,"Foreign witness origin or horizon");
            require(strings(q.path("history")).equals(witness.history().stream().map(Move::uci).toList()),"Foreign witness history");
            require(role.equals("free")?q.path("root").isNull():q.path("root").asText().equals(witness.originalMove()),"Foreign witness root");
        }
        return witness;
    }
    record Witness(JsonNode input,Map<String,JsonNode> rows) {
        String id(){return input.path("id").asText();}
        String originalMove(){return LearningGameAudit.mapper().convertValue(input.path("originalDecision").path("move"),Move.class).uci();}
        List<Move> history(){var result=new ArrayList<Move>();for(var m:input.path("history"))result.add(LearningGameAudit.mapper().convertValue(m,Move.class));return List.copyOf(result);}
        ChessMatch game(){
            var fields=input.path("checkpoint").asText().split("\\|",-1);require(fields.length==5&&fields[0].equals("3"),"Missing witness setup");
            var game=ChessMatch.fromPosition(ChessPosition.fromFen(input.path("initialFen").asText()),new MatchSetup(ChessPosition.Side.valueOf(fields[1]),MatchSetup.Level.valueOf(fields[2]),fields[3].equals("1")));
            for(var move:history())game=game.play(move);
            require(game.checkpoint().equals(input.path("checkpoint").asText())&&StudyFen.replay(input.path("initialFen").asText(),history()).equals(input.path("fen").asText()),"Witness no longer replays its full position");return game;
        }
        JsonNode row(String role,int depth){return rows.get(input.path(role).path(""+depth).asText());}
        double loss(String move,int depth){
            if(move.equals(originalMove()))return StockfishStudyAudit.loss(row("free",depth),row("forced",depth));
            require(move.equals(row("free",depth).path("bestmove").asText()),"Refresh the oracle for a new native choice; do not assume it is better");return 0;
        }
    }
    private static List<String> strings(JsonNode n){var r=new ArrayList<String>();n.forEach(v->r.add(v.asText()));return r;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
