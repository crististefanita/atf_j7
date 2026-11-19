package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class CoachingProofTest {
    private static final CoachingProof.Policy ROOM=new CoachingProof.Policy(1_000_000,17,48);
    private static final String CHAIN="3rk3/8/8/2p5/3p4/2P5/3R4/3QK3 w - - 0 1";

    @Test void continuousExchangesAreNotCutAfterOneOrTwoReplies(Checks check) {
        var board=ChessPosition.fromFen(CHAIN);var p=CoachingProof.compare(board,List.of(m("d2d4")),1,Set.of(),ROOM);
        check.same("The exchange tree completes",CoachingProof.Status.COMPLETE,p.work().status());
        check.same("The frontier really goes beyond two replies: "+p.work()+"; PV="+p.lines(),true,p.work().forcingPlies()>2&&p.work().captureReplies()>2);
        var line=p.lines().getFirst();var end=board;for(var move:line.line())end=end.play(move);
        check.same("The complete scored PV retains the actual material ledger",HintAuditSession.materialChangeCp(board,end),line.materialChange());
        check.same("Independent full minimax agrees, including optional recapture refusal",reference(board.play(m("d2d4")),0,1,12)*-1,line.score());
        check.same("No invented ordinary depth",1,p.work().ordinaryPlies());
        check.same("Allocation grows in steps rather than pre-spending its ceiling",true,p.work().nodes()<=p.work().allocatedNodes()&&p.work().allocatedNodes()-p.work().nodes()<17&&p.work().allocatedNodes()<ROOM.maxNodes());
    }

    @Test void rootUpperBoundsCannotReplaceEitherExactLeader(Checks check) {
        for(String fen:List.of(CHAIN,"7k/8/8/8/8/8/4R3/4K3 w - - 0 1")) {
            var board=ChessPosition.fromFen(fen);var expected=new ArrayList<Integer>();
            for(var move:board.legalMoves())expected.add(-reference(board.play(move),0,1,12));
            expected.sort(Comparator.reverseOrder());var actual=CoachingProof.compare(board,1,Set.of(),ROOM);
            check.same("Every root is covered, while losing bounds stay out of the final scores",true,actual.work().complete()&&actual.work().roots().size()==board.legalMoves().size());
            check.same("Both exact leaders match independent exhaustive minimax",expected.subList(0,2),actual.lines().subList(0,2).stream().map(TacticalAnalysis.Variation::score).toList());
        }
    }

    @Test void aRepeatedPathDrawDoesNotLeakIntoAnotherSearchOrOverrideMate(Checks check)throws Exception {
        var type=Class.forName("ro.atf.chess.CoachingProof$Budget");var ctor=type.getDeclaredConstructor(CoachingProof.Policy.class,int.class,ChessPosition.class,List.class);ctor.setAccessible(true);
        var search=CoachingProof.class.getDeclaredMethod("search",ChessPosition.class,int.class,int.class,int.class,int.class,type,Map.class,int.class);search.setAccessible(true);
        var board=ChessPosition.fromFen("7k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        for(var position:List.of(board,ChessPosition.fromFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1"))) {
            var budget=ctor.newInstance(ROOM,1,position,position.hasLegalMove()?position.legalMoves():List.of(m("h8g8")));
            var path=new HashMap<ChessPosition.SearchKey,Integer>();path.put(position.searchKey(),2);
            var result=search.invoke(null,position,0,2,-100001,100001,budget,path,0);var score=result.getClass().getDeclaredMethod("score");score.setAccessible(true);
            check.same("A real mate takes precedence over path repetition",position.isCheckmate()?-99998:0,score.invoke(result));
            check.same("The sibling path context is restored after evaluation",2,path.get(position.searchKey()));
        }
        check.same("Fresh history cannot inherit the mocked draw",true,CoachingProof.compare(board,List.of(m("e2e8")),1,Set.of(),ROOM).lines().getFirst().score()>0);
    }

    @Test void checkEvasionsAndSoundQueenSacrificesKeepTheirLegalProof(Checks check) {
        var board=ChessPosition.fromFen("4r2r/p1p2kp1/2p2p2/2Qb4/8/2B3Nq/PPP2R1P/5RK1 b - - 0 30");
        var proof=CoachingProof.compare(board,List.of(m("h3g3")),3,Set.of(),ROOM);
        check.same("Sound queen offer has a complete continuation",true,proof.work().complete());
        var line=proof.lines().getFirst();check.same("The proof retains the forced mate",3,line.signedMatePlies());
        var end=board;for(var move:line.line())end=end.play(move);
        check.same("Mate is on the actual legal board",true,end.isCheckmate());
        var checking=ChessPosition.fromFen("7k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        var evasion=CoachingProof.compare(checking,List.of(m("e2e8")),1,Set.of(),ROOM);
        check.same("A checked frontier searches legal evasions",true,evasion.work().checkEvasions()>0&&evasion.work().complete());
        var terminal=checking;for(var move:evasion.lines().getFirst().line())terminal=terminal.play(move);
        check.same("A nonterminal proof never stops with the opponent still in check",false,terminal.isCheck());
    }

    @Test void pinnedRecapturersAndEnPassantAreGovernedByLegality(Checks check) {
        var pinned=ChessPosition.fromFen("4k3/8/4p3/5p2/7N/8/8/K3R3 w - - 0 1");
        var free=ChessPosition.fromFen("2k5/8/4p3/5p2/7N/8/8/K3R3 w - - 0 1");
        var a=CoachingProof.compare(pinned,List.of(m("h4f5")),1,Set.of(),ROOM);var b=CoachingProof.compare(free,List.of(m("h4f5")),1,Set.of(),ROOM);
        check.same("Pinned pawn cannot recapture",false,pinned.play(m("h4f5")).legalMoves().contains(m("e6f5")));
        check.same("Real legal recapture changes the evaluated outcome",true,a.lines().getFirst().score()-b.lines().getFirst().score()>150);
        var ep=ChessPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        var proof=CoachingProof.compare(ep,List.of(m("e5d6")),1,Set.of(),ROOM);
        check.same("En passant victim is included in the actual ledger",100,proof.lines().getFirst().materialChange());
        check.same("A different en passant right is a different context",false,proof.work().positionKey().equals(ChessPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1").searchKey().toString()));
    }

    @Test void aSingleUnpaidNodeOrForcingPlyPublishesNoScore(Checks check) {
        var board=ChessPosition.fromFen(CHAIN);var root=List.of(m("d2d4"));var completed=CoachingProof.compare(board,root,1,Set.of(),ROOM);
        int exact=completed.work().nodes();
        var below=CoachingProof.compare(board,root,1,Set.of(),new CoachingProof.Policy(exact-1,1,48));
        var at=CoachingProof.compare(board,root,1,Set.of(),new CoachingProof.Policy(exact,1,48));
        check.same("One missing visited node is incomplete",CoachingProof.Status.NODE_LIMIT,below.work().status());
        check.same("No partial root score escapes",List.of(),below.lines());
        check.same("All interrupted work is charged",exact-1,below.work().nodes());
        check.same("The exact boundary completes the same scores",completed.lines(),at.lines());
        var shallow=CoachingProof.compare(board,root,1,Set.of(),new CoachingProof.Policy(1_000_000,1000,1));
        check.same("A forcing safety limit is distinct from a completed exchange",CoachingProof.Status.FORCING_LIMIT,shallow.work().status());
        check.same("A forcing cutoff also returns no scored lines",List.of(),shallow.lines());
    }

    @Test void anIncompleteProofCannotBecomeAHintOrAReviewGrade(Checks check) {
        var board=ChessPosition.fromFen(CHAIN);var line=new TacticalAnalysis.Variation(List.of(m("d2d4")),100,100,TacticalAnalysis.Ending.HORIZON);
        var seed=new TacticalAnalysis.HintSearch(List.of(line),null,100,1,1);
        var result=TacticalAnalysis.certifyHints(board,seed,new CoachingProof.Policy(1,1,48));
        check.same("No public hint, comparison or final depth is published",List.of(List.of(),0,true),List.of(result.lines(),result.selectedDepth(),result.comparison()==null));
        check.same("The incomplete work remains available",CoachingProof.Status.NODE_LIMIT,result.proof().status());
        check.same("Ordinary and reserve work are both retained",101,result.totalNodes());
        check.rejects("A partial proof cannot be put into a graded turn",IllegalArgumentException.class,()->new GameReview.Turn(1,board.turn(),m("d2d4"),GameReview.Grade.BEST,900,900,0,900,1,line.line(),line.line(),0,result.proof()));
        check.rejects("An incomplete public hint cannot smuggle scored variants",IllegalArgumentException.class,()->new TacticalAnalysis.HintSearch(List.of(line),null,100,1,1,result.proof()));
    }

    @Test void cancellationCannotLeaveAReusablePartialValue(Checks check) {
        var board=ChessPosition.fromFen(CHAIN);var before=CoachingProof.compare(board,1,Set.of(),ROOM);
        try {Thread.currentThread().interrupt();check.rejects("Cancellation interrupts actual search",CancellationException.class,()->CoachingProof.compare(board,1,Set.of(),ROOM));}
        finally {Thread.interrupted();}
        check.same("A subsequent clean search has identical values and charged work",before,CoachingProof.compare(board,1,Set.of(),ROOM));
        check.rejects("A foreign board cannot borrow this proof",IllegalArgumentException.class,()->before.work().validate(board.play(m("e1f1")),1));
    }

    @Test void retainsActualReserveWorkAndHistoricalReceiptShapes(Checks check)throws Exception {
        var board=ChessPosition.fromFen(CHAIN);var proof=CoachingProof.compare(board,1,Set.of(),ROOM);var ranked=proof.lines();var work=proof.work().afterOrdinary(77);
        var hint=new TacticalAnalysis.HintSearch(List.of(ranked.getFirst()),new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(ranked),1,0),77,1,1,work);
        var portable=LearningGameAudit.mapper().readValue(LearningGameAudit.mapper().writeValueAsBytes(hint),TacticalAnalysis.HintSearch.class);
        check.same("The receipt retains every reserve field",hint,portable);
        var audited=new HintAuditSession.Search(hint.lines(),ranked,77,1,1,1,work);HardHintAuditedGame.validateHint(board,audited);
        check.same("The ordinary ceiling and additional reserve are explicit",77+work.nodes(),work.totalNodes());
        var legacy=new TacticalAnalysis.HintSearch(List.of(ranked.getFirst()),null,77,1,1);
        var json=LearningGameAudit.mapper().valueToTree(legacy);
        check.same("Old receipt shape is preserved, without inserting null proof fields",false,json.has("proof"));
        check.same("Old comparison null is still retained for its original hash",true,json.has("comparison")&&json.path("comparison").isNull());
        check.same("Old shape decodes without a fabricated certificate",null,LearningGameAudit.mapper().treeToValue(json,TacticalAnalysis.HintSearch.class).proof());
        check.rejects("A real receipt cannot borrow another board's forcing work",IllegalArgumentException.class,()->HintAuditSession.validateProof(ChessPosition.initial(),work,77,1));
        var plan=new HintAuditPolicy.Run("forcing-receipt","forcing","e2e4 e7e5",1,List.of(50000),List.of(1));
        var identity=new HintAuditSession.Identity("forcing","forcing-receipt","0f7f08ce226f4de8126898cd336098a112c83cdd","a".repeat(64),"b".repeat(64));
        var study=new HintAuditSession(identity,plan);
        var receipt=study.advance((position,limit)->{
            var actual=CoachingProof.compare(position,1,Set.of(),ROOM);var facts=actual.work().afterOrdinary(77);
            return new HintAuditSession.Search(List.of(actual.lines().getFirst()),actual.lines(),77,1,1,1,facts);
        });
        check.same("The raw phase actually contains the reserve receipt",true,receipt.decision().proof()!=null);
        var reopened=HintAuditSession.resume(identity,plan,study.jsonl(),HintAuditSession.digest(study.jsonl()));
        check.same("Offline resumption preserves the whole receipt without recomputation",study.receipts(),reopened.receipts());
        try(var in=getClass().getResourceAsStream("/learning-frame-before-coaching.yaml")) {
            var historical=LearningGameAudit.mapper().readValue(in,LearningGameAudit.Envelope.class);
            check.same("Original historical frame identity is pinned","6d19422b9e856458e77cb71691e507eb35b54f473f78c40b83a8bab5ad16149f",historical.digest());
            check.same("A real pre-change frame retains its original digest",historical.digest(),LearningGameAudit.digest(historical.body()));
            var frame=historical.body().before();LearningGameAudit.validate(List.of(historical),frame.identity(),frame.caseId(),frame.initialFen());
            check.same("Legacy admission does not manufacture a current forcing receipt",null,frame.hint().proof());
        }
    }

    @Test void reviewCacheRetainsTheCompleteWorkAfterRestart(Checks check) {
        var board=ChessPosition.fromFen(CHAIN);var result=CoachingProof.compare(board,1,Set.of(),ROOM);var best=result.lines().getFirst();var match=ChessMatch.fromPosition(board,new MatchSetup(board.turn(),MatchSetup.Level.HARD,true)).play(best.move());
        var empty=new GameReview.Player(OptionalInt.empty(),Optional.empty(),0,Map.of());var turn=new GameReview.Turn(1,board.turn(),best.move(),GameReview.Grade.BEST,500,500,0,500,1,best.line(),best.line(),0,result.work().afterOrdinary(77));
        var review=new GameReview(List.of(turn),null,0,500,empty,empty);String cache=GameReviewCodec.encode(match,review);
        var restored=GameReviewCodec.decodeLine(match,cache,GameReviewCodec.checkpointDigest(match));
        check.same("Restart keeps the scores, complete proof, limits and all legal PV plies",review,restored);
        check.same("Review proof is still source-position bound",board.searchKey().toString(),restored.turns().getFirst().proof().positionKey());
    }

    @Test void invalidYamlCannotEnableUnboundedOrUnfundedSearch(Checks check) {
        var root=new HashMap<String,Object>();root.put("schema",1);root.put("max-nodes",1000);root.put("allocation-step",100);root.put("max-forcing-plies",48);
        check.same("A finite configurable reserve loads",1000,CoachingProof.Policy.read(root).maxNodes());
        for(String key:List.of("max-nodes","allocation-step","max-forcing-plies")) {var invalid=new HashMap<>(root);invalid.put(key,0);check.rejects("Reject zero "+key,IllegalArgumentException.class,()->CoachingProof.Policy.read(invalid));}
        var invalid=new HashMap<>(root);invalid.put("typo",true);check.rejects("Unknown settings cannot silently change policy",IllegalArgumentException.class,()->CoachingProof.Policy.read(invalid));
    }

    @Test void anAlreadyClosedLineDoesNotTriggerAGlobalChangeOfTheCoachingPolicy(Checks check) {
        var board=ChessPosition.initial();var line=new TacticalAnalysis.Variation(List.of(m("e2e4"),m("e7e5"),m("g1f3")),0,0,TacticalAnalysis.Ending.HORIZON);
        check.same("Quiet closed theory does not demand a forcing reserve",false,CoachingProof.needed(board,List.of(line)));
        var chain=ChessPosition.fromFen(CHAIN);var capture=new TacticalAnalysis.Variation(List.of(m("d2d4")),100,100,TacticalAnalysis.Ending.HORIZON);
        check.same("An actually unanswered recapture does demand reserve",true,CoachingProof.needed(chain,List.of(capture)));
    }

    @Test void theLegacyReviewRemainsReadableWithoutInventingAProof(Checks check)throws Exception {
        try(var in=getClass().getResourceAsStream("/legacy-review-v1.yaml")) {
            var fixture=LearningGameAudit.mapper().readTree(in);String encoded=fixture.path("encoded").asText();
            check.same("Original v1 payload is unchanged",fixture.path("encodedSha256").asText(),HardLeagueAudit.digest(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var match=ChessMatch.restore(fixture.path("checkpoint").asText());var review=GameReviewCodec.decode(match,encoded);
            check.same("Every historical move is still available",4,review.turns().size());new GameReviewTimeline(match,review);
            check.same("Reading legacy data does not retroactively qualify its tactical search",true,review.turns().stream().allMatch(t->t.proof()==null));
        }
    }

    @Test void nativeConsumersKeepIncompleteVerificationDistinctFromGameOver(Checks check)throws Exception {
        var root=ProjectRoot.locate();String hint=java.nio.file.Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/MatchAnalysisView.java"));
        String review=java.nio.file.Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/GameReviewDialog.java"));
        check.same("The hint binds its incomplete receipt to a distinct presentation",true,hint.contains("analyzed.proof().filter(p->!p.complete()).isPresent()")&&hint.contains("incomplete?R.string.match_analysis_incomplete:R.string.match_analysis_ended"));
        check.same("Review handles the typed incomplete result before generic failures",true,review.contains("catch (ro.atf.chess.CoachingProof.Incomplete incomplete)")&&review.contains("label.setText(R.string.game_review_incomplete)"));
        for(String folder:List.of("values","values-ro")) {
            String strings=java.nio.file.Files.readString(root.resolve("android/app/src/main/res/"+folder+"/strings.xml"));
            check.same("Both localized incomplete states exist in "+folder,true,strings.contains("name=\"match_analysis_incomplete\"")&&strings.contains("name=\"game_review_incomplete\""));
        }
        for(String name:List.of("CoachingProof.java","TacticalAnalysis.java")) {
            String source=java.nio.file.Files.readString(root.resolve("chess-core/src/main/java/ro/atf/chess/"+name));
            check.same("Shared mobile search avoids API 35 List first/last calls in "+name,false,source.contains(".getFirst(")||source.contains(".getLast("));
        }
    }

    private static Move m(String uci){return Move.fromUci(uci);}
    private static int reference(ChessPosition board,int left,int distance,int forcing) {
        var legal=board.legalMoves();if(legal.isEmpty())return board.isCheck()?distance-100000:0;
        if(left==0&&forcing==0)throw new AssertionError("Independent test horizon did not settle");
        boolean check=board.isCheck();var moves=left==0&&!check?board.legalCapturesAndPromotions():legal;
        int best=left==0&&!check?TacticalAnalysis.positionValue(board,board.turn()):-100001;
        for(var m:moves)best=Math.max(best,-reference(board.play(m),Math.max(0,left-1),distance+1,left==0?forcing-1:forcing));return best;
    }
}
