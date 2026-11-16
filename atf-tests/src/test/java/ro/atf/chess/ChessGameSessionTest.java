package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessGameSession.Outcome;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessGameSessionTest {
    private static final String ACTIVE = "1|f2f3 e7e5 g2g4";
    private static final String FINISHED = ACTIVE + " d8h4";

    @Test void onlyLegalMovesAtTheCurrentRevisionChangeTheMatch(Checks check) {
        var initial = ChessMatch.start(true);
        var session = new ChessGameSession(initial);
        check.same("A new local analysis game has an empty checkpoint at revision zero", List.of(0L, "2|analysis|"),
                List.of(session.revision(), session.match().checkpoint()));
        check.same("A pawn cannot jump three ranks", Outcome.UNAVAILABLE, session.play(0, move("f2f5")));
        check.same("A rejected move leaves the revision and board unchanged", List.of(0L, initial),
                List.of(session.revision(), session.match()));

        check.same("The first legal move advances the revision exactly once", Outcome.APPLIED, session.play(0, move("f2f3")));
        check.same("White's first move is committed and Black is to move", List.of(1L, 1, move("f2f3"), Side.BLACK),
                List.of(session.revision(), session.match().ply(), session.match().lastMove(), session.match().position().turn()));
        check.same("A legal Black reply from the old revision is stale", Outcome.STALE, session.play(0, move("e7e5")));
        check.same("A stale input leaves the current game untouched", List.of(1L, "2|analysis|f2f3"),
                List.of(session.revision(), session.match().checkpoint()));
        check.same("The same Black reply applies at the committed revision", Outcome.APPLIED, session.play(1, move("e7e5")));
        check.same("The immutable original game remains available", "2|analysis|", initial.checkpoint());
    }

    @Test void restoresAnActiveGameThenCompletesAndAnalyzesItsActualRecordedLine(Checks check) {
        var active = new ChessGameSession(ChessMatch.start());
        for (String uci : List.of("f2f3", "e7e5", "g2g4")) {
            check.same("The live game records " + uci, Outcome.APPLIED, active.play(active.revision(), move(uci)));
        }
        var checkpoint = active.match().checkpoint();
        check.same("The committed active checkpoint contains all three moves", ACTIVE, checkpoint);

        var resumed = new ChessGameSession(ChessMatch.restore(checkpoint));
        check.same("A fresh session restores the side, last move, ply and analysis settings",
                List.of(3, Side.BLACK, move("g2g4"), active.match().setup(), ACTIVE),
                List.of(resumed.match().ply(), resumed.match().position().turn(), resumed.match().lastMove(),
                        resumed.match().setup(), resumed.match().checkpoint()));
        check.same("The restored board matches the exact position before reload", true,
                resumed.match().position().sameForRepetition(active.match().position()));

        check.same("The resumed board accepts Black's legal mating reply", Outcome.APPLIED,
                resumed.play(resumed.revision(), move("d8h4")));
        var completed = resumed.match();
        check.same("The continued game completes at ply four with the correct checkpoint and winner",
                List.of(FINISHED, ChessMatch.Result.BLACK_WINS, move("d8h4"), true),
                List.of(completed.checkpoint(), completed.result(), completed.lastMove(), completed.position().isCheckmate()));
        check.same("The prior active session still contains the exact persisted checkpoint", ACTIVE, active.match().checkpoint());
        long finishedRevision = resumed.revision();
        check.same("A completed game refuses extra input", Outcome.UNAVAILABLE, resumed.play(finishedRevision, move("h2h3")));
        check.same("Terminal rejection preserves the result and revision", List.of(completed, finishedRevision),
                List.of(resumed.match(), resumed.revision()));

        var archive = GameReview.analyze(completed, ignored -> { });
        var review = new GameReviewSession(completed, archive, null);
        check.same("Analysis is generated for every move in the actual completed line", 4, archive.turns().size());
        check.same("Review can seek to White's recorded blunder", GameReviewSession.Outcome.APPLIED,
                review.dispatch(review.revision(), GameReviewSession.Action.SEEK, 3));
        var why = review.timeline().explanation().orElseThrow();
        check.same("The engine proves the mating queen reply after the blunder",
                List.of(GameReviewExplanation.Reason.ALLOWS_MATE, Kind.QUEEN, move("d8h4")),
                List.of(why.reason(), why.evidence().orElseThrow().piece(), why.evidence().orElseThrow().move()));
        check.same("Generating an analysis leaves the completed checkpoint intact", FINISHED, completed.checkpoint());
    }

    @Test void replayedCheckpointStillAllowsOnlyTheImmediateEnPassantCapture(Checks check) {
        var session = new ChessGameSession(ChessMatch.restore("2|analysis|e2e4 a7a6 e4e5 d7d5"));
        check.same("A restored game retains the immediate en passant right", Outcome.APPLIED,
                session.play(session.revision(), move("e5d6")));
        var board = session.match().position();
        check.same("The resumed capture removes the correct pawn", null, board.pieceAt(BoardSquare.parse("d5")));
        check.same("The capturing pawn reaches the right square", new Piece(Side.WHITE, Kind.PAWN),
                board.pieceAt(BoardSquare.parse("d6")));

        var delayed = new ChessGameSession(ChessMatch.restore("2|analysis|e2e4 a7a6 e4e5 d7d5 h2h3 a6a5"));
        check.same("A checkpoint restored after another turn refuses the same expired en passant move",
                Outcome.UNAVAILABLE, delayed.play(delayed.revision(), move("e5d6")));
        check.same("The expired attempt preserves the black pawn", new Piece(Side.BLACK, Kind.PAWN),
                delayed.match().position().pieceAt(BoardSquare.parse("d5")));
    }

    @Test void restoresAFreshUnfinishedAnalysisWithoutAcceptingItAsAnArchive(Checks check) {
        var source = ChessMatch.restore(ACTIVE);
        var report = GameReview.analyzeLine(source, ignored -> { });
        var digest = GameReviewCodec.checkpointDigest(source);
        var cache = GameReviewCodec.encode(report, digest);
        var reopened = ChessMatch.restore(ACTIVE);
        var resumed = GameReviewCodec.decodeLine(reopened, cache, digest);
        check.same("An unfinished analysis restores every evaluated move and score", report, resumed);
        check.same("An unfinished analysis carries no rating for either side", List.of(true, true),
                List.of(resumed.white().rating().isEmpty(), resumed.black().rating().isEmpty()));
        check.rejects("A strict archive cannot load an unfinished match as a completed game", IllegalArgumentException.class,
                () -> GameReviewCodec.decode(reopened, cache));

        var other = ChessMatch.restore("1|f2f3 e7e6 g2g4");
        check.rejects("An analysis cannot be resumed for a different checkpoint", IllegalArgumentException.class,
                () -> GameReviewCodec.decodeLine(other, cache, GameReviewCodec.checkpointDigest(other)));
        var bookmark = new GameReviewTimeline.Snapshot(2, GameReviewTimeline.Branch.HISTORY, 0);
        var review = new GameReviewSession(reopened, resumed, bookmark);
        check.same("A new review restores the selected position of the unfinished game", List.of(bookmark, move("e7e5")),
                List.of(review.snapshot(), review.lastMove()));
        check.same("The analyzed game still allows its next legal move after reopening", Outcome.APPLIED,
                new ChessGameSession(reopened).play(0, move("d8h4")));
        check.same("Analysis and continuation preserve the original unfinished snapshot", List.of(ACTIVE, ChessMatch.Result.IN_PROGRESS),
                List.of(source.checkpoint(), source.result()));
    }

    private static Move move(String uci) { return Move.fromUci(uci); }
}
