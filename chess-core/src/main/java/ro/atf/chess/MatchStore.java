package ro.atf.chess;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists match slots and finished games through one atomic preferences transaction per change. */
public final class MatchStore {
    public interface Preferences {
        String getString(String key, String fallback);
        boolean getBoolean(String key, boolean fallback);
        int getInt(String key, int fallback);
        long getLong(String key, long fallback);
        boolean contains(String key);
        Editor edit();
    }

    public interface Editor {
        Editor putString(String key, String value);
        Editor putBoolean(String key, boolean value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor remove(String key);
        boolean commit();
    }

    public static final String RESUME_CHAPTER = "resume-chapter";
    private static final String MATCH_STARTED_AT = "match-started-at";
    private static final String MATCH = "match-checkpoint";
    public static final String RESUME_MATCH = "resume-match";
    private static final String MATCH_BLACK_BELOW = "match-black-below";
    private static final String MATCH_MANUAL_AI = "match-manual-ai";
    private static final String MATCH_MANUAL_USED = "match-manual-used";
    private static final String ACTIVE_MATCH_SLOT = "active-match-slot";
    private static final String MATCH_ID = "match-id";
    private static final String MATCH_CREATION = "match-creation";
    private static final String MATCH_CREATION_SEQUENCE = "match-creation-sequence";
    private static final String MATCH_ARCHIVED = "match-archived";
    private static final String ARCHIVE_ORDER = "match-archive-order";

    private final Preferences preferences;
    private final Clock clock;

    public MatchStore(Preferences preferences, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
    }

    public ChessMatch readMatch() {
        return readMatch(activeMatchSlot());
    }

    public ChessMatch readMatch(int slot) {
        String encoded = preferences.getString(MatchSlots.key(MATCH, slot), null);
        return encoded == null ? null : ChessMatch.restore(encoded);
    }

    public ChessMatch readSavedMatch(int slot) {
        try {
            return readMatch(slot);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            return null;
        }
    }

    public List<Integer> savedMatchSlots() {
        return MatchSlots.newestFirst(this::hasMatch, this::matchCreationOrder);
    }

    private long matchCreationOrder(int slot) { return preferences.getLong(MatchSlots.key(MATCH_CREATION, slot), slot); }

    public long startedAt(int slot) { return preferences.getLong(MatchSlots.key(MATCH_STARTED_AT, slot), 0); }

