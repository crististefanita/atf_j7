package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestDefenseStudyTest {
    static List<QuestCatalog.Quest> studiedQuests() {
        return Stream.concat(QuestCatalog.loadTactics().stream(), QuestCatalog.loadTraining().stream())
                .filter(quest -> QuestDefenseStudy.ids().contains(quest.id())).toList();
    }

    @Test void everyReviewedExplanationReplaysFromItsAcceptedKey(Checks check) {
        var quests = studiedQuests();
        check.same("Every study identifies an active tactical exercise", QuestDefenseStudy.ids().size(), quests.size());
        check.same("The reviewed cohort covers seventeen existing exercises", 17, quests.size());
        for (var quest : quests) {
            var replay = QuestPayoff.forExplanation(quest);
            var study = replay.defenseStudy();
            var defense = replay.at(2);
            String label = quest.id() + " · Lichess " + study.sourceId();
            check.same(label + " keeps the accepted key", quest.solution(), replay.at(1).move());
            check.same(label + " shows the legal reviewed defense", true, replay.at(1).position().legalMoves().contains(defense.move()));
            check.same(label + " preserves the saved completion after the key", List.of(1, true),
                    List.of(QuestPayoff.forQuest(quest).plies(), quest.assess(quest.solution()).solved()));
            check.same(label + " provides the distinct lessons in Romanian and English", true, !study.lessonRo().equals(study.lessonEn()));
            check.same(label + " derives the shown material from the final board", replay.last().materialChange(),
                    QuestPayoff.balance(replay.last().position(), quest.position().turn()) - QuestPayoff.balance(quest.position(), quest.position().turn()));
        }
    }

    @Test void trainingOneShowsTheOpenFileAttackWithAnActualMaterialDeficit(Checks check) {
        var quest = studiedQuests().stream().filter(item -> item.id().equals("training-001")).findFirst().orElseThrow();
        var replay = QuestPayoff.forExplanation(quest);
        check.same("The g-pawn accepts the rook sacrifice", Move.fromUci("g7h6"), replay.at(2).move());
        check.same("The queen checks and the other rook enters on h1", List.of("g6h6", "h8g8", "d1h1"),
                replay.frames().subList(3, 6).stream().map(frame -> frame.move().uci()).toList());
        check.same("The five-ply attack still costs three pawn units", -300, replay.last().materialChange());
        check.same("The one-reply material floor remains separate from the end of the displayed line", -400, replay.immediateMaterialFloor());
        check.same("The after-key Stockfish estimate is separate from material", true, replay.defenseStudy().evaluationCp() > 0);
    }

    @Test void selectsTheExplanationForEachShippedTactic(Checks check) {
        var tactics = QuestCatalog.loadTactics();
        var simple = tactics.stream().filter(quest -> quest.id().equals("tactic-002")).findFirst().orElseThrow();
        var studied = tactics.stream().filter(quest -> quest.id().equals("tactic-043")).findFirst().orElseThrow();

        var normal = QuestPayoff.forExplanation(simple);
        check.same("Tactic 002 keeps its one-move explanation and rook capture without a study", List.of(1, "e4h1", 500, false),
                List.of(normal.plies(), normal.last().move().uci(), normal.last().materialChange(), normal.defenseStudy() != null));
        var study = QuestPayoff.forExplanation(studied);
        check.same("Tactic 043 replays every reviewed move in catalogue order", List.of("e2h5", "h7g8", "d3g6", "e5g6", "h5g6"),
                study.frames().subList(1, study.frames().size()).stream().map(frame -> frame.move().uci()).toList());
        var recorded = QuestPayoff.forQuest(studied).last();
        check.same("Tactic 043 keeps the same recorded position and material after the accepted key", true,
                study.at(1).position().sameForRepetition(recorded.position())
                        && study.at(1).materialChange() == recorded.materialChange());
    }

    @Test void rejectsAmbiguousEvidenceAndUnboundStudyKeys(Checks check) {
        var malformed = Map.of("schema", 1, "studies", Map.of("training-001", Map.of("source-id", "0199s", "depth", 19,
                "evaluation-cp", 723, "mate-in", 2, "line", "h1h6 g7h6 g6h6", "lesson-ro", "Atac", "lesson-en", "Attack")));
        check.rejects("An evaluation cannot contain both centipawns and mate", IllegalArgumentException.class, () -> QuestDefenseStudy.read(malformed));
        var active = studiedQuests().stream().filter(item -> item.id().equals("training-001")).findFirst().orElseThrow();
        var otherMove = active.position().legalMoves().stream().filter(move -> !move.equals(active.solution())).findFirst().orElseThrow();
        var stale = new QuestCatalog.Quest(active.id(), active.fen(), active.theme(), active.position(), List.of(otherMove), active.objective());
        check.rejects("A study cannot be replayed against a changed accepted key", IllegalArgumentException.class, () -> QuestPayoff.forExplanation(stale));
    }
}
