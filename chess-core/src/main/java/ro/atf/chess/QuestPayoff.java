package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

/** The authored tactic replay, with the actual change in material from the solver's side. */
public record QuestPayoff(List<Frame> frames, QuestCatalog.Lesson lesson, QuestDefenseStudy defenseStudy) {
    private static final int[] VALUES = values();
    public record Capture(Piece piece, int square) { }
    public record Frame(ChessPosition position, Move move, Capture capture, int materialChange) { }

    public QuestPayoff { frames = List.copyOf(frames); }

    public static QuestPayoff forQuest(QuestCatalog.Quest quest) {
        return replay(quest, quest.line(), null);
    }

    public static QuestPayoff forExplanation(QuestCatalog.Quest quest) {
        var study = QuestDefenseStudy.forId(quest.id());
        if (study == null || quest.line().size() > 1) return forQuest(quest);
        if (!study.line().get(0).equals(quest.solution())) {
            throw new IllegalArgumentException("The reviewed defense must start with the key of " + quest.id());
        }
        return replay(quest, study.line(), study);
    }

    public static QuestPayoff forMateLine(QuestCatalog.Quest quest) {
        if (quest.objective() != QuestCatalog.Objective.MATE) throw new IllegalArgumentException("Expected a mating line");
        var replay = replay(quest.position(), quest.line(), null, null);
        if (!replay.last().position().isCheckmate()) throw new IllegalArgumentException("The line must end in checkmate");
        return replay;
    }

    private static QuestPayoff replay(QuestCatalog.Quest quest, List<Move> line, QuestDefenseStudy study) {
        if (quest.objective() != QuestCatalog.Objective.MATERIAL) throw new IllegalArgumentException("Expected a material quest");
        return replay(quest.position(), line, quest.lesson(), study);
    }

    static QuestPayoff replay(ChessPosition position, List<Move> line, QuestCatalog.Lesson lesson, QuestDefenseStudy study) {
        var side = position.turn();
        int original = balance(position, side);
        var frames = new ArrayList<Frame>(line.size() + 1);
        frames.add(new Frame(position, null, null, 0));
        for (Move move : line) {
            var next = position.play(move);
            var victim = MatchMaterial.victim(position, move);
            Capture capture = null;
            if (victim != null) {
                int square = position.pieceAt(move.to()) == null ? BoardSquare.of(move.to() % 8, move.from() / 8) : move.to();
                capture = new Capture(victim, square);
            }
            frames.add(new Frame(next, move, capture, balance(next, side) - original));
            position = next;
        }
        return new QuestPayoff(frames, lesson, study);
    }

    public int plies() { return frames.size() - 1; }
    public Frame at(int ply) { return frames.get(ply); }
    public Frame last() { return at(plies()); }
    public QuestDanger dangerAt(int ply) { return QuestDanger.at(this, ply); }

    /** Lowest material shown now or after one legal reply at the end of a reviewed defense. */
    public Integer immediateMaterialFloor() {
        if (defenseStudy == null) return null;
        var origin = at(0).position();
        var side = origin.turn();
        var finish = last().position();
        if (finish.turn() == side) return null;
        int startingBalance = balance(origin, side);
        int floor = last().materialChange();
        for (var reply : finish.legalMoves()) floor = Math.min(floor, balance(finish.play(reply), side) - startingBalance);
        return floor;
    }

    static int balance(ChessPosition position, Side side) {
        int result = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece != null) result += (piece.side() == side ? 1 : -1) * VALUES[piece.kind().ordinal()];
        }
        return result;
    }

    private static int[] values() {
        var configured = ChessYaml.map(ChessYaml.resource("tactical-analysis.yaml"), "material");
        var result = new int[Kind.values().length];
        for (var kind : Kind.values()) result[kind.ordinal()] = ChessYaml.number(configured, kind.name());
        return result;
    }
}
