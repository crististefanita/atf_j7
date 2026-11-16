package ro.atf.chess;

import java.text.Normalizer;
import java.util.function.UnaryOperator;

/** Local display name, independent of game progress. */
public final class PlayerProfile {
    public static final int MAX_NAME_LENGTH = 10;

    private PlayerProfile() { }

    public static String normalize(String value) {
        return normalize(value, name -> Normalizer.normalize(name, Normalizer.Form.NFC));
    }

    static String normalize(String value, UnaryOperator<String> nfc) {
        if (value == null) throw new IllegalArgumentException("Name is required");
        String name = nfc.apply(value.trim());
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().noneMatch(Character::isLetterOrDigit)
                || name.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c) && c != ' ' && c != '-' && c != '\'')) {
            throw new IllegalArgumentException("Invalid player name");
        }
        return name;
    }
}
