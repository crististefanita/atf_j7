package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class RookRootReentryTest {
    @Test void nativeKingEntryRetainsMateAgainstEveryLegalDefense(Checks check) {
        verify(check,"4r3/2p2R2/2p5/2Pk4/KP1P4/P4P2/8/8 b - - 2 54","d5c4");
        verify(check,"8/8/p4p2/kp1p4/2pK4/2P5/2P2r2/4R3 w - - 2 54","d4c5");
    }

    @Test void nativeRookEntryRetainsMateAgainstEveryLegalDefense(Checks check) {
        verify(check,"1r6/6R1/3p4/KP1Rp3/2r1P3/2k2P2/8/8 b - - 2 45","c4a4");
        verify(check,"8/8/2K2p2/2R1p3/kp1rP3/3P4/6r1/1R6 w - - 2 45","c5a5");
    }

    private static void verify(Checks check,String fen,String expected) {
        var position=ChessPosition.fromFen(fen);var game=ChessMatch.fromPosition(position,new MatchSetup(position.turn().other(),MatchSetup.Level.HARD,false));
        var audit=NativeMateAudit.verify(check,game,3,fen);
        check.same("Ordinary Hard receives no declared mate objective or curriculum key",expected,audit.firstMove().uci());
        check.same("Every legal opposing reply was explored, not only the canonical defense",true,audit.defensiveBranches()>1);
        check.same("The continuation uses actual bounded native choices",true,audit.nativeDecisions()>1);
    }
}
