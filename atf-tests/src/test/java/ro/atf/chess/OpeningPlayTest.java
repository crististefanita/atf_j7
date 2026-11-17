package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningPlayTest {
    @Test void everyProfileUsesDocumentedTheoryFromDifferentHumanOpenings(Checks check) {
        var rows = List.of(List.of("", "e2e4"), List.of("e2e4", "e7e5"), List.of("d2d4", "d7d5"),
                List.of("c2c4", "e7e5"), List.of("g1f3", "d7d5"), List.of("e2e4 b8c6", "d2d4"));
        for (var row : rows) {
            var match = match(row.getFirst());
            for (var level : MatchSetup.Level.values()) {
                var decision = match.botDecision(level);
                check.same(level + " follows documented theory after " + row.getFirst(), row.get(1), decision.move().uci());
                check.same("The selected reply is legal from the actual board", true,
                        match.position().legalMoves().contains(decision.move()));
            }
        }
    }

    @Test void differentBranchesAreRecomputedFromTheActualPosition(Checks check) {
        for (var row : List.of(List.of("e2e4 c7c5", "g1f3"), List.of("e2e4 e7e6", "d2d4"),
                List.of("e2e4 c7c6", "d2d4"), List.of("d2d4 g8f6 c2c4 g7g6", "b1c3"))) {
            var board = match(row.getFirst()).position();
            for (var level : MatchSetup.Level.values()) {
                check.same(level + " rebranches after " + row.getFirst(), row.get(1),
                        OpeningRepertoire.CURRENT.playReply(board, level).uci());
            }
        }
        check.same("Theory selection is deterministic on the same saved position",
                match("e2e4").botDecision(), match("e2e4").botDecision());
    }

    @Test void theoryEndsAtTheActualExitAndTheProfileSearchTakesOver(Checks check) {
        var game = match("e2e4 e7e5 g1f3 b8c6 f1c4 a7a5");
        for (var level : MatchSetup.Level.values()) {
            check.same(level + " has no invented theory after an off-book reply", null,
                    OpeningRepertoire.CURRENT.playReply(game.position(), level));
            var decision = game.botDecision(level);
            check.same(level + " still searches a legal move", true, game.position().legalMoves().contains(decision.move()));
            check.same(level + " keeps its own search allowance outside theory", true,
                    level == MatchSetup.Level.BASIC ? decision.extraNodes() == 0 : decision.extraNodes() > 0);
        }
        var prior = game.positionAt(5);
        check.same("Returning to the documented frame restores the Italian reply", "f8c5",
                OpeningRepertoire.CURRENT.playReply(prior, MatchSetup.Level.BASIC).uci());
    }

    @Test void exactTheoryMatchesTranspositionsAndRequiresTheCastlingRights(Checks check) {
        var direct = match("d2d4 d7d5 c2c4 e7e6").position();
        var transposed = match("d2d4 e7e6 c2c4 d7d5").position();
        var noCastling = match("e2e4 e7e5 e1e2 e8e7 e2e1 e7e8").position();
        for (var level : MatchSetup.Level.values()) {
            check.same(level + " recognizes the same Queen's Gambit board", Move.fromUci("b1c3"),
                    OpeningRepertoire.CURRENT.playReply(transposed, level));
            check.same(level + " ignores the order used to reach that board", OpeningRepertoire.CURRENT.playReply(direct, level),
                    OpeningRepertoire.CURRENT.playReply(transposed, level));
            check.same(level + " cannot reuse book knowledge after losing castling rights", null,
                    OpeningRepertoire.CURRENT.playReply(noCastling, level));
        }
    }

    @Test void verifiedAndReviewOnlyOverridesKeepTheirScopes(Checks check) {
        String prefix = "e2e4 c7c5 g1f3 d7d6";
        var board = match(prefix).position();
        var configured = OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(
                Map.of("id", "scoped-reply", "moves", prefix, "reply", "b1c3", "levels", List.of("ADVANCED")))));
        check.same("A verified profile-specific reply outranks the generic theory", Move.fromUci("b1c3"),
                configured.playReply(board, MatchSetup.Level.ADVANCED));
        for (var level : List.of(MatchSetup.Level.BASIC, MatchSetup.Level.HARD)) {
            check.same("Other profiles use documented theory rather than borrowing the scoped override", Move.fromUci("d2d4"),
                    configured.playReply(board, level));
        }
        var reviewOnly = OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(
                Map.of("id", "review-reply", "moves", prefix, "reply", "b1c3", "review-only", true))));
        check.same("A review-only exclusion is not bypassed by the live theory fallback", null,
                reviewOnly.playReply(board, MatchSetup.Level.HARD));
        check.same("The review retains its exact reference", Move.fromUci("b1c3"), reviewOnly.reviewReply(board));
        for (var level : MatchSetup.Level.values()) {
            check.same("The live theory fallback does not become a forced coaching override", null,
                    OpeningRepertoire.CURRENT.reply(match("e2e4").position(), level));
        }
    }

    @Test void everyAuthoredFrameOffersLegalContinuationsToAllProfiles(Checks check) {
        int frames = 0;
        int continuations = 0;
        for (var lesson : OpeningCatalog.load()) {
            for (int ply = 0; ply < lesson.steps().size(); ply++) {
                var board = lesson.positionAt(ply);
                var paths = OpeningTheory.CURRENT.choices(board);
                check.same("Documented theory remains available at " + lesson.id() + " ply " + ply, false, paths.isEmpty());
                for (var path : paths) {
                    var replay = board;
                    for (var step : path.steps()) { replay = replay.play(step.move()); continuations++; }
                }
                for (var level : MatchSetup.Level.values()) {
                    var reply = OpeningRepertoire.CURRENT.playReply(board, level);
                    check.same("A live reply is legal, or deliberately left to search by its explicit scope", true,
                            reply == null || board.legalMoves().contains(reply));
                }
                frames++;
            }
        }
        check.same("The complete authored catalogue is covered, rather than only the e4 regression", true, frames > 100);
        check.same("Whole documented paths can be replayed by the real chess rules", true, continuations > 1000);
    }

    private static ChessMatch match(String moves) { return ChessMatch.restore("3|WHITE|HARD|0|" + moves); }
}
