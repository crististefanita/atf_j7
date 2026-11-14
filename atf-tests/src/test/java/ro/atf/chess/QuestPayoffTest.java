package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestPayoffTest {
    private static final String FEN = "5k2/p1p4p/8/8/8/8/R1R4R/K7 w - - 0 1";
    private static final String LINE = "a2a3 f8g8 a3a7";

    @Test void requiresEverySolverMoveAndReplaysTheCapture(Checks check) {
        var quest = catalog(Map.of("id", "fixture-001", "fen", FEN, "solution", "a2a3", "difficulty", 1,
                "payoff-line", LINE, "lesson", "QUIET_THREAT"));
        var first = quest.assess(quest.solution());
        var progress = QuestProgress.restore(quest.id(), Set.of(), List.of(quest.id(), "fixture-002"));
        check.same("The first correct move schedules the authored defense without unlocking Next", List.of(true, false, false),
                List.of(first.continues(), first.solved(), progress.canAdvance(0, 2)));
        check.same("A different legal move cannot skip the first stage", false, quest.assess(Move.fromUci("h2h7")).solved());
        var pending = QuestStep.restore(1, quest);
        var ready = QuestStep.restore(2, quest);
        check.same("An interrupted defense is resumed from the position after the actual first move", true,
                pending.defensePending() && pending.position(quest).sameForRepetition(quest.position().play(quest.solution())));
        check.same("The saved defense is applied once on resumption", true,
                !ready.defensePending() && ready.position(quest).sameForRepetition(first.position()));
        check.same("A legal wrong-stage move keeps the final goal locked", false, quest.assess(Move.fromUci("a3b3"), 1).solved());
        check.same("The final authored capture completes the puzzle", true, quest.assess(quest.finishingMove(), 1).solved());
        check.same("Next unlocks only after completing the final move", true, progress.solveCurrent().canAdvance(0, 2));
        var payoff = QuestPayoff.forQuest(quest);
        check.same("Each move produces a legal replay frame", 4, payoff.frames().size());
        check.same("The material gain appears only on the final capture", List.of(0, 0, 0, 100),
                payoff.frames().stream().map(QuestPayoff.Frame::materialChange).toList());
        check.same("The actual captured piece and square explain the result", List.of(Kind.PAWN, BoardSquare.parse("a7")),
                List.of(payoff.last().capture().piece().kind(), payoff.last().capture().square()));
        check.same("The reviewed cue is kept apart from the computed result", QuestCatalog.Lesson.QUIET_THREAT, payoff.lesson());
        check.same("An old solved ID without a checkpoint ends on the authored board", true,
                quest.restoreFinish(null).solvedPosition().sameForRepetition(payoff.last().position()));
    }

    @Test void acceptsAStableFinalAlternativeAndRejectsARecapturableGain(Checks check) {
        var quest = catalog(Map.of("id", "fixture-001", "fen", FEN, "solution", "a2a3", "difficulty", 1,
                "payoff-line", LINE, "lesson", "QUIET_THREAT"));
        var safe = Move.fromUci("c2c7");
        var exposed = Move.fromUci("h2h7");
        check.same("The alternate capture reaches and preserves the same gain", true, quest.assess(safe, 1).solved());
        check.same("A pawn capture which lets the king recapture the rook does not qualify", false, quest.assess(exposed, 1).solved());
        var restored = quest.restoreFinish(new QuestStep(2).finishCheckpoint(safe));
        check.same("Restoring keeps the actual move and capture on c7", safe, restored.finishingMove());
        check.same("An alternate line does not inherit an unaudited authored cue", true, restored.lesson() == null);
        check.same("The alternate replay ends at the saved board", true, restored.solvedPosition().sameForRepetition(quest.assess(safe, 1).position()));
        check.same("An invalid historical finish falls back to the authored move", quest.finishingMove(),
                quest.restoreFinish(new QuestStep(2).finishCheckpoint(exposed)).finishingMove());
    }

    @Test void preservesLegacyOneMoveAndRejectsMalformedPayoffs(Checks check) {
        var legacy = catalog(Map.of("id", "fixture-001", "fen", FEN, "solution", "a2a3", "difficulty", 1));
        check.same("A legacy quiet key still completes with no immediate gain claim", List.of(true, 0),
                List.of(legacy.assess(legacy.solution()).solved(), QuestPayoff.forQuest(legacy).last().materialChange()));
        var enriched = catalog(Map.of("id", "fixture-001", "fen", FEN, "solution", "a2a3", "difficulty", 1, "payoff-line", LINE));
        check.same("An old checkpoint for the key restores the enriched result", enriched.finishingMove(),
                enriched.restoreFinish("0:a2a3").finishingMove());
        for (String line : List.of("a2a3", "a2a3 f8g8", "a2a4 f8g8 a4a7", "a2a3 f8g8 b1b2")) {
            boolean rejected;
            try {
                catalog(Map.of("id", "fixture-001", "fen", FEN, "solution", "a2a3", "difficulty", 1, "payoff-line", line));
                rejected = false;
            } catch (IllegalArgumentException invalid) {
                rejected = true;
            }
            check.same("Reject malformed payoff " + line, true, rejected);
        }
    }

    @Test void showsEnPassantAtTheCapturedSquareAndAccountsForPromotion(Checks check) {
        var ep = catalog(Map.of("id", "fixture-001", "fen", "7k/8/8/3pP3/8/8/8/K7 w - d6 0 1", "solution", "e5d6", "difficulty", 1));
        var promotion = catalog(Map.of("id", "fixture-001", "fen", "7k/P7/8/8/8/8/8/K7 w - - 0 1", "solution", "a7a8q", "difficulty", 1));
        check.same("En passant identifies d5 as the victim square", BoardSquare.parse("d5"), QuestPayoff.forQuest(ep).last().capture().square());
        check.same("Promotion adds 800 centipawns without inventing a capture", List.of(800, false),
                List.of(QuestPayoff.forQuest(promotion).last().materialChange(), QuestPayoff.forQuest(promotion).last().capture() != null));
    }

    @Test void explainsTheImmediateFinishAndReplaysTheDelayedCaptureWithoutChangingScoring(Checks check) {
        var quest = QuestCatalog.loadTraining().stream().filter(row -> row.id().equals("training-069")).findFirst().orElseThrow();
        int finalStage = 5;
        var key = Move.fromUci("c1b2");
        var rookCheck = Move.fromUci("d1d8");
        var bishopCheck = Move.fromUci("c4f7");
        var start = quest.positionAt(finalStage);
        var afterKey = quest.assess(key, finalStage);
        var study = QuestFinishStudy.forStage(quest, finalStage);
        var replay = study.replay(quest);
        check.same("Only stage six exposes the finish explanation", null, QuestFinishStudy.forStage(quest, finalStage - 1));
        check.same("The key takes the rook for +500 cp immediately, before Qxd1+ is possible", List.of(true, 500, true),
                List.of(afterKey.solved(), QuestPayoff.balance(afterKey.position(), start.turn()) - QuestPayoff.balance(start, start.turn()),
                        afterKey.position().legalMoves().contains(Move.fromUci("a4d1"))));
        check.same("Neither check completes the puzzle", List.of(false, false),
                List.of(quest.assess(rookCheck, finalStage).solved(), quest.assess(bishopCheck, finalStage).solved()));
        check.same("The reviewed alternative begins at the exact final position", true, start.sameForRepetition(replay.at(0).position()));
        check.same("The legal replay shows Rd8+ Kg7 Qxb2 Qxc4 Rd2", List.of("d1d8", "g8g7", "c1b2", "a4c4", "d8d2"),
                replay.frames().subList(1, replay.frames().size()).stream().map(frame -> frame.move().uci()).toList());
        check.same("The rook check gains zero material immediately; only the third ply takes the rook", List.of(0, 500, Kind.ROOK),
                List.of(replay.at(1).materialChange(), replay.at(3).materialChange(), replay.at(3).capture().piece().kind()));
        check.same("The key and six-stage playable line survive restoration", List.of(key, 6),
                List.of(quest.restoreFinish("5:c1b2").finishingMove(), quest.playerMoves()));
        var progress = QuestProgress.restore(quest.id(), Set.of(), List.of(quest.id()));
        var rewards = QuestRewards.restore(Set.of(), Set.of(), 0).interrupt(quest.id());
        var award = rewards.complete(progress);
        check.same("After trying the alternative, the accepted key still earns the base award exactly once", List.of(10, 0),
                List.of(award.amount(), award.rewards().complete(progress.solveCurrent()).amount()));
        var altered = new QuestCatalog.Quest(quest.id(), quest.fen(), quest.theme(), start, List.of(key), quest.objective());
        check.same("A changed quest cannot inherit the stage-six study", null, QuestFinishStudy.forStage(altered, 0));
    }

    @Test void replaysTheMateInTwoAndTheActualAlternateMateInThree(Checks check) {
        var two = QuestCatalog.loadMateInTwo().getFirst();
        var authored = QuestCatalog.loadMateInThree().stream().filter(quest -> quest.id().equals("mate3-01Kz0")).findFirst().orElseThrow();
        var three = authored.restoreFinish("2:f8f1");
        for (var quest : List.of(two, three)) {
            var replay = QuestPayoff.forMateLine(quest);
            check.same(quest.id() + " shows the starting board and each individual move", quest.line(),
                    replay.frames().subList(1, replay.frames().size()).stream().map(QuestPayoff.Frame::move).toList());
            for (int stage = 0; stage < quest.mateLength(); stage++) {
                check.same(quest.id() + " reaches the expected board after defense " + stage,
                        true, replay.at(stage * 2).position().sameForRepetition(quest.positionAt(stage)));
            }
            check.same(quest.id() + " ends in mate on its recorded board", true, replay.last().position().isCheckmate()
                    && replay.last().position().sameForRepetition(quest.solvedPosition()));
        }
        check.same("The solved replay follows the alternate rook and preserves the authored puzzle", List.of("f8f1", "e1f1"),
                List.of(three.finishingMove().uci(), authored.finishingMove().uci()));
        check.rejects("A material puzzle cannot open the mate-only replay", IllegalArgumentException.class,
                () -> QuestPayoff.forMateLine(QuestCatalog.loadTactics().getFirst()));
    }

    private static QuestCatalog.Quest catalog(Map<String, Object> row) {
        return QuestCatalog.readMaterial(Map.of("schema", 2, "objective", "WIN_MATERIAL", "quests", List.of(row)), "fixture").getFirst();
    }
}
