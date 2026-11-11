package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.MatchSetup.Level;

/** Reconstructs the configured review ratings from ordered white/black outcomes. */
final class AiAnchorEvidence {
    private AiAnchorEvidence() { }

    static GameReview.Rating rating(Map<?, ?> evidence, Level level) {
        var protocol = ChessYaml.resource("ai-anchor.yaml");
        var league = AiLeague.load(ChessYaml.resource("ai-league.yaml"));
        int openings = ChessYaml.list(protocol, "openings").size();
        int elo = ChessYaml.number(protocol, "uci-elo");
        var sessions = ChessYaml.list(evidence, "sessions");
        if (ChessYaml.number(evidence, "schema") != 1 || sessions.size() < 3
                || !ChessYaml.string(evidence, "source-commit").matches("[a-f0-9]{40}")
                || !ChessYaml.string(evidence, "stockfish-sha256").matches("[a-f0-9]{64}")
                || !ChessYaml.list(evidence, "openings").equals(ChessYaml.list(protocol, "openings"))) {
            throw new IllegalArgumentException("Incomplete external anchor provenance");
        }
        var clusters = new ArrayList<List<PairedElo.Pair>>();
        for (int opening = 0; opening < openings; opening++) clusters.add(new ArrayList<>());
        for (Object item : sessions) {
            if (!(item instanceof Map<?, ?> session) || !ChessYaml.string(session, "replay-sha256").matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid external anchor session");
            }
            var results = ChessYaml.map(session, "profiles");
            if (!results.keySet().equals(Set.of("BASIC", "ADVANCED", "HARD"))) throw new IllegalArgumentException("Incomplete profile comparison");
            var pairs = ChessYaml.list(results, level.name());
            if (pairs.size() != openings) throw new IllegalArgumentException("Incomplete opening pairs for " + level);
            for (int opening = 0; opening < openings; opening++) clusters.get(opening).add(pair(pairs.get(opening)));
        }
        var fit = PairedElo.estimateRepeated(clusters, league.samples(), league.seed());
        var midpoint = round(elo + fit.eloMidpoint());
        if (midpoint == null) throw new IllegalArgumentException("The anchor did not yield a finite midpoint");
        return new GameReview.Rating(midpoint, round(elo + fit.eloLow95()), round(elo + fit.eloHigh95()));
    }

    private static PairedElo.Pair pair(Object value) {
        if (!(value instanceof String outcomes) || !outcomes.matches("[WDLU]{2}")) throw new IllegalArgumentException("Expected two ordered outcomes");
        var counts = new int[4];
        for (int index = 0; index < outcomes.length(); index++) counts["WDLU".indexOf(outcomes.charAt(index))]++;
        return new PairedElo.Pair(counts[0], counts[1], counts[2], counts[3]);
    }

    private static Integer round(double value) {
        return Double.isFinite(value) ? (int) Math.round(value) : null;
    }
}
