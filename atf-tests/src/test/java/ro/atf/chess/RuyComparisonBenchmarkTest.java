package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** A diagnostic comparison is not a certified optimal continuation. Keep its quality debt executable. */
@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class RuyComparisonBenchmarkTest {
    @Test void comparedContinuationsMeetTheSameIndependentFloorAsOfferedHints(Checks check)throws Exception {
        var data=RuyReplyTest.fixture().path("corrected");var rows=RuyReplyTest.rows(data);int large=0;
        var board=ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6").position();
        var now=TacticalAnalysis.verifiedHints(board);
        for(var line:data.path("plan").path("lines")) {
            var path=new ArrayList<String>();line.path("path").forEach(x->path.add(x.asText()));
            check.same("The audited diagnostic path is still the actual comparison",true,
                    now.comparison().result().ranked().stream().anyMatch(v->v.line().stream().map(ChessPosition.Move::uci).toList().equals(path)));
            for(var phase:line.path("phases")) {
                boolean bad=true;for(int d:List.of(17,19))bad&=StockfishStudyAudit.loss(rows.get(phase.path("free").path(""+d).asText()),rows.get(phase.path("forced").path(""+d).asText()))>=150;
                if(bad)large++;
            }
        }
        check.same("Weak compared replies must not be represented as optimal playable lines",0,large);
    }
}
