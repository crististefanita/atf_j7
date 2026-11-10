package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MateInTwoCatalogTest {
    @Test void everyDefenseHasMateAndTheOpeningIsUniqueInBothEngines(Checks check) throws Exception {
        var quests = QuestCatalog.loadMateInTwo();
        check.same("The two-move chapter has exactly 100 puzzles", 100, quests.size());
        check.same("Each color starts 50 puzzles", 50L, quests.stream().filter(q -> q.position().turn() == ChessPosition.Side.WHITE).count());
        var canonical = new StringBuilder();
        var invalid = new ArrayList<String>();
        int branching = 0;
        for (var quest : quests) {
            var position = quest.position();
            var candidates = new ArrayList<Move>();
            Map<String, List<String>> recorded = Map.of();
            boolean immediateMate = false;
            for (Move opening : position.legalMoves()) {
                var afterOpening = position.play(opening);
                immediateMate |= afterOpening.isCheckmate();
                var proof = defenses(afterOpening);
                if (!proof.isEmpty()) candidates.add(opening);
                if (opening.equals(quest.solution())) recorded = proof;
            }
            if (immediateMate || !candidates.equals(List.of(quest.solution()))
                    || !recorded.getOrDefault(quest.line().defense().uci(), List.of()).contains(quest.matingMove().uci())) invalid.add(quest.id());
            if (recorded.size() > 1) branching++;
            canonical.append(quest.id()).append(' ').append(quest.solution().uci());
            recorded.forEach((reply, ends) -> canonical.append(' ').append(reply).append('=').append(String.join(",", ends)));
            canonical.append('\n');
        }
        check.same("No immediate mate, extra opening key or missing reference reply", List.of(), invalid);
        check.same("The selection includes branching defenses", true, branching > 0);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        try (var oracle = getClass().getResourceAsStream("/mate-in-two.sha256")) {
            check.same("The complete defense and mate tree matches chess.js", new String(oracle.readAllBytes(), StandardCharsets.UTF_8).trim(), actual);
        }
    }

    @Test void progressAdvancesOnlyThroughTheKeyAndFinishesOnlyOnMate(Checks check) {
        var quest = QuestCatalog.loadMateInTwo().getFirst();
        var alternative = quest.position().legalMoves().stream().filter(move -> !move.equals(quest.solution())).findFirst().orElseThrow();
        var wrongKey = quest.assess(alternative);
        check.same("A legal wrong key never advances the puzzle", false, wrongKey.continues());
        var reply = quest.assess(quest.solution());
        check.same("The key advances to the recorded legal defense", quest.line().defense(), reply.defense());
        check.same("The key opens the second move with the original side to play", true,
                reply.continues() && reply.position().turn() == quest.position().turn() && reply.outcome() == ChessPosition.Outcome.PLAYING);
        var wrongMate = reply.position().legalMoves().stream().filter(move -> !reply.position().play(move).isCheckmate()).findFirst().orElseThrow();
        check.same("A legal second move without mate stays unfinished", false, quest.assess(wrongMate, true).outcome() == ChessPosition.Outcome.CHECKMATE);
        check.same("The second move finishes by checkmate", ChessPosition.Outcome.CHECKMATE, quest.assess(quest.matingMove(), true).outcome());
        check.same("The line exposes the final king for the explanation", true, !quest.explanation().checkers().isEmpty());
    }

    private static Map<String, List<String>> defenses(ChessPosition position) {
        var result = new TreeMap<String, List<String>>();
        for (Move reply : position.legalMoves()) {
            var continuation = position.play(reply);
            var mates = continuation.legalMoves().stream().filter(move -> continuation.play(move).isCheckmate()).map(Move::uci).sorted().toList();
            if (mates.isEmpty()) return Map.of();
            result.put(reply.uci(), mates);
        }
        return result;
    }
}
