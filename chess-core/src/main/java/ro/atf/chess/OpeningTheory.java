package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.SearchKey;
import ro.atf.chess.MatchHint.Theory;
import ro.atf.chess.OpeningCatalog.Step;
import ro.atf.chess.OpeningCatalog.Text;

/** Opening paths available from an exact position, irrespective of the move order used to reach it. */
final class OpeningTheory {
    private static final int MAX_PATHS = 3;
    static final OpeningTheory CURRENT = new OpeningTheory(OpeningCatalog.load(), OpeningIndex.CURRENT.lines());

    private record Frame(ChessPosition position, Map<Move, Frame> children) {
        Frame(ChessPosition position) { this(position, new HashMap<>()); }
    }

    private record Reference(GameReview.Opening opening, List<Step> steps, int ply, int coverage) {
        int length() { return steps.size() - ply; }
        Reference merge(Reference next) {
            var nearest = next.length() < length() ? next : this;
            return new Reference(nearest.opening, nearest.steps, nearest.ply, coverage + next.coverage);
        }
    }

    private final Map<SearchKey, List<Theory>> byPosition;

    private OpeningTheory(List<OpeningCatalog.Opening> lessons, List<OpeningIndex.BookLine> book) {
        var authored = new HashMap<SearchKey, Map<List<Move>, Theory>>();
        for (var lesson : lessons) {
            for (int ply = 0; ply < lesson.steps().size(); ply++) {
                var steps = lesson.steps().subList(ply, lesson.steps().size());
                var moves = steps.stream().map(Step::move).collect(Collectors.toList());
                var key = lesson.positionAt(ply).searchKey();
                authored.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(moves, new Theory(lesson.eco(), lesson.title(), lesson.variation(), steps));
            }
        }
        // Apply the display limit per position, after indexing every documented lesson.
        var choices = new HashMap<SearchKey, List<Theory>>();
        authored.forEach((key, paths) -> choices.put(key, paths.values().stream().limit(MAX_PATHS)
                .collect(Collectors.collectingAndThen(Collectors.toList(), List::copyOf))));
        references(book).forEach((key, indexed) -> choices.computeIfAbsent(key, ignored -> indexed.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Move, Reference>>comparingInt(entry -> entry.getValue().coverage()).reversed()
                        .thenComparing(entry -> entry.getKey().uci()))
                .limit(MAX_PATHS).map(entry -> fromIndex(entry.getValue()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), List::copyOf))));
        byPosition = Map.copyOf(choices);
    }

    List<Theory> choices(ChessPosition position) {
        return byPosition.getOrDefault(position.searchKey(), List.of());
    }

    private static Map<SearchKey, Map<Move, Reference>> references(List<OpeningIndex.BookLine> book) {
        var references = new HashMap<SearchKey, Map<Move, Reference>>();
        var root = new Frame(ChessPosition.initial());
        for (var line : book) {
            var steps = line.moves().stream().map(move -> new Step(Move.fromUci(move), null)).collect(Collectors.toList());
            var frame = root;
            for (int ply = 0; ply < steps.size(); ply++) {
                var move = steps.get(ply).move();
                var reference = new Reference(line.opening(), steps, ply, 1);
                var children = references.computeIfAbsent(frame.position().searchKey(), ignored -> new HashMap<>());
                children.merge(move, reference, Reference::merge);
                ChessPosition before = frame.position();
                frame = frame.children().computeIfAbsent(move, next -> new Frame(before.play(next)));
            }
        }
        return references;
    }

    private static Theory fromIndex(Reference reference) {
        String name = reference.opening().name();
        String family = name.split(":", 2)[0];
        return new Theory(reference.opening().eco(), new Text(family, family), new Text(name, name),
                reference.steps().subList(reference.ply(), reference.steps().size()));
    }
}
