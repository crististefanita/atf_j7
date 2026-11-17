package ro.atf.chess;

import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Kind;

/** Deterministic search reserve. Each decision retains its own stack and transpositions. */
record HardAdaptivePolicy(boolean enabled, int baseNodes, int maxNodes, int maxPlies,
                          int minimumPlies, int cacheObservations, int coldCachePerMille) {
    static final HardAdaptivePolicy CURRENT = read(ChessYaml.resource("hard-adaptive.yaml"));

    MatchAiPolicy.Search search() {
        return enabled ? new MatchAiPolicy.Search(maxPlies, maxNodes)
                : MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD);
    }

    Budget budget(ChessPosition position) {
        return enabled ? new Budget(this, kingInDanger(position)) : null;
    }

    static HardAdaptivePolicy read(Map<?, ?> values) {
        if (!values.keySet().equals(Set.of("schema", "enabled", "base-extra-nodes", "max-extra-nodes",
                "max-plies", "minimum-completed-plies", "cache-observations", "cold-cache-per-mille"))
                || ChessYaml.number(values, "schema") != 1 || !(values.get("enabled") instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Invalid adaptive Hard configuration");
        }
        int base = bounded(values, "base-extra-nodes", 1, 500_000);
        int maximum = bounded(values, "max-extra-nodes", base, 500_000);
        int maxPlies = bounded(values, "max-plies", MatchAiPolicy.BASE_PLIES, MatchAiPolicy.MAX_PLIES);
        int minimum = bounded(values, "minimum-completed-plies", MatchAiPolicy.BASE_PLIES, maxPlies);
        return new HardAdaptivePolicy(enabled, base, maximum, maxPlies, minimum,
                bounded(values, "cache-observations", 1, 500_000),
                bounded(values, "cold-cache-per-mille", 0, 1000));
    }

    private static int bounded(Map<?, ?> values, String name, int minimum, int maximum) {
        int value = ChessYaml.number(values, name);
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Invalid adaptive Hard " + name);
        return value;
    }

    static boolean kingInDanger(ChessPosition position) {
        if (position.isCheck()) return true;
        int king = -1;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece != null && piece.side() == position.turn() && piece.kind() == Kind.KING) {
                king = square;
                break;
            }
        }
        int kingFile = king % 8;
        if (kingFile != 3 && kingFile != 4) return false;
        int blockers = 0;
        int step = position.turn() == ChessPosition.Side.WHITE ? 8 : -8;
        for (int square = king + step; square >= 0 && square < 64; square += step) {
            var piece = position.pieceAt(square);
            if (piece == null) continue;
            if (piece.side() == position.turn()) {
                if (++blockers > 1) return false;
            } else {
                return piece.kind() == Kind.ROOK || piece.kind() == Kind.QUEEN;
            }
        }
        return false;
    }

    static final class Budget {
        private final HardAdaptivePolicy policy;
        private final boolean danger;
        private int completed = MatchAiPolicy.BASE_PLIES;
        private int lookups;
        private int hits;
        private boolean extended;
        private boolean refused;
        private boolean exchangeProof;

        Budget(HardAdaptivePolicy policy, boolean danger) {
            this.policy = policy;
            this.danger = danger;
        }

        void completed(int depth) { completed = depth; }

        void lookup(boolean hit) {
            lookups++;
            if (hit) hits++;
        }

        boolean allow(int spent) {
            return allow(spent, 0);
        }

        boolean allow(int spent, int reserved) {
            spent += reserved;
            if (refused || spent >= policy.maxNodes()) return false;
            if (spent < policy.baseNodes() || extended) return true;
            boolean cold = lookups >= policy.cacheObservations()
                    && (long) hits * 1000 < (long) policy.coldCachePerMille() * lookups;
            extended = completed < policy.minimumPlies() || danger || cold || exchangeProof;
            refused = !extended;
            return extended;
        }

        boolean extended() { return extended; }
        void exchangeProof(boolean pending) { exchangeProof = pending; }
    }
}
