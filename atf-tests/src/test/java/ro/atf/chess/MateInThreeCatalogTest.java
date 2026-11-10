package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MateInThreeCatalogTest {
    @Test void everyPositionHasExactlyOneOpeningThatForcesMateInThree(Checks check) throws Exception {
        var quests = QuestCatalog.loadMateInThree();
        check.same("The chapter has 100 positions", 100, quests.size());
        check.same("Each color starts 50 positions", 50L, quests.stream().filter(q -> q.position().turn() == ChessPosition.Side.WHITE).count());
        var canonical = new StringBuilder();
        var invalid = new ArrayList<String>();
        for (var quest : quests) {
            var position = quest.position();
            var keys = position.legalMoves().stream().filter(move -> wins(position, move, 3)).toList();
            if (!keys.equals(List.of(quest.solution())) || wins(position, quest.solution(), 2)) invalid.add(quest.id());
            var afterKey = position.play(quest.solution());
            var rows = new ArrayList<String>();
            for (Move reply : afterKey.legalMoves()) {
                var next = afterKey.play(reply);
                var second = next.legalMoves().stream().filter(move -> wins(next, move, 2)).toList();
                if (reply.equals(quest.defenseAfter(0)) && !second.equals(List.of(quest.playerMove(1)))) invalid.add(quest.id());
                rows.add(reply.uci() + "=" + String.join(",", second.stream().map(Move::uci).sorted().toList()));
            }
            canonical.append(quest.id()).append(' ').append(quest.solution().uci()).append(' ')
                    .append(String.join(" ", rows.stream().sorted().toList())).append('\n');
        }
        check.same("Every puzzle is exact and the played reference branch has a unique second key", List.of(), invalid);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        try (var oracle = getClass().getResourceAsStream("/mate-in-three.sha256")) {
            check.same("Winning continuations against every first defense match chess.js", new String(oracle.readAllBytes(), StandardCharsets.UTF_8).trim(), actual);
        }
    }

    @Test void bothDefensesAdvanceTheBoardAndOnlyTheLastMoveSolves(Checks check) {
        var quest = QuestCatalog.loadMateInThree().getFirst();
        for (int stage = 0; stage < 2; stage++) {
            var reply = quest.assess(quest.playerMove(stage), stage);
            check.same("The stage advances with the recorded defense", quest.defenseAfter(stage), reply.defense());
            check.same("The player is still to move after the defense", true,
                    reply.continues() && reply.position().turn() == quest.position().turn() && !reply.position().isCheckmate());
        }
        check.same("The third move delivers mate", ChessPosition.Outcome.CHECKMATE, quest.assess(quest.matingMove(), 2).outcome());
    }

    private static boolean wins(ChessPosition position, Move move, int budget) {
        var after = position.play(move);
        if (budget == 1) return after.isCheckmate();
        var replies = after.legalMoves();
        if (replies.isEmpty()) return after.isCheck();
        for (Move reply : replies) {
            var next = after.play(reply);
            if (!next.legalMoves().stream().anyMatch(candidate -> wins(next, candidate, budget - 1))) return false;
        }
        return true;
    }
}
