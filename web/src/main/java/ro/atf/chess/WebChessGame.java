package ro.atf.chess;

import java.util.function.Supplier;
import org.teavm.jso.JSBody;
import ro.atf.chess.ChessGameSession.Outcome;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.ColorChoice;
import ro.atf.chess.MatchSetup.Level;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

final class WebChessGame {
    private enum Mode { LOCAL, AI }

    private MatchLedger ledger;
    private ChessGameSession session;
    private Mode mode;
    private boolean blackBelow;
    private int slot;
    private String archiveId;
    private int viewPly;
    private int revision;

    String openLedger(String saved, String legacyLocal, String legacyAi) {
        ledger = MatchLedger.open(saved, legacyLocal, legacyAi, System::currentTimeMillis, WebChessGame::newId);
        return select(null, null, false, 0, null);
    }

    String open(String route, int selectedSlot, String selectedArchive, String newMode, String colorChoice, String level) {
        var matches = matches();
        if (selectedArchive != null) {
            var archived = matches.readArchivedMatch(selectedArchive);
            return archived == null ? select(null, null, false, 0, null)
                    : select(archived.match(), mode(archived.manualUsed()), archived.blackBelow(), 0, archived.id());
        }
        if (newMode != null) {
            Mode choice = Mode.valueOf(newMode);
            var match = choice == Mode.LOCAL ? ChessMatch.start()
                    : ChessMatch.start(new MatchSetup(ColorChoice.valueOf(colorChoice).resolve(WebChessGame::randomBlack),
                            Level.valueOf(level), false));
            return startMatch(match, choice);
        }
        if (route != null) return resumeMode(Mode.valueOf(route));
        return resumeSlot(selectedSlot);
    }

    private String startMatch(ChessMatch match, Mode choice) {
        var matches = matches();
        int next = matches.slotForNewMatch();
        boolean flipped = match.setup().humanSide() == Side.BLACK;
        boolean manual = choice == Mode.LOCAL;
        requireSaved(matches.startMatch(next, match, flipped, manual, manual));
        return select(match, choice, flipped, next, null);
    }

    private String resumeMode(Mode requested) {
        var matches = matches();
        int previous = matches.activeMatchSlot();
        if (matches.readSavedMatch(previous) != null && mode(matches.matchManualAi(previous)) == requested) {
            return resumeSlot(previous);
        }
        for (int candidate : matches.savedMatchSlots()) {
            if (mode(matches.matchManualAi(candidate)) == requested) return resumeSlot(candidate);
        }
        return select(null, null, false, 0, null);
    }

    private String resumeSlot(int selectedSlot) {
        var matches = matches();
        var match = matches.readSavedMatch(selectedSlot);
        if (match == null) return select(null, null, false, 0, null);
        requireSaved(matches.selectSaved(selectedSlot));
        return select(match, mode(matches.matchManualAi(selectedSlot)), matches.matchBlackBelow(selectedSlot), selectedSlot, null);
    }

    private String select(ChessMatch match, Mode selectedMode, boolean flipped, int selectedSlot, String selectedArchive) {
        session = match == null ? null : new ChessGameSession(match, selectedMode == Mode.AI);
        mode = selectedMode;
        blackBelow = flipped;
        slot = selectedSlot;
        archiveId = selectedArchive;
        viewPly = match == null ? 0 : match.ply();
        revision++;
        return state(null);
    }

    String play(int expectedRevision, String uci) {
        return change(expectedRevision, () -> session.play(session.revision(), Move.fromUci(uci)));
    }

    String playBot(int expectedRevision) {
        return change(expectedRevision, () -> session.playBot(session.revision()));
    }

