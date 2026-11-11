package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class TacticsCatalogTest {
    @Test void everyTacticHasOneMaterialWinningMoveAndAnIndependentProof(Checks check) throws Exception {
        var catalog = QuestCatalog.loadTactics();
        var proof = new StringBuilder();
        check.same("The initial chapter has twelve tactics", 12, catalog.size());
        check.same("The YAML difficulty starts with a direct capture and ends with a quiet key",
                List.of("tactic-005", "tactic-012"), List.of(catalog.getFirst().id(), catalog.getLast().id()));
        for (var quest : catalog.stream().sorted(Comparator.comparing(QuestCatalog.Quest::id)).toList()) {
            var variations = TacticalAnalysis.analyze(quest.position(), 3);
            var best = variations.best().get(0);
            check.same(quest.id() + " has a single best move", List.of(quest.solution()), variations.best().stream().map(TacticalAnalysis.Variation::move).toList());
            check.same(quest.id() + " wins material against the strongest reply", true, best.materialChange() >= 200 && best.ending() == TacticalAnalysis.Ending.HORIZON);
            check.same(quest.id() + " has no immediate checkmate", false, quest.position().legalMoves().stream().anyMatch(move -> quest.position().play(move).isCheckmate()));
            proof.append(quest.id()).append(' ').append(quest.solution().uci()).append(' ').append(best.materialChange()).append('\n');
        }
        check.same("Both sides get six tactics", 6L, catalog.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("Four positions require a quiet first move", 4L,
                catalog.stream().filter(quest -> quest.position().pieceAt(quest.solution().to()) == null).count());
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(proof.toString().getBytes(StandardCharsets.UTF_8)));
        check.same("Engine results agree with the chess.js proof", Files.readString(Path.of("chess-core/src/main/resources/tactics.sha256")).trim(), digest);
    }

    @Test void wrongLegalMovesKeepNextLockedAndBothHintsReferToTheWinningMove(Checks check) {
        var quest = QuestCatalog.loadTactics().get(0);
        var wrong = quest.position().legalMoves().stream().filter(move -> !move.equals(quest.solution())).findFirst().orElseThrow();
        check.same("Wrong legal move does not solve the objective", false, quest.assess(wrong).solved());
        var progress = QuestProgress.restore(null, Set.of(), List.of(quest.id(), "tactic-002"));
        check.same("Next stays locked before solving", false, progress.canAdvance(0, 2));
        check.same("The first hint exposes only the source", new QuestHint.Cue(quest.solution().from(), -1), QuestHint.PIECE.cue(quest.solution()));
        check.same("The second hint exposes the source and destination", new QuestHint.Cue(quest.solution().from(), quest.solution().to()), QuestHint.SOLUTION.cue(quest.solution()));
        var result = quest.assess(quest.solution());
        check.same("Material gain solves the quest without mate or a pending reply", List.of(true, false, false),
                List.of(result.solved(), result.position().isCheckmate(), result.continues()));
        check.same("The solved board matches the selected move", true, quest.solvedPosition().sameForRepetition(result.position()));
        check.same("Next unlocks after the correct move", true, progress.solveCurrent().canAdvance(0, 2));
    }
}
