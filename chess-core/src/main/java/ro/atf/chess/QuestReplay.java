package ro.atf.chess;

/** A read-only cursor over a puzzle explanation, independent of saved progress. */
public final class QuestReplay {
    private final QuestPayoff payoff;
    private final int ply;
    private final QuestDanger danger;
    private final boolean showingDanger;

    private QuestReplay(QuestPayoff payoff, int ply, QuestDanger danger, boolean showingDanger) {
        this.payoff = payoff;
        this.ply = ply;
        this.danger = danger;
        this.showingDanger = showingDanger;
    }

    public static QuestReplay at(QuestPayoff payoff, int ply) {
        if (ply < 0 || ply > payoff.plies()) throw new IllegalArgumentException("Invalid replay ply: " + ply);
        return new QuestReplay(payoff, ply, payoff.dangerAt(ply), false);
    }

    public static QuestReplay mate(QuestCatalog.Quest authored, QuestStep savedStep, QuestCatalog.Quest completed) {
        var explained = completed == null ? authored : completed;
        boolean oneMoveChapter = authored.mateLength() == 1 && explained.mateLength() == 1;
        int initial = oneMoveChapter ? 1 : completed == null ? savedStep.ply() : 0;
        return at(QuestPayoff.forMateLine(explained), initial);
    }

    public QuestPayoff payoff() { return payoff; }
    public int ply() { return ply; }
    public QuestPayoff.Frame frame() { return payoff.at(ply); }
    public boolean hasPrevious() { return ply > 0; }
    public boolean hasNext() { return ply < payoff.plies(); }
    public boolean hasDanger() { return danger != null; }
    public QuestDanger shownDanger() { return showingDanger ? danger : null; }

    public QuestReplay previous() { return hasPrevious() ? at(payoff, ply - 1) : this; }
    public QuestReplay next() { return hasNext() ? at(payoff, ply + 1) : this; }

    public QuestReplay toggleDanger() {
        return hasDanger() ? new QuestReplay(payoff, ply, danger, !showingDanger) : this;
    }
}
