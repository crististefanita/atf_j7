package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** Same-parity verification for an exposed queen in positions that retain opening material. */
record QueenReplyPolicy(boolean enabled, int minimumPhase, int aspirationWindowCp) {
    static final QueenReplyPolicy CURRENT = read(ChessYaml.resource("queen-reply-verification.yaml"));

    QueenReplyPolicy {
        if (minimumPhase < 0 || minimumPhase > 24 || aspirationWindowCp < 50 || aspirationWindowCp > 1000) {
            throw new IllegalArgumentException("Out-of-range queen reply verification setting");
        }
    }

    boolean applies(ChessPosition position) {
        return enabled && MatchPositionEvaluation.phase(position) >= minimumPhase;
    }

    boolean verifies(ChessPosition position, TacticalAnalysis.Variation previous, int completed) {
        return applies(position) && !previous.line().isEmpty()
                && MatchMaterial.victim(position, previous.move()) != null
                && TacticalAnalysis.unresolvedQueenRecapture(position, previous, completed);
    }

    static QueenReplyPolicy read(Map<?, ?> values) {
        if (!values.keySet().equals(Set.of("schema", "enabled", "minimum-phase", "aspiration-window-cp"))
                || ChessYaml.number(values, "schema") != 1 || !(values.get("enabled") instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Invalid queen reply verification configuration");
        }
        int phase = ChessYaml.number(values, "minimum-phase");
        int window = ChessYaml.number(values, "aspiration-window-cp");
        return new QueenReplyPolicy(enabled, phase, window);
    }
}
