package ro.atf.chess.android;

import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;

final class BoardCaption {
    private BoardCaption() { }

    /** Reserve the same space before and after a caption changes. */
    static void reserve(TextView caption, int lines) {
        caption.setLines(lines);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) caption.setFallbackLineSpacing(false);
    }
}
