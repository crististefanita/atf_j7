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
class FrenchCoachingTest {
    private static final String HISTORY="e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 d5e4 c3e4 f8e7 g5f6 g7f6 c2c3 f6f5 e4g3 d8d6 g1f3 h7h5 f3e5 h5h4";
    private static final String EXPECTED_TEACHER="74676b8be62031e9bd80bcae56ff95daac2708047d1579c78a17897f0000a672";

    @Test void bothCoachesRejectTheRefutedCheckWithFreeRepliesInBothColors(Checks check)throws Exception {
        var teacher=teacher();
        for(var label:List.of("french-original-root","french-mirror-root")) {
            var query=teacher.get(label+"-free-H19").path("query");var board=position(query);String checkpoint=board.searchKey().toString();
            var expected=Move.fromUci(label.contains("mirror")?"e4c5":"e5c4");var bad=Move.fromUci(label.contains("mirror")?"f8b4":"f1b5");
            var hint=TacticalAnalysis.verifiedHints(board);var review=TacticalAnalysis.review(board,bad,Set.of(),GameReviewPolicy.CURRENT.search);
            check.same(label+" bulb and review agree on the knight tempo",List.of(expected,expected),List.of(hint.lines().getFirst().move(),review.best().move()));
            check.same(label+" ordinary budget remains separate",200000,hint.extraNodes());
            for(var work:List.of(hint.proof(),review.proof())) {
                work.validate(board,6);var facts=work.counterplay();
                check.same(label+" contains the two completed deeper comparisons",true,work.complete()&&facts!=null&&facts.accepted()&&facts.earlierPlies()==5&&work.ordinaryPlies()==6);
                check.same(label+" actual material losses refute the check at both horizons",true,facts.earlierIncumbent().materialChange()<=-facts.minMaterialLoss()&&facts.incumbent().materialChange()<=-facts.minMaterialLoss());
                check.same(label+" the final legal score is not the old optimistic +7",true,facts.incumbent().score()<0&&facts.challenger().score()>facts.incumbent().score()+facts.minGainCp());
                check.same(label+" every additional comparison is charged",true,work.nodes()>0&&work.nodes()<=work.allocatedNodes()&&work.allocatedNodes()<=work.ceiling());
            }
            check.same(label+" the hint comparison uses the same scored recommendation",expected,hint.comparison().result().ranked().getFirst().move());
            check.same(label+" review score is the actually chosen complete line",review.proof().counterplay().challenger(),review.best());
            check.same(label+" played Bb5+ is worse and is never BEST",true,review.played().score()<review.best().score());
            for(int d:List.of(17,19)) {
                var free=teacher.get(label+"-free-H"+d);var next=teacher.get(label+"-"+expected.uci()+"-H"+d);var old=teacher.get(label+"-"+bad.uci()+"-H"+d);
                check.same(label+" H"+d+" independently confirms the repaired root",true,StockfishStudyAudit.loss(free,next)<50&&StockfishStudyAudit.loss(free,old)>150);
            }
            check.same(label+" no move or history is applied by coaching",checkpoint,board.searchKey().toString());
        }
    }

    @Test void oneMissingProofNodeCannotRestoreTheRejectedH4Score(Checks check) {
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();var roots=List.of(m("f1b5"),m("e5c4"));
        var complete=TacticalAnalysis.confirmCheckingHint(board,roots,roots.getFirst(),200000,4,CoachingProof.CURRENT);
        var budget=new CoachingProof.Policy(complete.proof().nodes()-1,1,48);
        var stopped=TacticalAnalysis.confirmCheckingHint(board,roots,roots.getFirst(),200000,4,budget);
        check.same("The actual last required node is missing",CoachingProof.Status.NODE_LIMIT,stopped.proof().status());
        check.same("All work including the interrupted second comparison is retained",budget.maxNodes(),stopped.proof().nodes());
        check.same("No hint score or comparison survives incomplete confirmation",List.of(List.of(),0,true),List.of(stopped.lines(),stopped.selectedDepth(),stopped.comparison()==null));
        check.rejects("Review cannot grade with an interrupted free-reply comparison",CoachingProof.Incomplete.class,
                ()->TacticalAnalysis.review(board,roots.getFirst(),Set.of(),GameReviewPolicy.CURRENT.search,new CoachingProof.Policy(1,1,48)));
    }

    @Test void counterplayCannotBeFabricatedFromASmallGainOrAnotherMaterialLedger(Checks check) {
        var old=line("f1b5",-100,-220);var next=line("e5c4",-90,0);
        check.rejects("A ten-centipawn gain cannot bypass the existing threshold",IllegalArgumentException.class,
                ()->new CoachingProof.Counterplay(5,old,next,old,next,next.move(),-90,25,220));
        var later=line("e5c4",100,0);var quiet=line("f1b5",0,0);
        check.rejects("An unrefuted material ledger cannot authorize the exception",IllegalArgumentException.class,
                ()->new CoachingProof.Counterplay(5,quiet,later,quiet,later,later.move(),100,25,220));
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();
        check.same("A proved mate does not demand this different mechanism",false,CoachingProof.shortCheckingLine(board,new TacticalAnalysis.Variation(List.of(m("f1b5")),99997,0,TacticalAnalysis.Ending.CHECKMATE),4));
        var traxler=ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 f8c5 g5f7").position();
        check.same("The explicit sound Traxler capture keeps its established sacrifice evaluation",false,
                CoachingProof.shortCheckingLine(traxler,line("c5f2",0,-220),4));
        var noPawn=ChessPosition.fromFen("rnb1k2r/pp2bp2/3qp3/4Np2/3P3p/2P3N1/PP3PPP/R2QKB1R w KQkq - 0 11");
        var proof=CoachingProof.counterplay(noPawn,List.of(m("f1b5"),m("e5c4")),m("f1b5"),2,CoachingProof.CURRENT,Set.of(),Set.of());
        check.same("The missing c-pawn control completes",true,proof.work().complete());
        for(var variation:proof.lines()){var p=noPawn;for(var move:variation.line()){check.same("No missing pawn is invented as a fixed defense",false,move.equals(m("c7c6")));p=p.play(move);}}
    }

