package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchMaterialTest {
    @Test void replaysCapturesAndAdvantageAtEveryHistoryFrame(Checks check) {
        var match = ChessMatch.restore("1|e2e4 d7d5 e4d5 d8d5");
        var empty = new MatchMaterial.Seat(List.of(), 0);
        var pawn = new MatchMaterial.Captured(Kind.PAWN, 1);
        check.same("No captures at the starting position", new MatchMaterial(empty, empty), match.materialAt(0));
        check.same("White took a pawn and leads by one", new MatchMaterial(new MatchMaterial.Seat(List.of(pawn), 1), empty), match.materialAt(3));
        var equal = new MatchMaterial.Seat(List.of(pawn), 0);
        check.same("Both captures remain visible after the recapture", new MatchMaterial(equal, equal), match.materialAt(4));
        check.same("Restoring an old checkpoint keeps captures in the correct frame", match.materialAt(3),
                ChessMatch.restore(match.checkpoint()).materialAt(3));
    }

    @Test void countsEnPassantAndPromotedPiecesWithoutConfusingThemWithMaterialBalance(Checks check) {
        var ep = ChessMatch.restore("1|e2e4 a7a6 e4e5 d7d5 e5d6");
        check.same("En passant records the pawn removed behind the destination",
                new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(Kind.PAWN, 1)), 1), ep.materialAt(5).white());
        var promotion = ChessMatch.fromPosition(ChessPosition.fromFen("r6k/1P6/8/8/8/8/8/7K w - - 0 1"))
                .play(Move.fromUci("b7b8n"));
        check.same("Promotion changes the material balance but adds no capture",
                new MatchMaterial.Seat(List.of(), 2), promotion.materialAt(1).black());
        var recapture = promotion.play(Move.fromUci("a8b8"));
        check.same("Capturing the promoted knight records a knight", new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(Kind.KNIGHT, 1)), 5),
                recapture.materialAt(2).black());
    }
}
