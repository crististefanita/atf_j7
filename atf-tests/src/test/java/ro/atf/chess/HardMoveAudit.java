package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Variation;

/** Evidence from the actual native decision, not a second position-only coaching search. */
final class HardMoveAudit {
    record Entry(String checkpoint, String fen, int ply, String side, String selected, int depth, int extraNodes,
                 String theoryReference, String scoreKind, int selectedRank, List<Variation> variations) {
        Entry { variations = List.copyOf(variations); }
    }

    static Entry capture(ChessMatch match) {
        var analysis = match.analyzeBot(MatchSetup.Level.HARD);
        var decision = analysis.decision();
        var lines = analysis.search().result().ranked();
        require(decision.depth() == analysis.search().depth() && decision.extraNodes() == analysis.search().extraNodes(),
                "Decision differs from its actual search");
        int rank = 0;
        for (int index = 0; index < lines.size(); index++) if (lines.get(index).move().equals(decision.move())) rank = index + 1;
        var theory = OpeningRepertoire.CURRENT.playReply(match.position(), MatchSetup.Level.HARD);
        var result = new Entry(match.checkpoint(), HintAuditSession.fen(match), match.ply(), match.position().turn().name(),
                decision.move().uci(), decision.depth(), decision.extraNodes(), theory == null ? null : theory.uci(),
                "NATIVE_POSITION_SCORE_WITH_POSSIBLE_HORIZON_CREDIT_NOT_ORACLE", rank, lines);
        validate(result);
        return result;
    }

    /** Replay validates identity and legality; strength and score authenticity require the trusted run manifest. */
    static void validate(Entry entry) {
        var match = ChessMatch.restore(entry.checkpoint());
        var board = match.position();
        require(match.result() == ChessMatch.Result.IN_PROGRESS, "Analysis belongs to a finished game");
        require(entry.ply() == match.ply() && entry.side().equals(board.turn().name())
                && entry.fen().equals(HintAuditSession.fen(match)), "Foreign position, clock or history");
        require(entry.depth() > 0 && entry.depth() <= 7 && entry.extraNodes() >= 0 && entry.extraNodes() <= 200_000,
                "Invalid native search limits");
        require(entry.scoreKind().equals("NATIVE_POSITION_SCORE_WITH_POSSIBLE_HORIZON_CREDIT_NOT_ORACLE"), "Unsupported score claim");
        var theory = OpeningRepertoire.CURRENT.playReply(board, MatchSetup.Level.HARD);
        require(java.util.Objects.equals(entry.theoryReference(), theory == null ? null : theory.uci()), "Foreign theory reference");
        var selected = Move.fromUci(entry.selected());
        require(board.legalMoves().contains(selected) && !entry.variations().isEmpty(), "Missing or illegal native decision");
        var roots = new java.util.HashSet<Move>();
        for (var variation : entry.variations()) {
            require(!variation.line().isEmpty() && roots.add(variation.move()), "Missing or duplicate native root");
            var replay = board;
            var path = new ArrayList<ChessPosition>();
            for (var move : variation.line()) {
                require(replay.legalMoves().contains(move), "Illegal native continuation");
                replay = replay.play(move);
                path.add(replay);
            }
            require(variation.materialChange() == HintAuditSession.materialChangeCp(board, replay), "Foreign material ledger");
            require(variation.ending() != TacticalAnalysis.Ending.CHECKMATE || replay.isCheckmate(), "Unproved native mate");
            require(variation.ending() != TacticalAnalysis.Ending.STALEMATE || !replay.hasLegalMove() && !replay.isCheck(), "Unproved stalemate");
            if (variation.ending() == TacticalAnalysis.Ending.HORIZON && variation.line().size() < entry.depth()) {
                int repetitions = priorOccurrences(match, replay);
                for (var state : path) if (state.sameForRepetition(replay)) repetitions++;
                require(variation.score() == 0 && repetitions >= 3,
                        "A truncated line is not a repetition convention");
            }
        }
        int rank = entry.selectedRank();
        if (rank > 0) require(rank <= entry.variations().size() && entry.variations().get(rank - 1).move().equals(selected), "Foreign selected rank");
        else require(rank == 0 && priorOccurrences(match, board.play(selected)) >= 4, "Missing selected root without a fivefold-draw override");
    }

    private static int priorOccurrences(ChessMatch match, ChessPosition board) {
        int count = 0;
        for (int ply = 0; ply <= match.ply(); ply++) if (match.positionAt(ply).sameForRepetition(board)) count++;
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
