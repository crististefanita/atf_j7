package ro.atf.chess;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import ro.atf.chess.ChessPosition.Side;

/** Settings resolved once before a match starts and retained in its checkpoint. */
public record MatchSetup(Side humanSide, Level level, boolean analysisEnabled) {
    public MatchSetup {
        Objects.requireNonNull(humanSide);
        Objects.requireNonNull(level);
    }

    public enum ColorChoice {
        WHITE, BLACK, RANDOM;

        public Side resolve(BooleanSupplier randomBlack) {
            return switch (this) {
                case WHITE -> Side.WHITE;
                case BLACK -> Side.BLACK;
                case RANDOM -> randomBlack.getAsBoolean() ? Side.BLACK : Side.WHITE;
            };
        }
    }

    public enum Level {
        BASIC, ADVANCED, HARD
    }
}
