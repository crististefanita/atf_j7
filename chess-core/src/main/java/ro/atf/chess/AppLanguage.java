package ro.atf.chess;

import java.util.Locale;
import java.util.Optional;

/** Languages offered by Chess Lab and their stable preference values. */
public enum AppLanguage {
    ROMANIAN(Locale.forLanguageTag("ro")),
    ENGLISH(Locale.ENGLISH);

    private final Locale locale;

    AppLanguage(Locale locale) { this.locale = locale; }

    public Locale locale() { return locale; }

    public static Optional<AppLanguage> fromTag(String tag) {
        for (AppLanguage language : values()) {
            if (language.locale.getLanguage().equals(tag)) return Optional.of(language);
        }
        return Optional.empty();
    }

    public static AppLanguage forDevice(Iterable<Locale> locales) {
        for (Locale locale : locales) {
            var supported = fromTag(locale.getLanguage());
            if (supported.isPresent()) return supported.get();
        }
        return ENGLISH;
    }
}
