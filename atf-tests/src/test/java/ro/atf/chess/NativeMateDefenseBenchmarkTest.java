package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class NativeMateDefenseBenchmarkTest {
    @Test void ordinaryHardForcesEveryPublishedMateAgainstAllLegalDefenses(Checks check) {
        int puzzles=0,decisions=0,defenses=0,terminals=0;
        for(var chapter:List.of(QuestCatalog.load(),QuestCatalog.loadMateInTwo(),QuestCatalog.loadMateInThree())) {
            for(var quest:chapter) {
                var position=quest.position();var game=ChessMatch.fromPosition(position,new MatchSetup(position.turn().other(),MatchSetup.Level.HARD,false));
                var audit=NativeMateAudit.verify(check,game,quest.playerMoves(),quest.id());
                // Consult the educational contract only after the ordinary opponent's complete audit.
                check.same(quest.id()+" actual initial move satisfies its existing goal",true,quest.assess(audit.firstMove()).continues()||quest.assess(audit.firstMove()).solved());
                puzzles++;decisions+=audit.nativeDecisions();defenses+=audit.defensiveBranches();terminals+=audit.terminalBranches();
            }
            check.same("All "+chapter.size()+" current mating exercises are nonempty",true,!chapter.isEmpty());
        }
        System.out.println("NATIVE_MATE_DEFENSE puzzles="+puzzles+" nativeDecisions="+decisions+" defenses="+defenses+" terminalBranches="+terminals);
        check.same("Every current mating exercise was visited",QuestCatalog.load().size()+QuestCatalog.loadMateInTwo().size()+QuestCatalog.loadMateInThree().size(),puzzles);
        check.same("The benchmark includes actual opposing branches",true,defenses>0&&terminals>puzzles&&decisions>puzzles);
    }
}
