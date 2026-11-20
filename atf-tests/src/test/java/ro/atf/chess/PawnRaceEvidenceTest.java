package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Independent, retained counterexamples; none of these keys is available to production search. */
@AtfComponentTest @ExtendWith(ExtentAudit.class)
class PawnRaceEvidenceTest {
    private static JsonNode fixture(String name,String hash)throws Exception {
        try(var stream=PawnRaceEvidenceTest.class.getResourceAsStream("/"+name+".yaml")) {
            if(stream==null)throw new IllegalArgumentException("Missing retained pawn evidence");
            var data=LearningGameAudit.mapper().readTree(stream);
            if(!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals(hash))
                throw new IllegalArgumentException("Changed retained pawn evidence");
            return data;
        }
    }
    private static JsonNode witnesses()throws Exception {
        return fixture("pawn-race-witnesses","1e25523465203f106bc79e5a8b5e5503cd375e58aacf0bb7ebff9c1d528699c3");
    }
    @Test void nativeAndHintRecommendationsKeepTheirColdIndependentWinningDistinction(Checks check)throws Exception {
        for(var input:witnesses().path("cases")) {
            var w=HardLearningWitnesses.read(input);var game=w.game();String checkpoint=game.checkpoint();
            var nativeSearch=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,nativeSearch);
            var hint=TacticalAnalysis.verifiedHints(game.position(),200000);
            check.same(w.id()+" native search retains its ordinary ceiling",true,nativeSearch.decision().extraNodes()<=200000);
            for(int d:List.of(17,19)) {
                check.same(w.id()+" original losing decision H"+d,true,w.loss(w.originalMove(),d)>=150);
                check.same(w.id()+" native H"+nativeSearch.decision().depth()+" at teacher H"+d,0.0,w.loss(nativeSearch.decision().move().uci(),d));
                check.same(w.id()+" maximum hint at teacher H"+d,0.0,w.loss(hint.lines().getFirst().move().uci(),d));
            }
            check.same("Independent evidence cannot apply a move to the actual game",checkpoint,game.checkpoint());
        }
    }
    @Test void aForeignTeacherOrRewrittenReplyCannotJustifyTheGeometry(Checks check)throws Exception {
        var original=witnesses().path("cases").get(0);var changed=original.deepCopy();
        ((ObjectNode)changed.path("raw").get(0).path("body")).put("score",9999);
        var modified=changed;check.rejects("Raw teacher scores retain their envelope",IllegalArgumentException.class,()->HardLearningWitnesses.read(modified));
        changed=original.deepCopy();((ObjectNode)changed.path("teacherIdentity")).put("run","another-run");
        var foreign=changed;check.rejects("A phase from another run is not current support",IllegalArgumentException.class,()->HardLearningWitnesses.read(foreign));
        changed=original.deepCopy();((ObjectNode)changed).put("initialFen","8/8/8/8/8/8/5k2/7K w - - 0 1");
        var origin=changed;check.rejects("A changed origin cannot borrow old winning phases",IllegalArgumentException.class,()->HardLearningWitnesses.read(origin));
    }
    @Test void theObservedKnightUnderpromotionRemainsASoundMateAgainstEveryLegalReply(Checks check)throws Exception {
        var data=fixture("pawn-race-underpromotion","622f82f12cef355cc3d1c04cc38236848acf7d61c07282e74a22a977809d1236");
        var frame=LearningGameAudit.mapper().convertValue(data.path("frame"),LearningGameAudit.Frame.class);
        var game=ChessMatch.fromPosition(ChessPosition.fromFen(frame.initialFen()),frame.setup());
        for(var move:frame.history())game=game.play(move);
        LearningGameAudit.validateFrame(frame,game);
        check.same("The actual producer remains the observed producer", "0548e18ecf6d2039ce51203b043302d0a4aa04d3",frame.identity().revision());
        var proof=NativeMateAudit.verify(check,game,2,"Sound supporting knight promotion");
        check.same("A superficially smaller promotion must not be banned as an absurd sacrifice","g2g1n",proof.firstMove().uci());
        check.same("At least one real opposing defense was explored",true,proof.defensiveBranches()>0);
    }
}
