package ro.atf.chess;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Exposes the portable chess engine to the browser worker. */
public final class ChessWebEngine {
    private static final WebGameReview REVIEW = new WebGameReview();
    private static final WebChessGame GAME = new WebChessGame();

    private ChessWebEngine() { }

    @JSExport
    public static String snapshot(String resourceNames) {
        return ChessWebParity.snapshot(resourceNames);
    }

    @JSExport
    public static String canonicalCheckpoint(String checkpoint) {
        return ChessMatch.restore(checkpoint).checkpoint();
    }

    @JSExport
    public static String openReview(String id, String checkpoint, String review, String bookmark, String digest) {
        return REVIEW.open(id, checkpoint, review, bookmark, digest);
    }

    @JSExport
    public static String reviewAction(int revision, String action, int ply) {
        return REVIEW.action(revision, action, ply);
    }

    @JSExport
    public static String openMatch(String checkpoint, String mode, String humanSide, String level) {
        return GAME.open(checkpoint, mode, humanSide, level);
    }

    @JSExport
    public static String playMatch(int revision, String move) { return GAME.play(revision, move); }

    @JSExport
    public static String playBot(int revision) { return GAME.playBot(revision); }

    @JSExport
    public static String analyzeReview(String checkpoint, String digest, Progress progress) {
        var match = ChessMatch.restore(checkpoint);
        // The local Web board records both sides without assigning a bot level or estimating Elo.
        var review = GameReview.analyzeLine(match, ply -> progress.onPly(ply, match.ply()));
        return GameReviewCodec.encode(review, digest);
    }

    @JSFunctor
    public interface Progress extends JSObject {
        void onPly(int completed, int total);
    }
}
