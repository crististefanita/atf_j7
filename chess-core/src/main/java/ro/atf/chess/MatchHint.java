package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.OpeningCatalog.Step;
import ro.atf.chess.OpeningCatalog.Text;

/** A reveal cursor for the best continuations of one analyzed position. */
public final class MatchHint {
    public record Theory(String eco, Text family, Text name, List<Step> steps) {
        public Theory {
            steps = List.copyOf(steps);
            if (steps.isEmpty()) throw new IllegalArgumentException("Opening path has no moves");
        }

        public Move move() { return steps.get(0).move(); }
        public Text idea() { return steps.get(0).idea(); }
    }

    public record TheoryPlayback(List<Theory> paths, int selected) {
        public TheoryPlayback { paths = List.copyOf(paths); }
    }

    /** A stable preview of the current recommendation. Scores are comparable only at the supplied depth. */
    public record Playback(List<TacticalAnalysis.Variation> lines, int depth, ChessPosition.Move selected,
                           List<TacticalAnalysis.Variation> scoredLines) {
        public Playback {
            lines = Collections.unmodifiableList(new ArrayList<>(lines));
            scoredLines = Collections.unmodifiableList(new ArrayList<>(scoredLines));
        }

        public TacticalAnalysis.Variation scoreLine(int index) { return scoredLines.get(index); }

        public Optional<HintComparison> comparison(ChessPosition position, int activeIndex) {
            if (depth == 0 || lines.size() < 2) return Optional.empty();
            return Optional.of(HintComparison.of(position, scoreLine(0), scoreLine(activeIndex == 0 ? 1 : activeIndex), depth));
        }
    }

    private final List<TacticalAnalysis.Variation> lines;
    private final ChessPosition position;
    private final TacticalAnalysis.SearchReport comparison;
    private final List<Theory> theory;
    private int index = -1;

    public MatchHint(List<TacticalAnalysis.Variation> lines) {
        this(null, lines, null);
    }

    MatchHint(ChessPosition position, List<TacticalAnalysis.Variation> lines, TacticalAnalysis.SearchReport comparison) {
        this(position, lines, comparison, List.of());
    }

    private MatchHint(ChessPosition position, List<TacticalAnalysis.Variation> lines, TacticalAnalysis.SearchReport comparison, List<Theory> theory) {
        this.position = position;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.comparison = comparison;
        this.theory = theory;
    }

    public static MatchHint analyze(ChessPosition position) {
        var search = TacticalAnalysis.verifiedHints(position);
        return new MatchHint(position, defendedChoices(position, search.lines()), search.comparison());
    }

    static MatchHint study(ChessPosition position) {
        var choices = OpeningTheory.CURRENT.choices(position);
        return choices.isEmpty() ? analyze(position) : new MatchHint(position, List.of(), null, choices);
    }

    static List<TacticalAnalysis.Variation> defendedChoices(ChessPosition position, List<TacticalAnalysis.Variation> ranked) {
        if (ranked.size() < 2) return ranked;
        var leader = ranked.get(0);
        var lead = HintPreview.openDefended(position, leader).orElseThrow();
        int limit = MatchAiPolicy.CURRENT.hint.maxVariants();
        if (lead.evidence().reason() == HintPreview.Reason.CHECKMATE) return ranked.subList(0, Math.min(ranked.size(), limit));
        int gain = lead.estimate().map(HintPreview.Estimate::changeCp).orElse(0);
        if (gain <= 0) return List.of(leader);

        var shown = new ArrayList<TacticalAnalysis.Variation>();
        shown.add(leader);
        int gap = MatchAiPolicy.CURRENT.hint.maxLossCp();
        for (int index = 1; index < ranked.size() && shown.size() < limit; index++) {
            var candidate = ranked.get(index);
            int afterDefense = HintPreview.openDefended(position, candidate).flatMap(HintPreview::estimate)
                    .map(HintPreview.Estimate::changeCp).orElse(0);
            if (afterDefense > 0 && gain - afterDefense <= gap) shown.add(candidate);
        }
        return shown;
    }

