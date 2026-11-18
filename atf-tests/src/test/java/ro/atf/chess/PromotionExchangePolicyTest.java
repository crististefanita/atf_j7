package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PromotionExchangePolicyTest {
    private static final String INITIAL="5r2/7P/5K2/8/2P2k2/P7/8/8 w - - 1 69";
    private static final String CURRENT="3r4/6KP/8/8/2P2k2/P7/8/8 w - - 3 70";

    @Test void closesThePromotionBeforeReplacingTheCompletedNativeLeader(Checks check) {
        var game=ChessMatch.fromPosition(ChessPosition.fromFen(INITIAL),new MatchSetup(ChessPosition.Side.BLACK,MatchSetup.Level.HARD,false));
        check.same("The preceding legal check evasion remains intact","f6g7",game.analyzeBot(MatchSetup.Level.HARD).decision().move().uci());
        game=game.play(ChessPosition.Move.fromUci("f6g7")).play(ChessPosition.Move.fromUci("f8d8"));String checkpoint=game.checkpoint();
        var analysis=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game.position(),analysis);
        check.same("The selected move promotes immediately","h7h8q",analysis.decision().move().uci());
        check.same("Confirmation really completes H6",6,analysis.decision().depth());
        check.same("Actual common-horizon score is positive",true,analysis.search().result().ranked().getFirst().score()>0);
        check.same("The selected principal variation contains the verified exchange",true,PromotionExchangePolicy.CURRENT.matches(game.position(),analysis.search().result().ranked().getFirst()));
        check.same("Native search preserves the exact full history",checkpoint,game.checkpoint());
        var mirror=ChessMatch.fromPosition(ChessPosition.fromFen("8/8/p7/2p2K2/8/8/6kp/3R4 b - - 3 70"),new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,false));
        var other=mirror.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(mirror.position(),other);
        check.same("Black uses the same structural rule","h2h1q",other.decision().move().uci());
        check.same("Black confirmation completes the same horizon",6,other.decision().depth());
    }

    @Test void recognizesOnlyARealClosedExchangeAndActualMaterialGain(Checks check) {
        var position=ChessPosition.fromFen(CURRENT);var closed=line("h7h8q d8h8 g7h8 f4e4 h8g7 e4d4");
        check.same("The loaded reusable pattern is enabled",true,PromotionExchangePolicy.CURRENT.enabled());
        check.same("The observed capture and legal recapture qualify",true,PromotionExchangePolicy.CURRENT.matches(position,closed));
        check.same("Rook underpromotion uses the same exchange, not queen-specific square data",true,PromotionExchangePolicy.CURRENT.matches(position,line("h7h8r d8h8 g7h8")));
        check.same("An unclosed promotion is not evidence",false,PromotionExchangePolicy.CURRENT.matches(position,line("h7h8q d8h8")));
        check.same("A declined exchange stays outside this scoped override",false,PromotionExchangePolicy.CURRENT.matches(position,line("h7h8q f4e5 h8g8")));
        check.same("Leaving the captured promotion unrecaptured stays outside",false,PromotionExchangePolicy.CURRENT.matches(position,line("h7h8q d8h8 g7f6")));
        check.same("Four actual pawn units are present",true,new PromotionExchangePolicy(true,4).matches(position,closed));
        check.same("A fabricated ledger cannot supply a fifth pawn",false,new PromotionExchangePolicy(true,5).matches(position,closed));
        check.same("The diagnostic off switch rejects the pattern",false,new PromotionExchangePolicy(false,1).matches(position,closed));
        check.rejects("An illegal king jump cannot masquerade as recapture",IllegalArgumentException.class,()->PromotionExchangePolicy.CURRENT.matches(ChessPosition.fromFen("3r4/7P/5K2/8/2P2k2/P7/8/8 w - - 3 70"),line("h7h8q d8h8 f6h8")));
    }

    @Test void yamlRejectsCaseLookupsUnknownModesAndInvalidThresholds(Checks check) {
        var values=new HashMap<Object,Object>(ChessYaml.resource("hard-promotion-exchange.yaml"));
        check.same("The active policy is exactly its typed YAML snapshot",PromotionExchangePolicy.CURRENT,PromotionExchangePolicy.read(values));
        for(var extra:List.of("fen","winning-move","extra-nodes","oracle")) {
            var changed=new HashMap<Object,Object>(values);changed.put(extra,"not-a-runtime-lookup");
            check.rejects("Reject ungoverned field "+extra,IllegalArgumentException.class,()->PromotionExchangePolicy.read(changed));
        }
        for(var bad:List.of(0,9,"1")) {
            var changed=new HashMap<Object,Object>(values);changed.put("minimum-material-gain-pawns",bad);
            check.rejects("Reject invalid gain "+bad,IllegalArgumentException.class,()->PromotionExchangePolicy.read(changed));
        }
        values.put("enabled","true");check.rejects("A string cannot enable the feature",IllegalArgumentException.class,()->PromotionExchangePolicy.read(values));
    }

    private static TacticalAnalysis.Variation line(String uci) {
        return new TacticalAnalysis.Variation(java.util.Arrays.stream(uci.split(" ")).map(ChessPosition.Move::fromUci).toList(),1,999999,TacticalAnalysis.Ending.HORIZON);
    }
}