    private String change(int expectedRevision, Supplier<Outcome> command) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || archiveId != null || viewPly != session.match().ply()) return state("UNAVAILABLE");
        Outcome outcome = command.get();
        if (outcome == Outcome.APPLIED) {
            var matches = matches();
            var match = session.match();
            boolean manual = mode == Mode.LOCAL;
            requireSaved(matches.saveMatch(slot, match, blackBelow, manual, manual));
            if (match.result() != ChessMatch.Result.IN_PROGRESS) {
                archiveId = matches.archivedId(slot);
                slot = 0;
            }
            viewPly = match.ply();
            revision++;
        }
        return state(outcome.name());
    }

    String history(int expectedRevision, int ply) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || ply < 0 || ply > session.match().ply() || ply == viewPly) return state("UNAVAILABLE");
        viewPly = ply;
        revision++;
        return state("APPLIED");
    }

    String continueGame(int expectedRevision, boolean automatic) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || viewPly >= session.match().ply()) return state("UNAVAILABLE");
        int next = ledger.continueGame(slot, archiveId, viewPly, automatic);
        if (next == 0) return state(archiveId == null ? "UNAVAILABLE" : "NO_FREE_SLOT");
        return openContinuation(next);
    }

    String continueStudy(String checkpoint, boolean automatic, boolean flipped) {
        var match = ChessMatch.restore(checkpoint);
        if (match.result() != ChessMatch.Result.IN_PROGRESS) return state("UNAVAILABLE");
        var matches = matches();
        int next = matches.freeMatchSlot();
        if (next == 0) return state("NO_FREE_SLOT");
        if (!matches.startMatch(next, match, flipped, !automatic, match.ply() > 0)) return state("SAVE_FAILED");
        return openContinuation(next);
    }

    String openContinuation(MatchLedger.Continuation result) {
        return result.outcome() == MatchLedger.ContinuationOutcome.APPLIED ? openContinuation(result.slot()) : state(result.outcome().name());
    }

    private String openContinuation(int next) {
        var continuation = matches().readSavedMatch(next);
        mode = mode(matches().matchManualAi(next));
        session = new ChessGameSession(continuation, mode == Mode.AI);
        blackBelow = matches().matchBlackBelow(next);
        slot = next;
        archiveId = null;
        viewPly = continuation.ply();
        revision++;
        return state("APPLIED");
    }

    String importLegacyReview(String id, ChessMatch match, String review, String bookmark, String digest) {
        return state(ledger.importLegacyReview(id, match, review, bookmark, digest) ? "MIGRATED" : "SKIPPED");
    }

    String state(String outcome) {
        var matches = matches();
        var saved = new StringBuilder("[");
        for (int number : matches.savedMatchSlots()) {
            var match = matches.readSavedMatch(number);
            appendRow(saved, match, matches.archivedId(number), mode(matches.matchManualAi(number)),
                    MatchListEntry.saved(number, match, matches.startedAt(number)));
        }
        var finished = new StringBuilder("[");
        for (var entry : matches.archivedMatches()) {
            appendRow(finished, entry.match(), entry.id(), mode(entry.manualUsed()), MatchListEntry.finished(entry));
        }
        String result = outcome == null ? "" : "\"outcome\":" + quote(outcome) + ",";
        return "{" + result + "\"ledger\":" + quote(ledger.snapshot()) + ",\"match\":" + (session == null ? "null" : view())
                + ",\"saved\":" + saved.append(']') + ",\"finished\":" + finished.append(']') + "}";
    }

    private static void appendRow(StringBuilder rows, ChessMatch match, String id, Mode mode, MatchListEntry entry) {
        if (rows.length() > 1) rows.append(',');
        rows.append("{\"slot\":").append(entry.slot()).append(",\"id\":").append(quote(id)).append(",\"mode\":").append(quote(mode.name()))
                .append(",\"humanSide\":").append(quote(match.setup().humanSide().name()))
                .append(",\"level\":").append(quote(entry.opponent().name())).append(",\"ply\":").append(entry.moves())
                .append(",\"status\":").append(quote(entry.status().name())).append(",\"result\":").append(quote(match.result().name()))
                .append(",\"reviewed\":").append(entry.reviewed())
                .append(",\"timestamp\":").append(entry.timestamp()).append('}');
    }

    private String view() {
        var match = session.match();
        var position = match.positionAt(viewPly);
        boolean latest = viewPly == match.ply();
        boolean awaitingBot = latest && session.awaitingBot();
        var legal = new StringBuilder("[");
        if (latest && !awaitingBot && archiveId == null && match.result() == ChessMatch.Result.IN_PROGRESS) {
            for (Move candidate : position.legalMoves()) appendMove(legal, candidate);
        }
        var history = new StringBuilder("[");
        for (int ply = 1; ply <= match.ply(); ply++) appendMove(history, match.moveAt(ply));
        String raw = match.checkpoint();
        String checkpoint = mode == Mode.AI ? MatchLedger.AI_PREFIX + raw : raw;
        return "{\"revision\":" + revision + ",\"fen\":" + quote(placement(position))
                + ",\"sideToMove\":" + quote(position.turn().name()) + ",\"checkpoint\":" + quote(checkpoint)
                + ",\"reviewCheckpoint\":" + quote(raw) + ",\"mode\":" + quote(mode.name())
                + ",\"humanSide\":" + quote(match.setup().humanSide().name()) + ",\"level\":" + quote(match.setup().level().name())
                + ",\"awaitingBot\":" + awaitingBot + ",\"blackBelow\":" + blackBelow
                + ",\"ply\":" + match.ply() + ",\"result\":" + quote(match.result().name()) + ",\"viewPly\":" + viewPly
                + ",\"slot\":" + slot + ",\"archiveId\":" + quote(archiveId) + ",\"readOnly\":" + (archiveId != null)
                + ",\"canContinue\":" + !latest + ",\"history\":" + history.append(']')
                + ",\"legalMoves\":" + legal.append(']') + ",\"lastMove\":" + move(match.moveAt(viewPly)) + "}";
    }

    private static void appendMove(StringBuilder moves, Move value) {
        if (moves.length() > 1) moves.append(',');
        moves.append(move(value));
    }

    MatchStore matches() {
        return ledger().matches();
    }

    MatchLedger ledger() {
        if (ledger == null) throw new IllegalStateException("Open the match library first");
        return ledger;
    }

    private static Mode mode(boolean manual) { return manual ? Mode.LOCAL : Mode.AI; }

    private static void requireSaved(boolean saved) {
        if (!saved) throw new IllegalStateException("The match library could not be updated");
    }

    @JSBody(script = "return crypto.randomUUID();")
    private static native String newId();

    @JSBody(script = "return Math.random() < 0.5;")
    private static native boolean randomBlack();
}
