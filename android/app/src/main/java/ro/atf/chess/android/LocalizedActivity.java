package ro.atf.chess.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.ArrayList;
import java.util.Locale;
import ro.atf.chess.AppLanguage;
import ro.atf.chess.AppLanguagePreference;

/** Applies an explicit app language before a screen reads resources. */
abstract class LocalizedActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        var language = preference(base).selected();
        if (!language.isPresent()) {
            super.attachBaseContext(base);
            return;
        }
        var override = new Configuration();
        override.setLocales(new LocaleList(language.get().locale()));
        super.attachBaseContext(base.createConfigurationContext(override));
    }

    final AppLanguagePreference languagePreference() { return preference(this); }

    final AppLanguage effectiveLanguage() {
        var configured = getResources().getConfiguration().getLocales();
        var deviceLocales = new ArrayList<Locale>(configured.size());
        for (int index = 0; index < configured.size(); index++) deviceLocales.add(configured.get(index));
        return AppLanguage.forDevice(deviceLocales);
    }

    private static AppLanguagePreference preference(Context context) {
        var settings = context.getSharedPreferences("app-settings", Context.MODE_PRIVATE);
        return new AppLanguagePreference(new AndroidGamePreferences(settings));
    }
}
