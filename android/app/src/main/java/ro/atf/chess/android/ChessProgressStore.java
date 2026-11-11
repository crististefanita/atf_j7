package ro.atf.chess.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.GameReview;
import ro.atf.chess.GameReviewCodec;
import ro.atf.chess.MatchArchive;
import ro.atf.chess.MatchSlots;
import ro.atf.chess.QuestProgress;
import ro.atf.chess.QuestRewards;

final class ChessProgressStore {
    private static final String CURRENT = "current-id";
    private static final String SOLVED = "solved-ids";
    private static final String REWARD_POINTS = "reward-points";
    private static final String REWARD_ASSISTED = "reward-assisted";
    private static final String REWARD_STREAK = "reward-streak";
    private static final String AWAITING_MATE = "awaiting-mate";
    private static final String RESUME_CHAPTER = "resume-chapter";
    private static final String MATCH = "match-checkpoint";
    private static final String RESUME_MATCH = "resume-match";
    private static final String MATCH_BLACK_BELOW = "match-black-below";
    private static final String MATCH_MANUAL_AI = "match-manual-ai";
    private static final String MATCH_MANUAL_USED = "match-manual-used";
    private static final String ACTIVE_MATCH_SLOT = "active-match-slot";
    private static final String MATCH_ID = "match-id";
    private static final String MATCH_ARCHIVED = "match-archived";
    private static final String ARCHIVE_ORDER = "match-archive-order";
    private static final String PLAYER_NAME = "player-name";
    private final SharedPreferences preferences;

    ChessProgressStore(Context context) { preferences = context.getSharedPreferences("quest-progress", Context.MODE_PRIVATE); }

    String playerName() { return preferences.getString(PLAYER_NAME, ""); }

    boolean savePlayerName(String name) {
        return (name.isEmpty() ? preferences.edit().remove(PLAYER_NAME) : preferences.edit().putString(PLAYER_NAME, name)).commit();
    }

    ChessMatch readMatch() {
        return readMatch(activeMatchSlot());
    }

    ChessMatch readMatch(int slot) {
        String encoded = preferences.getString(MatchSlots.key(MATCH, slot), null);
        return encoded == null ? null : ChessMatch.restore(encoded);
    }

