package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestReplayTest {
    @Test void preservesCatalogReplayCheckpointsAndTheAward(Checks check) {
        verifyPendingMateInTwo(check);
        verifyCompletedCatalogMateInThree(check);
    }

    private static void verifyPendingMateInTwo(Checks check) {
        var quest = QuestCatalog.loadMateInTwo().getFirst();
        var order = List.of(quest.id());
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        var current = store.read(order);
        var rewards = store.readRewards().interrupt(quest.id());
        check.same("Seed an assisted attempt after the first defense", true, store.save(current, rewards, 2));
        var original = Map.copyOf(disk.data);

        var reopened = new QuestStore(disk);
        int savedPly = reopened.ply(quest.id());
        check.same("The persisted Mat in 2 cursor is unsolved after the first defense",
                List.of(false, 2), List.of(reopened.read(order).currentSolved(), savedPly));
        var step = QuestStep.restore(savedPly, quest);
        var replay = QuestReplay.mate(quest, step, null);
        check.same("The unsolved replay opens at the saved defense", quest.defenseAfter(0), replay.frame().move());
        check.same("The starting frame is the pending mate position", true,
                step.position(quest).sameForRepetition(replay.frame().position()));
        check.same("The saved defense can be inspected in both directions", List.of(true, true),
                List.of(replay.hasPrevious(), replay.hasNext()));

        var finalFrame = replay.next();
        check.same("A single forward step reaches the actual mate", quest.matingMove(), finalFrame.frame().move());
        check.same("The final board is mate and forward ends at the same frame", List.of(true, false, true),
                List.of(finalFrame.frame().position().isCheckmate(), finalFrame.hasNext(), finalFrame.next() == finalFrame));
        check.same("Leaving the replay keeps the unsolved cursor, ply and assisted reward", original, Map.copyOf(disk.data));
        check.same("The persisted attempt remains unsolved", false, new QuestStore(disk).read(order).currentSolved());
    }

    private static void verifyCompletedCatalogMateInThree(Checks check) {
        var authored = QuestCatalog.loadMateInThree().getFirst();
        var order = List.of(authored.id());
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        var pending = store.read(order);
        check.same("Seed the first catalog Mat in 3 after the second defense", true,
                store.save(pending, store.readRewards(), 4));
        var step = QuestStep.restore(store.ply(authored.id()), authored);
        var mate = authored.matingMove();
        check.same("The finishing move is checkmate from the persisted stage", true, authored.assess(mate, step.stage()).solved());
        var award = store.readRewards().complete(pending);
        check.same("Save the catalog mate and award exactly once", true,
                store.saveFinish(pending.solveCurrent(), award.rewards(), step.finishCheckpoint(mate)));
        var original = Map.copyOf(disk.data);
        var reopened = new QuestStore(disk);
        check.same("The unassisted catalog Mat in 3 is worth ten points", 10, reopened.readRewards().points().get(authored.id()));

        var completed = authored.restoreFinish(reopened.finish(authored.id()));
        var replay = QuestReplay.mate(authored, step, completed);
        var expectedBoards = new ArrayList<ChessPosition>();
        expectedBoards.add(authored.position());
        for (var move : authored.line()) expectedBoards.add(expectedBoards.getLast().play(move));
        int last = authored.line().size();
        check.same("The catalog Mat in 3 contains five plies", 5, last);
        for (int ply = 0; ply <= last; ply++) {
            verifyFrame(check, "Forward", replay, authored, expectedBoards, ply);
            if (ply < last) replay = replay.next();
        }
        check.same("The final frame is mate and stops forward navigation", List.of(true, false, true),
                List.of(replay.frame().position().isCheckmate(), replay.hasNext(), replay.next() == replay));
        for (int ply = last - 1; ply >= 0; ply--) {
            replay = replay.previous();
            verifyFrame(check, "Backward", replay, authored, expectedBoards, ply);
        }
        check.same("The initial frame stops backward navigation", List.of(false, true),
                List.of(replay.hasPrevious(), replay.previous() == replay));
        check.same("Replaying the catalog mate preserves its finish and only award", original, Map.copyOf(disk.data));
    }

    private static void verifyFrame(Checks check, String direction, QuestReplay replay, QuestCatalog.Quest authored,
                                    List<ChessPosition> expectedBoards, int ply) {
        Move expectedMove = ply == 0 ? null : authored.line().get(ply - 1);
        check.same(direction + " cursor at ply " + ply, ply, replay.ply());
        check.same(direction + " catalog move at ply " + ply, expectedMove, replay.frame().move());
        check.same(direction + " board matches the catalog line at ply " + ply, true,
                expectedBoards.get(ply).sameForRepetition(replay.frame().position()));
    }

    @Test void replaysTheActualCompletedMateInThreeWithoutRewritingTheAward(Checks check) {
        var authored = QuestCatalog.loadMateInThree().stream().filter(quest -> quest.id().equals("mate3-01Kz0")).findFirst().orElseThrow();
        var order = List.of(authored.id());
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        var attempt = store.read(order);
        var award = store.readRewards().complete(attempt);
        check.same("Persist the actual alternate mate and its single award", true,
                store.saveFinish(attempt.solveCurrent(), award.rewards(), "2:f8f1"));
        var original = Map.copyOf(disk.data);
        var reopened = new QuestStore(disk);
        var completed = authored.restoreFinish(reopened.finish(authored.id()));

        var start = QuestReplay.mate(authored, QuestStep.restore(4, authored), completed);
        check.same("A completed longer puzzle opens the entire line from the initial board", true,
                start.ply() == 0 && !start.hasPrevious() && start.previous() == start
                        && authored.position().sameForRepetition(start.frame().position()));
        var secondDefense = start.next().next().next().next();
        check.same("Previous and Next return to the same actual second defense", completed.defenseAfter(1),
                secondDefense.previous().next().frame().move());
        var finalFrame = secondDefense.next();
        check.same("The preview ends on the alternate rook mate rather than the authored finish", "f8f1", finalFrame.frame().move().uci());
        check.same("The saved board and shown board agree on the final mate", true,
                completed.solvedPosition().sameForRepetition(finalFrame.frame().position()));
        check.same("Review and rewind leave the finish and score untouched", original, Map.copyOf(disk.data));
        check.same("The saved finish is still worth one award", 10, reopened.readRewards().points().get(authored.id()));
    }

    @Test void keepsDangerHiddenUntilRequestedAndClearsItOnFrameChange(Checks check) {
        var mate = QuestCatalog.load().getFirst();
        var terminal = QuestReplay.mate(mate, QuestStep.restore(0, mate), null);
        var expectedMate = new QuestDanger(QuestDanger.Kind.MATE, mate.explanation().king(), 0);
        check.same("Mat in one starts at mate with danger available but hidden", List.of(1, true, false),
                List.of(terminal.ply(), terminal.hasDanger(), terminal.shownDanger() != null));
        var marked = terminal.toggleDanger();
        check.same("The button reveals only the proven mated square", expectedMate, marked.shownDanger());
        var initial = marked.previous();
        check.same("Rewinding clears the request and removes the unavailable danger control", List.of(false, true),
                List.of(initial.hasDanger(), initial.toggleDanger() == initial));
        check.same("Revisiting the mate needs a new request", List.of(true, false),
                List.of(initial.next().hasDanger(), initial.next().shownDanger() != null));

        var tactic = QuestCatalog.loadTactics().stream().filter(quest -> quest.id().equals("tactic-002")).findFirst().orElseThrow();
        var payoff = QuestPayoff.forQuest(tactic);
        var capture = QuestReplay.at(payoff, payoff.plies());
        var expectedCapture = new QuestDanger(QuestDanger.Kind.CAPTURE, BoardSquare.parse("h1"), 500);
        check.same("The retained capture on h1 starts hidden", null, capture.shownDanger());
        var revealed = capture.toggleDanger();
        check.same("The requested reason carries the retained five-pawn value", expectedCapture, revealed.shownDanger());
        check.same("A second request hides the marker without changing the frame", List.of(true, false),
                List.of(revealed.toggleDanger().frame().equals(capture.frame()), revealed.toggleDanger().shownDanger() != null));
        check.rejects("An out-of-bounds replay cursor fails before the dialog opens", IllegalArgumentException.class,
                () -> QuestReplay.at(payoff, payoff.plies() + 1));
    }
}
