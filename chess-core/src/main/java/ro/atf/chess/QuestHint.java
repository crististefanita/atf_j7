package ro.atf.chess;

/** How much of the engine-verified reply the player has chosen to reveal. */
public enum QuestHint {
    NONE, PIECE, SOLUTION;

    public QuestHint advance() {
        return this == NONE ? PIECE : SOLUTION;
    }

    public Cue cue(ChessPosition.Move move) {
        return new Cue(this == NONE ? -1 : move.from(), this == SOLUTION ? move.to() : -1);
    }

    public record Cue(int origin, int destination) {
        public DisplayCue onBoard(boolean blackBelow) {
            return new DisplayCue(origin < 0 ? -1 : BoardSquare.displayIndex(origin, blackBelow),
                    destination < 0 ? -1 : BoardSquare.displayIndex(destination, blackBelow));
        }
    }

    public record DisplayCue(int originIndex, int destinationIndex) { }
}
