package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview.Grade;
import ro.atf.chess.GameReviewExplanation.MoveEvidence;
import ro.atf.chess.GameReviewExplanation.Reason;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewTimelineTest {
    @Test void navigatesTheReviewAndReplaysTheBestLineWithoutChangingTheArchivedGame(Checks check) {
        var source = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        String archived = source.checkpoint();
        var review = GameReview.analyze(source, ignored -> { });
        var timeline = new GameReviewTimeline(source, review);

        check.same("The review opens on the final recorded position", source.ply(), timeline.ply());
        check.same("The opening belongs to the entry state", true, timeline.atEntry());
        check.same("The final frame has no following key moment", true, timeline.nextKeyMomentPly().isEmpty());
        check.same("An unavailable next move leaves the entry intact", List.of(false, true), List.of(timeline.next(), timeline.atEntry()));
        timeline.seek(1);
        check.same("Selecting a move hides the entry opening", false, timeline.atEntry());
        check.same("Next key moment skips ordinary moves and selects the missed defense", 3,
                timeline.nextKeyMomentPly().orElse(-1));
        check.same("Previous stops at the initial position", true, timeline.previous());
        check.same("Start is the exact initial position and cannot move backward", List.of(0, false, true),
                List.of(timeline.ply(), timeline.previous(), timeline.next()));
        check.same("The next control advances one move and clears any analysis branch", 1, timeline.ply());

        timeline.seek(3);
        var turn = timeline.turn().orElseThrow();
        var recommended = turn.bestLine().get(0);
        check.same("The selected review move is the source game move", source.moveAt(3), turn.played());
        check.same("The recommended line begins with a legal move from before the selected move", true,
                source.positionAt(2).legalMoves().contains(recommended));
        check.same("Show starts before the recommended move and marks its route", true, timeline.showBestLine());
        check.same("The board returns to the selected move's pre-position", true,
                source.positionAt(2).sameForRepetition(timeline.position()));
        check.same("The first recommended move is visible as the board cue", recommended,
                timeline.nextBestMove().orElseThrow());
        check.same("The first PV step advances the board and records the played branch move", true,
                timeline.nextBestLineStep());
        check.same("PV step one is the position after the recommended move", true,
                source.positionAt(2).play(recommended).sameForRepetition(timeline.position()));
        check.same("Previous PV returns to the pre-move position", true, timeline.previousBestLineStep());
        timeline.hideBestLine();
        check.same("Turning off the bulb restores the same played move without showing the opening again", List.of(3, false, false),
                List.of(timeline.ply(), timeline.showingBestLine(), timeline.atEntry()));
        check.same("The restored frame shows the exact played position", true, source.positionAt(3).sameForRepetition(timeline.position()));
        timeline.showBestLine();
        check.same("Selecting a different game ply exits PV mode", true, timeline.next());
        check.same("Selecting a different game ply restores the archived line", true,
                source.positionAt(4).sameForRepetition(timeline.position()));
        check.same("Review and PV exploration never modify the archived checkpoint or result",
                List.of(archived, ChessMatch.Result.BLACK_WINS), List.of(source.checkpoint(), source.result()));
        check.same("Returning to the final move keeps the opening hidden until reopening", false, timeline.atEntry());
        check.same("Reopening restores the entry state", true, new GameReviewTimeline(source, review).atEntry());
        check.rejects("A cached review cannot be paired with a different game having the same length",
                IllegalArgumentException.class, () -> new GameReviewTimeline(
                        ChessMatch.restore("1|e2e4 e7e5 g1f3 b8c6"), review));
        check.rejects("Out-of-range review navigation is rejected", IllegalArgumentException.class,
                () -> timeline.seek(source.ply() + 1));
    }

    @Test void explainsTheMatingReplyAndRestoresItsPlayedBranchFromTheArchivedCache(Checks check) {
        var source = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var recommended = moves("d2d4", "e5d4");
        var played = moves("g2g4", "d8h4");
        var report = review(source, 3, Grade.BLUNDER, 544, 0, recommended, played);
        String archived = source.checkpoint();
        String cache = GameReviewCodec.encode(source, report);
        var timeline = new GameReviewTimeline(source, report);
        timeline.seek(3);
        check.same("White's pawn move permits the queen's mating reply in the played line",
                new GameReviewExplanation(Reason.ALLOWS_MATE, Side.WHITE, 544, 4, 0,
                        Optional.of(new MoveEvidence(Move.fromUci("d8h4"), Kind.QUEEN, null))),
                timeline.explanation().orElseThrow());
        check.same("The played branch opens before the selected move", true, timeline.showBranch(Branch.PLAYED));
        check.same("The cue shows the actual pawn move from the pre-move position",
                List.of(true, played.get(0)), List.of(source.positionAt(2).sameForRepetition(timeline.position()),
                        timeline.nextLineMove().orElseThrow()));
        timeline.stepForward();
        check.same("At the first step, the next cue shows the opponent's queen reply", played.get(1),
                timeline.nextLineMove().orElseThrow());

        var reopenedGame = ChessMatch.restore(archived);
        var reopenedReport = GameReviewCodec.decode(reopenedGame, cache);
        var resumed = new GameReviewTimeline(reopenedGame, reopenedReport);
        check.same("Reopening restores the exact move, played branch and first step", true, resumed.restore(timeline.snapshot()));
        check.same("Resuming preserves the next cue and the selected game ply", List.of(timeline.snapshot(), played.get(1)),
                List.of(resumed.snapshot(), resumed.nextLineMove().orElseThrow()));
        check.same("The played line reaches the witnessed checkmate and stops there", List.of(true, true, false),
                List.of(resumed.stepForward(), resumed.position().isCheckmate(), resumed.stepForward()));
        check.same("The recommended branch resets to the same pre-move position", true, resumed.showBranch(Branch.RECOMMENDED));
        check.same("The recommendation cue differs from the played move while the source cursor stays at ply three",
                List.of(3, true, recommended.get(0)), List.of(resumed.ply(),
                        source.positionAt(2).sameForRepetition(resumed.position()), resumed.nextLineMove().orElseThrow()));
        resumed.stepForward();
        check.same("Going back reverses only the recommended branch", List.of(true, true),
                List.of(resumed.stepBackward(), source.positionAt(2).sameForRepetition(resumed.position())));
        check.same("Comparison leaves the archived move sequence, result and encoded report intact",
                List.of(archived, ChessMatch.Result.BLACK_WINS, cache),
                List.of(source.checkpoint(), source.result(), GameReviewCodec.encode(source, report)));
    }

    @Test void explainsBlackLosingMaterialAndUsesTheScoreForAPositionalDifference(Checks check) {
        var black = ChessMatch.restore("1|e2e4 e7e5 g1f3 h7h6");
        var report = review(black, 4, Grade.MISTAKE, 620, 490, moves("b8c6", "f1b5"), moves("h7h6", "f3e5"));
        var timeline = new GameReviewTimeline(black, report);
        check.same("Black's move loses a pawn to the white knight in the played line",
                new GameReviewExplanation(Reason.LOSES_MATERIAL, Side.BLACK, 130, 4, 100,
                        Optional.of(new MoveEvidence(Move.fromUci("f3e5"), Kind.KNIGHT, Kind.PAWN))),
                timeline.explanation().orElseThrow());

        var white = ChessMatch.restore("1|e2e4");
        var positional = review(white, 1, Grade.INACCURACY, 600, 535, moves("d2d4", "d7d5"), moves("e2e4", "e7e5"));
        check.same("A positional inaccuracy keeps the mover's score and depth without claiming a tactic",
                new GameReviewExplanation(Reason.EVALUATION, Side.WHITE, 65, 4, 0, Optional.empty()),
                new GameReviewTimeline(white, positional).explanation().orElseThrow());
    }

    @Test void identifiesTheMissedMateForBlackFromItsLegalRecommendation(Checks check) {
        var source = ChessMatch.restore("1|f2f3 e7e5 g2g4 b8c6");
        var report = review(source, 4, Grade.MISS, 1000, 710, moves("d8h4"), moves("b8c6", "g1h3"));
        var timeline = new GameReviewTimeline(source, report);
        check.same("The saved recommendation shows Black's missed mate with the queen",
                new GameReviewExplanation(Reason.MISSES_MATE, Side.BLACK, 290, 4, 0,
                        Optional.of(new MoveEvidence(Move.fromUci("d8h4"), Kind.QUEEN, null))),
                timeline.explanation().orElseThrow());
        timeline.showBranch(Branch.RECOMMENDED);
        timeline.stepForward();
        check.same("The one-move branch actually reaches checkmate", true, timeline.position().isCheckmate());
        timeline.showBranch(Branch.HISTORY);
        check.same("Returning to history restores the original uncompleted game and its last move",
                List.of(ChessMatch.Result.IN_PROGRESS, source.moveAt(4), true),
                List.of(source.result(), timeline.lastMove(), source.positionAt(4).sameForRepetition(timeline.position())));
    }

    @Test void rejectsIllegalCachedLinesAndFallsBackToTheSelectedHistoryPosition(Checks check) {
        var source = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var report = review(source, 3, Grade.BLUNDER, 544, 0, moves("d2d5"), moves("g2g4", "d8h4", "a2a3"));
        var timeline = new GameReviewTimeline(source, report);
        check.same("A corrupt continuation after mate cannot resume, but ply three remains selected",
                List.of(false, new Snapshot(3, Branch.HISTORY, 0)),
                List.of(timeline.restore(new Snapshot(3, Branch.PLAYED, 1)), timeline.snapshot()));
        check.same("An illegal recommendation cannot be opened", false, timeline.canShowBranch(Branch.RECOMMENDED));
        check.same("Unavailable lines preserve numerical evidence and the actual board",
                List.of(Reason.EVALUATION, true), List.of(timeline.explanation().orElseThrow().reason(),
                        source.positionAt(3).sameForRepetition(timeline.position())));
        check.same("An out-of-range bookmark leaves the current cursor alone", List.of(false, 3),
                List.of(timeline.restore(new Snapshot(5, Branch.HISTORY, 0)), timeline.ply()));

        var wrong = review(source, 3, Grade.BLUNDER, 544, 0, moves("d2d4"), moves("f3f4"));
        var wrongLine = new GameReviewTimeline(source, wrong);
        wrongLine.seek(3);
        check.same("A legal move different from the recorded move cannot stand in for the played line",
                List.of(false, true), List.of(wrongLine.canShowBranch(Branch.PLAYED), wrongLine.canShowBranch(Branch.RECOMMENDED)));
        check.same("An excessive step in a valid branch falls back to the selected history position",
                List.of(false, new Snapshot(3, Branch.HISTORY, 0)),
                List.of(wrongLine.restore(new Snapshot(3, Branch.RECOMMENDED, 2)), wrongLine.snapshot()));
    }

    private static GameReview review(ChessMatch source, int selected, Grade grade, int best, int played,
                                     List<Move> recommendedLine, List<Move> playedLine) {
        var turns = new ArrayList<GameReview.Turn>();
        for (int ply = 1; ply <= source.ply(); ply++) {
            var move = source.moveAt(ply);
            var side = source.positionAt(ply - 1).turn();
            if (ply == selected) {
                turns.add(new GameReview.Turn(ply, side, move, grade, best, played, best - played,
                        side == Side.WHITE ? played : 1000 - played, 4, recommendedLine, playedLine, 0));
            } else {
                turns.add(new GameReview.Turn(ply, side, move, Grade.GOOD, 500, 500, 0, 500, 4,
                        List.of(move), List.of(move), 0));
            }
        }
        var player = new GameReview.Player(OptionalInt.empty(), Optional.empty(), 0, Map.of());
        return new GameReview(turns, null, 0, 500, player, player);
    }

    private static List<Move> moves(String... values) {
        return Arrays.stream(values).map(Move::fromUci).toList();
    }
}
