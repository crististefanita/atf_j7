package ro.atf.chess.android;

import android.content.Context;
import ro.atf.chess.MatchSetup;

final class MatchLabels {
    private MatchLabels() { }

    static String botName(Context context, MatchSetup.Level level) {
        return switch (level) {
            case BASIC -> context.getString(R.string.match_bot_label, "Alex", context.getString(R.string.match_basic));
            case ADVANCED -> context.getString(R.string.match_bot_label, "Mira", context.getString(R.string.match_advanced));
            case HARD -> context.getString(R.string.match_bot_label, "Atlas", context.getString(R.string.match_hard));
        };
    }
}
