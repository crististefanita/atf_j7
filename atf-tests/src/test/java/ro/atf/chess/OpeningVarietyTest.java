package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

/** Real opening selection and atomic saved-game transactions; no mocked move generator. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningVarietyTest {
    private static final MatchSetup SETUP = new MatchSetup(Side.BLACK, Level.HARD, true);

    @Test void rotatesDistinctAuthoredMovesInsteadOfRepeatedDisplayPaths(Checks check) {
        var initial = ChessPosition.initial();
        check.same("The display paths still share the first e4 prefix", List.of("e2e4"),
                OpeningTheory.CURRENT.choices(initial).stream().map(path -> path.move().uci()).distinct().toList());
        for (String prefix : List.of("", "e2e4", "e2e4 e7e5 g1f3 b8c6")) {
            var board = replay(0, prefix).position();
            var moves = OpeningTheory.CURRENT.playMoves(board);
            check.same("The authored pool has genuine branching at " + prefix, true, moves.size() >= 3);
            for (var level : Level.values()) {
                var cycle = new HashSet<Move>();
                for (long choice = 1; choice <= moves.size(); choice++) {
                    var selected = OpeningRepertoire.CURRENT.playReply(board, level, choice);
                    check.same("A unique documented legal move is used before repeating", true,
                            moves.contains(selected) && board.legalMoves().contains(selected) && cycle.add(selected));
                }
                check.same("The next cycle is explicit and reproducible", moves.getFirst(),
                        OpeningRepertoire.CURRENT.playReply(board, level, moves.size() + 1L));
            }
        }
    }

    @Test void everyAuthoredPositionCanVaryWithoutImportingUncuratedEcoMoves(Checks check) {
        int frames = 0;
        for (var lesson : OpeningCatalog.load()) for (int ply = 0; ply < lesson.steps().size(); ply++) {
            var board = lesson.positionAt(ply);
            var expected = new HashSet<Move>();
            for (var other : OpeningCatalog.load()) for (int index = 0; index < other.steps().size(); index++) {
                if (other.positionAt(index).searchKey().equals(board.searchKey())) expected.add(other.steps().get(index).move());
            }
            check.same("Only authored immediate continuations enter " + lesson.id() + ":" + ply,
                    expected, new HashSet<>(OpeningTheory.CURRENT.playMoves(board)));
            frames++;
        }
        check.same("The entire authored catalogue is governed", true, frames > 100);
    }

    @Test void nativeDecisionUsesTheSavedChoiceForEveryProfile(Checks check) {
        for (var level : Level.values()) for (long choice = 1; choice <= 4; choice++) {
            var game = ChessMatch.start(new MatchSetup(Side.WHITE, level, true), choice).play(Move.fromUci("e2e4"));
            var expected = OpeningRepertoire.CURRENT.playReply(game.position(), level, choice);
            check.same("The actual native decision follows this game's opening choice", expected, game.chooseBotMove());
            check.same("Restart retains the same actual decision", game.botDecision(), ChessMatch.restore(game.checkpoint()).botDecision());
        }
    }

    @Test void atomicNewGamesRotateAcrossRestartAndTenSlotReplacement(Checks check) {
        var disk = new MemoryPreferences();
        var openings = new HashSet<Move>();
        for (int number = 1; number <= 12; number++) {
            var store = store(disk);
            var match = store.prepareAiMatch(SETUP);
            check.same("The next game has a durable monotonic choice", (long) number, match.openingChoice());
            int slot = store.slotForNewMatch();
            check.same("The slot and choice commit together", true, store.startMatch(slot, match, true, false, false));
            var reopened = store(disk).readMatch(slot);
            check.same("Process restart preserves the checkpoint byte for byte", match.checkpoint(), reopened.checkpoint());
            openings.add(reopened.chooseBotMove());
        }
        check.same("New games genuinely vary White's first move", true, openings.size() >= 3);
        check.same("Retention remains ten slots", 10, store(disk).savedMatchSlots().size());
        check.same("Replacing an old slot does not reset the rotation", 13L, store(disk).prepareAiMatch(SETUP).openingChoice());
    }

    @Test void failedSaveAndCancelledSetupDoNotConsumeTheNextOpening(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk);
        var prepared = store.prepareAiMatch(SETUP);
        check.same("Repeated setup preparation makes no writes", 0, disk.data.size());
        check.same("Cancelled setup retains the same choice", prepared.checkpoint(), store.prepareAiMatch(SETUP).checkpoint());
        disk.rejectCommit = true;
        check.same("The caller sees the failed save", false, store.startMatch(1, prepared, true, false, false));
        check.same("A rejected transaction consumes neither slot nor choice", 0, disk.data.size());
        disk.rejectCommit = false;
        check.same("Retry saves the original game", true, store.startMatch(1, prepared, true, false, false));
        check.same("Only successful creation advances the choice", 2L, store(disk).prepareAiMatch(SETUP).openingChoice());
    }

    @Test void localGamesAndOrdinaryResavesDoNotAdvanceAiRotation(Checks check) {
        var disk = new MemoryPreferences();
        var store = store(disk);
        var ai = store.prepareAiMatch(SETUP);
        store.startMatch(1, ai, true, false, false);
        store.saveMatch(1, ai.play(ai.chooseBotMove()), true, false);
        store.startMatch(2, ChessMatch.start(), false, MatchStore.GameOrigin.LOCAL, true, true);
        check.same("Human-only play and resuming an existing slot preserve the next choice", 2L,
                store(disk).prepareAiMatch(SETUP).openingChoice());
    }

    @Test void restartHistoryDecisionsAndAnalysisRetainTheGameChoice(Checks check) {
        var game = replay(17, "e2e4 e7e5 g1f3");
        for (var version : List.of(game, game.continueFrom(1), game.withAnalysis(false), game.resign(), game.resign().forAnalysis())) {
            var reopened = ChessMatch.restore(version.checkpoint());
            check.same("Every session transition retains the choice", 17L, reopened.openingChoice());
            check.same("Every session transition retains board and terminal result", List.of(version.position().searchKey(), version.result()),
                    List.of(reopened.position().searchKey(), reopened.result()));
        }
        check.same("Restarting the same history branch preserves the exact next move", game.continueFrom(2).botDecision(),
                ChessMatch.restore(game.checkpoint()).continueFrom(2).botDecision());
    }

    @Test void legacyCheckpointsKeepTheirEncodingAndDeterministicMoves(Checks check) {
        for (String legacy : List.of("1|e2e4", "2|analysis|e2e4", "3|BLACK|HARD|1|e2e4", "4|RESIGN|1|e2e4")) {
            var restored = ChessMatch.restore(legacy);
            check.same("Legacy checkpoints do not acquire a new opening retroactively", legacy, restored.checkpoint());
            check.same("Old sessions retain the original selector", 0L, restored.openingChoice());
        }
        check.same("Legacy deterministic benchmarks retain e5", "e7e5", replay(0, "e2e4").botDecision(Level.HARD).move().uci());
    }

    @Test void rejectsCorruptNestedAndOverflowingChoiceCheckpoints(Checks check) {
        for (String invalid : List.of("5|0|1|", "5|-1|1|", "5|01|1|", "5|+1|1|", "5|9223372036854775808|1|",
                "5|1|5|2|1|", "5|1|4|RESIGN|1|", "5|1|", "4|RESIGN|5|1|4|RESIGN|1|")) {
            check.rejects("Malformed choice is not silently repaired: " + invalid, IllegalArgumentException.class,
                    () -> ChessMatch.restore(invalid));
        }
        check.rejects("Negative direct choices are invalid", IllegalArgumentException.class, () -> ChessMatch.start(SETUP, -1));
        var disk = new MemoryPreferences();
        for (long invalid : List.of(-1L, Long.MAX_VALUE)) {
            disk.data.put("ai-opening-sequence", invalid);
            check.rejects("Invalid counters do not wrap into a repeat", IllegalStateException.class, () -> store(disk).prepareAiMatch(SETUP));
        }
        check.same("Maximum checkpoint value round-trips without arithmetic overflow", Long.MAX_VALUE,
                ChessMatch.restore(ChessMatch.start(SETUP, Long.MAX_VALUE).checkpoint()).openingChoice());
    }

    @Test void verifiedOverridesAndReviewExclusionsOutrankTheRotation(Checks check) {
        String prefix = "e2e4 c7c5 g1f3 d7d6";
        var board = replay(1, prefix).position();
        var verified = OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(
                Map.of("id", "verified", "moves", prefix, "reply", "b1c3", "levels", List.of("ADVANCED")))));
        var excluded = OpeningRepertoire.read(Map.of("schema", 1, "positions", List.of(
                Map.of("id", "excluded", "moves", prefix, "reply", "b1c3", "review-only", true))));
        for (long choice : List.of(1L, 2L, Long.MAX_VALUE)) {
            check.same("A verified level override remains authoritative", Move.fromUci("b1c3"), verified.playReply(board, Level.ADVANCED, choice));
            check.same("Rotation cannot bypass a review-only exclusion", null, excluded.playReply(board, Level.HARD, choice));
        }
    }

    @Test void transpositionsAndOffBookSearchRespectTheActualBoard(Checks check) {
        var direct = replay(2, "d2d4 d7d5 c2c4 e7e6");
        var transposed = replay(2, "d2d4 e7e6 c2c4 d7d5");
        check.same("A transposition preserves the documented choice", direct.botDecision(), transposed.botDecision());
        String exit = "e2e4 e7e5 g1f3 b8c6 f1c4 a7a5";
        var legacy = replay(0, exit);
        for (var level : Level.values()) {
            check.same("Off-book positions have no invented rotating entry", null,
                    OpeningRepertoire.CURRENT.playReply(legacy.position(), level, 2));
            check.same("The existing profile search, horizon and costs remain unchanged", legacy.botDecision(level), replay(2, exit).botDecision(level));
        }
    }

    @Test void openingRotationCannotAlterThePublicHintOrFinalReview(Checks check) {
        var first = replay(1, "e2e4");
        var second = replay(2, "e2e4");
        check.same("The maximum public hint is position-based, not the bot's opening ticket", first.hintAt(1).lines(), second.hintAt(1).lines());
        var a = GameReview.analyze(first.resign(), ignored -> { });
        var b = GameReview.analyze(second.resign(), ignored -> { });
        check.same("Native final review retains the actual history and evaluations", a.turns(), b.turns());
    }

    private static ChessMatch replay(long choice, String moves) {
        var match = ChessMatch.start(SETUP, choice);
        if (!moves.isEmpty()) for (String move : moves.split(" ")) match = match.play(Move.fromUci(move));
        return match;
    }

    private static MatchStore store(MemoryPreferences disk) {
        return new MatchStore(disk, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC));
    }
}
