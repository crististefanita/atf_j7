package ro.atf.chess;

import ro.atf.chess.GameReviewSession.Action;
import ro.atf.chess.GameReviewSession.Outcome;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.MatchLedger.Continuation;
import ro.atf.chess.MatchLedger.ContinuationOutcome;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

/** Adapts the session to the worker's serializable view and revision. */
final class WebGameReview {
    private Archive archive;
    private int revision;

    String open(String id, ChessMatch match, String review, String bookmark, String digest, boolean stored, boolean blackBelow,
                boolean automaticContinuation) {
        if (!id.matches("[a-zA-Z0-9_.-]{1,100}")) throw new IllegalArgumentException("Invalid review archive ID");
        var cached = GameReviewCodec.decodeLine(match, review, digest);
        var snapshot = GameReviewBookmark.decode(id, match, bookmark, digest);
        var candidate = new Archive(id, match, digest, new GameReviewSession(match, cached, snapshot), stored, blackBelow, automaticContinuation);
        String view = view(candidate, revision + 1);
        archive = candidate;
        revision++;
        return view;
    }

    String action(String archiveId, int expectedRevision, String action, int ply, MatchStore matches) {
        if (archive == null) throw new IllegalStateException("Open a game review first");
        if (archive.stored() ? !archive.id().equals(archiveId) : archiveId != null) {
            throw new IllegalArgumentException("Review belongs to another archived match");
        }
        var session = archive.session();
        Outcome outcome = expectedRevision != revision ? Outcome.STALE
                : session.dispatch(session.revision(), Action.valueOf(action), ply);
        if (outcome == Outcome.APPLIED) {
            revision++;
            if (archive.stored() && !matches.saveReviewBookmark(archiveId, archive.match(), session.timeline().snapshot(), archive.digest())) {
                throw new IllegalStateException("The review position could not be saved");
            }
        }
        return "{\"outcome\":" + quote(outcome.name()) + ",\"view\":" + view(archive, revision) + "}";
    }

    Continuation continueArchive(String id, int expectedRevision, boolean automatic, MatchLedger ledger) {
        if (archive == null || expectedRevision != revision || !archive.id().equals(id)) {
            return new Continuation(ContinuationOutcome.STALE);
        }
        if (!archive.stored()) return new Continuation(ContinuationOutcome.UNAVAILABLE);
        return ledger.continueArchiveReview(id, archive.match(), archive.session().snapshot(), automatic);
    }

    void clear() { archive = null; }

    private static String view(Archive archive, int revision) {
        var timeline = archive.session().timeline();
        var position = timeline.position();
        var snapshot = timeline.snapshot();
        var turn = timeline.turn().orElse(null);
        var explanation = snapshot.explanationOpen() ? timeline.explanation().orElse(null) : null;
        var evidence = explanation == null ? null : explanation.evidence().orElse(null);
        boolean history = snapshot.branch() == Branch.HISTORY;
        return "{\"revision\":" + revision + ",\"fen\":" + quote(placement(position))
                + ",\"sideToMove\":" + quote(position.turn().name())
                + ",\"blackBelow\":" + archive.blackBelow()
                + ",\"ply\":" + snapshot.ply() + ",\"totalPlies\":" + timeline.totalPlies()
                + ",\"branch\":" + quote(snapshot.branch().name()) + ",\"step\":" + snapshot.step()
                + ",\"lineLength\":" + timeline.lineLength() + ",\"explanationOpen\":" + snapshot.explanationOpen()
                + ",\"canContinue\":" + (archive.stored() && MatchLedger.canContinueArchiveReview(archive.match(), snapshot))
                + ",\"automaticContinuation\":" + archive.automaticContinuation()
                + ",\"previous\":" + (history ? timeline.hasPrevious() : timeline.canStepBackward())
                + ",\"next\":" + (history ? timeline.hasNext() : timeline.canStepForward())
                + ",\"linePrevious\":" + timeline.canStepBackward() + ",\"lineNext\":" + timeline.canStepForward()
                + ",\"played\":" + timeline.canShowBranch(Branch.PLAYED) + ",\"recommended\":" + timeline.canShowBestLine()
                + ",\"why\":" + (turn != null && turn.loss() > 0)
                + ",\"lastMove\":" + move(timeline.lastMove()) + ",\"cueFrom\":" + square(timeline.cue().origin())
                + ",\"cueTo\":" + square(timeline.cue().destination()) + ",\"bookmark\":"
                + quote(GameReviewBookmark.encode(archive.id(), archive.match(), snapshot, archive.digest()))
                + ",\"moves\":" + moves(archive.match()) + ",\"grade\":" + (turn == null ? "null" : quote(turn.grade().name()))
                + ",\"loss\":" + (turn == null ? 0 : turn.loss()) + ",\"bestPoints\":" + (turn == null ? 0 : turn.bestPoints())
                + ",\"playedPoints\":" + (turn == null ? 0 : turn.playedPoints()) + ",\"depth\":" + (turn == null ? 0 : turn.depth())
                + ",\"reason\":" + (explanation == null ? "null" : quote(explanation.reason().name()))
                + ",\"materialCp\":" + (explanation == null ? 0 : explanation.materialCp())
                + ",\"decisivePiece\":" + (evidence == null ? "null" : quote(evidence.piece().name()))
                + ",\"decisiveSquare\":" + (evidence == null ? "null" : square(evidence.move().to()))
                + ",\"capturedPiece\":" + (evidence == null || evidence.captured() == null ? "null" : quote(evidence.captured().name())) + "}";
    }

    private static String moves(ChessMatch match) {
        var moves = new StringBuilder("[");
        for (int ply = 1; ply <= match.ply(); ply++) {
            if (ply > 1) moves.append(',');
            moves.append(move(match.moveAt(ply)));
        }
        return moves.append(']').toString();
    }

    private static String square(int square) { return square < 0 ? "null" : quote(BoardSquare.name(square)); }

    private record Archive(String id, ChessMatch match, String digest, GameReviewSession session,
                           boolean stored, boolean blackBelow, boolean automaticContinuation) { }
}
