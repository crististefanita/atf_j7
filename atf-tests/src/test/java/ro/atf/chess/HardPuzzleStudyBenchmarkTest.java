package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Replays objective-blind native decisions; canonical stage success is not a free-defense game proof. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardPuzzleStudyBenchmarkTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private record Chapter(String name,List<QuestCatalog.Quest> quests) { }
    private static List<Chapter> chapters() {
        return List.of(new Chapter("mate-in-one",QuestCatalog.load()),new Chapter("mate-in-two",QuestCatalog.loadMateInTwo()),new Chapter("mate-in-three",QuestCatalog.loadMateInThree()),
                new Chapter("tactics",QuestCatalog.loadTactics()),new Chapter("training",QuestCatalog.loadTraining()),new Chapter("intermezzo",QuestCatalog.loadIntermezzo()));
    }
    @Test void publishesEveryNativeCanonicalStageWithoutInventingCompleteSolutions(Checks check) throws Exception {inspect(check,true);}
    @Test void nativeHardMustActuallyCompleteEveryCanonicalExerciseToClaimAllSolved(Checks check) throws Exception {
        var grade=inspect(check,false);check.same("Every canonical exercise, not just its key or some later isolated stage, must pass",0,grade.path("incompleteExercises").asInt());
    }
    private static com.fasterxml.jackson.databind.JsonNode inspect(Checks check,boolean details) throws Exception {
        var configured=System.getProperty("chess.puzzleStudy.output","");require(!configured.isBlank(),"Select an external native puzzle study");var folder=Path.of(configured).toAbsolutePath().normalize();
        require(!folder.startsWith(ProjectRoot.locate().toAbsolutePath().normalize()),"Keep native puzzle evidence outside the checkout");
        var raw=trusted(folder.resolve("native.jsonl"),"nativeSha256");var identity=JSON.readTree(trusted(folder.resolve("identity.json"),"identitySha256"));
        String revision=System.getProperty("chess.puzzleStudy.revision","");require(revision.matches("[a-f0-9]{40}")&&revision.equals(identity.path("revision").asText()),"Foreign native puzzle source");
        check.same("The complete public baseline uses the active native policy, not a diagnostic overlay",JSON.valueToTree(HardAdaptivePolicy.CURRENT),identity.path("nativePolicy"));
        check.same("The closed promotion feature is bound to its actually loaded policy",JSON.valueToTree(PromotionExchangePolicy.CURRENT),identity.path("promotionExchangePolicy"));
        check.same("The forced promotion phase is bound to its actually loaded policy",JSON.valueToTree(PawnPushPolicy.CURRENT),identity.path("pawnPushPolicy"));
        NativeModelAudit.validate(identity);
        check.same("Additional nodes do not include the initial native horizon",MatchAiPolicy.BASE_PLIES,identity.path("unbudgetedBasePlies").asInt());
        var chapters=chapters();var expectedCatalog=new ArrayList<Object>();
        for(var chapter:chapters)for(var q:chapter.quests())expectedCatalog.add(java.util.Map.of("chapter",chapter.name(),"id",q.id(),"fen",q.fen(),"line",q.line(),"objective",q.objective()));
        check.same("The complete live catalog and its canonical lines are pinned",JSON.valueToTree(expectedCatalog),JSON.readTree(trusted(folder.resolve("catalog.json"),"catalogSha256")));
        var records=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for(var line:new String(raw,java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            var envelope=JSON.readTree(line);var body=envelope.path("body");require(envelope.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8))),"Modified native puzzle receipt");
            require(body.path("identity").equals(identity),"Foreign native puzzle producer");records.add(body);
        }
        int index=0,first=0,complete=0,total=0,stages=0,acceptedStages=0;var chapterGrades=new ArrayList<Object>();
        for(var chapter:chapters) {
            int chapterFirst=0,chapterComplete=0,chapterStages=0,chapterAccepted=0;var misses=new ArrayList<Object>();
            for(var quest:chapter.quests()) {
                total++;boolean prior=true,intervened=false;var game=ChessMatch.fromPosition(quest.position(),new MatchSetup(quest.position().turn().other(),MatchSetup.Level.HARD,false));
                for(int stage=0;stage<quest.playerMoves();stage++) {
                    require(index<records.size(),"Missing native puzzle stage");var row=records.get(index++);
                    require(row.path("chapter").asText().equals(chapter.name())&&row.path("id").asText().equals(quest.id())&&row.path("stage").asInt()==stage,"Missing, reordered or borrowed canonical stage");
                    require(row.path("rootFen").asText().equals(quest.fen())&&row.path("checkpoint").asText().equals(game.checkpoint())&&row.path("fen").asText().equals(StudyFen.replay(quest.fen(),quest.line().subList(0,stage*2))),"Foreign puzzle origin or prefix");
                    var analysis=nativeAnalysis(row.path("analysis"));HardQuestAudit.validate(game,analysis);
                    NativeModelAudit.validateClock(row,game);
                    check.same("Actual profile ceiling "+quest.id()+":"+stage,HardQuestAudit.maximumNodes(game.position()),row.path("maximumExtraNodes").asInt());
                    var outcome=quest.assess(analysis.decision().move(),stage);boolean accepted=outcome.continues()||outcome.solved();
                    require(row.path("canonicalStageAccepted").asBoolean()==accepted&&row.path("priorNativeStagesAccepted").asBoolean()==prior&&row.path("prefixReferenceIntervention").asBoolean()==intervened,"Invented canonical success or hidden reference intervention");
                    require(row.path("referenceKeyReadAfterSearch").asText().equals(quest.playerMove(stage).uci())&&row.path("catalogKeyMatched").asBoolean()==analysis.decision().move().equals(quest.playerMove(stage)),"Substituted curriculum key");
                    if(details)check.same(quest.id()+":"+stage+" accepted="+accepted+" prior="+prior+" H"+analysis.decision().depth()+" extra="+analysis.decision().extraNodes()+" choice="+analysis.decision().move().uci()+" PV="+analysis.search().result().ranked(),true,game.position().legalMoves().contains(analysis.decision().move()));
                    if(stage==0&&accepted)chapterFirst++;if(accepted)chapterAccepted++;else misses.add(java.util.Map.of("id",quest.id(),"stage",stage,"choice",analysis.decision().move().uci(),"key",quest.playerMove(stage).uci(),"depth",analysis.decision().depth(),"extraNodes",analysis.decision().extraNodes()));
                    prior&=accepted;if(stage<quest.playerMoves()-1&&!analysis.decision().move().equals(quest.playerMove(stage)))intervened=true;
                    chapterStages++;game=game.play(quest.playerMove(stage));if(stage<quest.playerMoves()-1)game=game.play(quest.defenseAfter(stage));
                }
                if(prior&&!intervened)chapterComplete++;
            }
            first+=chapterFirst;complete+=chapterComplete;stages+=chapterStages;acceptedStages+=chapterAccepted;
            chapterGrades.add(java.util.Map.of("chapter",chapter.name(),"exercises",chapter.quests().size(),"firstAccepted",chapterFirst,"canonicalComplete",chapterComplete,"stages",chapterStages,"acceptedStages",chapterAccepted,"misses",misses));
        }
        check.same("Every native record was consumed exactly once",records.size(),index);
        var result=JSON.valueToTree(java.util.Map.of("identity",identity,"exercises",total,"firstAccepted",first,"canonicalComplete",complete,"incompleteExercises",total-complete,"stages",stages,"acceptedStages",acceptedStages,"chapters",chapterGrades));
        var path=folder.resolve("qualified-grades.json");if(Files.exists(path))require(JSON.readTree(Files.readString(path)).equals(result),"Republication changed puzzle observations");else Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));return result;
    }
    private static byte[] trusted(Path path,String property)throws Exception {
        String expected=System.getProperty("chess.puzzleStudy."+property,"");require(expected.matches("[a-f0-9]{64}"),"Missing separately trusted "+property);var bytes=Files.readAllBytes(path);require(HardLeagueAudit.digest(bytes).equals(expected),"Modified native puzzle input "+property);return bytes;
    }
    private static ChessMatch.BotAnalysis nativeAnalysis(com.fasterxml.jackson.databind.JsonNode row)throws Exception {
        return HardQuestAudit.read(row);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
}
