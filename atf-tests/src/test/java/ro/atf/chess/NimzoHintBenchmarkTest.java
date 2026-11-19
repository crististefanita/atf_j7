package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** An open native quality goal: independent evidence is not a repaired search. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class NimzoHintBenchmarkTest {
    @Test void publicAlternativesCannotOfferTheIndependentlyLosingBe7(Checks check) throws Exception {
        var data = NimzoWitnessTest.fixture();
        var hint = TacticalAnalysis.verifiedHints(NimzoWitnessTest.position(data), LearningGameAudit.MAX_HINT_NODES);
        var unqualified = ChessPosition.Move.fromUci("f8e7");
        check.same("Every public alternative needs a sound free-reply evaluation; Be7 loses 249.5 per mille at H17 and H19",
                false, hint.lines().stream().anyMatch(line -> line.move().equals(unqualified)));
    }
}
