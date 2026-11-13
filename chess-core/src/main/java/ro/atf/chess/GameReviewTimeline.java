package ro.atf.chess;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReviewExplanation.Line;
import ro.atf.chess.GameReview.Grade;
import ro.atf.chess.GameReview.Turn;

/** Navigates an analysis and its alternatives without modifying the recorded game. */
public final class GameReviewTimeline {
    public enum Branch { HISTORY, PLAYED, RECOMMENDED }
    public record Snapshot(int ply, Branch branch, int step) { }

    private static final Set<Grade> KEY_MOMENTS = Set.of(Grade.BRILLIANT, Grade.GREAT, Grade.INACCURACY,
            Grade.MISTAKE, Grade.MISS, Grade.BLUNDER);

    private final ChessMatch source;
    private final GameReview review;
    private int ply;
    private int lineStep;
    private Branch branch = Branch.HISTORY;
    private ComparedLines comparedLines;
    private boolean atEntry = true;

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
    public boolean atEntry() { return atEntry; }
    public Branch branch() { return branch; }
    public int lineStep() { return lineStep; }
    public int lineLength() { return line(branch).length(); }
    public Snapshot snapshot() { return new Snapshot(ply, branch, lineStep); }

    /** An unavailable variation falls back to the selected position in the recorded game. */
    public boolean restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.ply() < 0 || snapshot.ply() > totalPlies()) return false;
        seek(snapshot.ply());
        Branch restoredBranch = snapshot.branch();
        if (restoredBranch == null || snapshot.step() < 0 || snapshot.step() > line(restoredBranch).length()
                || !showBranch(restoredBranch)) return false;
        lineStep = snapshot.step();
        return true;
    }

    public void seek(int selectedPly) {
        if (selectedPly < 0 || selectedPly > source.ply()) throw new IllegalArgumentException("Invalid review ply: " + selectedPly);
        ply = selectedPly;
        atEntry = false;
        comparedLines = null;
        hideBestLine();
    }

    public void hideBestLine() {
        branch = Branch.HISTORY;
        lineStep = 0;
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

    public boolean canShowBranch(Branch selected) {
        return selected == Branch.HISTORY || line(selected).available();
    }

    /** Both alternatives start at the exact position before the selected move. */
    public boolean showBranch(Branch selected) {
        if (!canShowBranch(selected)) return false;
        atEntry = false;
        branch = selected;
        lineStep = 0;
        return true;
    }

    public boolean canStepBackward() { return lineStep > 0; }
    public boolean canStepForward() { return lineStep < lineLength(); }

    public boolean stepBackward() {
        if (!canStepBackward()) return false;
        lineStep--;
        return true;
    }

    public boolean stepForward() {
        if (!canStepForward()) return false;
        lineStep++;
        return true;
    }

    public ChessPosition position() {
        return branch == Branch.HISTORY ? source.positionAt(ply) : line(branch).position(lineStep);
    }

    public Move lastMove() {
        if (branch == Branch.HISTORY) return source.moveAt(ply);
        return lineStep == 0 ? null : line(branch).moves().get(lineStep - 1);
    }

    /** Marks the next move on the selected alternative. */
    public QuestHint.Cue cue() {
        return nextLineMove().map(move -> new QuestHint.Cue(move.from(), move.to())).orElse(new QuestHint.Cue(-1, -1));
    }

    public Optional<Move> nextLineMove() {
        return canStepForward() ? Optional.of(line(branch).moves().get(lineStep)) : Optional.empty();
    }

    public Optional<GameReviewExplanation> explanation() {
        if (ply == 0) return Optional.empty();
        return Optional.of(comparedLines().explanation());
    }

    public boolean showingBestLine() { return branch == Branch.RECOMMENDED; }
    public int bestLineStep() { return showingBestLine() ? lineStep : 0; }
    public int bestLineLength() { return line(Branch.RECOMMENDED).length(); }
    public boolean canShowBestLine() { return canShowBranch(Branch.RECOMMENDED); }
    public boolean canStepBestLineBackward() { return showingBestLine() && canStepBackward(); }
    public boolean canStepBestLineForward() { return showingBestLine() && canStepForward(); }
    public boolean showBestLine() { return showBranch(Branch.RECOMMENDED); }
    public boolean previousBestLineStep() { return showingBestLine() && stepBackward(); }
    public boolean nextBestLineStep() { return showingBestLine() && stepForward(); }
    public Optional<Move> nextBestMove() {
        return showingBestLine() ? nextLineMove() : Optional.empty();
    }

    private Line line(Branch selected) {
        Objects.requireNonNull(selected);
        if (ply == 0 || selected == Branch.HISTORY) return Line.UNAVAILABLE;
        var lines = comparedLines();
        return selected == Branch.PLAYED ? lines.played() : lines.recommended();
    }

    private ComparedLines comparedLines() {
        if (comparedLines == null) {
            var turn = review.turns().get(ply - 1);
            var root = source.continueFrom(ply - 1);
            var playedMoves = turn.playedLine();
            var played = playedMoves.isEmpty() || !turn.played().equals(playedMoves.get(0))
                    ? Line.UNAVAILABLE : Line.replay(root, playedMoves);
            var recommended = Line.replay(root, turn.bestLine());
            comparedLines = new ComparedLines(played, recommended, GameReviewExplanation.compare(turn, played, recommended));
        }
        return comparedLines;
    }

    private record ComparedLines(Line played, Line recommended, GameReviewExplanation explanation) { }
}
