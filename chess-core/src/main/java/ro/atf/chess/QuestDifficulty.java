package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.QuestCatalog.Quest;

/** A reproducible curriculum score, independent of the stable puzzle identifier. */
public final class QuestDifficulty {
    record Rank(String id, int score) { }

    private QuestDifficulty() { }

    static List<Rank> rank(List<Quest> quests) {
        var entries = new ArrayList<Rank>();
        for (Quest quest : quests) entries.add(new Rank(quest.id(), score(quest)));
        entries.sort(Comparator.comparingInt(Rank::score).thenComparing(Rank::id));
        return Collections.unmodifiableList(entries);
    }

    private static int score(Quest quest) {
        if (quest.id().equals("mate-001")) return 0; // Familiar introductory pattern.
        var legal = quest.position().legalMoves();
        int checkingDecoys = 0;
        int oneReplyDecoys = 0;
        for (var move : legal) {
            if (move.equals(quest.solution())) continue;
            var after = quest.position().play(move);
            if (after.isCheck()) {
                checkingDecoys++;
                if (after.legalMoves().size() == 1) oneReplyDecoys++;
            }
        }
        var matingPosition = quest.position().play(quest.solution());
        int motif = switch (quest.theme()) {
            case "CASTLING" -> 18;
            case "EN_PASSANT" -> 24;
            case "PROMOTION" -> quest.solution().promotion() == Kind.QUEEN ? 10 : 24;
            default -> matingPosition.isCheckedFrom(quest.solution().to()) ? 0 : 14;
        };
        // Starting in check can hide a mating key among defenses, even in a position with few legal moves.
        int counterMate = quest.position().isCheck() ? 16 : 0;
        return 1 + Math.min(12, legal.size() / 4) + Math.min(28, checkingDecoys * 4) + Math.min(15, oneReplyDecoys * 5) + motif + counterMate;
    }

    public static void main(String[] args) {
        System.out.println("schema: 1\norder:");
        for (Rank rank : rank(QuestCatalog.loadPositions())) System.out.printf("  - [%s, %d]%n", rank.id(), rank.score());
    }
}
