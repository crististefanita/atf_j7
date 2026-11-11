package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview.Grade;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewTest {
    @Test void explainsAnActualMissedDefenseAndTheMateEvenWithLiveHintsOff(Checks check) {
        var match = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var progress = new ArrayList<Integer>();
        var review = GameReview.analyze(match, progress::add);
        check.same("Every move is analyzed in chronological order with analysis disabled during play", List.of(1, 2, 3, 4), progress);
        var blunder = review.turns().get(2);
        check.same("The move opening the diagonal is compared with a better defense", Grade.BLUNDER, blunder.grade());
        check.same("The played line demonstrates the immediate mate", List.of(Move.fromUci("g2g4"), Move.fromUci("d8h4")), blunder.playedLine());
        check.same("The better line starts with a different legal White move", true,
                !blunder.played().equals(blunder.bestLine().get(0)) && match.positionAt(2).legalMoves().contains(blunder.bestLine().get(0)));
        var mate = review.turns().get(3);
        check.same("Black's actual mate stays on the Black-winning end of the White graph", List.of(Side.BLACK, 1000, 0),
                List.of(mate.side(), mate.playedPoints(), mate.whitePoints()));
        check.same("The graph already reflects the forced mate after White's mistake", 0, blunder.whitePoints());
        check.same("The short game withholds a player estimate and retains the AI benchmark", List.of(Optional.empty(),
                Optional.of(GameReviewPolicy.CURRENT.opponentRatings.get(MatchSetup.Level.BASIC))),
                List.of(review.white().rating(), review.black().rating()));
        check.same("White's blunder is counted on the White side only", List.of(1, 0),
                List.of(review.white().grades().getOrDefault(Grade.BLUNDER, 0), review.black().grades().getOrDefault(Grade.BLUNDER, 0)));
        check.same("Review leaves the playable record intact", "1|f2f3 e7e5 g2g4 d8h4", match.checkpoint());
    }

    @Test void recognizesTheOpeningByPositionAndDoesNotMarkAnUnknownSuffixAsBook(Checks check) {
        var start = OpeningIndex.CURRENT.recognize(ChessMatch.start());
        check.same("An unplayed match has no opening", new OpeningIndex.Match(null, 0), start);
        var italian = OpeningIndex.CURRENT.recognize(ChessMatch.restore("1|e2e4 e7e5 g1f3 b8c6 f1c4"));
        check.same("The familiar Italian position is identified from the broader ECO index", List.of("C50", 5),
                List.of(italian.opening().eco(), italian.bookPlies()));
        var declined = OpeningIndex.CURRENT.recognize(ChessMatch.restore("1|d2d4 d7d5 c2c4 e7e6"));
        var transposed = OpeningIndex.CURRENT.recognize(ChessMatch.restore("1|d2d4 e7e6 c2c4 d7d5"));
        check.same("A transposition identifies the same named opening", declined.opening(), transposed.opening());
        var deviated = OpeningIndex.CURRENT.recognize(ChessMatch.restore("1|e2e4 e7e5 g1f3 b8c6 f1c4 a7a5 a2a4"));
        check.same("An unfamiliar suffix is not counted as book", 5, deviated.bookPlies());
    }

    @Test void classifiesExpectedLossAndRequiresEvidenceForExceptionalMoves(Checks check) {
        check.same("The same loss thresholds apply to both colors", List.of(Grade.EXCELLENT, Grade.GOOD, Grade.INACCURACY, Grade.MISTAKE, Grade.BLUNDER),
                List.of(19, 20, 50, 100, 200).stream()
                        .map(loss -> GameReview.classify(false, false, false, 5, 700, 700 - loss, 700, 0, 0, false)).toList());
        check.same("Missing an exposed winning chance is distinguished from an ordinary blunder", Grade.MISS,
                GameReview.classify(false, false, false, 5, 800, 500, 800, 0, 130, false));
        check.same("A unique winning sacrifice receives the exceptional classification", Grade.BRILLIANT,
                GameReview.classify(false, false, true, 5, 810, 810, 600, 320, 0, false));
        check.same("A near-tied alternative prevents a false brilliant label", Grade.BEST,
                GameReview.classify(false, false, true, 5, 810, 810, 780, 320, 0, false));
        check.same("A shallow comparison cannot prove an exceptional move", Grade.BEST,
                GameReview.classify(false, false, true, 3, 810, 810, 600, 320, 0, false));
        check.same("A legal move with no alternative is forced even when it gives mate", Grade.FORCED,
                GameReview.classify(false, true, true, 5, 1000, 1000, 0, 320, 0, true));
    }

    @Test void provesTheQueenSacrificeAndDistinguishesTwoMatingLines(Checks check) {
        var moves = List.of(Move.fromUci("g6h7"), Move.fromUci("h8h7"), Move.fromUci("a1h1"));
        var finished = ChessMatch.fromPosition(ChessPosition.fromFen("r1n3rk/q3N1pp/6Q1/8/8/8/2K5/R7 w - - 0 1"));
        for (var move : moves) finished = finished.play(move);
        check.same("The studied sacrifice actually ends in mate", ChessMatch.Result.WHITE_WINS, finished.result());
        var turn = GameReview.analyze(finished, ignored -> { }).turns().get(0);
        check.same("The unique proved mate is recognized as a brilliant sacrifice", Grade.BRILLIANT, turn.grade());
        check.same("The evidence shows the accepted queen sacrifice and mate", moves, turn.playedLine());
        check.same("Queen for pawn costs eight pawns before the mate", 800, turn.sacrificeCp());

        var twoMates = ChessMatch.fromPosition(ChessPosition.fromFen("rrn4k/4N1pp/8/8/8/7Q/2K5/R7 w - - 0 1"));
        var search = TacticalAnalysis.review(twoMates.position(), Move.fromUci("h3h7"), twoMates.automaticDrawMoves(), GameReviewPolicy.CURRENT.search);
        check.same("The presence of another proved mate suppresses the uniqueness label", false, search.onlyProvedMate());
    }

    @Test void rejectsAnUnfinishedMatchAndStopsAtTheNextPlyWhenCancelled(Checks check) {
        check.rejects("Live play cannot enter the finished-game review", IllegalArgumentException.class,
                () -> GameReview.analyze(ChessMatch.start(), ignored -> { }));
        var finished = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        var visited = new ArrayList<Integer>();
        try {
            check.rejects("An interrupted review cannot publish a partial result", CancellationException.class,
                    () -> GameReview.analyze(finished, ply -> {
                        visited.add(ply);
                        Thread.currentThread().interrupt();
                    }));
        } finally {
            Thread.interrupted();
        }
        check.same("Cancellation prevents evaluation of further plies", List.of(1), visited);
    }

    @Test void performanceUsesTheActualResultFromTheHumanPerspectiveAndShowsUncertainty(Checks check) {
        var reference = new GameReview.Rating(1500, 1350, 1650);
        int minimum = 10;
        var win = GameReview.estimate(ChessMatch.Result.BLACK_WINS, Side.BLACK, minimum, reference, minimum).orElseThrow();
        var draw = GameReview.estimate(ChessMatch.Result.REPETITION_DRAW, Side.BLACK, minimum, reference, minimum).orElseThrow();
        var loss = GameReview.estimate(ChessMatch.Result.BLACK_WINS, Side.WHITE, minimum, reference, minimum).orElseThrow();
        check.same("The same outcome is a win or a loss according to the player's color", List.of(1620, 1500, 1380),
                List.of(win.elo(), draw.elo(), loss.elo()));
        check.same("The draw interval is symmetric and incorporates the reference uncertainty", true,
                draw.elo() - draw.low() == draw.high() - draw.elo() && draw.low() < reference.low() && draw.high() > reference.high());
        check.same("One win leaves a wider range than the reference and still overlaps it", true,
                win.low() < reference.low() && win.high() > reference.high());
        check.same("The model retains an unbounded endpoint from the reference", null,
                GameReview.estimate(ChessMatch.Result.WHITE_WINS, Side.WHITE, minimum, new GameReview.Rating(1245, null, 1442), minimum)
                        .orElseThrow().low());
        check.same("Too few non-book choices yield no player estimate", Optional.empty(),
                GameReview.estimate(ChessMatch.Result.WHITE_WINS, Side.WHITE, minimum - 1, reference, minimum));
    }
}
