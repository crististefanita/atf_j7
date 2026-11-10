package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import java.util.function.Predicate;
import ro.atf.chess.PlayerProfile;

final class PlayerNameEditor {
    private PlayerNameEditor() { }

    static void show(Context context, String current, Predicate<String> save, Runnable refresh) {
        var field = new EditText(context);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(current);
        field.setSelectAllOnFocus(true);
        field.setHint(R.string.profile_name_hint);
        field.setContentDescription(context.getString(R.string.profile_name_hint));
        var content = new FrameLayout(context);
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(pad, 0, pad, 0);
        content.addView(field, new FrameLayout.LayoutParams(-1, -2));
        var dialog = new AlertDialog.Builder(context).setTitle(R.string.profile_title).setView(content)
                .setPositiveButton(R.string.profile_save, null).setNeutralButton(R.string.profile_reset, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try { save(dialog, field, PlayerProfile.normalize(field.getText().toString()), save, refresh); }
                catch (IllegalArgumentException invalid) { field.setError(context.getString(R.string.profile_invalid)); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> save(dialog, field, "", save, refresh));
        });
        dialog.show();
    }

    private static void save(AlertDialog dialog, EditText field, String name, Predicate<String> save, Runnable refresh) {
        if (!save.test(name)) {
            field.setError(field.getContext().getString(R.string.progress_save_failed));
            return;
        }
        dialog.dismiss();
        refresh.run();
    }
}
