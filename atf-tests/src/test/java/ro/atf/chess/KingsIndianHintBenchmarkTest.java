package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Open native quality goals; a correct evidence validator must not qualify the engine. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class KingsIndianHintBenchmarkTest {
    @Test void hintMustResolveTheCentralBreakWithFreeOpposingReplies(Checks check) throws Exception {
        var match = KingsIndianWitnessTest.match(KingsIndianWitnessTest.fixture());
        check.same("The public hint must recover the independently better e6 rather than Bd7", "e7e6",
                TacticalAnalysis.verifiedHints(match.position()).lines().getFirst().move().uci());
    }

    @Test void hardMustResolveTheFirstActualLossWithoutBorrowingTheHint(Checks check) throws Exception {
        var match = KingsIndianWitnessTest.match(KingsIndianWitnessTest.fixture());
        check.same("The actual native decision needs its own qualified e6 calculation", "e7e6", match.chooseBotMove().uci());
    }
}
