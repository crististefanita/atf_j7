package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class LearningReviewAuditTest {
    @Test void recordedCaseInventoryRejectsMissingDuplicatedOrInventedControlTails(Checks check) {
        var fresh=java.util.Set.of("ruy-lopez");var withControl=java.util.Set.of("ruy-lopez","sound-queen-offer");
        LearningReviewAudit.validateControlInventory(fresh,0);
        LearningReviewAudit.validateControlInventory(withControl,1);
        check.rejects("A new-opening game cannot invent a historical control",IllegalArgumentException.class,
                ()->LearningReviewAudit.validateControlInventory(fresh,1));
        check.rejects("Collecting the sound-sacrifice case makes its tail mandatory",IllegalArgumentException.class,
                ()->LearningReviewAudit.validateControlInventory(withControl,0));
        check.rejects("A repeated control cannot replace missing public phases",IllegalArgumentException.class,
                ()->LearningReviewAudit.validateControlInventory(withControl,2));
    }
    private static final LearningGameAudit.Identity SOURCE=new LearningGameAudit.Identity("review-fixture","a".repeat(40),"b".repeat(64),"c".repeat(64));
    private static final LearningGameAudit.Identity REVIEWER=new LearningGameAudit.Identity("review-verifier","d".repeat(40),"e".repeat(64),"f".repeat(64));
    private static final String JOURNAL="1".repeat(64),POLICY="2".repeat(64);

    @Test void completedAndCappedGamesKeepTheirOwnModesAndEveryPlayedMove(Checks check) {
        for(var prefix:List.of("e2e4 e7e5","f2f3 e7e5 g2g4 d8h4")) {
            var journal=journal(prefix);var before=journal.getLast().digest();var receipt=LearningReviewAudit.capture(journal,JOURNAL,REVIEWER,POLICY);
            var review=LearningReviewAudit.validate(receipt,journal,JOURNAL,REVIEWER,POLICY);
            check.same("Every actual White and Black move is reviewed",prefix.split(" ").length,review.turns().size());
            check.same("The original journal is untouched",before,journal.getLast().digest());
            check.same("Review preserves the actual chronology",List.of(prefix.split(" ")),review.turns().stream().map(t->t.played().uci()).toList());
            boolean capped=prefix.startsWith("e2e4");
            check.same("An unfinished line cannot claim completed-game analysis",capped?LearningReviewAudit.Mode.CAPPED_LINE:LearningReviewAudit.Mode.FINISHED_GAME,receipt.body().mode());
            if(capped)check.same("Capped lines do not invent Elo",true,review.white().rating().isEmpty()&&review.black().rating().isEmpty());
        }
    }

    @Test void partialForeignAndSubstitutedReviewReceiptsAreRejected(Checks check)throws Exception {
        var journal=journal("f2f3 e7e5 g2g4 d8h4");var receipt=LearningReviewAudit.capture(journal,JOURNAL,REVIEWER,POLICY);
        check.rejects("Another journal cannot borrow a final review",IllegalArgumentException.class,()->LearningReviewAudit.validate(receipt,journal,"3".repeat(64),REVIEWER,POLICY));
        check.rejects("Another reviewer cannot relabel the same cache",IllegalArgumentException.class,()->LearningReviewAudit.validate(receipt,journal,JOURNAL,SOURCE,POLICY));
        check.rejects("A changed review policy cannot reuse the old scores",IllegalArgumentException.class,()->LearningReviewAudit.validate(receipt,journal,JOURNAL,REVIEWER,"4".repeat(64)));
        var json=LearningGameAudit.mapper();
        for(String target:List.of("observedPlies","turns")) {
            var bad=json.valueToTree(receipt.body());var array=(com.fasterxml.jackson.databind.node.ArrayNode)(target.equals("turns")?bad.path("snapshot").path(target):bad.path(target));array.remove(array.size()-1);
            var body=json.treeToValue(bad,LearningReviewAudit.Body.class);var rehashed=new LearningReviewAudit.Envelope(body,LearningReviewAudit.digest(body));
            check.rejects("Rehashing does not legitimize missing "+target,IllegalArgumentException.class,()->LearningReviewAudit.validate(rehashed,journal,JOURNAL,REVIEWER,POLICY));
        }
        var bad=json.valueToTree(receipt.body());((com.fasterxml.jackson.databind.node.ObjectNode)bad.path("snapshot").path("turns").get(2)).put("loss",0);
        var body=json.treeToValue(bad,LearningReviewAudit.Body.class);var rehashed=new LearningReviewAudit.Envelope(body,LearningReviewAudit.digest(body));
        check.rejects("Readable scores cannot disagree with the retained native cache",IllegalArgumentException.class,()->LearningReviewAudit.validate(rehashed,journal,JOURNAL,REVIEWER,POLICY));
    }

    @Test void lastMoveExposureUsesLegalRepliesAndDoesNotBanSoundSacrifices(Checks check) {
        var caro=ChessPosition.fromFen("rnbqkbnr/pp3ppp/2p5/8/3PN3/8/PP3PPP/R1BQKBNR b KQkq - 0 6");
        var queenForPawn=LearningReviewAudit.tail(caro,List.of(move("d8d4")));
        check.same("The retained Caro tail actually takes a pawn",ChessPosition.Kind.PAWN,queenForPawn.capturedKind());
        check.same("The exposed piece is the queen",ChessPosition.Kind.QUEEN,queenForPawn.resultingKind());
        check.same("The real immediate reply removes that queen",List.of(move("d1d4")),queenForPawn.legalCapturesOfMovedPiece());
        var queenOffer=ChessPosition.fromFen("4r2r/p1p2kp1/2p2p2/2Qb4/8/2B3Nq/PPP2R1P/5RK1 b - - 0 30");
        var tail=LearningReviewAudit.tail(queenOffer,List.of(move("h3g3")));
        check.same("The sound queen offer still exposes the actual pawn reply",List.of(move("h2g3")),tail.legalCapturesOfMovedPiece());
        check.same("Exposure is geometry, not a quality rejection",ChessPosition.Kind.QUEEN,tail.resultingKind());
        var mate=queenOffer.play(move("h3g3")).play(move("h2g3")).play(move("h8h1"));
        check.same("The accepted offer genuinely mates after the pawn capture",true,mate.isCheckmate());
        var pinned=ChessPosition.fromFen("4r2k/8/8/8/6b1/8/4P3/4K3 b - - 0 1");
        check.same("The pinned pawn cannot become an invented recapture",List.of(),LearningReviewAudit.tail(pinned,List.of(move("g4f3"))).legalCapturesOfMovedPiece());
        check.rejects("An empty hint is not a terminal move",IllegalArgumentException.class,()->LearningReviewAudit.tail(queenOffer,List.of()));
    }

    private static ChessPosition.Move move(String uci){return ChessPosition.Move.fromUci(uci);}
    @Test void independentlyValidatedColdPhasesKeepTheirOwnRunsAndRejectForeignContexts(Checks check)throws Exception {
        var json=LearningGameAudit.mapper();
        var free=json.readTree("""
                {"identity":{"run":"old","source":{"revision":"same"},"binarySha256":"%s","options":{"Threads":1,"Hash":32,"UCI_LimitStrength":false,"UCI_ShowWDL":true}},
                "query":{"initialFen":"origin","history":["e2e4"],"root":null,"depth":19,"binarySha256":"%s","options":{"Threads":1,"Hash":32,"UCI_LimitStrength":false,"UCI_ShowWDL":true}},"expectedPermille":600}
                """.formatted("a".repeat(64),"a".repeat(64)));
        var forced=free.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)forced.path("identity")).put("run","new");
        ((com.fasterxml.jackson.databind.node.ObjectNode)forced.path("query")).put("root","e7e5");((com.fasterxml.jackson.databind.node.ObjectNode)forced).put("expectedPermille",200);
        check.same("The original runs stay distinct while the cold context agrees",400.0,LearningReviewAudit.oracleLoss(free,forced));
        check.same("The inherited receipt identity is untouched","old",free.path("identity").path("run").asText());
        for(String field:List.of("depth","initialFen","history","binarySha256","root")) {
            var bad=forced.deepCopy();var query=(com.fasterxml.jackson.databind.node.ObjectNode)bad.path("query");
            if(field.equals("depth"))query.put(field,17);else if(field.equals("history"))query.putArray(field).add("d2d4");else if(field.equals("root"))query.putNull(field);else query.put(field,"foreign");
            check.rejects("A different "+field+" cannot reuse a cold oracle score",IllegalArgumentException.class,()->LearningReviewAudit.oracleLoss(free,bad));
        }
        var source=forced.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)source.path("identity").path("source")).put("revision","foreign");
        check.rejects("Another measured producer is rejected",IllegalArgumentException.class,()->LearningReviewAudit.oracleLoss(free,source));
        var hash=forced.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)hash.path("query").path("options")).put("Hash",64);
        check.rejects("Another cache configuration is rejected",IllegalArgumentException.class,()->LearningReviewAudit.oracleLoss(free,hash));
    }
    private static List<LearningGameAudit.Envelope> journal(String prefix) {
        var rows=new ArrayList<LearningGameAudit.Envelope>();var start=ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,true));
        var session=new LearningGameAudit(SOURCE,"fixture","rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",start,rows::add,g->{
            var line=line(g.position());return new ChessMatch.BotAnalysis(new ChessMatch.BotDecision(line.move(),1,0),new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of(line)),1,0));
        },(board,nodes)->new TacticalAnalysis.HintSearch(List.of(line(board)),null,0,1,1));
        for(String uci:prefix.split(" ")){session.observe();session.play(move(uci),LearningGameAudit.Actor.THEORY,null);}return List.copyOf(rows);
    }
    private static TacticalAnalysis.Variation line(ChessPosition board) {
        var move=board.legalMoves().getFirst();return new TacticalAnalysis.Variation(List.of(move),0,HintAuditSession.materialChangeCp(board,board.play(move)),TacticalAnalysis.Ending.HORIZON);
    }
}
