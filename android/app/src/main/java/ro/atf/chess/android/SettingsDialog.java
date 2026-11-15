package ro.atf.chess.android;

import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import ro.atf.chess.AppLanguage;

final class SettingsDialog {
    private SettingsDialog() { }

    static void show(LocalizedActivity activity) {
        var preference = activity.languagePreference();
        var effective = activity.effectiveLanguage();
        var languages = AppLanguage.values();
        var previous = preference.selected();
        var selected = previous.orElse(effective);
        var entries = new String[languages.length];
        for (int index = 0; index < languages.length; index++) {
            entries[index] = activity.getString(switch (languages[index]) {
                case ROMANIAN -> R.string.settings_language_romanian;
                case ENGLISH -> R.string.settings_language_english;
            });
        }

        var dropdown = new Spinner(activity, Spinner.MODE_DROPDOWN);
        dropdown.setId(R.id.settings_language);
        dropdown.setMinimumHeight(dp(activity, 48));
        var adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(selected.ordinal());

        var label = new TextView(activity);
        label.setText(R.string.settings_language_label);
        label.setLabelFor(dropdown.getId());

        var content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 24);
        content.setPadding(horizontal, dp(activity, 8), horizontal, 0);
        content.addView(label, new LinearLayout.LayoutParams(-1, -2));
        content.addView(dropdown, new LinearLayout.LayoutParams(-1, -2));

        var dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_title)
                .setView(content)
                .setPositiveButton(R.string.settings_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            var requested = languages[dropdown.getSelectedItemPosition()];
            if (!preference.save(requested)) {
                Toast.makeText(activity, R.string.settings_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (previous.orElse(null) != requested) activity.recreate();
        }));
        dialog.show();
    }

    private static int dp(LocalizedActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