    @Test void counterplaySurvivesRestartAndTheActualV2ReceiptKeepsItsShape(Checks check)throws Exception {
        var board=ChessMatch.restore("2|analysis|"+HISTORY).position();var bad=m("f1b5");
        var proof=CoachingProof.counterplay(board,List.of(bad,m("e5c4")),bad,2,CoachingProof.CURRENT,Set.of(),Set.of(bad));
        var best=proof.lines().stream().filter(v->v.move().equals(proof.work().counterplay().selected())).findFirst().orElseThrow();
        var played=proof.lines().stream().filter(v->v.move().equals(bad)).findFirst().orElseThrow();
        var game=ChessMatch.fromPosition(board,new MatchSetup(board.turn(),MatchSetup.Level.HARD,true)).play(bad);
        var empty=new GameReview.Player(OptionalInt.empty(),Optional.empty(),0,Map.of());
        var turn=new GameReview.Turn(1,board.turn(),bad,GameReview.Grade.MISTAKE,480,280,200,280,6,best.line(),played.line(),0,proof.work());
        var review=new GameReview(List.of(turn),null,0,500,empty,empty);String encoded=GameReviewCodec.encode(game,review);
        check.same("Version 3 retains all four scored PVs and the chosen root",review,GameReviewCodec.decodeLine(game,encoded,GameReviewCodec.checkpointDigest(game)));
        try(var in=getClass().getResourceAsStream("/legacy-review-v2.yaml")) {
            var old=LearningGameAudit.mapper().readTree(in);String cache=old.path("encoded").asText();
            check.same("The real v2 bytes remain immutable",old.path("encodedSha256").asText(),HardLeagueAudit.digest(cache.getBytes(StandardCharsets.UTF_8)));
            var previous=ChessMatch.fromPosition(ChessPosition.fromFen(old.path("fen").asText()),new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,true)).play(m(old.path("played").asText()));
            var restored=GameReviewCodec.decodeLine(previous,cache,GameReviewCodec.checkpointDigest(previous));var work=restored.turns().getFirst().proof();
            check.same("A legacy proof never acquires fabricated counterplay",null,work.counterplay());
            check.same("Serializing the restored proof preserves its original hash-relevant shape",old.path("proof"),LearningGameAudit.mapper().valueToTree(work));
        }
    }

    @Test void freshTeacherPhasesRejectChangedScoresAndForeignPositions(Checks check)throws Exception {
        var rows=teacher();var row=rows.get("french-original-root-e5c4-H19");var altered=row.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)altered).put("score",99999);
        check.rejects("Changed summaries cannot qualify a different hint",IllegalArgumentException.class,()->StockfishStudyAudit.validate(row.path("query"),row.path("identity"),altered));
        check.rejects("A mirrored phase cannot replace the original full history",IllegalArgumentException.class,()->StockfishStudyAudit.validate(rows.get("french-mirror-root-e4c5-H19").path("query"),row.path("identity"),row));
        check.same("The sound queen sacrifice is still a teacher-proved mate",true,rows.get("sound-queen-sacrifice-h3g3-H19").path("scoreType").asText().equals("mate"));
    }

    private static Map<String,JsonNode> teacher()throws Exception {
        try(var in=FrenchCoachingTest.class.getResourceAsStream("/french-coaching-witnesses.yaml")) {
            var data=LearningGameAudit.mapper().readTree(in);String hash=HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8));
            if(!hash.equals(EXPECTED_TEACHER))throw new IllegalArgumentException("Changed cold counterplay evidence: "+hash);
            var rows=new HashMap<String,JsonNode>();
            for(var env:data.path("raw")){var r=env.path("body");if(!HardLeagueAudit.digest(HardLeagueAudit.canonical(r).getBytes(StandardCharsets.UTF_8)).equals(env.path("digest").asText()))throw new IllegalArgumentException("Changed raw counterplay phase");rows.put(r.path("key").asText(),r);}
            for(var q:data.path("queries")){var r=rows.get(q.path("key").asText());StockfishStudyAudit.validate(q.path("query"),data.path("identity"),r);}
            if(rows.size()!=32||data.path("queries").size()!=32)throw new IllegalArgumentException("Missing counterplay phases");return rows;
        }
    }
    private static ChessPosition position(JsonNode q){var p=ChessPosition.fromFen(q.path("initialFen").asText());for(var m:q.path("history"))p=p.play(m(m.asText()));return p;}
    private static TacticalAnalysis.Variation line(String uci,int score,int material){return new TacticalAnalysis.Variation(List.of(m(uci)),score,material,TacticalAnalysis.Ending.HORIZON);}
    private static Move m(String uci){return Move.fromUci(uci);}
}