    List<TacticalAnalysis.Variation> lines() { return lines; }
    public int count() { return theory.isEmpty() ? lines.size() : theory.size(); }
    public int number() { return index + 1; }
    public TacticalAnalysis.Variation first() { return lines.get(0); }
    public TacticalAnalysis.Variation revealed() { return index < 0 || lines.isEmpty() ? null : lines.get(index); }

    public Theory revealedTheory() { return index < 0 || theory.isEmpty() ? null : theory.get(index); }

    public Optional<TheoryPlayback> theoryPlayback(ChessPosition position) {
        return revealedTheory() == null || !matches(position) ? Optional.empty() : Optional.of(new TheoryPlayback(theory, index));
    }

    public Move revealedMove() {
        var opening = revealedTheory();
        if (opening != null) return opening.move();
        var line = revealed();
        return line == null ? null : line.move();
    }

    public MatchEvaluation evaluation(ChessPosition position) {
        if (count() == 0) return null;
        return theory.isEmpty() ? MatchEvaluation.forPosition(position, first()) : MatchEvaluation.forTheory(position);
    }

    public Optional<HintPreview> preview(ChessPosition position) {
        var line = revealed();
        return line == null || !matches(position) ? Optional.empty() : HintPreview.open(position, line);
    }

    public Optional<Playback> playback(ChessPosition position) {
        var selected = revealed();
        if (selected == null || !matches(position) || HintPreview.open(position, selected).isEmpty()) return Optional.empty();
        if (comparison == null) return Optional.of(singleLine(selected));

        var playable = new ArrayList<TacticalAnalysis.Variation>();
        var scored = new ArrayList<TacticalAnalysis.Variation>();
        for (var line : lines) {
            if (playable.size() == 3) break;
            var verified = comparedLine(line.move());
            if (verified == null || HintPreview.open(position, verified).isEmpty()) continue;
            boolean extendsVerifiedLine = line.line().size() >= verified.line().size()
                    && line.line().subList(0, verified.line().size()).equals(verified.line());
            playable.add(extendsVerifiedLine && HintPreview.open(position, line).isPresent() ? line : verified);
            scored.add(verified);
        }
        if (playable.size() < 2 || playable.stream().noneMatch(line -> line.move().equals(selected.move()))) {
            return Optional.of(singleLine(selected));
        }
        return Optional.of(new Playback(playable, comparison.depth(), selected.move(), scored));
    }

    public Optional<HintComparison> compare(ChessPosition position) {
        var current = revealed();
        if (current == null || comparison == null || !matches(position)) return Optional.empty();
        var ranked = comparison.result().ranked();
        if (ranked.size() < 2) return Optional.empty();
        var first = comparedLine(first().move());
        var other = index == 0 ? ranked.get(1) : comparedLine(current.move());
        return first == null || other == null ? Optional.empty() : Optional.of(HintComparison.of(position, first, other, comparison.depth()));
    }

    private TacticalAnalysis.Variation comparedLine(ChessPosition.Move move) {
        return comparison.result().ranked().stream().filter(line -> line.move().equals(move)).findFirst().orElse(null);
    }

    private static Playback singleLine(TacticalAnalysis.Variation line) {
        return new Playback(List.of(line), 0, line.move(), List.of(line));
    }

    public ChessPosition.Move moveForTap(ChessPosition position, int selected, int square) {
        var move = revealedMove();
        if (selected >= 0 || move == null || move.to() != square || !matches(position)) return null;
        return position.legalMovesFrom(move.from()).contains(move) ? move : null;
    }

    public void hide() { index = -1; }

    public void revealNext() {
        int size = count();
        if (size > 0) index = (index + 1) % size;
    }

    private boolean matches(ChessPosition board) { return position == null || position == board; }
}