    int activeMatchSlot() { return preferences.getInt(ACTIVE_MATCH_SLOT, 1); }
    boolean hasMatch(int slot) { return preferences.contains(MatchSlots.key(MATCH, slot)); }
    int freeMatchSlot() { return MatchSlots.firstFree(this::hasMatch); }
    boolean resumeMatch() { return preferences.getBoolean(RESUME_MATCH, false); }
    boolean matchBlackBelow() { return matchBlackBelow(activeMatchSlot()); }
    boolean matchBlackBelow(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), false); }
    boolean matchManualAi(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), false); }
    boolean matchManualUsed(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), false); }
    boolean leaveMatch() { return preferences.edit().remove(RESUME_MATCH).commit(); }

    boolean saveMatch(ChessMatch match, boolean blackBelow) {
        int slot = activeMatchSlot();
        return saveMatch(slot, match, blackBelow, matchManualAi(slot));
    }

    boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi) {
        return saveMatch(slot, match, blackBelow, manualAi, matchManualUsed(slot));
    }

    boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, manualAi, manualUsed, false);
    }

    boolean startMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, manualAi, manualUsed, true);
    }

    private boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed, boolean fresh) {
        String idKey = MatchSlots.key(MATCH_ID, slot);
        String archivedKey = MatchSlots.key(MATCH_ARCHIVED, slot);
        String id = fresh ? null : preferences.getString(idKey, null);
        if (id == null) id = UUID.randomUUID().toString();
        boolean archived = !fresh && preferences.getBoolean(archivedKey, false);
        var edit = preferences.edit().putString(idKey, id).putString(MatchSlots.key(MATCH, slot), match.checkpoint())
                .putBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), blackBelow)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), manualUsed)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), manualAi).putInt(ACTIVE_MATCH_SLOT, slot)
                .putBoolean(RESUME_MATCH, true).remove(RESUME_CHAPTER);
        if (!archived && match.result() != ChessMatch.Result.IN_PROGRESS) {
            recordArchive(edit, id, match, blackBelow, manualUsed);
            archived = true;
        }
        return edit.putBoolean(archivedKey, archived).commit();
    }

    record ArchivedMatch(String id, ChessMatch match, boolean blackBelow, boolean manualUsed, boolean reviewed) { }

    private List<String> archiveOrder() {
        String encoded = preferences.getString(ARCHIVE_ORDER, "");
        return encoded.isEmpty() ? List.of() : List.of(encoded.split(","));
    }

    private String archiveKey(String id, String field) { return "match-archive-" + id + "-" + field; }

    private void recordArchive(SharedPreferences.Editor edit, String id, ChessMatch match, boolean flipped, boolean manualUsed) {
        var retention = MatchArchive.retain(archiveOrder(), id);
        edit.putString(ARCHIVE_ORDER, String.join(",", retention.kept()))
                .putString(archiveKey(id, "checkpoint"), match.checkpoint())
                .putBoolean(archiveKey(id, "black-below"), flipped)
                .putBoolean(archiveKey(id, "manual-used"), manualUsed);
        for (var previous : retention.evicted()) {
            for (var field : List.of("checkpoint", "black-below", "manual-used", "review")) edit.remove(archiveKey(previous, field));
        }
    }

    void migrateCompletedMatches() {
        int active = activeMatchSlot();
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) if (slot != active) migrateCompletedMatch(slot);
        migrateCompletedMatch(active);
    }

    private void migrateCompletedMatch(int slot) {
        if (!hasMatch(slot) || preferences.contains(MatchSlots.key(MATCH_ID, slot))) return;
        try {
            var saved = readMatch(slot);
            String id = UUID.randomUUID().toString();
            var edit = preferences.edit().putString(MatchSlots.key(MATCH_ID, slot), id);
            if (saved.result() != ChessMatch.Result.IN_PROGRESS) {
                edit.putBoolean(MatchSlots.key(MATCH_ARCHIVED, slot), true);
                recordArchive(edit, id, saved, matchBlackBelow(slot), matchManualUsed(slot));
            }
            edit.commit();
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            // The other slots remain readable even if one older checkpoint is damaged.
        }
    }

    List<ArchivedMatch> archivedMatches() {
        var result = new ArrayList<ArchivedMatch>();
        for (String id : archiveOrder()) {
            try {
                var match = ChessMatch.restore(preferences.getString(archiveKey(id, "checkpoint"), ""));
                result.add(new ArchivedMatch(id, match, preferences.getBoolean(archiveKey(id, "black-below"), false),
                        preferences.getBoolean(archiveKey(id, "manual-used"), false), preferences.contains(archiveKey(id, "review"))));
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
                // Skip damaged entries while preserving the remaining history.
            }
        }
        return result;
    }

    String archivedId(int slot) { return preferences.getString(MatchSlots.key(MATCH_ID, slot), null); }
    boolean hasArchived(String id) { return id != null && preferences.contains(archiveKey(id, "checkpoint")); }

    GameReview readReview(String id, ChessMatch match) {
        if (id == null) return null;
        try {
            String encoded = preferences.getString(archiveKey(id, "review"), null);
            return encoded == null ? null : GameReviewCodec.decode(match, encoded);
        } catch (IllegalArgumentException | ClassCastException invalid) {
            return null;
        }
    }

    boolean saveReview(String id, ChessMatch match, GameReview review) {
        if (id == null || !match.checkpoint().equals(preferences.getString(archiveKey(id, "checkpoint"), null))) return false;
        return preferences.edit().putString(archiveKey(id, "review"), GameReviewCodec.encode(match, review)).commit();
    }

    QuestProgress read(List<String> order) {
        String saved = preferences.getString(CURRENT, null);
        if (saved != null && !order.contains(saved)) {
            String key = chapterKey(saved);
            var chapter = order.stream().filter(id -> chapterKey(id).equals(key)).collect(Collectors.toList());
            if (!chapter.isEmpty()) order = chapter;
        }
        return QuestProgress.restore(saved, preferences.getStringSet(SOLVED, Collections.emptySet()), order);
    }

    QuestRewards readRewards() {
        return QuestRewards.restore(preferences.getStringSet(REWARD_POINTS, Collections.emptySet()),
                preferences.getStringSet(REWARD_ASSISTED, Collections.emptySet()), preferences.getInt(REWARD_STREAK, 0));
    }

    String remembered(String firstId) {
        String key = chapterKey(firstId);
        String current = preferences.getString(CURRENT, firstId);
        String fallback = key.equals(chapterKey(current)) ? current : firstId;
        return preferences.getString(key, fallback);
    }

    boolean resumeChapter() { return preferences.getBoolean(RESUME_CHAPTER, false); }

    boolean leaveChapter() { return preferences.edit().remove(RESUME_CHAPTER).commit(); }

    String finish(String id) { return preferences.getString("quest-finish-" + id, null); }

    int ply(String id) {
        String saved = "quest-ply-" + id;
        if (preferences.contains(saved)) return preferences.getInt(saved, 0);
        String key = chapterKey(id);
        if (id.equals(preferences.getString(key + "-stage-id", null))) {
            return preferences.getInt(key + "-ply", preferences.getInt(key + "-stage", 0) * 2);
        }
        return id.equals(preferences.getString(AWAITING_MATE, null)) ? 2 : 0;
    }

    boolean save(QuestProgress progress, QuestRewards rewards) {
        return save(progress, rewards, progress.currentSolved() ? 0 : ply(progress.currentId()));
    }

    boolean save(QuestProgress progress, QuestRewards rewards, int ply) {
        return save(progress, rewards, ply, null);
    }

    boolean saveFinish(QuestProgress progress, QuestRewards rewards, String finish) {
        return save(progress, rewards, 0, finish);
    }

    private boolean save(QuestProgress progress, QuestRewards rewards, int ply, String finish) {
        String id = progress.currentId();
        String previous = preferences.getString(CURRENT, null);
        var edit = preferences.edit();
        if (previous != null) edit.putString(chapterKey(previous), previous);
        String key = chapterKey(id);
        if (id.equals(preferences.getString(AWAITING_MATE, null))) edit.remove(AWAITING_MATE);
        if (id.equals(preferences.getString(key + "-stage-id", null))) edit.remove(key + "-stage-id").remove(key + "-stage").remove(key + "-ply");
        if (ply == 0) edit.remove("quest-ply-" + id);
        else edit.putInt("quest-ply-" + id, ply);
        if (finish != null) edit.putString("quest-finish-" + id, finish);
        return edit.putString(key, id).putBoolean(RESUME_CHAPTER, true).remove(RESUME_MATCH)
                .putString(CURRENT, id).putStringSet(SOLVED, progress.solvedIds())
                .putStringSet(REWARD_POINTS, rewards.encodedPoints()).putStringSet(REWARD_ASSISTED, rewards.assistedIds())
                .putInt(REWARD_STREAK, rewards.streak()).commit();
    }

    private static String chapterKey(String id) {
        if (id.startsWith("tactic-")) return "last-tactics";
        if (id.startsWith("mate3-")) return "last-mate-three";
        return id.startsWith("mate2-") ? "last-mate-two" : "last-mate-one";
    }
}
