package ro.atf.chess;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;

/** Authored opening lines, replayed by the same rules as a live match. */
public final class OpeningCatalog {
    public record Text(String en, String ro) {
        public String in(Locale locale) { return "ro".equals(locale.getLanguage()) ? ro : en; }
    }

    public record Step(Move move, Text idea) { }

    public static final class Opening {
        private final String id;
        private final String eco;
        private final URI source;
        private final Text title;
        private final Text variation;
        private final Text plan;
        private final List<Step> steps;
        private final List<ChessPosition> positions;

        private Opening(String id, String eco, URI source, Text title, Text variation, Text plan, List<Step> steps) {
            this.id = id;
            this.eco = eco;
            this.source = source;
            this.title = title;
            this.variation = variation;
            this.plan = plan;
            this.steps = List.copyOf(steps);
            var frames = new ArrayList<ChessPosition>();
            frames.add(ChessPosition.initial());
            for (Step step : steps) {
                try {
                    frames.add(frames.get(frames.size() - 1).play(step.move()));
                } catch (IllegalArgumentException illegal) {
                    throw new IllegalArgumentException(id + " ply " + frames.size() + " has illegal move " + step.move().uci(), illegal);
                }
            }
            positions = List.copyOf(frames);
        }

        public String id() { return id; }
        public String eco() { return eco; }
        public URI source() { return source; }
        public Text title() { return title; }
        public Text variation() { return variation; }
        public Text plan() { return plan; }
        public List<Step> steps() { return steps; }
        public ChessPosition positionAt(int ply) { return positions.get(ply); }
    }

    private OpeningCatalog() { }

    public static List<Opening> load() { return read(ChessYaml.resource("openings.yaml")); }

    static List<Opening> read(Map<?, ?> root) {
        requireKeys(root, "schema", "openings");
        if (ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid opening catalog schema");
        var catalog = new ArrayList<Opening>();
        var ids = new HashSet<String>();
        var lines = new HashSet<List<Move>>();
        for (Object item : ChessYaml.list(root, "openings")) {
            if (!(item instanceof Map<?, ?> row)) throw new IllegalArgumentException("Expected an opening mapping");
            requireKeys(row, "id", "eco", "source", "title", "variation", "plan", "line");
            String id = ChessYaml.string(row, "id");
            String eco = ChessYaml.string(row, "eco");
            if (!id.matches("[a-z]+(?:-[a-z]+)*") || !ids.add(id) || !eco.matches("[A-E][0-9]{2}")) {
                throw new IllegalArgumentException("Invalid or duplicate opening identity " + id);
            }
            URI source = URI.create(ChessYaml.string(row, "source"));
            if (!"https".equals(source.getScheme()) || source.getHost() == null || source.getUserInfo() != null) {
                throw new IllegalArgumentException("Opening source must use HTTPS: " + id);
            }
            var steps = new ArrayList<Step>();
            for (Object value : ChessYaml.list(row, "line")) {
                if (!(value instanceof Map<?, ?> step)) throw new IllegalArgumentException("Expected a step in " + id);
                requireKeys(step, "move", "idea");
                steps.add(new Step(Move.fromUci(ChessYaml.string(step, "move")), text(step, "idea")));
            }
            if (steps.size() < 4 || !lines.add(steps.stream().map(Step::move).collect(java.util.stream.Collectors.toList()))) {
                throw new IllegalArgumentException("Incomplete or duplicate opening line " + id);
            }
            catalog.add(new Opening(id, eco, source, text(row, "title"), text(row, "variation"), text(row, "plan"), steps));
        }
        return List.copyOf(catalog);
    }

    private static Text text(Map<?, ?> row, String key) {
        var translations = ChessYaml.map(row, key);
        requireKeys(translations, "en", "ro");
        return new Text(ChessYaml.string(translations, "en"), ChessYaml.string(translations, "ro"));
    }

    private static void requireKeys(Map<?, ?> row, String... keys) {
        if (!row.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Invalid opening keys: " + row.keySet());
    }
}
