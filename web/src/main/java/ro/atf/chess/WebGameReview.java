package ro.atf.chess;

import ro.atf.chess.ChessPosition.Kind;
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
    private static final String ARTWORK = artwork();
    private Archive archive;
    private int revision;

    String open(String id, ChessMatch match, String review, String bookmark, String digest, boolean stored, boolean blackBelow,
                boolean automaticContinuation) {
        if (!id.matches("[a-zA-Z0-9_.-]{1,100}")) throw new IllegalArgumentException("Invalid review archive ID");
        var cached = GameReviewCodec.decodeLine(match, review, digest);
        var snapshot = GameReviewBookmark.decode(id, match, bookmark, digest);
        var candidate = new Archive(id, match, digest, new GameReviewSession(match, cached, snapshot), stored, blackBelow,
                automaticContinuation, report(match, cached));
        String view = view(candidate, revision + 1);
        archive = candidate;
        revision++;
        return view;
    }

    String selectGraph(String id, int expectedRevision, double x, double width, MatchStore matches) {
        if (archive == null) throw new IllegalStateException("Open a game review first");
        int ply = GameReviewGraphLayout.plyAtX(x, archive.match().ply(), width, 2);
        return action(id, expectedRevision, Action.SEEK.name(), ply, matches);
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
                + ",\"atEntry\":" + timeline.atEntry()
                + ",\"nextKey\":" + timeline.nextKeyMomentPly().isPresent()
                + ",\"nextLineMove\":" + move(timeline.nextLineMove().orElse(null))
                + ",\"mode\":" + quote(mode(archive))
                + ",\"blackBelow\":" + archive.blackBelow()
                + ",\"ply\":" + snapshot.ply() + ",\"totalPlies\":" + timeline.totalPlies()
                + ",\"branch\":" + quote(snapshot.branch().name()) + ",\"step\":" + snapshot.step()
                + ",\"lineLength\":" + timeline.lineLength() + ",\"explanationOpen\":" + snapshot.explanationOpen()
                + ",\"canContinue\":" + (archive.stored() && MatchLedger.canContinueArchiveReview(archive.match(), snapshot))
                + ",\"canContinueStudy\":" + (!archive.stored() && history
                        && (snapshot.ply() < archive.match().ply() || archive.match().result() == ChessMatch.Result.IN_PROGRESS))
                + ",\"automaticContinuation\":" + archive.automaticContinuation()
                + ",\"previous\":" + (history ? timeline.hasPrevious() : timeline.canStepBackward())
                + ",\"next\":" + (history ? timeline.hasNext() : timeline.canStepForward())
                + ",\"linePrevious\":" + timeline.canStepBackward() + ",\"lineNext\":" + timeline.canStepForward()
                + ",\"played\":" + timeline.canShowBranch(Branch.PLAYED) + ",\"recommended\":" + timeline.canShowBestLine()
                + ",\"why\":" + (turn != null && turn.loss() > 0)
                + ",\"lastMove\":" + move(timeline.lastMove()) + ",\"cueFrom\":" + square(timeline.cue().origin())
                + ",\"cueTo\":" + square(timeline.cue().destination()) + ",\"bookmark\":"
                + quote(GameReviewBookmark.encode(archive.id(), archive.match(), snapshot, archive.digest()))
                + archive.report() + ",\"pieceArt\":" + ARTWORK
                + ",\"grade\":" + (turn == null ? "null" : quote(turn.grade().name()))
                + ",\"sacrificeCp\":" + (turn == null ? 0 : turn.sacrificeCp())
                + ",\"loss\":" + (turn == null ? 0 : turn.loss()) + ",\"bestPoints\":" + (turn == null ? 0 : turn.bestPoints())
                + ",\"playedPoints\":" + (turn == null ? 0 : turn.playedPoints()) + ",\"depth\":" + (turn == null ? 0 : turn.depth())
                + ",\"reason\":" + (explanation == null ? "null" : quote(explanation.reason().name()))
                + ",\"materialCp\":" + (explanation == null ? 0 : explanation.materialCp())
                + ",\"decisivePiece\":" + (evidence == null ? "null" : quote(evidence.piece().name()))
                + ",\"decisiveSquare\":" + (evidence == null ? "null" : square(evidence.move().to()))
                + ",\"capturedPiece\":" + (evidence == null || evidence.captured() == null ? "null" : quote(evidence.captured().name())) + "}";
    }

    private static String report(ChessMatch match, GameReview review) {
        var moves = new StringBuilder("[");
        var grades = new StringBuilder("[");
        var points = new StringBuilder("[").append(review.initialWhitePoints());
        for (var turn : review.turns()) {
            appendElement(moves, move(turn.played()));
            appendElement(grades, quote(turn.grade().name()));
            points.append(',').append(turn.whitePoints());
        }
        var human = match.setup().humanSide();
        var counts = new StringBuilder("[");
        for (var count : review.compareGrades(human)) {
            appendElement(counts, "{\"grade\":" + quote(count.grade().name()) + ",\"human\":" + count.human() + ",\"ai\":" + count.ai() + "}");
        }
        var opening = review.opening();
        return ",\"moves\":" + moves.append(']') + ",\"gradeKeys\":" + grades.append(']') + ",\"whitePoints\":" + points.append(']')
                + ",\"opening\":" + (opening == null ? "null" : "{\"eco\":" + quote(opening.eco()) + ",\"name\":" + quote(opening.name()) + "}")
                + ",\"humanSide\":" + quote(human.name()) + ",\"level\":" + quote(match.setup().level().name())
                + ",\"gradeCounts\":" + counts.append(']') + ",\"players\":{\"WHITE\":" + player(review.white()) + ",\"BLACK\":" + player(review.black()) + "}";
    }

    private static String player(GameReview.Player player) {
        var rating = player.rating().orElse(null);
        String estimate = rating == null ? "null" : "{\"elo\":" + rating.elo() + ",\"low\":" + rating.low() + ",\"high\":" + rating.high() + "}";
        return "{\"accuracy\":" + (player.accuracy().isPresent() ? Integer.toString(player.accuracy().getAsInt()) : "null")
                + ",\"ratedMoves\":" + player.ratedMoves() + ",\"rating\":" + estimate + "}";
    }

    static String pieceArtwork() { return ARTWORK; }

    private static String artwork() {
        var paths = new StringBuilder("{");
        for (Kind kind : Kind.values()) {
            var art = ChessPieceArtwork.forKind(kind);
            appendElement(paths, quote("pnbrqk".substring(kind.ordinal(), kind.ordinal() + 1)) + ":{\"body\":" + quote(art.body())
                    + ",\"details\":" + quote(art.details()) + "}");
        }
        return paths.append('}').toString();
    }

    private static void appendElement(StringBuilder sequence, String element) {
        if (sequence.length() > 1) sequence.append(',');
        sequence.append(element);
    }

    private static String mode(Archive archive) {
        if (archive.stored()) return archive.automaticContinuation() ? "REVIEW_MATCH" : "REVIEW_MANUAL";
        return archive.match().result() == ChessMatch.Result.IN_PROGRESS ? "REVIEW_LIVE" : "REVIEW_STUDY";
    }

    private static String square(int square) { return square < 0 ? "null" : quote(BoardSquare.name(square)); }

    private record Archive(String id, ChessMatch match, String digest, GameReviewSession session,
                           boolean stored, boolean blackBelow, boolean automaticContinuation, String report) { }
}
