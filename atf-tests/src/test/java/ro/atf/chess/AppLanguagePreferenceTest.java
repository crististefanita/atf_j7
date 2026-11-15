package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AppLanguagePreferenceTest {
    @Test void recognizesExplicitChoicesAndUsesSupportedDeviceLanguagesByPriority(Checks check) {
        check.same("Only stable English and Romanian tags select an explicit language",
                List.of(Optional.of(AppLanguage.ROMANIAN), Optional.of(AppLanguage.ENGLISH), Optional.empty(), Optional.empty()),
                List.of(AppLanguage.fromTag("ro"), AppLanguage.fromTag("en"), AppLanguage.fromTag("de"), AppLanguage.fromTag(null)));
        check.same("The first supported device language wins and unsupported devices resolve to English",
                List.of(AppLanguage.ROMANIAN, AppLanguage.ENGLISH, AppLanguage.ENGLISH),
                List.of(AppLanguage.forDevice(List.of(Locale.FRENCH, Locale.forLanguageTag("ro-RO"), Locale.US)),
                        AppLanguage.forDevice(List.of(Locale.UK, Locale.forLanguageTag("ro-MD"))),
                        AppLanguage.forDevice(List.of(Locale.FRENCH))));
    }

    @Test void readsTheSavedLanguageWithoutChangingOtherPreferences(Checks check) {
        var disk = new MemoryPreferences();
        disk.edit().putString("other-setting", "unchanged").commit();
        var settings = new AppLanguagePreference(disk);
        check.same("An earlier installation has no explicit choice", Optional.empty(), settings.selected());
        check.same("Saving Romanian survives opening a new store", List.of(true, Optional.of(AppLanguage.ROMANIAN)),
                List.of(settings.save(AppLanguage.ROMANIAN), new AppLanguagePreference(disk).selected()));

        int attempts = disk.commitAttempts;
        check.same("Saving the same choice twice does not write again", List.of(true, attempts),
                List.of(settings.save(AppLanguage.ROMANIAN), disk.commitAttempts));

        disk.rejectCommit = true;
        check.same("A failed English save retains Romanian and does not alter other preferences",
                List.of(false, Optional.of(AppLanguage.ROMANIAN), "unchanged"),
                List.of(settings.save(AppLanguage.ENGLISH), new AppLanguagePreference(disk).selected(),
                        disk.getString("other-setting", "")));

        disk.rejectCommit = false;
        check.same("The next successful English save survives reopening", List.of(true, Optional.of(AppLanguage.ENGLISH)),
                List.of(settings.save(AppLanguage.ENGLISH), new AppLanguagePreference(disk).selected()));
    }
}
