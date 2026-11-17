package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintTransferRegressionTest {
    @Test void aShallowSicilianSingletonStillReceivesIndependentRootVerification(Checks check) {
        // Frozen opening-hints-v1 collection: sicilian-ranked, decision index 7.
        var board = ChessMatch.restore("3|WHITE|HARD|1|e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f1b5 c8d7 f3d4 d7b5 d4b5 b8c6").position();
        for (int nodes : List.of(100_000, 200_000)) {
            var decision = TacticalAnalysis.verifiedHints(board, nodes);
            var first = decision.lines().getFirst();
            check.same("The independently checked Sicilian knight retreat remains first at " + nodes,
                    move("b1c3"), first.move());
            check.same("The shallow seed is verified at a completed deeper common horizon at " + nodes, true,
                    decision.comparison() != null && decision.selectedDepth() > decision.seedDepth()
                            && decision.comparison().depth() == decision.selectedDepth());
            check.same("The scored comparison and public continuation select the same root at " + nodes,
                    first.move(), decision.comparison().result().ranked().getFirst().move());
            check.same("The entire Sicilian decision stays within its requested node ceiling at " + nodes, true,
                    decision.extraNodes() > 0 && decision.extraNodes() <= nodes);
            var replay = HintPreview.openDefended(board, first).orElseThrow();
            check.same("The Sicilian continuation replays from the frozen checkpoint without adding a defense at " + nodes,
                    List.of(first.line().size(), false), List.of(replay.length(), replay.addedDefense()));
        }
    }

    @Test void theItalianOpeningRetainsAnIndependentlyCheckedDevelopingReply(Checks check) {
        // Frozen opening-hints-v1 collection: italian-ranked, decision index 1, at the production ceiling.
        var board = ChessMatch.restore("3|WHITE|HARD|1|e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b1c3").position();
        var decision = TacticalAnalysis.verifiedHints(board, 200_000);
        var first = decision.lines().getFirst();
        // Independent free-root and forced-root SF19 checks support both knight developments.
        check.same("The Italian opening chooses an independently checked knight development", true,
                Set.of(move("g8f6"), move("g8e7")).contains(first.move()));
        check.same("The Italian decision charges all work to the unchanged production node ceiling", true,
                decision.extraNodes() > 0 && decision.extraNodes() <= 200_000);
        var replay = HintPreview.openDefended(board, first).orElseThrow();
        check.same("The Italian continuation starts at the actual frozen checkpoint and adds no unscored defense",
                List.of(first.line().size(), false), List.of(replay.length(), replay.addedDefense()));
        if (decision.comparison() != null) {
            check.same("A published Italian comparison shares the selected completed horizon",
                    decision.selectedDepth(), decision.comparison().depth());
            check.same("A published Italian comparison supports the same first move",
                    first.move(), decision.comparison().result().ranked().getFirst().move());
        }
    }

    @Test void aSoundBishopSacrificeMatesAgainstEveryLegalDefenseAndNeedsItsSupport(Checks check) {
        var board = ChessPosition.fromFen("2kr3r/1pq2pp1/p1pbb3/B6p/Q2P2n1/5N2/PP3PPP/2R1RBK1 b - - 2 20");
        var decision = TacticalAnalysis.verifiedHints(board, 200_000);
        var first = decision.lines().getFirst();
        check.same("The independently checked bishop sacrifice proves mate in three", List.of(move("d6h2"), 3),
                List.of(first.move(), first.signedMatePlies()));
        check.same("The bishop witness stays within the unchanged whole-decision node ceiling", true,
                decision.extraNodes() > 0 && decision.extraNodes() <= 200_000);
        var replay = HintPreview.openDefended(board, first).orElseThrow();
        replay.seek(replay.length());
        check.same("The actual scored bishop continuation reaches checkmate", true, replay.position().isCheckmate());
        var offered = board.play(first.move());
        check.same("The offered bishop leaves both legal defenses available", Set.of(move("f3h2"), move("g1h1")),
                Set.copyOf(offered.legalMoves()));
        var replies = Map.of(move("f3h2"), move("c7h2"), move("g1h1"), move("g4f2"));
        for (var defense : offered.legalMoves()) {
            var defended = offered.play(defense);
            var response = TacticalAnalysis.verifiedHints(defended, 200_000);
            var mate = response.lines().getFirst();
            check.same("The mating reply is independently recomputed after " + defense.uci(), replies.get(defense), mate.move());
            check.same("Every legal defense reaches actual checkmate", true, defended.play(mate.move()).isCheckmate());
            check.same("Recomputing the reply respects its own whole-decision ceiling after " + defense.uci(), true,
                    response.extraNodes() > 0 && response.extraNodes() <= 200_000);
        }
        var unsupported = ChessPosition.fromFen("2kr3r/1pq2pp1/p1pbbn2/B6p/Q2P4/5N2/PP3PPP/2R1RBK1 b - - 2 20");
        var failed = unsupported.play(move("d6h2")).play(move("f3h2")).play(move("c7h2"));
        check.same("Relocating the supporting knight gives the king a legal queen capture", true,
                failed.legalMoves().contains(move("g1h2")));
        check.same("The unsupported continuation is not checkmate", false, failed.isCheckmate());
        var control = TacticalAnalysis.verifiedHints(unsupported, 200_000);
        check.same("The changed support position cannot reuse the mating claim", 0,
                control.lines().getFirst().signedMatePlies());
        check.same("The negative support control respects the production node ceiling", true,
                control.extraNodes() > 0 && control.extraNodes() <= 200_000);
    }

    private static Move move(String uci) { return Move.fromUci(uci); }
}
