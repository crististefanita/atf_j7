package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardAdaptivePolicyTest {
    @Test void defaultsProvideAReusableReserveAfterOneHundredThousand(Checks check) {
        var policy = HardAdaptivePolicy.CURRENT;
        check.same("Hard base allowance", 100_000, policy.baseNodes());
        check.same("Hard maximum allowance", 200_000, policy.maxNodes());
        check.same("Hard target remains six plies", 6, policy.search().maxPlies());
        check.same("Basic allowance is unchanged", 0, MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.BASIC).extraNodes());
        check.same("Advanced allowance is unchanged", 10_000, MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.ADVANCED).extraNodes());
    }

    @Test void malformedPoliciesAreRejectedBeforeSearch(Checks check) {
        for (var entry : Map.of("schema", 2, "base-extra-nodes", 0, "max-extra-nodes", 99_999,
                "max-plies", 8, "minimum-completed-plies", 7, "cache-observations", 0,
                "cold-cache-per-mille", 1001).entrySet()) {
            var source = settings();
            source.put(entry.getKey(), entry.getValue());
            check.rejects("Reject " + entry.getKey(), IllegalArgumentException.class, () -> HardAdaptivePolicy.read(source));
        }
        var typed = settings();
        typed.put("enabled", "true");
        check.rejects("Boolean enabled is typed", IllegalArgumentException.class, () -> HardAdaptivePolicy.read(typed));
        var extra = settings();
        extra.put("unexpected-option", 1);
        check.rejects("Unexpected keys are rejected", IllegalArgumentException.class, () -> HardAdaptivePolicy.read(extra));
    }

    @Test void completedSearchWithWarmCacheStopsAtBaseAndStaysStopped(Checks check) {
        var budget = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, false);
        budget.completed(5);
        for (int i = 0; i < 4_096; i++) budget.lookup(true);
        check.same("Base visit is allowed", true, budget.allow(99_999));
        check.same("A complete target with useful cache stops", false, budget.allow(100_000));
        budget.completed(3);
        check.same("The decision cannot restart after stopping", false, budget.allow(100_000));
    }

    @Test void incompleteDepthExtendsInTheSameBudgetUpToTheMaximum(Checks check) {
        var budget = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, false);
        check.same("Incomplete fifth ply receives a reserve", true, budget.allow(100_000));
        budget.completed(5);
        check.same("Accepted continuation retains the reserve", true, budget.allow(150_000));
        check.same("Maximum visit is forbidden", false, budget.allow(200_000));
        check.same("Reserve was activated", true, budget.extended());
    }

    @Test void dangerAndColdCacheAreEvidenceForContinuing(Checks check) {
        var danger = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, true);
        danger.completed(5);
        check.same("Threatened king receives reserve", true, danger.allow(100_000));
        var cold = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, false);
        cold.completed(5);
        for (int i = 0; i < 4_096; i++) cold.lookup(false);
        check.same("Measured cold cache receives reserve", true, cold.allow(100_000));
        var unmeasured = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, false);
        unmeasured.completed(5);
        check.same("Missing observations alone do not grant reserve", false, unmeasured.allow(100_000));
    }

    @Test void coldCacheThresholdAndMaximumHaveExactBoundaries(Checks check) {
        var policy = new HardAdaptivePolicy(true, 100, 200, 6, 5, 1000, 10);
        var atLimit = new HardAdaptivePolicy.Budget(policy, false);
        var belowLimit = new HardAdaptivePolicy.Budget(policy, false);
        atLimit.completed(5);
        belowLimit.completed(5);
        for (int i = 0; i < 1000; i++) {
            atLimit.lookup(i < 10);
            belowLimit.lookup(i < 9);
        }
        check.same("Exactly one percent reuse is sufficient", false, atLimit.allow(100));
        check.same("Less than one percent receives reserve", true, belowLimit.allow(100));
        check.same("The same budget permits its final node", true, belowLimit.allow(199));
        check.same("The maximum remains strict", false, belowLimit.allow(200));
    }

    @Test void kingFileRiskRequiresAnUnblockedEnemyHeavyPiece(Checks check) {
        check.same("King in check", true, HardAdaptivePolicy.kingInDanger(
                ChessPosition.fromFen("4k3/8/8/8/8/8/8/4R1K1 b - - 0 1")));
        check.same("One friendly blocker on the king file", true, HardAdaptivePolicy.kingInDanger(
                ChessPosition.fromFen("4k3/8/8/8/4n3/8/8/4R1K1 b - - 0 1")));
        check.same("Two friendly blockers suppress file risk", false, HardAdaptivePolicy.kingInDanger(
                ChessPosition.fromFen("4k3/4p3/8/8/4n3/8/8/4R1K1 b - - 0 1")));
        check.same("A bishop cannot attack along the file", false, HardAdaptivePolicy.kingInDanger(
                ChessPosition.fromFen("4k3/8/8/8/4n3/8/8/4B1K1 b - - 0 1")));
        check.same("Ordinary starting board", false, HardAdaptivePolicy.kingInDanger(ChessPosition.initial()));
    }

    @Test void eachDecisionHasItsOwnCacheEvidenceAndReserve(Checks check) {
        var first = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, true);
        var second = new HardAdaptivePolicy.Budget(HardAdaptivePolicy.CURRENT, false);
        first.completed(5);
        second.completed(5);
        check.same("First decision extends", true, first.allow(100_000));
        check.same("Other decision has no inherited trigger", false, second.allow(100_000));
        check.same("A disabled policy restores the original foreground", MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD),
                new HardAdaptivePolicy(false, 100_000, 200_000, 6, 5, 4096, 10).search());
    }

    @Test void exchangeVerificationRemainsReservedWhenBaseSearchStops(Checks check) {
        var policy = new HardAdaptivePolicy(true, 100, 200, 6, 5, 1000, 10);
        var budget = new HardAdaptivePolicy.Budget(policy, false);
        budget.completed(5);
        check.same("Search can consume its final unreserved visit", true, budget.allow(92, 7));
        check.same("The seven verification visits count toward the base", false, budget.allow(93, 7));
        check.same("The reserve remains within the base ceiling", 100, 93 + 7);
        var dangerous = new HardAdaptivePolicy.Budget(policy, true);
        check.same("Critical search can extend while preserving verification escrow", true, dangerous.allow(93, 7));
        check.same("Verification also counts toward the final maximum", false, dangerous.allow(193, 7));
    }

    @Test void completeHorizonProofRespectsTheGrantedAllowance(Checks check) throws Exception {
        var policy = new HardAdaptivePolicy(true, 100, 200, 6, 5, 1000, 10);
        var stable = new HardAdaptivePolicy.Budget(policy, false);
        stable.completed(5);
        check.same("A safe complete H5 proof stops after seven remaining base visits", 7,
                visitsBeforeStop(stable, 93, 107));
        var tactical = new HardAdaptivePolicy.Budget(policy, false);
        tactical.completed(5);
        tactical.exchangeProof(true);
        check.same("A pending exchange grants reserve only while nodes are actually consumed", false, tactical.extended());
        check.same("The nested proof shares the exact final maximum", 107, visitsBeforeStop(tactical, 93, 107));
        check.same("Grant is recorded when the proof crosses the base", true, tactical.extended());
        tactical.exchangeProof(false);
    }

    private static int visitsBeforeStop(HardAdaptivePolicy.Budget adaptive, int priorVisits, int allowance) throws Exception {
        var budgetType = java.util.Arrays.stream(TacticalAnalysis.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("SearchBudget")).findFirst().orElseThrow();
        var constructor = budgetType.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        var budget = constructor.newInstance(allowance, 2);
        var context = budgetType.getDeclaredField("adaptive");
        var offset = budgetType.getDeclaredField("adaptiveOffset");
        var visit = budgetType.getDeclaredMethod("visit");
        context.setAccessible(true);
        offset.setAccessible(true);
        visit.setAccessible(true);
        context.set(budget, adaptive);
        offset.setInt(budget, priorVisits);
        int visited = 0;
        try {
            while (visited <= allowance) {
                visit.invoke(budget);
                visited++;
            }
        } catch (java.lang.reflect.InvocationTargetException stopped) {
            if (!stopped.getCause().getClass().getSimpleName().equals("BudgetExhausted")) throw stopped;
        }
        return visited;
    }

    private static HashMap<Object, Object> settings() {
        return new HashMap<>(ChessYaml.resource("hard-adaptive.yaml"));
    }
}
