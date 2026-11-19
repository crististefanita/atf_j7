package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

/** A verified opening reply is knowledge, not a positional bonus or a scripted opposing continuation. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NimzoRepertoireTest {
    private static final String PREFIX = "d2d4 g8f6 c2c4 e7e6 b1c3";

    @Test void publicHintRecalculatesBb4WithoutGivingUpTheCompletedH6(Checks check) {
        var board = match(PREFIX).position();
        var hint = TacticalAnalysis.verifiedHints(board);
        check.same("The qualified theory move is actually searched for the public hint", Move.fromUci("f8b4"), hint.lines().getFirst().move());
        check.same("Be7 cannot remain an equivalent public recommendation", false,
                hint.lines().stream().anyMatch(line -> line.move().equals(Move.fromUci("f8e7"))));
        check.same("The original completed horizon is preserved", true, hint.seedDepth() >= 6 && hint.selectedDepth() >= 6);
        check.same("The ordinary allowance has not been raised to pay for the result", true, hint.extraNodes() <= 200_000);
        var replay = board;
        for (var move : hint.lines().getFirst().line()) replay = replay.play(move);
        check.same("The preview retains a calculated opposing continuation", true, hint.lines().getFirst().line().size() >= 6);
    }

    @Test void exactPositionsAndTranspositionsShareKnowledgeButAdjacentBoardsDoNot(Checks check) {
        var exact = match(PREFIX).position();
        var transposed = match("d2d4 e7e6 c2c4 g8f6 b1c3").position();
        check.same("The entry is positional, rather than a required human move script", exact.searchKey(), transposed.searchKey());
        for (var board : List.of(exact, transposed)) {
            check.same("The exact verified reply is available through either move order", Move.fromUci("f8b4"), OpeningRepertoire.CURRENT.reply(board));
        }
        String fen = StudyFen.replay("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                java.util.Arrays.stream(PREFIX.split(" ")).map(Move::fromUci).toList());
        check.same("Losing castling rights invalidates this theory identity", null,
                OpeningRepertoire.CURRENT.reply(ChessPosition.fromFen(fen.replace(" KQkq ", " KQk "))));
        check.same("The prior position does not borrow the Black reply", null,
                OpeningRepertoire.CURRENT.reply(match("d2d4 g8f6 c2c4 e7e6").position()));
        check.same("Knowledge does not become a scripted reply after Bb4", null,
                OpeningRepertoire.CURRENT.reply(exact.play(Move.fromUci("f8b4"))));
    }

    @Test void openingKnowledgeDoesNotBanSoundBe7InTheFrench(Checks check) {
        var french = match("e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5").position();
        check.same("The same bishop move remains legal in its proper French context", true,
                french.legalMoves().contains(Move.fromUci("f8e7")));
        check.same("The new Nimzo entry is not applied to another opening", null, OpeningRepertoire.CURRENT.reply(french));
        var data = fixture();
        var rows = rows(data);
        var control = data.path("cases").get(0);
        boolean largeLoss = true;
        for (int depth : List.of(17, 19)) largeLoss &= loss(rows, control.path("free"), control.path("roots").path("f8e7"), depth) >= 150;
        check.same("The legal French development is not the independently losing Nimzo alternative", false, largeLoss);
    }

    @Test void everyOfferedNimzoPlyKeepsItsOwnIndependentFreeReplyEvidence(Checks check) {
        var data = fixture();
        var rows = rows(data);
        var actual = TacticalAnalysis.verifiedHints(match(PREFIX).position());
        var recorded = data.path("line");
        check.same("The teacher phases belong to the actual calculated public PV",
                recorded.path("path"), LearningGameAudit.mapper().valueToTree(actual.lines().getFirst().line().stream().map(Move::uci).toList()));
        int phases = 0;
        for (var phase : recorded.path("phases")) {
            boolean bad = true;
            for (int depth : List.of(17, 19)) bad &= loss(rows, phase.path("free"), phase.path("forced"), depth) >= 150;
            check.same("No weak root or assumed opposing reply in " + phase.path("move").asText(), false, bad);
            phases++;
        }
        check.same("Both players and the final move are covered", actual.lines().getFirst().line().size(), phases);
    }

    @Test void theVerifiedRootIsSeparatedFromBe7AtBothTeacherHorizons(Checks check) {
        var data = fixture();
        var rows = rows(data);
        var root = data.path("nimzo");
        for (int depth : List.of(17, 19)) {
            check.same("The verified Bb4 stays near the independent best with all replies free H" + depth, true,
                    loss(rows, root.path("free"), root.path("roots").path("f8b4"), depth) < 50);
            check.same("The same teacher rejects Be7 as an equivalent recommendation H" + depth, true,
                    loss(rows, root.path("free"), root.path("roots").path("f8e7"), depth) >= 150);
        }
    }

    @Test void reviewUsesTheVerifiedRootWithoutRewritingThePlayedMoveOrBorrowingHintScores(Checks check) {
        var played = Move.fromUci("f8e7");
        var review = TacticalAnalysis.review(match(PREFIX).position(), played, java.util.Set.of(), GameReviewPolicy.CURRENT.search);
        check.same("Review also considers the independently verified development", Move.fromUci("f8b4"), review.best().move());
        check.same("The actual Be7 remains separately evaluated", played, review.played().move());
        check.same("Review keeps its own ordinary work allowance", true, review.ordinaryNodes() <= GameReviewPolicy.CURRENT.search.extraNodes());
    }

    private static ChessMatch match(String prefix) { return ChessMatch.restore("3|WHITE|HARD|1|" + prefix); }

    private static JsonNode fixture() {
        try (var input = NimzoRepertoireTest.class.getResourceAsStream("/nimzo-repertoire-qualified.yaml")) {
            var data = LearningGameAudit.mapper().readTree(input);
            if (!HardLeagueAudit.digest(HardLeagueAudit.canonical(data).getBytes(StandardCharsets.UTF_8)).equals("e6b47f67eafced480a434a12c6c8bc7aadcd503032cda8092278f8ef4832cbcf")) {
                throw new IllegalArgumentException("Changed Nimzo qualification witness");
            }
            return data;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private static Map<String, JsonNode> rows(JsonNode data) {
        var rows = new HashMap<String, JsonNode>();
        for (var envelope : data.path("raw")) {
            var body = envelope.path("body");
            if (!envelope.path("digest").asText().equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(body).getBytes(StandardCharsets.UTF_8)))
                    || rows.putIfAbsent(body.path("key").asText(), body) != null) throw new IllegalArgumentException("Changed Nimzo oracle phase");
        }
        if (rows.size() != data.path("plan").path("queries").size()) throw new IllegalArgumentException("Missing Nimzo oracle phase");
        for (var query : data.path("plan").path("queries")) {
            StockfishStudyAudit.validate(query.path("query"), data.path("plan").path("identity"), rows.get(query.path("key").asText()));
        }
        return rows;
    }

    private static double loss(Map<String, JsonNode> rows, JsonNode free, JsonNode forced, int depth) {
        return StockfishStudyAudit.loss(rows.get(free.path("" + depth).asText()), rows.get(forced.path("" + depth).asText()));
    }
}
