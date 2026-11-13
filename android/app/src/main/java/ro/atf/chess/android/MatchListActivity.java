package ro.atf.chess.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Date;
import ro.atf.chess.MatchListEntry;
import ro.atf.chess.MatchStore;

public final class MatchListActivity extends Activity {
    static final int SAVED_REQUEST = 18;
    static final int FINISHED_REQUEST = 19;
    static final String SAVED_SLOT = "saved-slot";
    static final String ARCHIVE_ID = "archive-id";
    private static final String FINISHED = "finished-games";
    private static final View.AccessibilityDelegate ROW_ACCESSIBILITY = new View.AccessibilityDelegate() {
        @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
        }
    };

    static void openSaved(Activity owner) {
        owner.startActivityForResult(new Intent(owner, MatchListActivity.class), SAVED_REQUEST);
    }

    static void openFinished(Activity owner) {
        owner.startActivityForResult(new Intent(owner, MatchListActivity.class).putExtra(FINISHED, true), FINISHED_REQUEST);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var page = getLayoutInflater().inflate(R.layout.match_list, null);
        if (Build.VERSION.SDK_INT >= 35) {
            page.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        boolean finished = getIntent().getBooleanExtra(FINISHED, false);
        var store = new ChessProgressStore(this).matches();
        TextView title = page.findViewById(R.id.match_list_title);
        title.setText(finished ? R.string.match_archive : R.string.match_saved_games);
        LinearLayout rows = page.findViewById(R.id.match_list_rows);
        if (finished) showFinished(rows, store);
        else showSaved(rows, store);
        if (rows.getChildCount() == 0) {
            TextView empty = page.findViewById(R.id.match_list_empty);
            empty.setText(finished ? R.string.match_archive_empty : R.string.match_saved_empty);
            empty.setVisibility(View.VISIBLE);
        } else rows.setVisibility(View.VISIBLE);
        page.findViewById(R.id.match_list_back).setOnClickListener(view -> finish());
        setContentView(page);
    }

    private void showSaved(LinearLayout rows, MatchStore store) {
        for (int slot : store.savedMatchSlots()) {
            var saved = store.readSavedMatch(slot);
            if (saved == null) continue;
            var entry = MatchListEntry.saved(slot, saved, store.startedAt(slot));
            addRow(rows, entry, new Intent().putExtra(SAVED_SLOT, slot));
        }
    }

    private void showFinished(LinearLayout rows, MatchStore store) {
        for (var entry : store.archivedMatches()) {
            addRow(rows, MatchListEntry.finished(entry), new Intent().putExtra(ARCHIVE_ID, entry.id()));
        }
    }

    private void addRow(LinearLayout rows, MatchListEntry entry, Intent selection) {
        var row = getLayoutInflater().inflate(R.layout.match_list_row, rows, false);
        TextView title = row.findViewById(R.id.match_row_label);
        TextView details = row.findViewById(R.id.match_row_details);
        String label = rowTitle(entry);
        title.setText(label);
        row.setAccessibilityDelegate(ROW_ACCESSIBILITY);
        String moves = getResources().getQuantityString(R.plurals.match_row_moves, entry.moves(), entry.moves());
        String summary = moves + " · " + getString(statusLabel(entry.status()));
        if (entry.reviewed()) summary += " · " + getString(R.string.match_archive_reviewed);
        row.setOnClickListener(view -> select(selection));
        details.setText(summary);
        String description = label + ", " + summary;
        if (entry.hasTimestamp()) {
            var date = new Date(entry.timestamp());
            String localDate = DateFormat.getDateFormat(this).format(date);
            String localTime = DateFormat.getTimeFormat(this).format(date);
            TextView badge = row.findViewById(R.id.match_row_date);
            badge.setText(localDate + " · " + localTime);
            badge.setVisibility(View.VISIBLE);
            description += ", " + localDate + ", " + localTime;
        }
        row.setContentDescription(description);
        rows.addView(row);
    }

    private String rowTitle(MatchListEntry entry) {
        String opponent = MatchLabels.opponentName(entry.opponent());
        return entry.slot() > 0 ? getString(R.string.match_saved_title, entry.slot(), opponent)
                : getString(R.string.match_finished_title, opponent);
    }

    private static int statusLabel(MatchListEntry.Status status) {
        return switch (status) {
            case IN_PROGRESS -> R.string.match_slot_active;
            case WON -> R.string.match_list_won;
            case LOST -> R.string.match_list_lost;
            case DRAW -> R.string.match_archive_draw;
        };
    }

    private void select(Intent selection) {
        setResult(RESULT_OK, selection);
        finish();
    }
}
