package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardLearningWitnessTest {
    @Test void permanentLossesRetainTheirCompleteLegalContextAndRawTeacher(Checks check)throws Exception {
        var cases=HardLearningWitnesses.load();check.same("Two distinct retained opening losses",2,cases.size());
        for(var witness:cases) {
            check.same(witness.id()+" has independent loss at both horizons",true,witness.loss(witness.originalMove(),17)>=150&&witness.loss(witness.originalMove(),19)>=150);
            check.same("The historical loss is a legal move, not a malformed replay",true,witness.game().position().legalMoves().contains(ChessPosition.Move.fromUci(witness.originalMove())));
        }
    }
    @Test void aForeignHistoryOrRewrittenOracleCannotRedefineTheDefect(Checks check)throws Exception {
        var witness=HardLearningWitnesses.load().getFirst();var foreign=witness.input().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)foreign).put("fen",ChessPosition.initial().toString());
        check.rejects("Another board cannot inherit the loss",IllegalArgumentException.class,()->HardLearningWitnesses.read(foreign));
        var rewritten=witness.input().deepCopy();var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)rewritten.path("raw").get(0).path("body");receipt.put("score",receipt.path("score").asInt()+999);
        check.rejects("A changed score cannot explain the old game",IllegalArgumentException.class,()->HardLearningWitnesses.read(rewritten));
    }
}
