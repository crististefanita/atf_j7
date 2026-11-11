package ro.atf.chess;

import ro.atf.chess.ChessPosition.Side;

/** The information a game list needs before applying its language and layout. */
public record MatchListEntry(int slot, MatchSetup.Level opponent, int moves, Status status, boolean reviewed, long timestamp) {
    public enum Status { IN_PROGRESS, FINISHED, WON, LOST, DRAW, UNAVAILABLE }

    public static MatchListEntry saved(int slot, ChessMatch match, long startedAt) {
        if (match == null) return new MatchListEntry(slot, null, 0, Status.UNAVAILABLE, false, 0);
        var status = match.result() == ChessMatch.Result.IN_PROGRESS ? Status.IN_PROGRESS : Status.FINISHED;
        return new MatchListEntry(slot, match.setup().level(), match.ply(), status, false, startedAt);
    }

    public static MatchListEntry finished(MatchStore.ArchivedMatch entry) {
        var match = entry.match();
        var side = match.setup().humanSide();
        var status = switch (match.result()) {
            case WHITE_WINS -> side == Side.WHITE ? Status.WON : Status.LOST;
            case BLACK_WINS -> side == Side.BLACK ? Status.WON : Status.LOST;
            case STALEMATE, MATERIAL_DRAW, REPETITION_DRAW, MOVE_COUNT_DRAW, AGREED_DRAW -> Status.DRAW;
            case IN_PROGRESS -> throw new IllegalArgumentException("A finished list entry requires a result");
        };
        return new MatchListEntry(0, match.setup().level(), match.ply(), status, entry.reviewed(), entry.finishedAt());
    }

    public boolean hasTimestamp() { return timestamp != 0; }
}
