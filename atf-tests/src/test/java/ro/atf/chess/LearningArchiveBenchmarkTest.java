package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class LearningArchiveBenchmarkTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=LearningGameAudit.mapper();

    @Test void everyActualMoveHasItsOwnSourceBoundMaximumHint(Checks check)throws Exception {
        inspectGames(check);
    }

    @Test void maximumHintsHaveNoStableLargeLossInEitherSideOfThePreview(Checks check)throws Exception {
        int large=inspectOracle(check);
        check.same("Absolute hint quality is separate from legal, complete and source-bound capture; stable PV losses >=150 permille",0,large);
    }

    @Test void everyHypotheticalPlyHasRawIndependentOracleEvidence(Checks check)throws Exception {
        inspectOracle(check);
    }

    private static int inspectOracle(Checks check)throws Exception {
        var games=inspectGames(check);var root=root();
        var manifest=trusted(root.resolve("deep-oracle/manifest.json"),"oracleManifestSha256");
        verify(root.resolve("deep-oracle/plan.json"),manifest.path("planSha256").asText());
        verify(root.resolve("deep-oracle/oracle.jsonl"),manifest.path("oracleSha256").asText());
        var plan=JSON.readTree(root.resolve("deep-oracle/plan.json").toFile());
        require(plan.path("identity").path("source").equals(games.identity()),"Foreign deep oracle producer");
        require(plan.path("identity").path("corpusSha256").asText().equals(hash(root.resolve("manifest.json"))),"Foreign oracle corpus");
        var expected=new HashMap<String,JsonNode>();
        for(var q:plan.path("queries"))require(expected.putIfAbsent(q.path("key").asText(),q.path("query"))==null,"Repeated planned oracle query");
        var receipts=new HashMap<String,JsonNode>();
        try(var reader=Files.newBufferedReader(root.resolve("deep-oracle/oracle.jsonl"))) {
            for(String line;(line=reader.readLine())!=null;) {
                var env=JSON.readTree(line);var body=env.path("body");String key=body.path("key").asText();
                require(env.path("digest").asText().equals(hash(body)),"Modified raw oracle");
                require(key.equals(hash(body.path("query"))),"Substituted oracle query key");
                StockfishStudyAudit.validate(expected.get(key),plan.path("identity"),body);
                require(receipts.putIfAbsent(key,body)==null,"Repeated raw oracle receipt");
            }
        }
        check.same("Every planned query has raw exact H17/H19 evidence",expected.keySet(),receipts.keySet());
        var covered=new java.util.HashSet<String>();int pvPlies=0,stableLarge=0,opposingLarge=0;
        for(var step:plan.path("steps")) {
            String id=step.path("case").asText()+":"+step.path("ply").asInt();
            var envelope=games.moves().get(id);require(envelope!=null&&covered.add(id),"Foreign or repeated actual phase");
            var body=envelope.body();var frame=body.before();require(step.path("receiptDigest").asText().equals(envelope.digest()),"Substituted pre-move phase");
            var history=frame.history().stream().map(ChessPosition.Move::uci).toList();
            pair(step.path("free"),frame.initialFen(),history,null,receipts);
            pair(step.path("played"),frame.initialFen(),history,body.played().uci(),receipts);
            pair(step.path("native"),frame.initialFen(),history,frame.hard().decision().move().uci(),receipts);
            require(step.path("hints").size()==frame.hint().lines().size(),"Missing offered hint");
            for(int rank=0;rank<frame.hint().lines().size();rank++) {
                var hint=step.path("hints").get(rank);var path=frame.hint().lines().get(rank).line();
                require(hint.path("rank").asInt()==rank+1&&hint.path("plies").size()==path.size(),"Missing or reordered hypothetical PV ply");
                var prefix=new ArrayList<>(history);
                for(int i=0;i<path.size();i++) {
                    var ply=hint.path("plies").get(i);String move=path.get(i).uci();
                    require(ply.path("index").asInt()==i&&ply.path("move").asText().equals(move),"Substituted hypothetical reply");
                    pair(ply.path("free"),frame.initialFen(),prefix,null,receipts);pair(ply.path("forced"),frame.initialFen(),prefix,move,receipts);
                    double h17=loss(ply,17,receipts),h19=loss(ply,19,receipts);
                    if(h17>=150&&h19>=150){stableLarge++;if(i%2==1)opposingLarge++;}
                    check.same(id+" hint="+(rank+1)+" PV-ply="+i+" move="+move+" WDL-loss H17="+h17+" H19="+h19,move,receipts.get(ply.path("forced").path("19").asText()).path("query").path("root").asText());
                    prefix.add(move);pvPlies++;
                }
                pair(hint.path("endpoint"),frame.initialFen(),prefix,null,receipts);
            }
        }
        check.same("Every actual move is included in the deep audit",games.moves().keySet(),covered);
        check.same("Hypothetical plies independently counted",manifest.path("pvPlies").asInt(),pvPlies);
        check.same("Stable large errors are disclosed separately from integrity",manifest.path("stableLarge").asInt(),stableLarge);
        check.same("Stable weak opposing replies remain visible",manifest.path("opposingLarge").asInt(),opposingLarge);
        return stableLarge;
    }

    private record Games(JsonNode identity,java.util.Map<String,LearningGameAudit.Envelope> moves) { }
    private static Games inspectGames(Checks check)throws Exception {
        var root=root();var manifest=trusted(root.resolve("manifest.json"),"manifestSha256");
        require(manifest.path("complete").asBoolean(),"Incomplete game collection");
        verify(root.resolve("plan.json"),manifest.path("planSha256").asText());var plan=JSON.readTree(root.resolve("plan.json").toFile());
        require(plan.path("identity").equals(manifest.path("identity")),"Foreign collection producer");
        require(manifest.path("identity").path("revision").asText().equals(System.getProperty("chess.learning.revision","")),"Foreign measured revision");
        require(manifest.path("games").size()>0&&manifest.path("games").size()==plan.path("cases").size(),"Empty or missing games");
        var moves=new java.util.LinkedHashMap<String,LearningGameAudit.Envelope>();
        for(var game:manifest.path("games")) {
            var definition=game.path("case");String id=definition.path("id").asText();
            require(java.util.stream.StreamSupport.stream(plan.path("cases").spliterator(),false).anyMatch(definition::equals),"Unplanned game");
            var dir=root.resolve(id).normalize();require(dir.startsWith(root),"Game path escapes archive");
            verify(dir.resolve("moves.jsonl"),game.path("movesSha256").asText());verify(dir.resolve("opponent.jsonl"),game.path("opponentSha256").asText());verify(dir.resolve("game.pgn"),game.path("pgnSha256").asText());
            var rows=new ArrayList<LearningGameAudit.Envelope>();var oracles=new HashMap<String,JsonNode>();
            for(var line:Files.readAllLines(dir.resolve("opponent.jsonl"))) {
                var env=JSON.readTree(line);var body=env.path("body");require(env.path("digest").asText().equals(hash(body)),"Changed actual opponent receipt");
                StockfishStudyAudit.validate(body.path("query"),game.path("oracleIdentity"),body);require(oracles.putIfAbsent(body.path("key").asText(),body)==null,"Repeated opponent receipt");
            }
            for(var line:Files.readAllLines(dir.resolve("moves.jsonl"))) {
                var row=JSON.readValue(line,LearningGameAudit.Envelope.class);var body=row.body();var f=body.before();rows.add(row);
                require(JSON.valueToTree(f.identity()).equals(manifest.path("identity")),"Foreign native producer");
                require(moves.putIfAbsent(id+":"+f.ply(),row)==null,"Repeated actual ply");
                var prefix=definition.path("prefix").asText();var theory=prefix.isBlank()?new String[0]:prefix.split(" ");
                if(f.ply()<theory.length)require(body.actor()==LearningGameAudit.Actor.THEORY&&body.played().uci().equals(theory[f.ply()]),"Changed theory prefix");
                else require(body.actor()==(f.side().equals(definition.path("human").asText())?LearningGameAudit.Actor.STOCKFISH:LearningGameAudit.Actor.HARD),"Substituted actor");
                if(body.actor()==LearningGameAudit.Actor.STOCKFISH) {
                    var raw=oracles.get(body.choiceEvidence());require(raw!=null,"Missing actual free opponent calculation");
                    var query=raw.path("query");require(query.path("initialFen").asText().equals(f.initialFen())&&query.path("root").isNull()&&query.path("depth").asInt()==19&&query.path("history").equals(JSON.valueToTree(f.history().stream().map(ChessPosition.Move::uci).toList())),"Foreign actual opponent context");
                    require(raw.path("bestmove").asText().equals(body.played().uci()),"Opponent move differs from its calculation");
                }
                check.same(id+" ply="+f.ply()+" side="+f.side()+" native="+f.hard().decision().move().uci()+" nativeWork="+f.hard().decision().extraNodes()+" hintWork="+f.hint().extraNodes(),200000,f.requestedHintNodes());
            }
            var first=rows.getFirst().body().before();var replay=LearningGameAudit.validate(rows,first.identity(),id,definition.path("fen").asText());
            check.same(id+" independently replayed ply count",game.path("plies").asInt(),replay.ply());
            check.same(id+" independently replayed outcome",game.path("replay").path("result").asText(),replay.result().name());
        }
        require(!moves.isEmpty(),"Empty learning corpus");return new Games(manifest.path("identity"),moves);
    }
    private static double loss(JsonNode ply,int depth,java.util.Map<String,JsonNode> rows){return StockfishStudyAudit.loss(rows.get(ply.path("free").path(Integer.toString(depth)).asText()),rows.get(ply.path("forced").path(Integer.toString(depth)).asText()));}
    private static void pair(JsonNode pair,String origin,List<String> history,String root,java.util.Map<String,JsonNode> rows) {
        for(int depth:List.of(17,19)) {
            var row=rows.get(pair.path(Integer.toString(depth)).asText());require(row!=null,"Missing planned oracle phase");var q=row.path("query");
            require(q.path("initialFen").asText().equals(origin)&&q.path("history").equals(JSON.valueToTree(history))&&q.path("depth").asInt()==depth&&(root==null?q.path("root").isNull():q.path("root").asText().equals(root)),"Foreign full-history PV query");
        }
    }
    private static Path root(){String s=System.getProperty("chess.learning.output","");require(!s.isBlank(),"Select external learning evidence");return Path.of(s).toAbsolutePath().normalize();}
    private static JsonNode trusted(Path path,String property)throws Exception {verify(path,System.getProperty("chess.learning."+property,""));return JSON.readTree(path.toFile());}
    private static void verify(Path path,String sha)throws Exception {require(sha.matches("[a-f0-9]{64}")&&sha.equals(hash(path)),"Missing or changed trusted archive bytes: "+path.getFileName());}
    private static String hash(Path path)throws Exception{return HardLeagueAudit.digest(Files.readAllBytes(path));}
    private static String hash(JsonNode value){return HardLeagueAudit.digest(HardLeagueAudit.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
