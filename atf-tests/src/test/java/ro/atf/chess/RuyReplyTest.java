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
class RuyReplyTest {
    private static final String EXPECTED="da2389e7413f4338e5506b44ac8aaa5c2e8682a22e169fe7b21093ef436a79f9";
    private static final String HISTORY="e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=LearningGameAudit.mapper();

    @Test void anInteriorBishopOfferCannotSupportTheOptimisticNg5Preview(Checks check)throws Exception {
        var data=fixture();var board=ChessMatch.restore("2|analysis|"+HISTORY).position();
        var old=JSON.treeToValue(data.path("legacyHint"),TacticalAnalysis.HintSearch.class);
        check.same("A safe last square does not hide the unsupported interior reply",true,CoachingProof.needed(board,old.lines()));
        var now=TacticalAnalysis.verifiedHints(board);var roots=now.comparison().result().ranked();
        check.same("The public recommendation now has the freely defended score",m("b1c3"),now.lines().getFirst().move());
        var knight=roots.stream().filter(v->v.move().equals(m("f3g5"))).findFirst().orElseThrow();
        check.same("The Ng5 comparison searches castling rather than relying on the offer",m("e8g8"),knight.line().get(1));
        check.same("No +67 score or material bonus is restored",true,knight.score()<0&&knight.materialChange()==0&&now.lines().getFirst().score()<0);
        var work=now.proof();work.validate(board,now.selectedDepth());
        check.same("The interior offer receives all-capture closure, not just same-square recaptures",48,work.tacticalPlies());
        check.same("The old leader was replaced only after charged equal-turn confirmation",m("b1c3"),work.confirmation().selected());
        check.same("Ordinary and additional work remain distinct",true,now.extraNodes()==200000&&work.nodes()>0&&work.nodes()<=work.allocatedNodes()&&work.allocatedNodes()<=work.ceiling());
        var teacher=rows(data.path("cold"));
        for(int d:List.of(17,19))check.same("The selected root meets its independent H"+d+" floor",true,
                StockfishStudyAudit.loss(teacher.get("ruy-parent-free-H"+d),teacher.get("ruy-parent-b1c3-H"+d))<50);
    }

    @Test void freeDefensesRefuteBe6InBothColorsWithoutBanningSoundDevelopment(Checks check)throws Exception {
        var data=fixture();var teacher=rows(data.path("cold"));
        for(var label:List.of("ruy-reply","ruy-mirror-reply")) {
            var q=teacher.get(label+"-free-H19").path("query");var board=position(q);
            String castle=label.contains("mirror")?"e1g1":"e8g8",offer=label.contains("mirror")?"c1e3":"c8e6";
            var proof=CoachingProof.compare(board,List.of(m(offer),m(castle)),5,Set.of(),CoachingProof.CURRENT);
            check.same(label+" completes with all replies free",true,proof.work().complete());
            check.same(label+" has no fixed-refutation or root-order dependency",m(castle),proof.lines().getFirst().move());
            check.same(label+" the offered bishop loses material in the completed line",true,proof.lines().get(1).materialChange()<0);
            for(int d:List.of(17,19))check.same(label+" independently separates the two choices at H"+d,true,
                    StockfishStudyAudit.loss(teacher.get(label+"-free-H"+d),teacher.get(label+"-"+offer+"-H"+d))>150
                    &&StockfishStudyAudit.loss(teacher.get(label+"-free-H"+d),teacher.get(label+"-"+castle+"-H"+d))<50);
        }
        for(int d:List.of(17,19))check.same("The same Be6 is sound in its Italian context H"+d,true,
                StockfishStudyAudit.loss(teacher.get("italian-sound-development-free-H"+d),teacher.get("italian-sound-development-c8e6-H"+d))<50);
    }

    @Test void incompleteInteriorVerificationPublishesNeitherScoreNorDecorativeLine(Checks check)throws Exception {
        var data=fixture();var board=ChessMatch.restore("2|analysis|"+HISTORY).position();
        var old=JSON.treeToValue(data.path("legacyHint"),TacticalAnalysis.HintSearch.class);
        var full=TacticalAnalysis.certifyHints(board,old,CoachingProof.CURRENT);
        var policy=new CoachingProof.Policy(full.proof().nodes()-1,1,48);
        var shortProof=TacticalAnalysis.certifyHints(board,old,policy);
        check.same("The actual required node is missing",CoachingProof.Status.NODE_LIMIT,shortProof.proof().status());
        check.same("The full spent allowance is retained",policy.maxNodes(),shortProof.proof().nodes());
        check.same("The optimistic old line cannot survive failed verification",true,
                shortProof.lines().isEmpty()&&shortProof.comparison()==null&&shortProof.selectedDepth()==0);
        check.rejects("A proof from the parent is not valid after Ng5",IllegalArgumentException.class,
                ()->full.proof().validate(board.play(m("f3g5")),full.selectedDepth()));
        var forcing=TacticalAnalysis.certifyHints(board,old,new CoachingProof.Policy(5000000,25000,1));
        check.same("A forcing limit cannot turn a different-square pending capture into a quiet frontier",CoachingProof.Status.FORCING_LIMIT,forcing.proof().status());
        check.same("A forcing-limited comparison also withholds its final score",true,forcing.lines().isEmpty()&&forcing.comparison()==null);
    }

