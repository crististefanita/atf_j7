package ro.atf.chess;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import ro.atf.chess.GameReviewTimeline.Snapshot;

/** Persists match slots and finished games through one atomic preferences transaction per change. */
public final class MatchStore {
    private static final String LEGACY_REVIEW_CHECKED = "legacy-web-review-checked";

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
    private static final String MATCH_USES_AI = "match-uses-ai";
    private static final String ACTIVE_MATCH_SLOT = "active-match-slot";
    private static final String MATCH_ID = "match-id";
    private static final String MATCH_CREATION = "match-creation";
    private static final String MATCH_CREATION_SEQUENCE = "match-creation-sequence";
    private static final String AI_OPENING_SEQUENCE = "ai-opening-sequence";
    private static final String MATCH_ARCHIVED = "match-archived";
    private static final String ARCHIVE_ORDER = "match-archive-order";
    private static final String ARCHIVE_USES_AI = "uses-ai";
    private static final String REVIEW_BOOKMARK = "review-bookmark";

    private final Preferences preferences;
    private final LongSupplier now;
    private final Supplier<String> ids;

    public MatchStore(Preferences preferences, Clock clock) {
        this(preferences, clock::millis, () -> UUID.randomUUID().toString());
    }

    MatchStore(Preferences preferences, LongSupplier now, Supplier<String> ids) {
        this.preferences = preferences;
        this.now = now;
        this.ids = ids;
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
            var match = readMatch(slot);
            return match != null && match.result() == ChessMatch.Result.IN_PROGRESS ? match : null;
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            return null;
        }
    }

    public List<Integer> savedMatchSlots() {
        return MatchSlots.newestFirst(slot -> readSavedMatch(slot) != null, this::matchCreationOrder);
    }

    private long matchCreationOrder(int slot) { return preferences.getLong(MatchSlots.key(MATCH_CREATION, slot), slot); }

    public long startedAt(int slot) { return preferences.getLong(MatchSlots.key(MATCH_STARTED_AT, slot), 0); }

    public int activeMatchSlot() { return preferences.getInt(ACTIVE_MATCH_SLOT, 1); }
    public boolean selectSaved(int slot) {
        return readSavedMatch(slot) != null && preferences.edit().putInt(ACTIVE_MATCH_SLOT, slot)
                .putBoolean(RESUME_MATCH, true).remove(RESUME_CHAPTER).commit();
    }
    public boolean hasMatch(int slot) { return preferences.contains(MatchSlots.key(MATCH, slot)); }
    public int freeMatchSlot() {
        int empty = MatchSlots.firstFree(this::hasMatch);
        if (empty != 0) return empty;
        var archived = MatchSlots.newestFirst(this::canReuseArchivedSlot, this::matchCreationOrder);
        return archived.isEmpty() ? 0 : archived.get(archived.size() - 1);
    }

    public int slotForNewMatch() {
        int available = freeMatchSlot();
        return available != 0 ? available : MatchSlots.forNewGame(this::hasMatch, this::matchCreationOrder);
    }

    /** Preparation consumes nothing; the new AI game and its choice commit atomically. */
    public ChessMatch prepareAiMatch(MatchSetup setup) {
        long sequence = preferences.getLong(AI_OPENING_SEQUENCE, 0);
        if (sequence < 0 || sequence == Long.MAX_VALUE) throw new IllegalStateException("Invalid AI opening sequence");
        return ChessMatch.start(setup, sequence + 1);
    }

    private boolean canReuseArchivedSlot(int slot) {
        return preferences.getBoolean(MatchSlots.key(MATCH_ARCHIVED, slot), false) && readSavedMatch(slot) == null;
    }
    public boolean resumeMatch() { return preferences.getBoolean(RESUME_MATCH, false); }
    public boolean matchBlackBelow() { return matchBlackBelow(activeMatchSlot()); }
    public boolean matchBlackBelow(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), false); }
    public boolean matchManualAi(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), false); }
    public boolean matchManualUsed(int slot) { return preferences.getBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), false); }
    public boolean leaveMatch() { return preferences.edit().remove(RESUME_MATCH).commit(); }

    enum GameOrigin { AI, LOCAL }

    // Older Web snapshots used the active control and archived participation flags to classify games.
    GameOrigin matchOrigin(int slot) {
        return gameOrigin(preferences.getBoolean(MatchSlots.key(MATCH_USES_AI, slot), !matchManualAi(slot)));
    }

    GameOrigin archiveOrigin(ArchivedMatch match) {
        return gameOrigin(preferences.getBoolean(archiveKey(match.id(), ARCHIVE_USES_AI), !match.manualUsed()));
    }

    private static GameOrigin gameOrigin(boolean usesAi) { return usesAi ? GameOrigin.AI : GameOrigin.LOCAL; }

    public boolean saveMatch(ChessMatch match, boolean blackBelow) {
        int slot = activeMatchSlot();
        return saveMatch(slot, match, blackBelow, matchManualAi(slot));
    }

    public boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi) {
        return saveMatch(slot, match, blackBelow, manualAi, matchManualUsed(slot));
    }

    public boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, null, manualAi, manualUsed, false);
    }

    public boolean startMatch(int slot, ChessMatch match, boolean blackBelow, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, null, manualAi, manualUsed, true);
    }

    boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, GameOrigin origin, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, origin, manualAi, manualUsed, false);
    }

    boolean startMatch(int slot, ChessMatch match, boolean blackBelow, GameOrigin origin, boolean manualAi, boolean manualUsed) {
        return saveMatch(slot, match, blackBelow, origin, manualAi, manualUsed, true);
    }

    private boolean saveMatch(int slot, ChessMatch match, boolean blackBelow, GameOrigin origin,
                              boolean manualAi, boolean manualUsed, boolean fresh) {
        String originKey = MatchSlots.key(MATCH_USES_AI, slot);
        GameOrigin savedOrigin = fresh ? null : recordedOrigin(originKey);
        if (origin != null && savedOrigin != null && origin != savedOrigin) return false;
        GameOrigin effectiveOrigin = origin == null ? savedOrigin : origin;
        if (effectiveOrigin == GameOrigin.LOCAL && !manualAi) return false;
        boolean recordedManualUse = manualUsed || !fresh && effectiveOrigin == GameOrigin.AI && matchManualUsed(slot);
        String idKey = MatchSlots.key(MATCH_ID, slot);
        String archivedKey = MatchSlots.key(MATCH_ARCHIVED, slot);
        String id = fresh ? null : preferences.getString(idKey, null);
        if (id == null) id = ids.get();
        boolean archived = !fresh && preferences.getBoolean(archivedKey, false);
        boolean newlyFinished = !archived && match.result() != ChessMatch.Result.IN_PROGRESS;
        long recordedAt = fresh || newlyFinished ? now.getAsLong() : 0;
        var edit = preferences.edit().putString(idKey, id).putString(MatchSlots.key(MATCH, slot), match.checkpoint())
                .putBoolean(MatchSlots.key(MATCH_BLACK_BELOW, slot), blackBelow)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_USED, slot), recordedManualUse)
                .putBoolean(MatchSlots.key(MATCH_MANUAL_AI, slot), manualAi).putInt(ACTIVE_MATCH_SLOT, slot)
                .putBoolean(RESUME_MATCH, true).remove(RESUME_CHAPTER);
        if (fresh) {
            if (effectiveOrigin != GameOrigin.LOCAL && match.openingChoice() > 0) {
                edit.putLong(AI_OPENING_SEQUENCE, Math.max(preferences.getLong(AI_OPENING_SEQUENCE, 0), match.openingChoice()));
            }
            long creation = preferences.getLong(MATCH_CREATION_SEQUENCE, MatchSlots.LIMIT) + 1;
            edit.putLong(MATCH_CREATION_SEQUENCE, creation).putLong(MatchSlots.key(MATCH_CREATION, slot), creation)
                    .putLong(MatchSlots.key(MATCH_STARTED_AT, slot), recordedAt);
            if (effectiveOrigin == null) edit.remove(originKey);
        }
        if (effectiveOrigin != null) edit.putBoolean(originKey, effectiveOrigin == GameOrigin.AI);
        if (newlyFinished) recordArchive(edit, id, match, blackBelow, effectiveOrigin, recordedManualUse, recordedAt);
        return edit.putBoolean(archivedKey, archived || newlyFinished).commit();
    }

    private GameOrigin recordedOrigin(String key) {
        return preferences.contains(key) ? gameOrigin(preferences.getBoolean(key, false)) : null;
    }

    public record ArchivedMatch(String id, ChessMatch match, boolean blackBelow, boolean manualUsed, boolean reviewed, long finishedAt) { }

    private List<String> archiveOrder() {
        String encoded = preferences.getString(ARCHIVE_ORDER, "");
        return encoded.isEmpty() ? List.of() : List.of(encoded.split(","));
    }

    private String archiveKey(String id, String field) { return "match-archive-" + id + "-" + field; }

    private void recordArchive(Editor edit, String id, ChessMatch match, boolean flipped, GameOrigin origin,
                               boolean manualUsed, long finishedAt) {
        var retention = MatchArchive.retain(archiveOrder(), id);
        edit.putString(ARCHIVE_ORDER, String.join(",", retention.kept()))
                .putString(archiveKey(id, "checkpoint"), match.checkpoint())
                .putBoolean(archiveKey(id, "black-below"), flipped)
                .putBoolean(archiveKey(id, "manual-used"), manualUsed).putLong(archiveKey(id, "finished-at"), finishedAt);
        if (origin != null) edit.putBoolean(archiveKey(id, ARCHIVE_USES_AI), origin == GameOrigin.AI);
        for (var previous : retention.evicted()) {
            for (var field : List.of("checkpoint", "black-below", "manual-used", ARCHIVE_USES_AI, "finished-at", "review", REVIEW_BOOKMARK)) {
                edit.remove(archiveKey(previous, field));
            }
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
            String id = ids.get();
            var edit = preferences.edit().putString(MatchSlots.key(MATCH_ID, slot), id);
            if (saved.result() != ChessMatch.Result.IN_PROGRESS) {
                edit.putBoolean(MatchSlots.key(MATCH_ARCHIVED, slot), true);
                recordArchive(edit, id, saved, matchBlackBelow(slot), recordedOrigin(MatchSlots.key(MATCH_USES_AI, slot)),
                        matchManualUsed(slot), 0);
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
        if (!matchesArchivedCheckpoint(id, match)) return false;
        return preferences.edit().putString(archiveKey(id, "review"), GameReviewCodec.encode(match, review)).commit();
    }

    public Snapshot readReviewBookmark(String id, ChessMatch match) {
        if (!matchesArchivedCheckpoint(id, match)) return null;
        try {
            String encoded = preferences.getString(archiveKey(id, REVIEW_BOOKMARK), null);
            return encoded == null ? null : GameReviewBookmark.decode(id, match, encoded);
        } catch (ClassCastException invalid) {
            return null;
        }
    }

    public boolean saveReviewBookmark(String id, ChessMatch match, Snapshot snapshot) {
        if (!matchesArchivedCheckpoint(id, match)) return false;
        String encoded = GameReviewBookmark.encode(id, match, snapshot);
        return encoded != null && preferences.edit().putString(archiveKey(id, REVIEW_BOOKMARK), encoded).commit();
    }

    record ReviewRecord(String review, String bookmark) { }

    ReviewRecord readReviewRecord(String id) {
        if (!hasArchived(id)) return null;
        return new ReviewRecord(readArchiveText(id, "review"), readArchiveText(id, REVIEW_BOOKMARK));
    }

    boolean saveReviewRecord(String id, ChessMatch match, String review, String digest) {
        if (!matchesArchivedCheckpoint(id, match) || !validReview(match, review, digest)) return false;
        if (validReview(match, readArchiveText(id, "review"), digest)) return false;
        return preferences.edit().putString(archiveKey(id, "review"), review).remove(archiveKey(id, REVIEW_BOOKMARK)).commit();
    }

    boolean saveReviewBookmark(String id, ChessMatch match, Snapshot snapshot, String digest) {
        if (!matchesArchivedCheckpoint(id, match) || !validReview(match, readArchiveText(id, "review"), digest)) return false;
        String encoded = GameReviewBookmark.encode(id, match, snapshot, digest);
        return encoded != null && preferences.edit().putString(archiveKey(id, REVIEW_BOOKMARK), encoded).commit();
    }

    boolean importLegacyReview(String oldId, ChessMatch match, String review, String bookmark, String digest) {
        if (preferences.getBoolean(LEGACY_REVIEW_CHECKED, false)) return false;
        var edit = preferences.edit().putBoolean(LEGACY_REVIEW_CHECKED, true);
        String id = null;
        if (match != null && match.result() != ChessMatch.Result.IN_PROGRESS && validReview(match, review, digest)) {
            id = uniqueArchiveId(match);
        }
        if (id != null && readArchiveText(id, "review") == null) {
            edit.putString(archiveKey(id, "review"), review);
            var snapshot = GameReviewBookmark.decode(oldId, match, bookmark, digest);
            if (snapshot != null) edit.putString(archiveKey(id, REVIEW_BOOKMARK), GameReviewBookmark.encode(id, match, snapshot, digest));
        } else {
            id = null;
        }
        if (!edit.commit()) throw new IllegalStateException("The legacy review check could not be saved");
        return id != null;
    }

    private String uniqueArchiveId(ChessMatch match) {
        String found = null;
        String checkpoint = match.checkpoint();
        for (String id : archiveOrder()) {
            if (!checkpoint.equals(readArchiveText(id, "checkpoint"))) continue;
            if (found != null) return null;
            found = id;
        }
        return found;
    }

    private String readArchiveText(String id, String field) {
        try {
            return preferences.getString(archiveKey(id, field), null);
        } catch (ClassCastException invalid) {
            return null;
        }
    }

    private static boolean validReview(ChessMatch match, String review, String digest) {
        if (review == null || digest == null) return false;
        try {
            GameReviewCodec.decodeLine(match, review, digest);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private boolean matchesArchivedCheckpoint(String id, ChessMatch match) {
        if (id == null || match == null || match.result() == ChessMatch.Result.IN_PROGRESS) return false;
        try {
            return match.checkpoint().equals(preferences.getString(archiveKey(id, "checkpoint"), null));
        } catch (ClassCastException invalid) {
            return false;
        }
    }

}
