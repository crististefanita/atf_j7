package ro.atf.chess;

import ro.atf.chess.ChessPosition.Move;

/** A resumable point on the reference line, before the player's final move. */
public record QuestStep(int ply) {
    public static QuestStep restore(int ply, QuestCatalog.Quest quest) {
        return new QuestStep(Math.max(0, Math.min(ply, (quest.playerMoves() - 1) * 2)));
    }

    public int stage() { return ply / 2; }
    public boolean defensePending() { return ply % 2 != 0; }
    public QuestStep advance() { return new QuestStep(ply + 1); }
    public Move lastMove(QuestCatalog.Quest quest) { return ply == 0 ? null : quest.line().get(ply - 1); }
    public String finishCheckpoint(Move move) { return stage() + ":" + move.uci(); }

    public ChessPosition position(QuestCatalog.Quest quest) {
        var ready = quest.positionAt(stage());
        return defensePending() ? ready.play(lastMove(quest)) : ready;
    }
}
