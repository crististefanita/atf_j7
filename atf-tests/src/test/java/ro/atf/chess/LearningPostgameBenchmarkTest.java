package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class LearningPostgameBenchmarkTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=LearningGameAudit.mapper();
    private record Grade(int exposedBadTails,int recommendedBadRoots) { }

    @Test void everySavedGameRetainsItsCompleteReviewAndEveryOraclePhase(Checks check)throws Exception {
        inspect(check);
    }
    @Test void exposedFinalHintPiecesHaveNoStableLargeOracleLoss(Checks check)throws Exception {
        check.same("A fully audited archive must still fail quality for an unsound exposed final piece",0,inspect(check).exposedBadTails());
    }
    @Test void fullReviewRecommendationsMeetTheIndependentOracleFloor(Checks check)throws Exception {
        check.same("Native review recommendations are graded independently of receipt integrity",0,inspect(check).recommendedBadRoots());
    }

    private static Grade inspect(Checks check)throws Exception {
        var root=Path.of(required("output")).toAbsolutePath().normalize();
        var input=trusted(root.resolve("manifest.json"),required("manifestSha256"));
        var original=trusted(root.resolve("deep-oracle/manifest.json"),required("oracleManifestSha256"));
        var finalManifest=trusted(root.resolve("postgame-audit/manifest.json"),required("postgameManifestSha256"));
        var plan=trusted(root.resolve("postgame-audit/plan.json"),finalManifest.path("planSha256").asText());
        var base=trusted(root.resolve("deep-oracle/plan.json"),original.path("planSha256").asText());
        var config=trusted(root.resolve("postgame-audit/review-producer.json"),plan.path("producerSha256").asText());
        var summary=trusted(root.resolve("postgame-audit/summary.json"),finalManifest.path("summarySha256").asText());
        verify(root.resolve("postgame-audit/tails.json"),finalManifest.path("tailsSha256").asText());
        require(finalManifest.path("complete").asBoolean()&&plan.path("identity").equals(finalManifest.path("identity")),"Incomplete or foreign post-game collection");
        require(plan.path("identity").path("source").equals(input.path("identity"))&&plan.path("identity").path("reviewer").equals(config.path("reviewer")),"Relabeled source or reviewer");
        require(config.path("inputManifestSha256").asText().equals(hash(root.resolve("manifest.json")))
                &&plan.path("basePlanSha256").asText().equals(hash(root.resolve("deep-oracle/plan.json")))
                &&plan.path("identity").path("baseOracleSha256").asText().equals(original.path("oracleSha256").asText()),"Foreign original input");
        require(plan.path("baseOracleIdentity").equals(base.path("identity")),"Original oracle identity was relabeled");
        require(config.path("reviewPolicySha256").asText().equals(hash(ProjectRoot.locate().resolve("chess-core/src/main/resources/game-review.yaml"))),"Verifier loaded another review policy");
        var baseQueries=queries(base);var planned=queries(plan);var additions=new HashMap<String,JsonNode>();
        for(var q:plan.path("queries")) {
            String key=q.path("key").asText();boolean inherited=baseQueries.containsKey(key);
            require(q.path("receiptSource").asText().equals(inherited?"BASE":"ADDITION"),"Foreign oracle provenance");
            if(inherited)require(q.path("query").equals(baseQueries.get(key)),"Changed inherited query");else additions.put(key,q.path("query"));
        }
        var rows=raw(root.resolve("deep-oracle/oracle.jsonl"),original.path("oracleSha256").asText(),baseQueries,base.path("identity"));
        var added=raw(root.resolve("postgame-audit/oracle-additions.jsonl"),finalManifest.path("oracleAdditionsSha256").asText(),additions,plan.path("identity"));
        for(var e:added.entrySet())require(rows.putIfAbsent(e.getKey(),e.getValue())==null,"A new phase replaced an old oracle receipt");
        require(rows.keySet().containsAll(planned.keySet()),"Missing planned post-game query");
        var reviewer=JSON.treeToValue(config.path("reviewer"),LearningGameAudit.Identity.class);
        var games=new HashMap<String,List<LearningGameAudit.Envelope>>();var files=new HashMap<String,JsonNode>();
        for(var g:input.path("games")) {
            String id=g.path("case").path("id").asText();require(id.matches("[a-z0-9-]+"),"Foreign case path");
            var journal=root.resolve(id+"/moves.jsonl");verify(journal,g.path("movesSha256").asText());
            var entries=new ArrayList<LearningGameAudit.Envelope>();for(var line:Files.readAllLines(journal))entries.add(JSON.readValue(line,LearningGameAudit.Envelope.class));
            require(games.putIfAbsent(id,List.copyOf(entries))==null,"Repeated source game");files.put(id,g);
        }
        var expectedPublicTails=new HashMap<String,List<ChessPosition.Move>>();
        for(var game:games.entrySet())for(var e:game.getValue())for(int rank=0;rank<e.body().before().hint().lines().size();rank++)expectedPublicTails.put(game.getKey()+":"+e.body().before().ply()+":"+(rank+1),e.body().before().hint().lines().get(rank).line());
        var resultFiles=new HashMap<String,JsonNode>();var readableTails=new HashMap<String,JsonNode>();
        for(var g:finalManifest.path("games"))require(resultFiles.putIfAbsent(g.path("case").asText(),g)==null,"Repeated readable game analysis");
        require(resultFiles.keySet().equals(games.keySet()),"Missing readable game analysis");
        var reviewed=new HashSet<String>();int turns=0,badRoots=0;
        for(var item:plan.path("reviews")) {
            String id=item.path("case").asText();require(games.containsKey(id)&&reviewed.add(id),"Foreign or repeated reviewed game");
            var received=trusted(root.resolve(id+"/review.json"),item.path("reviewSha256").asText());
            var envelope=JSON.treeToValue(received,LearningReviewAudit.Envelope.class);
            var nativeReview=LearningReviewAudit.validate(envelope,games.get(id),files.get(id).path("movesSha256").asText(),reviewer,config.path("reviewPolicySha256").asText());
            require(item.path("reviewDigest").asText().equals(envelope.digest()),"Foreign reviewed phase");
            require(resultFiles.get(id).path("reviewSha256").equals(item.path("reviewSha256")),"Readable analysis belongs to another review");
            var readable=trusted(root.resolve(id+"/analysis.json"),resultFiles.get(id).path("analysisSha256").asText());
            require(readable.path("reviewDigest").asText().equals(envelope.digest())&&readable.path("turns").size()==nativeReview.turns().size(),"Readable analysis is incomplete or foreign");
            for(var tail:readable.path("hintTails"))require(readableTails.putIfAbsent(id+":"+tail.path("ply").asInt()+":"+tail.path("rank").asInt(),tail)==null,"Repeated readable hint tail");
            var source=games.get(id).getFirst().body().before();
            require(JSON.valueToTree(source.identity()).equals(input.path("identity")),"Foreign original game source");
            require(item.path("turns").size()==nativeReview.turns().size(),"Partial final analysis");
            for(int i=0;i<nativeReview.turns().size();i++) {
                var turn=nativeReview.turns().get(i);var phase=item.path("turns").get(i);var history=games.get(id).get(i).body().before().history().stream().map(ChessPosition.Move::uci).toList();
                require(phase.path("ply").asInt()==i+1&&phase.path("native").equals(JSON.valueToTree(turn)),"Changed review move or native score");
                pair(phase.path("free"),source.initialFen(),history,null,rows);
                pair(phase.path("played"),source.initialFen(),history,turn.played().uci(),rows);
                pair(phase.path("recommended"),source.initialFen(),history,turn.bestLine().getFirst().uci(),rows);
                var display=readable.path("turns").get(i);
                require(display.path("ply").asInt()==turn.ply()&&display.path("played").asText().equals(turn.played().uci())&&display.path("grade").asText().equals(turn.grade().name())&&display.path("nativeLoss").asInt()==turn.loss(),"Rewritten readable review label");
                for(int d:List.of(17,19))require(display.path("oraclePlayedLoss").path(Integer.toString(d)).asDouble(-1)==loss(phase.path("free"),phase.path("played"),d,rows)&&display.path("oracleRecommendedLoss").path(Integer.toString(d)).asDouble(-1)==loss(phase.path("free"),phase.path("recommended"),d,rows),"Readable Stockfish loss differs from raw evidence");
                if(large(phase.path("free"),phase.path("recommended"),rows))badRoots++;
                require(phase.path("branches").size()==2,"Missing played or recommended review branch");
                for(int b=0;b<2;b++) {
                    var branch=phase.path("branches").get(b);var path=b==0?turn.bestLine():turn.playedLine();
                    require(branch.path("name").asText().equals(b==0?"RECOMMENDED":"PLAYED")&&branch.path("plies").size()==path.size(),"Substituted review branch");
                    var prefix=new ArrayList<>(history);
                    for(int index=0;index<path.size();index++) {
                        var p=branch.path("plies").get(index);String move=path.get(index).uci();
                        require(p.path("index").asInt()==index&&p.path("move").asText().equals(move),"Substituted review reply");
                        pair(p.path("free"),source.initialFen(),prefix,null,rows);pair(p.path("forced"),source.initialFen(),prefix,move,rows);prefix.add(move);
                    }
                    pair(branch.path("endpoint"),source.initialFen(),prefix,null,rows);
                }
                check.same(id+" move="+(i+1)+" "+turn.side()+" "+turn.played().uci()+" grade="+turn.grade()+" nativeLoss="+turn.loss()+" nativeDepth="+turn.depth()+" SF-loss H17="+loss(phase.path("free"),phase.path("played"),17,rows)+" H19="+loss(phase.path("free"),phase.path("played"),19,rows),turn.played().uci(),rows.get(phase.path("played").path("19").asText()).path("query").path("root").asText());
                turns++;
            }
        }
        check.same("Every saved game has complete final analysis",games.keySet(),reviewed);
        var coveredTails=new HashSet<String>();int publicTails=0,exposed=0,badTails=0,controls=0;
        for(var tail:plan.path("tails")) {
            String id=tail.path("case").asText();require(games.containsKey(id),"Foreign hint-tail game");
            int ply=tail.path("ply").asInt();var f=games.get(id).get(ply).body().before();
            var path=strings(tail.path("path")).stream().map(ChessPosition.Move::fromUci).toList();
            if(tail.path("kind").asText().equals("PUBLIC_HINT")) {
                String key=id+":"+ply+":"+tail.path("rank").asInt();require(coveredTails.add(key)&&path.equals(expectedPublicTails.get(key)),"Missing, duplicated or substituted public tail");publicTails++;
            } else {require(tail.path("kind").asText().equals("SOUND_TRUNCATED_OFFER_CONTROL")&&id.equals("sound-queen-offer")&&ply==0&&path.equals(List.of(ChessPosition.Move.fromUci("h3g3"))),"Invented sound-sacrifice control");controls++;}
            var history=f.history().stream().map(ChessPosition.Move::uci).toList();
            require(tail.path("initialFen").asText().equals(f.initialFen())&&strings(tail.path("history")).equals(history),"Foreign tail history");
            var origin=ChessPosition.fromFen(f.fen());var geometry=LearningReviewAudit.tail(origin,path);
            require(tail.path("last").asText().equals(geometry.move().uci()),"Foreign last move");
            var legal=geometry.legalCapturesOfMovedPiece().stream().map(ChessPosition.Move::uci).collect(java.util.stream.Collectors.toSet());
            require(new HashSet<>(strings(tail.path("legalRecaptureMoves"))).equals(legal),"Illegal, pinned or missing recapture");
            var beforeTail=new ArrayList<>(history);beforeTail.addAll(path.subList(0,path.size()-1).stream().map(ChessPosition.Move::uci).toList());
            pair(tail.path("free"),f.initialFen(),beforeTail,null,rows);pair(tail.path("forced"),f.initialFen(),beforeTail,geometry.move().uci(),rows);
            var afterTail=new ArrayList<>(history);afterTail.addAll(path.stream().map(ChessPosition.Move::uci).toList());pair(tail.path("endpoint"),f.initialFen(),afterTail,null,rows);
            var recaptured=new HashSet<String>();for(var c:tail.path("recaptures")) {String move=c.path("move").asText();require(recaptured.add(move)&&legal.contains(move),"Foreign recapture phase");pair(c.path("query"),f.initialFen(),afterTail,move,rows);}
            require(recaptured.equals(geometry.resultingKind()==ChessPosition.Kind.PAWN?java.util.Set.of():legal),"Missing important-piece recapture evidence");
            boolean exposedPiece=!legal.isEmpty()&&geometry.resultingKind()!=ChessPosition.Kind.PAWN;
            boolean unsound=exposedPiece&&large(tail.path("free"),tail.path("forced"),rows);
            if(tail.path("kind").asText().equals("PUBLIC_HINT")){
                if(exposedPiece)exposed++;if(unsound)badTails++;
                var display=readableTails.get(id+":"+ply+":"+tail.path("rank").asInt());require(display!=null&&display.path("last").asText().equals(geometry.move().uci())&&display.path("exposedImportantPiece").asBoolean()==exposedPiece&&display.path("unsoundExposedTail").asBoolean()==unsound,"Readable tail verdict differs from its legal and raw evidence");
                for(int d:List.of(17,19))require(display.path("losses").path(Integer.toString(d)).asDouble(-1)==loss(tail.path("free"),tail.path("forced"),d,rows),"Changed readable tail loss");
            }
            else check.same("The sound queen offer survives independent final-move grading",false,unsound);
            check.same(id+" hint phase="+ply+" rank="+tail.path("rank").asInt()+" last="+geometry.move().uci()+" legal recaptures="+legal+" SF-loss H17="+loss(tail.path("free"),tail.path("forced"),17,rows)+" H19="+loss(tail.path("free"),tail.path("forced"),19,rows),geometry.move().uci(),rows.get(tail.path("forced").path("19").asText()).path("query").path("root").asText());
        }
        check.same("Every public hint has its exact last move audited",expectedPublicTails.keySet(),coveredTails);
        check.same("Readable tail tables have the same complete inventory",expectedPublicTails.keySet(),readableTails.keySet());
        check.same("An explicitly collected sound-sacrifice control stays separate from public samples",
                games.containsKey("sound-queen-offer")?1:0,controls);
        check.same("Reviewed actual moves independently counted",summary.path("actualPlies").asInt(),turns);
        check.same("Public tails independently counted",summary.path("publicHintTails").asInt(),publicTails);
        check.same("Exposed important pieces independently counted",summary.path("exposedImportantTails").asInt(),exposed);
        check.same("Unsound exposed tails remain disclosed",summary.path("unsoundExposedTails").asInt(),badTails);
        check.same("Unsound native review recommendations remain disclosed",summary.path("reviewRecommendationsWithLargeLoss").asInt(),badRoots);
        return new Grade(badTails,badRoots);
    }

    private static Map<String,JsonNode> queries(JsonNode plan) {
        var map=new HashMap<String,JsonNode>();for(var q:plan.path("queries")){String key=q.path("key").asText();require(key.equals(hash(q.path("query")))&&map.putIfAbsent(key,q.path("query"))==null,"Changed or repeated planned query");}return map;
    }
    private static Map<String,JsonNode> raw(Path path,String sha,Map<String,JsonNode> expected,JsonNode identity)throws Exception {
        verify(path,sha);var rows=new HashMap<String,JsonNode>();
        try(var reader=Files.newBufferedReader(path)){for(String line;(line=reader.readLine())!=null;){var env=JSON.readTree(line);var body=env.path("body");String key=body.path("key").asText();require(env.path("digest").asText().equals(hash(body)),"Changed raw oracle");StockfishStudyAudit.validate(expected.get(key),identity,body);require(rows.putIfAbsent(key,body)==null,"Repeated raw oracle phase");}}
        require(rows.keySet().equals(expected.keySet()),"Missing or extra oracle receipt");return rows;
    }
    private static void pair(JsonNode pair,String origin,List<String> history,String forced,Map<String,JsonNode> rows) {
        for(int depth:List.of(17,19)){var row=rows.get(pair.path(Integer.toString(depth)).asText());require(row!=null,"Missing review oracle phase");var q=row.path("query");require(q.path("initialFen").asText().equals(origin)&&strings(q.path("history")).equals(history)&&q.path("depth").asInt()==depth&&(forced==null?q.path("root").isNull():q.path("root").asText().equals(forced)),"Foreign full-history review or tail query");}
    }
    private static boolean large(JsonNode free,JsonNode forced,Map<String,JsonNode> rows){return loss(free,forced,17,rows)>=150&&loss(free,forced,19,rows)>=150;}
    private static double loss(JsonNode free,JsonNode forced,int depth,Map<String,JsonNode> rows){return LearningReviewAudit.oracleLoss(rows.get(free.path(Integer.toString(depth)).asText()),rows.get(forced.path(Integer.toString(depth)).asText()));}
    private static List<String> strings(JsonNode node){require(node.isArray(),"Missing ordered history");var out=new ArrayList<String>();node.forEach(v->out.add(v.asText()));return out;}
    private static JsonNode trusted(Path path,String sha)throws Exception{verify(path,sha);return JSON.readTree(path.toFile());}
    private static void verify(Path path,String sha)throws Exception{require(sha.matches("[a-f0-9]{64}")&&sha.equals(hash(path)),"Missing or changed trusted "+path.getFileName());}
    private static String required(String name){var value=System.getProperty("chess.learning."+name,"");require(!value.isBlank(),"Select trusted learning "+name);return value;}
    private static String hash(Path path)throws Exception{return HardLeagueAudit.digest(Files.readAllBytes(path));}
    private static String hash(JsonNode value){return HardLeagueAudit.digest(HardLeagueAudit.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
