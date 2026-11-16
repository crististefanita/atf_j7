package ro.atf.chess;

import ro.atf.chess.ChessGameSession.Outcome;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

final class WebChessGame {
    private enum Mode { LOCAL, AI }
    private static final String AI_PREFIX = "ai|";
    private ChessGameSession session;
    private Mode mode;
    private int revision;

    String open(String checkpoint, String mode, String humanSide, String level) {
        if (checkpoint != null) {
            boolean automatic = checkpoint.startsWith(AI_PREFIX);
            var raw = automatic ? checkpoint.substring(AI_PREFIX.length()) : checkpoint;
            return replace(ChessMatch.restore(raw), automatic ? Mode.AI : Mode.LOCAL);
        }
        Mode choice = mode == null ? Mode.LOCAL : Mode.valueOf(mode);
        var match = choice == Mode.LOCAL ? ChessMatch.start()
                : ChessMatch.start(new MatchSetup(Side.valueOf(humanSide), Level.valueOf(level), false));
        return replace(match, choice);
    }

    private String replace(ChessMatch match, Mode choice) {
        var next = new ChessGameSession(match, choice == Mode.AI);
        String projection = view(next, choice, revision + 1);
        session = next;
        mode = choice;
        revision++;
        return projection;
    }

    String play(int expectedRevision, String uci) {
        requireSession();
        return finish(expectedRevision != revision ? Outcome.STALE : session.play(session.revision(), Move.fromUci(uci)));
    }

    String playBot(int expectedRevision) {
        requireSession();
        return finish(expectedRevision != revision ? Outcome.STALE : session.playBot(session.revision()));
    }

    private void requireSession() {
        if (session == null) throw new IllegalStateException("Open a game first");
    }

    private String finish(Outcome outcome) {
        if (outcome == Outcome.APPLIED) revision++;
        return "{\"outcome\":" + quote(outcome.name()) + ",\"match\":" + view(session, mode, revision) + "}";
    }

    private static String view(ChessGameSession session, Mode mode, int revision) {
        var match = session.match();
        var position = match.position();
        boolean awaitingBot = session.awaitingBot();
        var legal = new StringBuilder("[");
        if (!awaitingBot && match.result() == ChessMatch.Result.IN_PROGRESS) {
            for (Move candidate : position.legalMoves()) {
                if (legal.length() > 1) legal.append(',');
                legal.append(move(candidate));
            }
        }
        String raw = match.checkpoint();
        String checkpoint = mode == Mode.AI ? AI_PREFIX + raw : raw;
        return "{\"revision\":" + revision + ",\"fen\":" + quote(placement(position))
                + ",\"sideToMove\":" + quote(position.turn().name()) + ",\"checkpoint\":" + quote(checkpoint)
                + ",\"reviewCheckpoint\":" + quote(raw) + ",\"mode\":" + quote(mode.name())
                + ",\"humanSide\":" + quote(match.setup().humanSide().name()) + ",\"level\":" + quote(match.setup().level().name())
                + ",\"awaitingBot\":" + awaitingBot
                + ",\"ply\":" + match.ply() + ",\"result\":" + quote(match.result().name())
                + ",\"legalMoves\":" + legal.append(']') + ",\"lastMove\":" + move(match.lastMove()) + "}";
    }
}
