package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class LearningReviewAuditTest {
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
