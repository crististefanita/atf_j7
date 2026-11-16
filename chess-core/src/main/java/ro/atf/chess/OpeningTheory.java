package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.SearchKey;
import ro.atf.chess.MatchHint.Theory;
import ro.atf.chess.OpeningCatalog.Text;

/** Continuations from any indexed position, including positions reached through another move order. */
final class OpeningTheory {
    static final OpeningTheory CURRENT = new OpeningTheory(OpeningCatalog.load(), OpeningIndex.CURRENT.lines());

    private record Frame(ChessPosition position, Map<Move, Frame> children) {
        Frame(ChessPosition position) { this(position, new HashMap<>()); }
    }

    private record Reference(GameReview.Opening opening, int distance, int coverage) {
        Reference merge(Reference next) {
            var nearest = next.distance < distance ? next : this;
            return new Reference(nearest.opening, nearest.distance, coverage + next.coverage);
        }
    }

    private final Map<SearchKey, List<Theory>> byPosition;

    private OpeningTheory(List<OpeningCatalog.Opening> lessons, List<OpeningIndex.BookLine> book) {
        var curated = new HashMap<SearchKey, Map<Move, Theory>>();
        for (var lesson : lessons) {
            for (int ply = 0; ply < lesson.steps().size(); ply++) {
                var step = lesson.steps().get(ply);
                var key = lesson.positionAt(ply).searchKey();
                curated.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(step.move(), new Theory(step.move(), lesson.eco(), lesson.title(), lesson.variation(), step.idea()));
            }
        }
        var references = references(book);
        references.keySet().forEach(key -> curated.computeIfAbsent(key, ignored -> new LinkedHashMap<>()));
        var choices = new HashMap<SearchKey, List<Theory>>();
        curated.forEach((key, authored) -> choices.put(key, order(authored, references.getOrDefault(key, Map.of()))));
        byPosition = Map.copyOf(choices);
    }

    List<Theory> choices(ChessPosition position) {
        return byPosition.getOrDefault(position.searchKey(), List.of());
    }

    private static Map<SearchKey, Map<Move, Reference>> references(List<OpeningIndex.BookLine> book) {
        var references = new HashMap<SearchKey, Map<Move, Reference>>();
        var root = new Frame(ChessPosition.initial());
        for (var line : book) {
            var frame = root;
            for (int ply = 0; ply < line.moves().size(); ply++) {
                var move = Move.fromUci(line.moves().get(ply));
                var reference = new Reference(line.opening(), line.moves().size() - ply - 1, 1);
                var children = references.computeIfAbsent(frame.position().searchKey(), ignored -> new HashMap<>());
                children.merge(move, reference, Reference::merge);
                ChessPosition before = frame.position();
                frame = frame.children().computeIfAbsent(move, next -> new Frame(before.play(next)));
            }
        }
        return references;
    }

    private static List<Theory> order(Map<Move, Theory> authored, Map<Move, Reference> indexed) {
        var choices = new ArrayList<>(authored.values());
        indexed.entrySet().stream()
                .filter(entry -> !authored.containsKey(entry.getKey()))
                .sorted(Comparator.<Map.Entry<Move, Reference>>comparingInt(entry -> entry.getValue().coverage()).reversed()
                        .thenComparing(entry -> entry.getKey().uci()))
                .forEach(entry -> choices.add(fromIndex(entry.getKey(), entry.getValue().opening())));
        int limit = MatchAiPolicy.CURRENT.hint.maxVariants();
        return List.copyOf(choices.subList(0, Math.min(limit, choices.size())));
    }

    private static Theory fromIndex(Move move, GameReview.Opening opening) {
        String name = opening.name();
        // Keep the family on the board and the complete pinned name in the explanation.
        String family = name.split(":", 2)[0];
        return new Theory(move, opening.eco(), new Text(family, family), new Text(name, name), null);
    }
}
