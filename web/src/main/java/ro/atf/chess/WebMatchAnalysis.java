package ro.atf.chess;

import ro.atf.chess.ChessMatch.Result;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.quote;

/** Analyzes one displayed match position without access to the match ledger. */
final class WebMatchAnalysis {
    private final WebHintReplay replay = new WebHintReplay();
    private Frame frame;

    String open(String checkpoint, int ply, int revision) {
        if (frame != null && revision <= frame.revision()) {
            boolean sameFrame = revision == frame.revision() && ply == frame.ply() && checkpoint.equals(frame.checkpoint());
            return state(sameFrame ? "APPLIED" : "STALE");
        }
        var match = ChessMatch.restore(checkpoint);
        if (ply < 0 || ply > match.ply()) throw new IllegalArgumentException("Analysis position is outside the match");
        var position = match.positionAt(ply);
        MatchHint hint = null;
        String evaluation = "null";
        if (match.analysisEnabled()) {
            if (ply == match.ply() && match.result() != Result.IN_PROGRESS) {
                evaluation = WebBoardState.terminalEvaluation(match.result());
            } else {
                var analyzed = match.hintAt(ply);
                evaluation = WebBoardState.evaluation(analyzed.evaluation(position));
                if (analyzed.count() > 0) hint = analyzed;
            }
        }
        replay.close();
        frame = new Frame(checkpoint, ply, revision, position, hint, evaluation);
        return state("APPLIED");
    }

    String action(int revision, String action, int index) {
        if (frame == null) return state("UNAVAILABLE");
        if (revision != frame.revision()) return state("STALE");
        boolean applied = switch (action) {
            case "hint" -> reveal();
            case "preview" -> replay.open(frame.position(), frame.hint());
            case "preview-close" -> replay.close();
            case "preview-branch" -> replay.select(index);
            case "preview-seek" -> replay.seek(index);
            case "preview-proof" -> replay.prove();
            default -> throw new IllegalArgumentException("Unknown match analysis action");
        };
        return state(applied ? "APPLIED" : "UNAVAILABLE");
    }

    private boolean reveal() {
        if (frame.hint() == null || replay.isOpen()) return false;
        frame.hint().revealNext();
        return true;
    }

    private String state(String outcome) {
        String fields = "{\"outcome\":" + quote(outcome) + ",\"analysis\":";
        if (frame == null) return fields + "null}";
        return fields + "{\"revision\":" + frame.revision() + ",\"ply\":" + frame.ply()
                + ",\"evaluation\":" + frame.evaluation() + ",\"hint\":" + hint(frame.hint()) + ",\"preview\":" + replay.state() + "}}";
    }

    private static String hint(MatchHint hint) {
        return hint == null ? "null" : "{\"count\":" + hint.count() + ",\"number\":" + hint.number()
                + ",\"move\":" + move(hint.revealedMove()) + "}";
    }

    private record Frame(String checkpoint, int ply, int revision, ChessPosition position, MatchHint hint, String evaluation) { }
}
