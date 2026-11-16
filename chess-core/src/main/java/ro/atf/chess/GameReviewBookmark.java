package ro.atf.chess;

import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

/** The stored review cursor, scoped to an archive entry and its exact game. */
final class GameReviewBookmark {
    private static final String VERSION = "2";

    private GameReviewBookmark() { }

    static String encode(String id, ChessMatch match, Snapshot snapshot) {
        return encode(id, match, snapshot, GameReviewCodec.checkpointDigest(match));
    }

    static String encode(String id, ChessMatch match, Snapshot snapshot, String digest) {
        if (!validId(id) || !valid(match, snapshot)) return null;
        return VERSION + "|" + id + "|" + digest + "|" + snapshot.ply() + "|" + snapshot.branch().name()
                + "|" + snapshot.step() + "|" + snapshot.explanationOpen();
    }

    static Snapshot decode(String id, ChessMatch match, String encoded) {
        return decode(id, match, encoded, GameReviewCodec.checkpointDigest(match));
    }

    static Snapshot decode(String id, ChessMatch match, String encoded, String digest) {
        if (!validId(id) || encoded == null) return null;
        try {
            String[] fields = encoded.split("\\|", -1);
            boolean legacy = fields.length == 6 && "1".equals(fields[0]);
            boolean current = fields.length == 7 && VERSION.equals(fields[0])
                    && ("true".equals(fields[6]) || "false".equals(fields[6]));
            if ((!legacy && !current) || !id.equals(fields[1]) || !digest.equals(fields[2])) return null;
            int ply = Integer.parseInt(fields[3]);
            var branch = Branch.valueOf(fields[4]);
            int step = Integer.parseInt(fields[5]);
            var snapshot = legacy ? new Snapshot(ply, branch, step) : new Snapshot(ply, branch, step, "true".equals(fields[6]));
            return valid(match, snapshot) ? snapshot : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean validId(String id) { return id != null && !id.isBlank() && id.indexOf('|') < 0; }

    private static boolean valid(ChessMatch match, Snapshot snapshot) {
        if (snapshot == null || snapshot.branch() == null || snapshot.ply() < 0 || snapshot.ply() > match.ply()
                || snapshot.step() < 0 || snapshot.step() > GameReviewCodec.MAX_LINE_LENGTH) return false;
        if (snapshot.ply() == 0 && snapshot.explanationOpen()) return false;
        return snapshot.branch() == Branch.HISTORY ? snapshot.step() == 0 : snapshot.ply() > 0;
    }
}