    @Test void everyOfferedCorrectedPlyHasIndependentEvidenceAndDiagnosticsStayDistinct(Checks check)throws Exception {
        var data=fixture();var plan=data.path("corrected").path("plan");var teacher=rows(data.path("corrected"));int count=0;
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();var actual=TacticalAnalysis.verifiedHints(board);
        var selected=plan.path("lines").get(0);var expected=new ArrayList<String>();selected.path("path").forEach(x->expected.add(x.asText()));
        check.same("Teacher evidence belongs to the actual offered PV",expected,actual.lines().getFirst().line().stream().map(Move::uci).toList());
        check.same("The rejected Ng5 comparison is not a second public hint",1,actual.lines().size());
        for(var phase:selected.path("phases")) {
            boolean large=true;
            for(int d:List.of(17,19)) {
                var free=teacher.get(phase.path("free").path(""+d).asText());var forced=teacher.get(phase.path("forced").path(""+d).asText());
                check.same("The forced reply retains the actual hypothetical context",phase.path("history"),forced.path("query").path("history"));
                large&=StockfishStudyAudit.loss(free,forced)>=150;
            }
            check.same("The complete corrected PV has no large loss confirmed at both H17/H19 at "+phase.path("move").asText(),false,large);
            count++;
        }
        check.same("Every offered move including both opposing replies is independently graded",5,count);
        var move=actual.lines().getFirst().move();var game=ChessMatch.fromPosition(board,new MatchSetup(board.turn(),MatchSetup.Level.HARD,true)).play(move);
        var empty=new GameReview.Player(OptionalInt.empty(),Optional.empty(),0,Map.of());var line=actual.lines().getFirst().line();
        var turn=new GameReview.Turn(1,board.turn(),move,GameReview.Grade.BEST,500,500,0,500,actual.selectedDepth(),line,line,0,actual.proof());
        var review=new GameReview(List.of(turn),null,0,500,empty,empty);var encoded=GameReviewCodec.encode(game,review);
        check.same("The full-capture scope and work counters survive a real cache round trip",review,
                GameReviewCodec.decodeLine(game,encoded,GameReviewCodec.checkpointDigest(game)));
    }

    @Test void anUnacceptedOfferCannotReplaceTheQualifiedScandinavianAttack(Checks check)throws Exception {
        try(var in=getClass().getResourceAsStream("/ruy-transfer-witnesses.yaml")) {
            var data=JSON.readTree(in);
            check.same("The independently confirmed transfer counterexamples stay immutable",
                    "ee5c196159014c9846830e0515c91751a704d5bfa8b46bf4f009722d04e0ea1d",
                    HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)));
            var teacher=rows(data);
            for(var sample:data.path("positions")) {
                var row=sample.path("row");var board=ChessPosition.fromFen(row.path("initialFen").asText());
                for(var move:row.path("history"))board=board.play(JSON.treeToValue(move,Move.class));
                var c=sample.path("case");var expected=m(c.path("old").asText());
                var actual=c.path("kind").asText().equals("hint")?TacticalAnalysis.verifiedHints(board).lines().getFirst().move()
                        :TacticalAnalysis.review(board,JSON.treeToValue(row.path("oldReview").path("played"),Move.class),Set.of(),GameReviewPolicy.CURRENT.search).best().move();
                check.same("An attacked piece which the PV does not take cannot erase the qualified attack "+c.path("ply").asInt()+" "+c.path("kind").asText(),expected,actual);
                for(int d:List.of(17,19))check.same("The preserved move and rejected broad-scope regression have independent H"+d+" evidence",true,
                        StockfishStudyAudit.loss(teacher.get(c.path("pairs").path("free-"+d).asText()),teacher.get(c.path("pairs").path("old-"+d).asText()))<50
                        &&StockfishStudyAudit.loss(teacher.get(c.path("pairs").path("free-"+d).asText()),teacher.get(c.path("pairs").path("new-"+d).asText()))>150);
            }
        }
    }

    static JsonNode fixture()throws Exception {
        try(var in=RuyReplyTest.class.getResourceAsStream("/ruy-reply-witnesses.yaml")) {
            var data=JSON.readTree(in);String hash=HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8));
            if(!EXPECTED.equals(hash))throw new IllegalArgumentException("Changed free-reply witnesses: "+hash);
            rows(data.path("cold"));rows(data.path("corrected"));return data;
        }
    }
    static Map<String,JsonNode> rows(JsonNode data) {
        var plan=data.has("plan")?data.path("plan"):data;var map=new HashMap<String,JsonNode>();
        for(var env:data.path("raw")){var r=env.path("body");if(!env.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(r).getBytes(StandardCharsets.UTF_8)))||map.putIfAbsent(r.path("key").asText(),r)!=null)throw new IllegalArgumentException("Changed or duplicated raw reply");}
        for(var q:plan.path("queries"))StockfishStudyAudit.validate(q.path("query"),plan.path("identity"),map.get(q.path("key").asText()));
        if(map.isEmpty()||map.size()!=plan.path("queries").size())throw new IllegalArgumentException("Missing free-reply phase");return map;
    }
    private static ChessPosition position(JsonNode q){var p=ChessPosition.fromFen(q.path("initialFen").asText());for(var move:q.path("history"))p=p.play(m(move.asText()));return p;}
    private static Move m(String text){return Move.fromUci(text);}
}
