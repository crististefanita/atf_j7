package ro.atf.chess;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReview.Grade;
import ro.atf.chess.GameReview.Turn;

/** Read-only cursor and replay branch for a completed game review. */
public final class GameReviewTimeline {
    private static final Set<Grade> KEY_MOMENTS = Set.of(Grade.BRILLIANT, Grade.GREAT, Grade.INACCURACY,
            Grade.MISTAKE, Grade.MISS, Grade.BLUNDER);

    private final ChessMatch source;
    private final GameReview review;
    private int ply;
    private int bestLineStep;
    private boolean bestLineVisible;

    public GameReviewTimeline(ChessMatch source, GameReview review) {
        if (source == null || review == null || source.ply() != review.turns().size()) {
            throw new IllegalArgumentException("Review must describe every move in the source game");
        }
        for (int index = 0; index < review.turns().size(); index++) {
            Turn turn = review.turns().get(index);
            if (turn.ply() != index + 1 || !turn.played().equals(source.moveAt(index + 1))
                    || turn.side() != source.positionAt(index).turn()) {
                throw new IllegalArgumentException("Review move does not match source ply " + (index + 1));
            }
        }
        this.source = source;
        this.review = review;
        ply = source.ply();
    }

    public int ply() { return ply; }
    public int totalPlies() { return source.ply(); }
    public boolean hasPrevious() { return ply > 0; }
    public boolean hasNext() { return ply < source.ply(); }
    public Optional<Turn> turn() { return ply == 0 ? Optional.empty() : Optional.of(review.turns().get(ply - 1)); }
    public boolean showingBestLine() { return bestLineVisible; }
    public int bestLineStep() { return bestLineStep; }
    public int bestLineLength() { return turn().map(value -> value.bestLine().size()).orElse(0); }
    public boolean canShowBestLine() { return turn().map(value -> !value.bestLine().isEmpty()).orElse(false); }
    public boolean canStepBestLineBackward() { return bestLineVisible && bestLineStep > 0; }
    public boolean canStepBestLineForward() { return bestLineVisible && bestLineStep < bestLineLength(); }

    public void seek(int selectedPly) {
        if (selectedPly < 0 || selectedPly > source.ply()) throw new IllegalArgumentException("Invalid review ply: " + selectedPly);
        ply = selectedPly;
        bestLineVisible = false;
        bestLineStep = 0;
    }

    public boolean previous() {
        if (!hasPrevious()) return false;
        seek(ply - 1);
        return true;
    }

    public boolean next() {
        if (!hasNext()) return false;
        seek(ply + 1);
        return true;
    }

    /** Finds the next meaningful positive or negative turning point without wrapping. */
    public OptionalInt nextKeyMomentPly() {
        for (int index = ply; index < review.turns().size(); index++) {
            if (KEY_MOMENTS.contains(review.turns().get(index).grade())) return OptionalInt.of(index + 1);
        }
        return OptionalInt.empty();
    }

    /** Starts the recommended line before the selected played move. */
    public boolean showBestLine() {
        if (!canShowBestLine()) return false;
        bestLineVisible = true;
        bestLineStep = 0;
        return true;
    }

    public boolean previousBestLineStep() {
        if (!canStepBestLineBackward()) return false;
        bestLineStep--;
        return true;
    }

    public boolean nextBestLineStep() {
        if (!canStepBestLineForward()) return false;
        bestLineStep++;
        return true;
    }

    public ChessPosition position() {
        if (!bestLineVisible) return source.positionAt(ply);
        ChessPosition position = source.positionAt(ply - 1);
        var line = turn().orElseThrow().bestLine();
        for (int index = 0; index < bestLineStep; index++) position = position.play(line.get(index));
        return position;
    }

    public Move lastMove() {
        if (!bestLineVisible) return source.moveAt(ply);
        return bestLineStep == 0 ? null : turn().orElseThrow().bestLine().get(bestLineStep - 1);
    }

    /** Marks the next recommended move until the line has been fully replayed. */
    public QuestHint.Cue cue() {
        if (!bestLineVisible || !canStepBestLineForward()) return new QuestHint.Cue(-1, -1);
        Move next = turn().orElseThrow().bestLine().get(bestLineStep);
        return new QuestHint.Cue(next.from(), next.to());
    }

    public Optional<Move> nextBestMove() {
        if (!bestLineVisible || !canStepBestLineForward()) return Optional.empty();
        return Optional.of(turn().orElseThrow().bestLine().get(bestLineStep));
    }
}
