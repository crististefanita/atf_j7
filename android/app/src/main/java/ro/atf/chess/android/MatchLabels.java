package ro.atf.chess.android;

import android.content.Context;
import ro.atf.chess.MatchSetup;

final class MatchLabels {
    private MatchLabels() { }

    static String opponentName(MatchSetup.Level level) {
        return switch (level) {
            case BASIC -> "Alex";
            case ADVANCED -> "Mira";
            case HARD -> "Atlas";
        };
    }

    static String botName(Context context, MatchSetup.Level level) {
        int label = switch (level) {
            case BASIC -> R.string.match_basic;
            case ADVANCED -> R.string.match_advanced;
            case HARD -> R.string.match_hard;
        };
        return context.getString(R.string.match_bot_label, opponentName(level), context.getString(label));
    }
}
