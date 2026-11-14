package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class TacticsCatalogTest {
    @Test void everyTacticHasOneMaterialWinningMoveAndAnIndependentProof(Checks check) throws Exception {
        var catalog = QuestCatalog.loadTactics();
        check.same("The chapter has exactly 100 tactics", 100, catalog.size());
        var first = catalog.getFirst();
        var quiet = catalog.get(11);
        check.same("The chapter begins with the direct capture used in existing progress", "tactic-005", first.id());
        check.same("The introduction starts with a capture", true, first.position().pieceAt(first.solution().to()) != null);
        check.same("The introduction ends with a quiet move", null, quiet.position().pieceAt(quiet.solution().to()));
        check.same("Both sides get fifty tactics", 50L, catalog.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("The extended curriculum includes quiet keys beyond the introductory puzzles", true,
                catalog.stream().skip(12).anyMatch(quest -> quest.position().pieceAt(quest.solution().to()) == null));
        verifyMaterialProof(catalog, "tactics", check);
    }

    @Test void trainingAddsBalancedPracticeAndEveryKeyHasAnIndependentProof(Checks check) throws Exception {
        var catalog = QuestCatalog.loadTraining();
        check.same("The app offers 114 tactical training quests", 114, catalog.size());
        var additions = catalog.stream().filter(quest -> quest.id().compareTo("training-072") > 0 && quest.id().compareTo("training-096") <= 0).toList();
        check.same("Twenty-four new IDs preserve the first 72", 24, additions.size());
        check.same("Both colors get twelve new positions", 12L,
                additions.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("The chapter opens with the lowest-rated introductory exercise", "training-105", catalog.getFirst().id());
        var rookStudies = catalog.stream().filter(quest -> quest.id().compareTo("training-096") > 0 && quest.id().compareTo("training-100") <= 0).toList();
        check.same("The four rook studies follow their source difficulty", List.of("training-097", "training-098", "training-099", "training-100"),
                rookStudies.stream().map(QuestCatalog.Quest::id).toList());
        check.same("Both colors have two rook studies", 2L, rookStudies.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("Every new rook study has a playable line and a demonstrated lesson", true,
                rookStudies.stream().allMatch(quest -> quest.playerMoves() > 1 && quest.lesson() != null && demonstratesGain(quest)));
        var newStudies = catalog.stream().filter(quest -> quest.id().compareTo("training-100") > 0 && quest.id().compareTo("training-104") <= 0).toList();
        check.same("The new studies are ordered by source difficulty", List.of("training-101", "training-102", "training-103", "training-104"),
                newStudies.stream().map(QuestCatalog.Quest::id).toList());
        check.same("The new studies teach four distinct ideas", 4L, newStudies.stream().map(QuestCatalog.Quest::lesson).distinct().count());
        check.same("Both colors have two new studies", 2L, newStudies.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("All new lessons can be played through to the gain", true, newStudies.stream().allMatch(TacticsCatalogTest::demonstratesGain));
        var endgames = catalog.stream().filter(quest -> quest.id().compareTo("training-104") > 0 && quest.id().compareTo("training-108") <= 0).toList();
        check.same("The new endgames follow source difficulty", List.of("training-105", "training-106", "training-107", "training-108"),
                endgames.stream().map(QuestCatalog.Quest::id).toList());
        check.same("Both sides get two new endgames", 2L, endgames.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        check.same("All four endgames teach a distinct gain that survives every reply", true,
                endgames.stream().map(QuestCatalog.Quest::lesson).distinct().count() == 4 && endgames.stream().allMatch(TacticsCatalogTest::demonstratesGain));
        var newEndgames = catalog.stream().filter(quest -> quest.id().compareTo("training-108") > 0 && quest.id().compareTo("training-112") <= 0).toList();
        check.same("Four new lessons are ordered by their source difficulty", List.of("training-109", "training-110", "training-111", "training-112"),
                newEndgames.stream().map(QuestCatalog.Quest::id).toList());
        check.same("The new lessons preserve both colors and demonstrate the gain on the board", true,
                newEndgames.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count() == 2
                        && newEndgames.stream().allMatch(TacticsCatalogTest::demonstratesGain));
        var newFork = catalog.stream().filter(quest -> quest.id().equals("training-113")).findFirst().orElseThrow();
        check.same("Two checks lead to the recorded fork payoff", List.of("f7f8", "d8e7", "f8e8", "e7d6", "g6e4", "f2b2", "c1b2"),
                newFork.line().stream().map(ChessPosition.Move::uci).toList());
        check.same("The new lesson shows a gain that survives immediate replies", true, demonstratesGain(newFork));
        var quietThreat = catalog.stream().filter(quest -> quest.id().equals("training-114")).findFirst().orElseThrow();
        check.same("The black-side lesson preserves the bishop move before the last check", List.of("d7g4", "g2h1", "h4g3", "f3h2", "g4e4"),
                quietThreat.line().stream().map(ChessPosition.Move::uci).toList());
        check.same("The quiet threat demonstrates a gain against immediate replies", true, demonstratesGain(quietThreat));
        verifyMaterialProof(catalog, "training", check);
    }

    @Test void replacementsHaveNewIdentitiesAndPreserveTheIntroductorySlots(Checks check) {
        var active = QuestCatalog.loadTactics().stream().map(QuestCatalog.Quest::id).toList();
        var retired = ChessYaml.list(ChessYaml.resource("quest-retired.yaml"), "ids");
        var oldIds = List.of("tactic-006", "tactic-004", "tactic-008", "tactic-009", "tactic-010", "tactic-012");
        var newIds = List.of("tactic-101", "tactic-102", "tactic-103", "tactic-104", "tactic-105", "tactic-106");
        check.same("Replaced positions cannot receive credit under their former saved IDs", true,
                retired.containsAll(oldIds) && oldIds.stream().noneMatch(active::contains));
        check.same("The source replacements occupy the same six instructional slots", List.of(2, 3, 7, 8, 9, 11),
                newIds.stream().map(active::indexOf).toList());
    }

    @Test void independentMaterialSampleMatchesTheExternalProof(Checks check) throws Exception {
        var catalog = QuestCatalog.readMaterial(ChessYaml.resource("tactical-transfer-holdout.yaml"), "holdout");
        check.same("The independent sample includes twelve positions per side", 12L,
                catalog.stream().filter(quest -> quest.position().turn() == ChessPosition.Side.BLACK).count());
        var proof = ProjectRoot.locate().resolve("atf-tests/src/test/resources/tactical-transfer-holdout.sha256");
        verifyMaterialProof(catalog, proof, check);
    }

    @Test void intermezzoKeysMatchTheExternalMaterialProof(Checks check) throws Exception {
        verifyMaterialProof(QuestCatalog.loadIntermezzo(), "intermezzo", check);
    }

    @Test void everyPublishedPayoffShowsMaterialThatSurvivesImmediateReplies(Checks check) {
        var chapters = List.of(QuestCatalog.loadTactics(), QuestCatalog.loadTraining(), QuestCatalog.loadIntermezzo());
        var unresolved = new TreeSet<String>();
        var minimumLines = List.of(76, 83, 20);
        for (int index = 0; index < chapters.size(); index++) {
            var chapter = chapters.get(index);
            var invalid = new ArrayList<String>();
            int lines = 0;
            for (var quest : chapter) {
                boolean hasLine = quest.line().size() > 1;
                if (hasLine) lines++;
                if (!demonstratesGain(quest)) {
                    if (hasLine) invalid.add(quest.id());
                    else unresolved.add(quest.id());
                }
            }
            check.same("The chapter preserves demonstrated material after every immediate legal reply", List.of(), invalid);
            check.same("The chapter preserves its verified payoff coverage: " + chapter.getFirst().id() + " (" + lines + " lines)",
                    true, lines >= minimumLines.get(index));
        }
        check.same("All three public chapters are included in the payoff audit",
                List.of(100, 114, 24), chapters.stream().map(List::size).toList());
        check.same("Every key-only case without immediate material has a reviewed explanation", QuestDefenseStudy.ids(), unresolved);
    }

    private static boolean demonstratesGain(QuestCatalog.Quest quest) {
        var origin = quest.position();
        var side = origin.turn();
        var finish = quest.solvedPosition();
        var replies = finish.legalMoves();
        int required = QuestPayoff.balance(origin, side) + 200;
        return QuestPayoff.balance(finish, side) >= required && !replies.isEmpty()
                && replies.stream().map(finish::play).allMatch(position -> !position.legalMoves().isEmpty() && QuestPayoff.balance(position, side) >= required);
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
        for (int stage = 0; stage < quest.playerMoves(); stage++) {
            var result = quest.assess(quest.playerMove(stage), stage);
            boolean finished = stage == quest.playerMoves() - 1;
            check.same("Material stage " + (stage + 1) + " advances or solves without mate", List.of(finished, false, !finished),
                    List.of(result.solved(), result.position().isCheckmate(), result.continues()));
            if (finished) check.same("The solved board matches the final move", true, quest.solvedPosition().sameForRepetition(result.position()));
        }
        check.same("Next unlocks after the final correct move", true, progress.solveCurrent().canAdvance(0, 2));
    }
}
