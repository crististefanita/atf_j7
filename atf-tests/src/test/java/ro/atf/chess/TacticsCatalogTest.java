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
        check.same("The chapter has exactly 100 tactics", 100, catalog.size());
        check.same("The introductory progression keeps its original direct capture and quiet key",
                List.of("tactic-005", "tactic-012"), List.of(catalog.getFirst().id(), catalog.get(11).id()));
        check.same("Both sides get fifty tactics", 50L, catalog.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("The extended curriculum includes quiet keys beyond the introductory puzzles", true,
                catalog.stream().skip(12).anyMatch(quest -> quest.position().pieceAt(quest.solution().to()) == null));
        verifyMaterialProof(catalog, "tactics", check);
    }

    @Test void trainingAddsBalancedPracticeAndEveryKeyHasAnIndependentProof(Checks check) throws Exception {
        var catalog = QuestCatalog.loadTraining();
        check.same("The app offers 96 tactical training quests", 96, catalog.size());
        var additions = catalog.stream().filter(quest -> quest.id().compareTo("training-072") > 0).toList();
        check.same("Twenty-four new IDs preserve the first 72", 24, additions.size());
        check.same("Both colors get twelve new positions", 12L,
                additions.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("The chapter opens with the new introductory exercises", "training-049", catalog.getFirst().id());
        verifyMaterialProof(catalog, "training", check);
    }

    @Test void independentMaterialSampleMatchesTheExternalProof(Checks check) throws Exception {
        var catalog = QuestCatalog.readMaterial(ChessYaml.resource("tactical-transfer-holdout.yaml"), "holdout");
        check.same("The independent sample includes twelve positions per side", 12L,
                catalog.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        var proof = ProjectRoot.locate().resolve("atf-tests/src/test/resources/tactical-transfer-holdout.sha256");
        verifyMaterialProof(catalog, proof, check);
    }

    private static void verifyMaterialProof(List<QuestCatalog.Quest> catalog, String chapter, Checks check) throws Exception {
        verifyMaterialProof(catalog, ProjectRoot.locate().resolve("chess-core/src/main/resources/" + chapter + ".sha256"), check);
    }

    private static void verifyMaterialProof(List<QuestCatalog.Quest> catalog, Path expectedProof, Checks check) throws Exception {
        var proof = new StringBuilder();
        for (var quest : catalog.stream().sorted(Comparator.comparing(QuestCatalog.Quest::id)).toList()) {
            var variations = TacticalAnalysis.analyze(quest.position(), 3);
            var best = variations.best().get(0);
            check.same(quest.id() + " has a single best move", List.of(quest.solution()), variations.best().stream().map(TacticalAnalysis.Variation::move).toList());
            check.same(quest.id() + " wins material against the strongest reply", true, best.materialChange() >= 200 && best.ending() == TacticalAnalysis.Ending.HORIZON);
            check.same(quest.id() + " has no immediate checkmate", false, quest.position().legalMoves().stream().anyMatch(move -> quest.position().play(move).isCheckmate()));
            proof.append(quest.id()).append(' ').append(quest.solution().uci()).append(' ').append(best.materialChange()).append('\n');
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(proof.toString().getBytes(StandardCharsets.UTF_8)));
        check.same(expectedProof.getFileName() + " matches the chess.js proof", Files.readString(expectedProof).trim(), digest);
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