    public int activeMatchSlot() { return preferences.getInt(ACTIVE_MATCH_SLOT, 1); }
    public boolean hasMatch(int slot) { return preferences.contains(MatchSlots.key(MATCH, slot)); }
    public int freeMatchSlot() { return MatchSlots.firstFree(this::hasMatch); }
    public int slotForNewMatch() { return MatchSlots.forNewGame(this::hasMatch, this::matchCreationOrder); }
    public boolean resumeMatch() { return preferences.getBoolean(RESUME_MATCH, false); }
    public boolean matchBlackBelow() { return matchBlackBelow(activeMatchSlot()); }
    public boolean matchBlackBelow(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), false); }
    public boolean matchManualAi(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), false); }
    public boolean matchManualUsed(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), false); }
    public boolean leaveMatch() { return preferences.edit().remove(RESUME_MATCH).commit(); }

    public boolean saveMatch(ChessMatch match, boolean blackBelow) {
        int slot = activeMatchSlot();
        return saveMatch(slot, match, blackBelow, matchManualAi(slot));
    }

    public boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi) {
        return saveMatch(slot, match, blackBelow, manualAi, matchManualUsed(slot));
    }

    public boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, manualAi, manualUsed, false);
    }

    public boolean startMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, manualAi, manualUsed, true);
    }

    private boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed, boolean fresh) {
        String idKey = MatchSlots.key(MATCH_ID, slot);
        String archivedKey = MatchSlots.key(MATCH_ARCHIVED, slot);
        String id = fresh ? null : preferences.getString(idKey, null);
        if (id == null) id = UUID.randomUUID().toString();
        boolean archived = !fresh && preferences.getBoolean(archivedKey, false);
        boolean newlyFinished = !archived && match.result() != ChessMatch.Result.IN_PROGRESS;
        long recordedAt = fresh || newlyFinished ? clock.millis() : 0;
        var edit = preferences.edit().putString(idKey, id).putString(MatchSlots.key(MATCH, slot), match.checkpoint())
                .putBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), blackBelow)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), manualUsed)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), manualAi).putInt(ACTIVE_MATCH_SLOT, slot)
                .putBoolean(RESUME_MATCH, true).remove(RESUME_CHAPTER);
        if (fresh) {
            long creation = preferences.getLong(MATCH_CREATION_SEQUENCE, MatchSlots.LIMIT) + 1;
            edit.putLong(MATCH_CREATION_SEQUENCE, creation).putLong(MatchSlots.key(MATCH_CREATION, slot), creation)
                    .putLong(MatchSlots.key(MATCH_STARTED_AT, slot), recordedAt);
        }
        if (newlyFinished) recordArchive(edit, id, match, blackBelow, manualUsed, recordedAt);
        return edit.putBoolean(archivedKey, archived || newlyFinished).commit();
    }

    public record ArchivedMatch(String id, ChessMatch match, boolean blackBelow, boolean manualUsed, boolean reviewed, long finishedAt) { }

    private List<String> archiveOrder() {
        String encoded = preferences.getString(ARCHIVE_ORDER, "");
        return encoded.isEmpty() ? List.of() : List.of(encoded.split(","));
    }

    private String archiveKey(String id, String field) { return "match-archive-" + id + "-" + field; }

    private void recordArchive(Editor edit, String id, ChessMatch match, boolean flipped, boolean manualUsed, long finishedAt) {
        var retention = MatchArchive.retain(archiveOrder(), id);
        edit.putString(ARCHIVE_ORDER, String.join(",", retention.kept()))
                .putString(archiveKey(id, "checkpoint"), match.checkpoint())
                .putBoolean(archiveKey(id, "black-below"), flipped)
                .putBoolean(archiveKey(id, "manual-used"), manualUsed).putLong(archiveKey(id, "finished-at"), finishedAt);
        for (var previous : retention.evicted()) {
            for (var field : List.of("checkpoint", "black-below", "manual-used", "finished-at", "review")) edit.remove(archiveKey(previous, field));
        }
    }

    public void migrateCompletedMatches() {
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
                recordArchive(edit, id, saved, matchBlackBelow(slot), matchManualUsed(slot), 0);
            }
            edit.commit();
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            // The other slots remain readable even if one older checkpoint is damaged.
        }
    }

    public List<ArchivedMatch> archivedMatches() {
        var result = new ArrayList<ArchivedMatch>();
        for (String id : archiveOrder()) {
            var entry = readArchivedMatch(id);
            if (entry != null) result.add(entry);
        }
        return result;
    }

    public ArchivedMatch readArchivedMatch(String id) {
        if (id == null) return null;
        try {
            String checkpoint = preferences.getString(archiveKey(id, "checkpoint"), null);
            if (checkpoint == null) return null;
            var match = ChessMatch.restore(checkpoint);
            if (match.result() == ChessMatch.Result.IN_PROGRESS) return null;
            return new ArchivedMatch(id, match, preferences.getBoolean(archiveKey(id, "black-below"), false),
                    preferences.getBoolean(archiveKey(id, "manual-used"), false), preferences.contains(archiveKey(id, "review")),
                    preferences.getLong(archiveKey(id, "finished-at"), 0));
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            return null;
        }
    }

    public String archivedId(int slot) { return preferences.getString(MatchSlots.key(MATCH_ID, slot), null); }
    public boolean hasArchived(String id) { return id != null && preferences.contains(archiveKey(id, "checkpoint")); }

    public GameReview readReview(String id, ChessMatch match) {
        if (id == null) return null;
        try {
            String encoded = preferences.getString(archiveKey(id, "review"), null);
            return encoded == null ? null : GameReviewCodec.decode(match, encoded);
        } catch (IllegalArgumentException | ClassCastException invalid) {
            return null;
        }
    }

    public boolean saveReview(String id, ChessMatch match, GameReview review) {
        if (id == null || !match.checkpoint().equals(preferences.getString(archiveKey(id, "checkpoint"), null))) return false;
        return preferences.edit().putString(archiveKey(id, "review"), GameReviewCodec.encode(match, review)).commit();
    }
}
