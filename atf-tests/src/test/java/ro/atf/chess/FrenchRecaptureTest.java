package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class FrenchRecaptureTest {
    private static final String SHA="d7d3b245c6e7d140a1936436f0fe04d4fdbfef126ee0450669a3cdfab9443f8d";
    @Test void nativeFrenchDecisionIncludesThePawnRecaptureBeforeKeepingTheGain(Checks check)throws Exception {
        var data=load();
        for(String label:List.of("french-original-root","french-mirror-root")) {
            var phase=phase(data,label);var game=game(phase);String checkpoint=game.checkpoint();var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,analysis);String move=analysis.decision().move().uci();
            check.same(label+" selects the independently verified knight tempo",row(data,phase,"free",19).path("bestmove").asText(),move);
            for(int depth:List.of(17,19))check.same(label+" H"+depth+" forced root loss stays below 50 permille",true,loss(data,phase,move,depth)<50);
            check.same("The ordinary common horizon really completed",6,analysis.decision().depth());
            check.same(label+" actual="+move+" H"+analysis.decision().depth()+" nodes="+analysis.decision().extraNodes()+" PV="+analysis.search().result().ranked()+"; one allowance pays for all comparisons",true,analysis.decision().extraNodes()>0&&analysis.decision().extraNodes()<=200000);
            check.same("No move or history is applied by analysis",checkpoint,game.checkpoint());
        }
    }
    @Test void theNonCheckingQueensGambitControlKeepsTheLowerLossDecision(Checks check)throws Exception {
        var data=load();var phase=phase(data,"queens-gambit-regression");var game=game(phase);var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,analysis);String move=analysis.decision().move().uci();
        check.same("The early-reuse regression must retain Bg3", "f4g3",move);
        for(int d:List.of(17,19))check.same("Current control H"+d+" chosen="+move+" loss="+loss(data,phase,move,d),true,loss(data,phase,move,d)<50&&loss(data,phase,"f4d6",d)>=150);
    }

    @Test void anInterruptedLastComparisonCannotClaimTheCompletedHorizon(Checks check)throws Exception {
        var g=game(phase(load(),"french-original-root"));int actual=g.analyzeBot(MatchSetup.Level.HARD).decision().extraNodes();
        var interrupted=atLimit(g,actual-1);var completed=atLimit(g,actual);
        check.same("One missing visited node retains the earlier complete horizon",true,interrupted.depth()<6);
        check.same("Interrupted work is still fully charged",actual-1,interrupted.extraNodes());
        check.same("The exact paid boundary completes H6",6,completed.depth());
        check.same("The completed choice is the verified knight move",Move.fromUci("e5c4"),completed.result().ranked().getFirst().move());
    }

    @Test void theRecordedFrontierIsACaptureAndTheActualReplyChangesItsScore(Checks check)throws Exception {
        var data=load();var phase=phase(data,"french-original-root");var game=game(phase);var previous=old(data);var before=game.position();for(var m:previous.line().subList(0,4))before=before.play(m);
        Move capture=previous.line().getLast();check.same("Nxf5 captures the pawn already on f5",ChessPosition.Kind.PAWN,MatchMaterial.victim(before,capture).kind());
        check.same("The legal recapture is visible at the old horizon",true,before.play(capture).legalMoves().contains(Move.fromUci("e6f5")));
        var closed=closedLine(game.position(),previous.move());var path=moves(closed);
        check.same("H6 contains the same H5 prefix",previous.line(),path.subList(0,5));check.same("The missing pawn response is searched, not appended for presentation",Move.fromUci("e6f5"),path.getLast());
        check.same("The recomputed full-window score changes sign",true,previous.score()>0&&(int)field(closed,"score")<0);
        var endpoint=phase(data,"french-original-native-frontier");for(int d:List.of(17,19))check.same("Independent oracle confirms exf5 at H"+d,0.0,loss(data,endpoint,"e6f5",d));
    }
    @Test void aBoundOrForeignPrefixCannotDisplaceTheHorizonGuard(Checks check)throws Exception {
        var data=load();var board=game(phase(data,"french-original-root")).position();var previous=old(data);var analysis=game(phase(data,"french-original-root")).analyzeBot(MatchSetup.Level.HARD);var closed=closedLine(board,previous.move());
        check.same("The completed same-context closure is eligible",true,eligible(board,previous,analysis.search().result(),closed));
        check.same("Missing evidence is unknown",false,eligible(board,previous,analysis.search().result(),null));
        var bounded=line((int)field(closed,"score"),(int)field(closed,"balance"),moves(closed),false);
        check.same("A cutoff bound is never an exact closure",false,eligible(board,previous,analysis.search().result(),bounded));
        var foreign=new ArrayList<>(moves(closed));foreign.set(0,Move.fromUci("e5c4"));
        check.same("Another root cannot close the incumbent",false,eligible(board,previous,analysis.search().result(),line((int)field(closed,"score"),(int)field(closed,"balance"),foreign,true)));
        var noGain=new TacticalAnalysis.Result(List.of(new TacticalAnalysis.Variation(analysis.search().result().ranked().getFirst().line(),(int)field(closed,"score"),0,TacticalAnalysis.Ending.HORIZON)));
        check.same("A tie cannot displace the guarded leader",false,eligible(board,previous,noGain,closed));
    }
    @Test void aPinnedPawnOrANonCaptureCannotStartTheExtraWork(Checks check) {
        var root=ChessPosition.fromFen("4k3/8/4p3/5p2/7N/8/8/K3R3 w - - 0 1");
        var pinned=variation("a1b1 e8d8 b1a1 d8e8 h4f5");var free=variation("a1b1 e8d8 b1a1 d8c8 h4f5");
        check.same("A geometric pawn attack is not a legal recapture",false,TacticalAnalysis.unresolvedPawnRecapture(root,pinned,5));
        check.same("The same capture is unresolved after the pin is removed",true,TacticalAnalysis.unresolvedPawnRecapture(root,free,5));
        var quiet=ChessPosition.fromFen("4k3/8/4p3/8/7N/8/8/K3R3 w - - 0 1");
        check.same("A quiet move cannot impersonate this capture proof",false,TacticalAnalysis.unresolvedPawnRecapture(quiet,free,5));
    }
    @Test void anotherRecaptureOrCheckMeansTheProofIsStillOpen(Checks check)throws Exception {
        var d=load();var previous=old(d);var more=ChessPosition.fromFen("rnb1k2r/ppp1bp2/3qp3/4Np1R/3P3p/2P3N1/PP3PPP/R2QKB1R w KQkq - 0 11");
        var path=new ArrayList<>(previous.line());path.add(Move.fromUci("e6f5"));var end=more;for(var m:path)end=end.play(m);
        check.same("A further legal recapture really exists",true,end.legalMoves().contains(Move.fromUci("h5f5")));
        var candidate=new TacticalAnalysis.Result(List.of(new TacticalAnalysis.Variation(List.of(Move.fromUci("e5c4")),0,0,TacticalAnalysis.Ending.HORIZON)));
        check.same("A second visible recapture is not a closed proof",false,eligible(more,previous,candidate,line(-204,270,path,true)));
        var checking=ChessPosition.fromFen("4k3/5p2/4p3/8/6KN/8/8/8 w - - 0 1");var evasion=variation("g4h3 e8d8 h3g4 f7f5 h4f5");var checked=new ArrayList<>(evasion.line());checked.add(Move.fromUci("e6f5"));var board=checking;for(var m:checked)board=board.play(m);
        check.same("The legal pawn recapture gives check",true,board.isCheck());
        check.same("Mandatory check evasions must precede a final verdict",false,eligible(checking,evasion,candidate,line(-204,-100,checked,true)));
    }

    @Test void portableOraclePhasesRejectSubstitution(Checks check)throws Exception {
        var data=load();validate(data);
        var wrong=data.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)wrong.path("phases").get(0).path("history")).removeAll();
        check.rejects("Another full prefix cannot inherit these scores",IllegalArgumentException.class,()->validate(wrong));
        var changed=data.deepCopy();var body=(com.fasterxml.jackson.databind.node.ObjectNode)changed.path("raw").get(0).path("body");body.put("expectedPermille",999);
        check.rejects("A rewritten score cannot certify the exchange",IllegalArgumentException.class,()->validate(changed));
    }
    @Test @Tag("benchmark") void thePublicHintMustAlsoAvoidTheConfirmedBadBishopCheck(Checks check)throws Exception {
        var data=load();var p=phase(data,"french-original-root");var hint=TacticalAnalysis.verifiedHints(game(p).position(),200000);String move=hint.lines().getFirst().move().uci();
        for(int d:List.of(17,19))check.same("Current public hint "+move+" H"+d+" loss="+loss(data,p,move,d),true,loss(data,p,move,d)<150);
    }
    @Test @Tag("benchmark") void theReviewMustNotCallTheConfirmedLossTheBestMove(Checks check)throws Exception {
        var data=load();var p=phase(data,"french-original-root");var before=game(p);var review=TacticalAnalysis.review(before.position(),Move.fromUci("f1b5"),before.automaticDrawMoves(),GameReviewPolicy.CURRENT.search);String move=review.best().move().uci();
        for(int d:List.of(17,19))check.same("Review recommendation="+move+" H"+d+" loss="+loss(data,p,move,d),true,loss(data,p,move,d)<150);
        check.same("Bb5+ cannot retain the best score after its verified refutation",true,review.played().score()<review.best().score());
    }

    private static TacticalAnalysis.Variation variation(String moves){return new TacticalAnalysis.Variation(Arrays.stream(moves.split(" ")).map(Move::fromUci).toList(),153,100,TacticalAnalysis.Ending.HORIZON);}
    private static JsonNode load()throws Exception{try(var in=FrenchRecaptureTest.class.getResourceAsStream("/french-recapture-witnesses.yaml")){require(in!=null,"Missing frontier witnesses");var d=LearningGameAudit.mapper().readTree(in.readAllBytes());require(hash(d).equals(SHA),"Changed frontier witnesses");validate(d);return d;}}
    private static void validate(JsonNode data) {
        var rows=new HashMap<String,JsonNode>();for(var env:data.path("raw")){var row=env.path("body");require(hash(row).equals(env.path("digest").asText()),"Modified cold receipt");StockfishStudyAudit.validate(row.path("query"),data.path("identity"),row);require(rows.putIfAbsent(row.path("key").asText(),row)==null,"Repeated oracle phase");}
        var expected=new HashSet<String>();for(var p:data.path("phases")){for(var it=p.path("queries").fields();it.hasNext();){var e=it.next();for(int d:List.of(17,19)){String key=e.getValue().path(""+d).asText();expected.add(key);var r=rows.get(key);require(r!=null,"Missing frontier phase");var q=r.path("query");require(q.path("initialFen").equals(p.path("initialFen"))&&q.path("history").equals(p.path("history"))&&q.path("depth").asInt()==d&&(e.getKey().equals("free")?q.path("root").isNull():q.path("root").asText().equals(e.getKey())),"Foreign frontier context");}}}
        require(expected.equals(rows.keySet()),"Unplanned frontier receipt");
    }
    private static JsonNode phase(JsonNode d,String name){for(var p:d.path("phases"))if(p.path("label").asText().equals(name))return p;throw new IllegalArgumentException("Missing phase "+name);}
    private static JsonNode row(JsonNode d,JsonNode phase,String role,int depth){String key=phase.path("queries").path(role).path(""+depth).asText();for(var e:d.path("raw"))if(e.path("body").path("key").asText().equals(key))return e.path("body");throw new IllegalArgumentException("No independent score for "+role);}
    private static double loss(JsonNode d,JsonNode p,String move,int depth){return StockfishStudyAudit.loss(row(d,p,"free",depth),row(d,p,move,depth));}
    private static ChessMatch game(JsonNode phase){var root=ChessPosition.fromFen(phase.path("initialFen").asText());var side=ChessPosition.fromFen(phase.path("fen").asText()).turn();var game=ChessMatch.fromPosition(root,new MatchSetup(side.other(),MatchSetup.Level.HARD,true));for(var u:phase.path("history"))game=game.play(Move.fromUci(u.asText()));return game;}
    private static TacticalAnalysis.Variation old(JsonNode d)throws Exception{return LearningGameAudit.mapper().treeToValue(d.path("oldVariation"),TacticalAnalysis.Variation.class);}
    private static TacticalAnalysis.SearchReport atLimit(ChessMatch g,int maximum)throws Exception {
        var rt=Class.forName("ro.atf.chess.TacticalAnalysis$RootSearch");var rc=rt.getDeclaredConstructor(ChessPosition.class,Set.class);rc.setAccessible(true);var ot=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");@SuppressWarnings({"rawtypes","unchecked"})var objective=Enum.valueOf((Class)ot,"POSITION");var root=rc.newInstance(g.position(),Set.of());var analyze=TacticalAnalysis.class.getDeclaredMethod("analyze",rt,int.class,List.class,ot);analyze.setAccessible(true);var base=(TacticalAnalysis.Result)analyze.invoke(null,root,3,g.position().legalMoves(),objective);
        var p=HardAdaptivePolicy.CURRENT;var cap=new HardAdaptivePolicy(true,Math.min(p.baseNodes(),maximum),maximum,p.maxPlies(),p.minimumPlies(),p.cacheObservations(),p.coldCachePerMille());var run=TacticalAnalysis.class.getDeclaredMethod("matchSearch",rt,MatchAiPolicy.Search.class,int.class,int.class,boolean.class,boolean.class,TacticalAnalysis.Result.class,HardAdaptivePolicy.class,List.class,int.class);run.setAccessible(true);return (TacticalAnalysis.SearchReport)run.invoke(null,root,p.search(),0,0,true,true,base,cap,g.searchHistory(),g.searchQuietPlies());
    }

    private static Object closedLine(ChessPosition position,Move move)throws Exception {
        var rt=Class.forName("ro.atf.chess.TacticalAnalysis$RootSearch");var rc=rt.getDeclaredConstructor(ChessPosition.class,Set.class);rc.setAccessible(true);var bt=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var bc=bt.getDeclaredConstructor(int.class,int.class,int.class);bc.setAccessible(true);var ot=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");@SuppressWarnings({"unchecked","rawtypes"})var objective=Enum.valueOf((Class)ot,"POSITION");var method=rt.getDeclaredMethod("line",Move.class,int.class,int.class,bt,ot,int.class);method.setAccessible(true);return method.invoke(rc.newInstance(position,Set.of()),move,6,100001,bc.newInstance(200000,0,50000),objective,0);
    }
    private static boolean eligible(ChessPosition board,TacticalAnalysis.Variation previous,TacticalAnalysis.Result candidate,Object line)throws Exception{var type=Class.forName("ro.atf.chess.TacticalAnalysis$Line");var method=TacticalAnalysis.class.getDeclaredMethod("closedPawnRecapture",ChessPosition.class,TacticalAnalysis.Variation.class,TacticalAnalysis.Result.class,type);method.setAccessible(true);return (boolean)method.invoke(null,board,previous,candidate,line);}
    private static Object line(int score,int balance,List<Move> moves,boolean exact)throws Exception{var type=Class.forName("ro.atf.chess.TacticalAnalysis$Line");var constructor=type.getDeclaredConstructor(int.class,int.class,List.class,TacticalAnalysis.Ending.class,boolean.class);constructor.setAccessible(true);return constructor.newInstance(score,balance,List.copyOf(moves),TacticalAnalysis.Ending.HORIZON,exact);}
    @SuppressWarnings("unchecked") private static List<Move> moves(Object line)throws Exception{return (List<Move>)field(line,"moves");}
    private static Object field(Object value,String name)throws Exception{var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);}
    private static String hash(JsonNode n){return HardLeagueAudit.digest(HardLeagueAudit.canonical(n).getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
}
