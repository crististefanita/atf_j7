package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardQuestAuditTest {
    @Test void adaptiveAndRepertoireAllowancesRemainDistinctAndStrict(Checks check) {
        var transfer = ChessYaml.resource("hard-quest-transfer.yaml");
        check.same("Accounting changes cannot relax the frozen accuracy floors",
                Map.of("mate-in-one", 500, "mate-in-two", 300, "mate-in-three", 190, "tactics", 86, "training", 39, "intermezzo", 24),
                ChessYaml.map(transfer, "minimum-solved"));
        check.same("Training retains the same frozen ID boundary", "training-048", ChessYaml.string(transfer, "training-baseline-through"));
        var game = ChessMatch.restore("3|WHITE|HARD|1|e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b1c3 d8f6 c3d5 f6d6 d5c3");
        var actual = game.analyzeBot(MatchSetup.Level.HARD);
        check.same("The old fixed ceiling would reject this real adaptive decision", true,
                actual.decision().extraNodes() > MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes());
        check.same("The adaptive ceiling comes from the active YAML policy", HardAdaptivePolicy.CURRENT.search().extraNodes(), HardQuestAudit.maximumNodes(game.position()));
        HardQuestAudit.validate(game.position(), actual);
        int maximum = HardQuestAudit.maximumNodes(game.position());
        check.rejects("One node beyond the effective adaptive maximum is refused", IllegalArgumentException.class,
                () -> HardQuestAudit.validate(game.position(), withNodes(actual, maximum + 1)));

        var start = ChessMatch.start(true);
        var book = start.analyzeBot(MatchSetup.Level.HARD);
        int bookMaximum = MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes();
        check.same("An exact repertoire decision retains its smaller explicit allowance", bookMaximum, HardQuestAudit.maximumNodes(start.position()));
        HardQuestAudit.validate(start.position(), book);
        check.rejects("The adaptive maximum cannot excuse an overspent repertoire decision", IllegalArgumentException.class,
                () -> HardQuestAudit.validate(start.position(), withNodes(book, bookMaximum + 1)));
    }

    @Test void substitutedDecisionAndPrincipalVariationCannotEarnAnAuditPass(Checks check) {
        var game = ChessMatch.start(true);
        var actual = game.analyzeBot(MatchSetup.Level.HARD);
        var decision = actual.decision();
        var search = actual.search();
        var changedDecision = new ChessMatch.BotDecision(decision.move(), decision.depth(), decision.extraNodes() + 1);
        check.rejects("A receipt cannot borrow another search's accounting", IllegalArgumentException.class,
                () -> HardQuestAudit.validate(game.position(), new ChessMatch.BotAnalysis(changedDecision, search)));
        var first = search.result().ranked().getFirst();
        var changed = new TacticalAnalysis.Variation(first.line(), first.score(), first.materialChange() + 1, first.ending());
        var forged = new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of(changed)), search.depth(), search.extraNodes());
        check.rejects("A substituted material result is refused after legal PV replay", IllegalArgumentException.class,
                () -> HardQuestAudit.validate(game.position(), new ChessMatch.BotAnalysis(decision, forged)));
        var empty = new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of()), search.depth(), search.extraNodes());
        check.rejects("A report without a native result cannot earn PASS", IllegalArgumentException.class,
                () -> HardQuestAudit.validate(game.position(), new ChessMatch.BotAnalysis(decision, empty)));
    }

    private static ChessMatch.BotAnalysis withNodes(ChessMatch.BotAnalysis original, int nodes) {
        var decision = original.decision();
        return new ChessMatch.BotAnalysis(new ChessMatch.BotDecision(decision.move(), decision.depth(), nodes),
                new TacticalAnalysis.SearchReport(original.search().result(), decision.depth(), nodes));
    }
}
