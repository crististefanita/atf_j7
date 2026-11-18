package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PawnPushProofTest {
    private static final String FEN="5r2/7P/5K2/8/2P2k2/P7/8/8 w - - 1 69";
    private static final String PREFIX="f6g7 f8d8 h7h8q d8h8 g7h8 f4e4";

    @Test void nativeConversionKeepsTheOrdinaryHorizonAndPublishesTheSeparateWinningPlan(Checks check) {
        var game=game();var before=game.checkpoint();var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,analysis);
        check.same("The irreversible waiting move preserves both winning threats","a3a4",analysis.decision().move().uci());
        check.same("The completed ordinary search is still H6",6,analysis.decision().depth());
        var proof=analysis.search().pawnProof();check.same("A complete proof is retained",true,proof!=null);
        check.same("The longer plan is explicitly distinct from ordinary search",true,proof.linePlies()>analysis.search().depth());
        check.same("Every retained proof ply is actual",proof.linePlies(),analysis.search().result().ranked().getFirst().line().size());
        check.same("The proof visits real opposing alternatives",true,proof.defenderEdgesVisited()>100);
        check.same("Ordinary work and proof share one ceiling",true,proof.extraNodes()<analysis.decision().extraNodes()&&analysis.decision().extraNodes()<=200000);
        check.same("The exact history remains intact",before,game.checkpoint());
        var mirror=ChessMatch.fromPosition(ChessPosition.fromFen("8/8/p7/2p1K3/8/8/8/7k b - - 1 72"),new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,false));
        var other=mirror.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(mirror,other);
        check.same("The same structural plan works for Black","a6a5",other.decision().move().uci());
    }

    @Test void aQueenMustBeSafeAndMustNotStalemate(Checks check) {
        var easy=ChessPosition.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1");
        var cached=proof(easy,1,50000,List.of(),0);var uncached=proof(easy,1,0,List.of(),0);
        check.same("Cold and disabled caches retain the same complete winning tree",cached,uncached);
        check.same("A safe actual promotion closes the proof","a7a8q",cached.line().getFirst().uci());
        var hanging=ChessPosition.fromFen("8/Pk6/8/8/8/8/8/K7 w - - 0 1");
        check.same("An immediately capturable queen cannot close a winning proof",null,proof(hanging,1,50000,List.of(),0));
        var stalemate=ChessPosition.fromFen("8/5P1k/5K2/8/8/8/8/8 w - - 0 1");
        check.same("The negative fixture really stalemates",true,stalemate.play(ChessPosition.Move.fromUci("f7f8q")).outcome()==ChessPosition.Outcome.STALEMATE);
        check.same("Stalemate is not a successful queen promotion",null,proof(stalemate,1,50000,List.of(),0));
    }

    @Test void drawsAndOutOfScopeEndingsCannotBecomeInventedWins(Checks check) {
        var drawn=ChessPosition.fromFen("k7/P7/K7/8/8/8/8/8 w - - 0 1");
        check.same("The rook-pawn corner draw has no certified plan",null,proof(drawn,12,50000,List.of(),0));
        for(String fen:List.of("7k/8/8/4p3/8/8/P7/K7 w - - 0 1","7k/8/8/8/8/8/P7/KR6 w - - 0 1","7k/8/8/8/8/PPP5/8/K7 w - - 0 1"))
            check.same("Opposing pawns, extra pieces or excessive material retain ordinary search "+fen,false,PawnPushProof.eligible(ChessPosition.fromFen(fen),2));
    }

    @Test void historyAndClaimableDrawsArePartOfTheProof(Checks check) {
        var easy=ChessPosition.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1");var promoted=easy.play(ChessPosition.Move.fromUci("a7a8q"));
        check.same("A foreign prior board cannot borrow the empty-history certificate",null,proof(easy,1,50000,List.of(promoted),0));
        var reply=ChessPosition.fromFen("7k/P7/8/8/8/8/8/K7 b - - 0 1");
        var fresh=new PawnPushProof(ChessPosition.Side.WHITE,()->{},50000);
        check.same("The bare king is genuinely unable to stop this promotion",true,fresh.prove(reply,3,0)!=null);
        check.same("A legal quiet reply may claim the fiftieth move",null,new PawnPushProof(ChessPosition.Side.WHITE,()->{},50000).prove(reply,3,99));
        check.same("An irreversible promotion resets the draw counter",true,new PawnPushProof(ChessPosition.Side.WHITE,()->{},50000).prove(easy,1,99)!=null);
    }

    @Test void anInterruptedProofCannotIssueAWinningCertificate(Checks check) {
        int[] nodes={0};var position=game().position();
        var interrupted=new PawnPushProof(position.turn(),()->{if(++nodes[0]>1)throw new IllegalStateException("controlled proof cutoff");},50000);
        check.rejects("Interruption propagates without completing a proof",IllegalStateException.class,()->interrupted.prove(position,24));
        check.same("The proof charges every attempted visit",2,nodes[0]);
        var actual=game().analyzeBot(MatchSetup.Level.HARD);
        check.same("The ordinary search allowance is not enlarged",200000,HardAdaptivePolicy.CURRENT.maxNodes());
        check.same("Only spent nodes are published",true,actual.decision().extraNodes()>=actual.search().pawnProof().extraNodes());
    }

    @Test void auditRejectsRemovedForeignOrModifiedProofs(Checks check) throws Exception {
        var game=game();var actual=game.analyzeBot(MatchSetup.Level.HARD);var s=actual.search();var c=s.pawnProof();
        var stripped=new TacticalAnalysis.SearchReport(s.result(),s.depth(),s.extraNodes(),s.horizonLeaders(),s.scoreCredits(),s.excludedRoots(),s.nonEquivalentRoots());
        check.rejects("A long native PV cannot pass after stripping its certificate",IllegalArgumentException.class,()->HardQuestAudit.validate(game,new ChessMatch.BotAnalysis(actual.decision(),stripped)));
        check.rejects("A composed checkpoint cannot discard its original history",IllegalArgumentException.class,()->HardQuestAudit.validate(game.position(),actual));
        for(var changed:List.of(new PawnPushProof.Certificate(c.maxPlies(),c.linePlies(),c.extraNodes()-1,c.defenderEdgesVisited(),c.cacheLimit(),c.quietPlies(),c.historySha256(),c.strategySha256()),
                new PawnPushProof.Certificate(c.maxPlies(),c.linePlies(),c.extraNodes(),c.defenderEdgesVisited()+1,c.cacheLimit(),c.quietPlies(),c.historySha256(),c.strategySha256()),
                new PawnPushProof.Certificate(c.maxPlies(),c.linePlies(),c.extraNodes(),c.defenderEdgesVisited(),c.cacheLimit(),c.quietPlies(),c.historySha256(),"0".repeat(64)))) {
            var forged=new TacticalAnalysis.SearchReport(s.result(),s.depth(),s.extraNodes(),s.horizonLeaders(),s.scoreCredits(),s.excludedRoots(),s.nonEquivalentRoots(),changed);
            check.rejects("Reject altered proof work, defense coverage or strategy",IllegalArgumentException.class,()->HardQuestAudit.validate(game,new ChessMatch.BotAnalysis(actual.decision(),forged)));
        }
        var line=s.result().ranked().getFirst();var inflated=new TacticalAnalysis.Variation(line.line(),line.score()+1,line.materialChange(),line.ending());
        var forged=new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of(inflated)),s.depth(),s.extraNodes(),s.horizonLeaders(),s.scoreCredits(),s.excludedRoots(),s.nonEquivalentRoots(),c);
        check.rejects("A proved plan cannot carry an invented evaluation",IllegalArgumentException.class,()->HardQuestAudit.validate(game,new ChessMatch.BotAnalysis(actual.decision(),forged)));
        var legacy=ChessMatch.start(true);var original=legacy.analyzeBot(MatchSetup.Level.HARD);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var oldShape=json.valueToTree(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode)oldShape.get("search")).remove("pawnProof");
        var restored=HardQuestAudit.read(oldShape);HardQuestAudit.validate(legacy.position(),restored);
        check.same("Legacy JSON without the optional proof field remains supported",original,restored);
        check.same("The same native reader retains the new complete proof",actual,HardQuestAudit.read(json.valueToTree(actual)));
    }

    @Test void yamlOwnsOnlyStructuralProofLimits(Checks check) {
        var source=new HashMap<Object,Object>(ChessYaml.resource("hard-pawn-promotion.yaml"));
        check.same("Loaded limits are typed and bounded",new PawnPushPolicy(true,2,24),PawnPushPolicy.read(source));
        for(var extra:List.of("fen","winning-move","extra-nodes","score-bonus")) {
            var changed=new HashMap<Object,Object>(source);changed.put(extra,"not-runtime-memory");
            check.rejects("Reject hidden solution field "+extra,IllegalArgumentException.class,()->PawnPushPolicy.read(changed));
        }
        for(var entry:List.of(Map.entry("max-pawns",0),Map.entry("max-pawns",3),Map.entry("max-plies",2),Map.entry("max-plies",33))) {
            var changed=new HashMap<Object,Object>(source);changed.put(entry.getKey(),entry.getValue());
            check.rejects("Reject unsupported proof limit "+entry,IllegalArgumentException.class,()->PawnPushPolicy.read(changed));
        }
        source.put("enabled","true");check.rejects("A string cannot enable the proof",IllegalArgumentException.class,()->PawnPushPolicy.read(source));
    }

    private static PawnPushProof.Proof proof(ChessPosition position,int plies,int cache,List<ChessPosition> history,int quiet) {
        int[] nodes={0};return new PawnPushProof(position.turn(),()->{if(++nodes[0]>200000)throw new IllegalStateException("test allowance exhausted");},cache,history).prove(position,plies,quiet);
    }
    private static ChessMatch game() {
        var game=ChessMatch.fromPosition(ChessPosition.fromFen(FEN),new MatchSetup(ChessPosition.Side.BLACK,MatchSetup.Level.HARD,false));
        for(String move:PREFIX.split(" "))game=game.play(ChessPosition.Move.fromUci(move));return game;
    }
}
