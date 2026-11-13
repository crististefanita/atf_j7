package ro.atf.chess.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Delivers system Back to an activity with its own internal navigation. */
abstract class BackNavigableActivity extends Activity {
    private Api33Back systemBack;

    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) systemBack = new Api33Back(this, this::back);
    }

    @Override protected void onStop() {
        if (Build.VERSION.SDK_INT >= 33 && systemBack != null) {
            systemBack.unregister();
            systemBack = null;
        }
        super.onStop();
    }

    // Only API 26–32 dispatch system Back through this override.
    @SuppressLint("GestureBackNavigation")
    @Override public final void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) back();
    }

    protected abstract void back();

    @TargetApi(33)
    private static final class Api33Back {
        private final OnBackInvokedDispatcher dispatcher;
        private final OnBackInvokedCallback callback;

        Api33Back(Activity activity, Runnable action) {
            dispatcher = activity.getOnBackInvokedDispatcher();
            callback = action::run;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        }

        void unregister() {
            dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}
