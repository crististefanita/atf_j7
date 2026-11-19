package ro.atf.chess;

import java.util.function.Supplier;
import org.teavm.jso.JSBody;
import ro.atf.chess.ChessGameSession.Outcome;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.ColorChoice;
import ro.atf.chess.MatchSetup.Level;
import ro.atf.chess.MatchControls.Action;
import ro.atf.chess.MatchStore.GameOrigin;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

final class WebChessGame {
    private MatchLedger ledger;
    private ChessGameSession session;
    private GameOrigin mode;
    private boolean blackBelow;
    private int slot;
    private String archiveId;
    private int viewPly;
    private int revision;

    String openLedger(String saved, String legacyLocal, String legacyAi) {
        ledger = MatchLedger.open(saved, legacyLocal, legacyAi, System::currentTimeMillis, WebChessGame::newId);
        return select(null, null, false, 0, null, null);
    }

    String open(String route, int selectedSlot, String selectedArchive, String newMode, String colorChoice, String level, boolean analysis) {
        var matches = matches();
        if (selectedArchive != null) {
            var archived = matches.readArchivedMatch(selectedArchive);
            return archived == null ? select(null, null, false, 0, null, null)
                    : select(archived.match(), matches.archiveOrigin(archived), archived.blackBelow(), 0, archived.id(), null);
        }
        if (newMode != null) {
            GameOrigin choice = GameOrigin.valueOf(newMode);
            var match = choice == GameOrigin.LOCAL ? ChessMatch.start()
                    : matches().prepareAiMatch(new MatchSetup(ColorChoice.valueOf(colorChoice).resolve(WebChessGame::randomBlack),
                            Level.valueOf(level), analysis));
            return startMatch(match, choice);
        }
        if (route != null) return resumeMode(GameOrigin.valueOf(route));
        return resumeSlot(selectedSlot);
    }

    private String startMatch(ChessMatch match, GameOrigin choice) {
        var matches = matches();
        int next = matches.slotForNewMatch();
        boolean flipped = match.setup().humanSide() == Side.BLACK;
        boolean manual = choice == GameOrigin.LOCAL;
        requireSaved(matches.startMatch(next, match, flipped, choice, manual, manual));
        return select(match, choice, flipped, next, null, null);
    }

    private String resumeMode(GameOrigin requested) {
        var matches = matches();
        int previous = matches.activeMatchSlot();
        if (matches.readSavedMatch(previous) != null && matches.matchOrigin(previous) == requested) {
            return resumeSlot(previous);
        }
        for (int candidate : matches.savedMatchSlots()) {
            if (matches.matchOrigin(candidate) == requested) return resumeSlot(candidate);
        }
        return select(null, null, false, 0, null, null);
    }

    private String resumeSlot(int selectedSlot) {
        var matches = matches();
        var match = matches.readSavedMatch(selectedSlot);
        if (match == null) return select(null, null, false, 0, null, null);
        requireSaved(matches.selectSaved(selectedSlot));
        return select(match, matches.matchOrigin(selectedSlot), matches.matchBlackBelow(selectedSlot), selectedSlot, null, null);
    }

    private String select(ChessMatch match, GameOrigin selectedMode, boolean flipped, int selectedSlot, String selectedArchive, String outcome) {
        boolean automatic = selectedMode == GameOrigin.AI && (selectedArchive != null || !matches().matchManualAi(selectedSlot));
        session = match == null ? null : new ChessGameSession(match, automatic);
        mode = selectedMode;
        blackBelow = flipped;
        slot = selectedSlot;
        archiveId = selectedArchive;
        viewPly = match == null ? 0 : match.ply();
        revision++;
        return state(outcome);
    }

    String play(int expectedRevision, String uci) {
        return change(expectedRevision, () -> session.play(session.revision(), Move.fromUci(uci)));
    }

    String playBot(int expectedRevision) {
        return change(expectedRevision, () -> session.playBot(session.revision()));
    }

    String applyBot(int expectedRevision, String uci) {
        return change(expectedRevision, () -> session.playBot(session.revision(), Move.fromUci(uci)));
    }

