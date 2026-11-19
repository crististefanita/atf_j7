package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class CoachingScopeTest {
    @Test void closedFrontiersPreserveEligibilityAndTheTruthOfTheNewScores(Checks check)throws Exception {
        var raw=teacher();
        var board=ChessMatch.restore("2|analysis|"+HintCandidateCoverageTest.HISTORY).position();
        var hint=MatchHint.analyze(board);hint.revealNext();var proof=hint.proof().orElseThrow();var choice=proof.confirmation();
        check.same("The completed defense remains playable from the bulb",Move.fromUci("b8d7"),hint.moveForTap(board,-1,hint.first().move().to()));
        check.same("The retained score is the actual new value, never the obsolete tie",choice.incumbentScore(),hint.first().score());
        check.same("The rejection records the actual configured gain threshold",MatchAiPolicy.CURRENT.hint.maxLossCp(),choice.minGainCp());
        check.same("Both horizons are evidence; neither may corroborate its own reversal",true,choice.challengerScore()>choice.incumbentScore()&&choice.earlierChallengerScore()>choice.earlierIncumbentScore()&&choice.challengerScore()-choice.incumbentScore()<=choice.minGainCp());
        check.same("No display calls a locally lower score the stronger comparison",true,hint.compare(board).isEmpty());
        for(int d:List.of(17,19)) {
            double retained=StockfishStudyAudit.loss(raw.get("pin-free-H"+d),raw.get("pin-b8d7-H"+d));
            double rejected=StockfishStudyAudit.loss(raw.get("pin-free-H"+d),raw.get("pin-b8c6-H"+d));
            check.same("Cold H"+d+" confirms the qualified defense and the harmful small-model reversal",true,retained<50&&rejected>250);
        }
        for(String history:List.of("e2e4 c7c5 g1f3 d7d6 d2d4","e2e4 c7c6 d2d4")) {
            var search=TacticalAnalysis.verifiedHints(ChessMatch.restore("2|analysis|"+history).position());
            check.same("Closing the frontier cannot manufacture an additional equivalent offer",1,search.lines().size());
            check.same("Ordinary and reserve work remain separately charged",search.extraNodes()+search.proof().nodes(),search.totalNodes());
        }
    }

    @Test void anOrdinaryOpeningCanGrowPastOneMillionWithoutPublishingPartialEvidence(Checks check) {
        var board=ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6 f1c4 a7a5").position();
        var hint=TacticalAnalysis.verifiedHints(board);var proof=hint.proof();
        check.same("A finite reserve grows to close the actual opening",true,proof.complete()&&proof.nodes()>1_000_000&&proof.allocatedNodes()<proof.ceiling());
        check.same("A real exit from theory still has a legal engine hint",true,board.legalMoves().contains(hint.lines().getFirst().move()));
        var limited=TacticalAnalysis.certifyHints(board,hint,new CoachingProof.Policy(1000,100,48));
        check.same("The very same opening reports its insufficient proof budget",CoachingProof.Status.NODE_LIMIT,limited.proof().status());
        check.same("An interrupted comparison never resurrects the completed seed score",List.of(),limited.lines());
        check.same("No interrupted comparison is exposed",null,limited.comparison());
    }

    @Test void aDrawCannotSuppressACompletedWinningAlternative(Checks check) {
        var board=ChessPosition.fromFen("7k/8/8/8/8/8/4R3/4K3 w - - 0 1");var draw=Move.fromUci("e2e7");
        var review=TacticalAnalysis.review(board,draw,Set.of(draw),new MatchAiPolicy.Search(4,2000));
        check.same("An offered immediate draw has its actual zero value",0,review.played().score());
        check.same("The draw is not allowed to override a genuinely superior completed root",true,!review.best().move().equals(draw)&&review.best().score()>0);
        var pirc=ChessMatch.restore("2|analysis|e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4").position();
        var root=Move.fromUci("f8g7");var graded=TacticalAnalysis.review(pirc,root,Set.of(),GameReviewPolicy.CURRENT.search);
        check.same("The normal review keeps the independently verified repertoire root",root,graded.best().move());
    }

    @Test void teacherReceiptCannotBorrowAnotherRootOrRewriteTheScore(Checks check)throws Exception {
        var rows=teacher();var row=rows.get("pin-b8d7-H19");var changed=row.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed).put("score",100000);
        check.rejects("A rewritten summary cannot replace raw Stockfish output",IllegalArgumentException.class,()->StockfishStudyAudit.validate(row.path("query"),row.path("identity"),changed));
        check.rejects("The Nc6 phase cannot qualify Nbd7",IllegalArgumentException.class,()->StockfishStudyAudit.validate(rows.get("pin-b8c6-H19").path("query"),row.path("identity"),row));
    }

    @Test void anExactReviewTieRetainsTheQualifiedRootAndItsNewClosedScore(Checks check)throws Exception {
        String history="c2c4 e7e5 b1c3 g8f6 g2g3 d7d5 c4d5 f6d5 f1g2 d5b6";
        var board=ChessMatch.restore("2|analysis|"+history).position();var root=Move.fromUci("g1f3");
        var review=TacticalAnalysis.review(board,root,Set.of(),GameReviewPolicy.CURRENT.search);
        check.same("An arbitrary lexical root order cannot change an exact tie",root,review.best().move());
        check.same("The recommendation keeps the actually completed played score",review.played().score(),review.best().score());
        check.same("The new score is closed rather than the former optimistic +651",21,review.best().score());
        try(var in=getClass().getResourceAsStream("/coaching-review-tie-witnesses.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);String sha=HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8));
            require(sha.equals("5dea442782315d6aff89ae97e260e4d9e812107b47fd3c92ae236743190b5e50"),"Changed review tie evidence");
            var rows=new HashMap<String,JsonNode>();
            for(var e:data.path("raw")) {
                var r=e.path("body");require(HardLeagueAudit.digest(HardLeagueAudit.canonical(r).getBytes(StandardCharsets.UTF_8)).equals(e.path("digest").asText()),"Changed raw review phase");
                StockfishStudyAudit.validate(r.path("query"),r.path("identity"),r);
                require(r.path("query").path("history").equals(LearningGameAudit.mapper().valueToTree(Arrays.asList(history.split(" ")))),"Foreign review history");
                rows.put(r.path("query").path("root").asText("free")+"-"+r.path("query").path("depth").asInt(),r);
            }
            require(rows.size()==6,"Missing review phase");
            for(int d:List.of(17,19))check.same("H"+d+" protects the original zero-loss root against a lexical tie reversal",true,
                    StockfishStudyAudit.loss(rows.get("free-"+d),rows.get("g1f3-"+d))<2&&StockfishStudyAudit.loss(rows.get("free-"+d),rows.get("e2e4-"+d))>100);
        }
    }

    private static Map<String,JsonNode> teacher()throws Exception {
        try(var in=CoachingScopeTest.class.getResourceAsStream("/coaching-scope-witnesses.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);var rows=new HashMap<String,JsonNode>();
            require(HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals("c4f176e5f158c8f51168f4568002955d1d7ad17727e47846457061b7f13d3ef5"),"Changed portable teacher evidence");
            for(var e:data.path("raw")) {
                var r=e.path("body");String sha=HardLeagueAudit.digest(HardLeagueAudit.canonical(r).getBytes(StandardCharsets.UTF_8));
                require(sha.equals(e.path("digest").asText()),"Changed teacher receipt");
                StockfishStudyAudit.validate(r.path("query"),data.path("identity"),r);
                require(rows.putIfAbsent(r.path("key").asText(),r)==null,"Repeated teacher phase");
            }
            require(rows.size()==12,"Missing cold control");return rows;
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
