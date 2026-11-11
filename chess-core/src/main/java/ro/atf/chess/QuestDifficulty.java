package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.QuestCatalog.Quest;

/** Generates the runtime order from the editorial difficulty profile. */
public final class QuestDifficulty {
    private enum Factor {
        LEGAL_MOVE, CHECKING_DECOY, SINGLE_REPLY, PIECE_ON_BOARD, KEY_TRAVEL, NON_CAPTURE,
        DISCOVERED, CASTLING, EN_PASSANT, QUEEN_PROMOTION, UNDERPROMOTION, IN_CHECK;

        String yamlKey() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    }

    record Rank(String id, int score) { }

    static final class Profile {
        private final String introduction;
        private final Map<Factor, Integer> weights;
        private final Map<String, Integer> tieBreaks;

        private Profile(String introduction, Map<Factor, Integer> weights, Map<String, Integer> tieBreaks) {
            this.introduction = introduction;
            this.weights = Map.copyOf(weights);
            this.tieBreaks = Map.copyOf(tieBreaks);
        }

        static Profile read(Map<?, ?> root) {
            if (!root.keySet().equals(Set.of("schema", "introduction", "weights", "tie-breaks"))
                    || ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid quest difficulty schema");
            String introduction = ChessYaml.string(root, "introduction");
            var settings = ChessYaml.map(root, "weights");
            var weights = new EnumMap<Factor, Integer>(Factor.class);
            for (Factor factor : Factor.values()) weights.put(factor, bounded(settings, factor.yamlKey(), 0, 1000));
            if (settings.size() != weights.size()) throw new IllegalArgumentException("Unknown quest difficulty weight");
            var tieBreaks = new HashMap<String, Integer>();
            var entries = ChessYaml.map(root, "tie-breaks");
            for (Object key : entries.keySet()) {
                if (!(key instanceof String id) || id.equals(introduction)) throw new IllegalArgumentException("Invalid tie-break ID: " + key);
                tieBreaks.put(id, bounded(entries, id, 1, 9));
            }
            return new Profile(introduction, weights, tieBreaks);
        }

        private static int bounded(Map<?, ?> source, String key, int minimum, int maximum) {
            int value = ChessYaml.number(source, key);
            if (value < minimum || value > maximum) throw new IllegalArgumentException("Out-of-range difficulty value: " + key);
            return value;
        }

        private int weight(Factor factor) { return weights.get(factor); }
    }

    private QuestDifficulty() { }

    static List<Rank> rank(List<Quest> quests) {
        return rank(quests, Profile.read(ChessYaml.resource("quest-difficulty.yaml")));
    }

    static List<Rank> rank(List<Quest> quests, Profile profile) {
        var ids = quests.stream().map(Quest::id).collect(Collectors.toSet());
        if (!ids.contains(profile.introduction) || !ids.containsAll(profile.tieBreaks.keySet())) {
            throw new IllegalArgumentException("Quest difficulty refers to an unknown ID");
        }
        var base = new ArrayList<Rank>();
        var counts = new HashMap<Integer, Integer>();
        for (Quest quest : quests) {
            int points = baseScore(quest, profile);
            base.add(new Rank(quest.id(), points));
            counts.merge(points, 1, Integer::sum);
        }
        var entries = new ArrayList<Rank>(base.size());
        for (Rank rank : base) {
            int tieBreak = counts.get(rank.score()) > 1 ? profile.tieBreaks.getOrDefault(rank.id(), 0) : 0;
            entries.add(new Rank(rank.id(), rank.score() * 10 + tieBreak));
        }
        entries.sort(Comparator.comparingInt(Rank::score).thenComparing(Rank::id));
        return List.copyOf(entries);
    }

    private static int baseScore(Quest quest, Profile profile) {
        var position = quest.position();
        var key = quest.solution();
        var legal = position.legalMoves();
        int score = 1 + legal.size() * profile.weight(Factor.LEGAL_MOVE);
        for (var move : legal) {
            if (move.equals(key)) continue;
            var after = position.play(move);
            if (!after.isCheck()) continue;
            int replies = after.legalMoves().size();
            if (replies == 0) {
                throw new IllegalArgumentException("Multiple mates for " + quest.id() + ": " + key.uci() + " and " + move.uci());
            }
            score += profile.weight(Factor.CHECKING_DECOY);
            if (replies == 1) score += profile.weight(Factor.SINGLE_REPLY);
        }
        if (quest.id().equals(profile.introduction)) return 0;
        for (int square = 0; square < 64; square++) {
            if (position.pieceAt(square) != null) score += profile.weight(Factor.PIECE_ON_BOARD);
        }
        int distance = Math.max(Math.abs(key.from() % 8 - key.to() % 8), Math.abs(key.from() / 8 - key.to() / 8));
        score += distance * profile.weight(Factor.KEY_TRAVEL);
        var pattern = quest.matingPattern();
        if (pattern != QuestCatalog.MatePattern.EN_PASSANT && position.pieceAt(key.to()) == null) score += profile.weight(Factor.NON_CAPTURE);
        if (position.isCheck()) score += profile.weight(Factor.IN_CHECK);
        score += switch (pattern) {
            case DIRECT -> 0;
            case DISCOVERED -> profile.weight(Factor.DISCOVERED);
            case CASTLING -> profile.weight(Factor.CASTLING);
            case EN_PASSANT -> profile.weight(Factor.EN_PASSANT);
            case PROMOTION -> profile.weight(key.promotion() == Kind.QUEEN ? Factor.QUEEN_PROMOTION : Factor.UNDERPROMOTION);
        };
        return score;
    }

    public static void main(String[] args) {
        if (args.length > 0 && (args.length != 1 || !"--oracle-input".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: QuestDifficulty [--oracle-input]");
        }
        var quests = QuestCatalog.loadPositions();
        if (args.length == 1) {
            for (Quest quest : quests) {
                String fen = quest.fen().trim().replaceAll("\\s+", " ");
                System.out.printf("%s\t%s\t%s\t%s%n", quest.id(), fen, quest.solution().uci(), quest.theme());
            }
            return;
        }
        var ranks = rank(quests);
        for (int index = 1; index < ranks.size(); index++) {
            var before = ranks.get(index - 1);
            var after = ranks.get(index);
            if (before.score() == after.score()) {
                throw new IllegalArgumentException("Set a YAML difficulty tie-break for " + before.id() + " / " + after.id());
            }
        }
        System.out.println("schema: 1\norder:");
        for (Rank rank : ranks) System.out.printf("  - [%s, %d]%n", rank.id(), rank.score());
    }
}