    private String change(int expectedRevision, Supplier<Outcome> command) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || archiveId != null || viewPly != session.match().ply()) return state("UNAVAILABLE");
        var before = session.match();
        Outcome outcome = command.get();
        if (outcome == Outcome.APPLIED) {
            var matches = matches();
            var match = session.match();
            boolean manual = !session.automatic();
            boolean manualUsed = MatchSlots.manualAiMoveUsed(before, manual, matches.matchManualUsed(slot));
            requireSaved(matches.saveMatch(slot, match, blackBelow, mode, manual, manualUsed));
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

    String flipBoard(int expectedRevision) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null) return state("UNAVAILABLE");
        boolean flipped = !blackBelow;
        if (archiveId == null) {
            var matches = matches();
            if (!matches.saveMatch(slot, session.match(), flipped, mode, !session.automatic(), matches.matchManualUsed(slot))) {
                return state("SAVE_FAILED");
            }
        }
        blackBelow = flipped;
        revision++;
        return state("APPLIED");
    }

    String changeAutomation(int expectedRevision, boolean automatic) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || mode != GameOrigin.AI || archiveId != null || session.automatic() == automatic) return state("UNAVAILABLE");
        var match = session.match();
        if (!MatchControls.automation(match, viewPly < match.ply(), !session.automatic()).enabled()) return state("UNAVAILABLE");
        var matches = matches();
        if (!matches.saveMatch(slot, match, blackBelow, mode, !automatic, matches.matchManualUsed(slot))) return state("SAVE_FAILED");
        session = new ChessGameSession(match, automatic);
        revision++;
        return state("APPLIED");
    }

    String decide(int expectedRevision, Action action) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || mode != GameOrigin.AI || archiveId != null) return state("UNAVAILABLE");
        var match = session.match();
        boolean manual = !session.automatic();
        var item = new MatchControls.MenuItem(action, true);
        if (!MatchControls.menu(match, viewPly < match.ply(), manual).contains(item)) return state("UNAVAILABLE");
        var ending = switch (action) {
            case OFFER_DRAW -> match.offerDraw();
            case RESIGN -> match.resign();
            default -> throw new IllegalArgumentException("Unknown match decision");
        };
        if (ending == match) return state("DECLINED");
        var matches = matches();
        if (!matches.saveMatch(slot, ending, blackBelow, mode, manual, matches.matchManualUsed(slot))) return state("SAVE_FAILED");
        return select(ending, mode, blackBelow, 0, matches.archivedId(slot), "APPLIED");
    }

    String continueGame(int expectedRevision, boolean automatic) {
        if (expectedRevision != revision) return state("STALE");
        if (session == null || viewPly >= session.match().ply()) return state("UNAVAILABLE");
        int next = ledger.continueGame(slot, archiveId, viewPly, automatic, blackBelow);
        if (next == 0) return state(archiveId == null ? "UNAVAILABLE" : "NO_FREE_SLOT");
        return openContinuation(next);
    }

    String continueStudy(String checkpoint, boolean automatic, boolean flipped) {
        var match = ChessMatch.restore(checkpoint);
        if (match.result() != ChessMatch.Result.IN_PROGRESS) return state("UNAVAILABLE");
        var matches = matches();
        int next = matches.freeMatchSlot();
        if (next == 0) return state("NO_FREE_SLOT");
        var origin = automatic ? GameOrigin.AI : GameOrigin.LOCAL;
        if (!matches.startMatch(next, match, flipped, origin, !automatic, match.ply() > 0)) return state("SAVE_FAILED");
        return openContinuation(next);
    }

    String openContinuation(MatchLedger.Continuation result) {
        return result.outcome() == MatchLedger.ContinuationOutcome.APPLIED ? openContinuation(result.slot()) : state(result.outcome().name());
    }

    private String openContinuation(int next) {
        var matches = matches();
        return select(matches.readSavedMatch(next), matches.matchOrigin(next), matches.matchBlackBelow(next), next, null, "APPLIED");
    }

    String importLegacyReview(String id, ChessMatch match, String review, String bookmark, String digest) {
        return state(ledger.importLegacyReview(id, match, review, bookmark, digest) ? "MIGRATED" : "SKIPPED");
    }

    String state(String outcome) {
        var matches = matches();
        var saved = new StringBuilder("[");
        for (int number : matches.savedMatchSlots()) {
            var match = matches.readSavedMatch(number);
            appendRow(saved, match, matches.archivedId(number), matches.matchOrigin(number),
                    MatchListEntry.saved(number, match, matches.startedAt(number)));
        }
        var finished = new StringBuilder("[");
        for (var entry : matches.archivedMatches()) {
            appendRow(finished, entry.match(), entry.id(), matches.archiveOrigin(entry), MatchListEntry.finished(entry));
        }
        String result = outcome == null ? "" : "\"outcome\":" + quote(outcome) + ",";
        return "{" + result + "\"ledger\":" + quote(ledger.snapshot()) + ",\"match\":" + (session == null ? "null" : view())
                + ",\"saved\":" + saved.append(']') + ",\"finished\":" + finished.append(']') + "}";
    }

    private static void appendRow(StringBuilder rows, ChessMatch match, String id, GameOrigin mode, MatchListEntry entry) {
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
        boolean isAi = mode == GameOrigin.AI;
        boolean manualUsed = archiveId == null ? matches().matchManualUsed(slot) : matches().readArchivedMatch(archiveId).manualUsed();
        boolean manualAiTurn = latest && isAi && !session.automatic() && match.awaitingBot();
        var automation = MatchControls.automation(match, !latest, !session.automatic());
        boolean showAutomation = isAi && archiveId == null && automation.visible();
        var legal = new StringBuilder("[");
        if (latest && !awaitingBot && archiveId == null && match.result() == ChessMatch.Result.IN_PROGRESS) {
            for (Move candidate : position.legalMoves()) appendMove(legal, candidate);
        }
        var history = new StringBuilder("[");
        for (int ply = 1; ply <= match.ply(); ply++) appendMove(history, match.moveAt(ply));
        var navigation = MatchHistorySelection.at(match, viewPly);
        String raw = match.checkpoint();
        String checkpoint = isAi ? MatchLedger.AI_PREFIX + raw : raw;
        return "{\"revision\":" + revision + ",\"fen\":" + quote(placement(position))
                + ",\"sideToMove\":" + quote(position.turn().name()) + ",\"checkpoint\":" + quote(checkpoint)
                + ",\"reviewCheckpoint\":" + quote(raw) + ",\"mode\":" + quote(mode.name())
                + ",\"humanSide\":" + quote(match.setup().humanSide().name()) + ",\"level\":" + quote(match.setup().level().name())
                + ",\"analysisEnabled\":" + match.analysisEnabled()
                + ",\"automatic\":" + session.automatic() + ",\"manualUsed\":" + manualUsed + ",\"rated\":" + (isAi && !manualUsed)
                + ",\"manualAiTurn\":" + manualAiTurn + ",\"automation\":{\"visible\":" + showAutomation
                + ",\"enabled\":" + (showAutomation && automation.enabled()) + ",\"checked\":" + automation.checked() + "}"
                + ",\"awaitingBot\":" + awaitingBot + ",\"blackBelow\":" + blackBelow
                + ",\"ply\":" + match.ply() + ",\"result\":" + quote(match.result().name()) + ",\"viewPly\":" + viewPly
                + ",\"resigned\":" + match.resigned() + ",\"menu\":" + menu(match, latest)
                + ",\"slot\":" + slot + ",\"archiveId\":" + quote(archiveId) + ",\"readOnly\":" + (archiveId != null)
                + ",\"canContinue\":" + !latest + ",\"history\":" + history.append(']')
                + ",\"navigation\":" + navigation(navigation)
                + ",\"legalMoves\":" + legal.append(']') + ",\"lastMove\":" + move(match.moveAt(viewPly)) + "}";
    }

    private static String navigation(MatchHistorySelection value) {
        return "{\"ply\":" + value.ply() + ",\"total\":" + value.total() + ",\"number\":" + value.number()
                + ",\"side\":" + (value.side() == null ? "null" : quote(value.side().name())) + ",\"move\":" + move(value.move())
                + ",\"boundary\":" + quote(value.boundary().name()) + ",\"compact\":" + quote(value.compact()) + "}";
    }

    private String menu(ChessMatch match, boolean latest) {
        var rows = new StringBuilder("{");
        for (var item : MatchControls.menu(match, !latest, !session.automatic())) {
            if (mode == GameOrigin.LOCAL && (item.action() == Action.OFFER_DRAW || item.action() == Action.RESIGN)) continue;
            if (rows.length() > 1) rows.append(',');
            rows.append(quote(item.action().name())).append(':').append(item.enabled());
        }
        return rows.append('}').toString();
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

    private static void requireSaved(boolean saved) {
        if (!saved) throw new IllegalStateException("The match library could not be updated");
    }

    @JSBody(script = "return crypto.randomUUID();")
    private static native String newId();

    @JSBody(script = "return Math.random() < 0.5;")
    private static native boolean randomBlack();
}
