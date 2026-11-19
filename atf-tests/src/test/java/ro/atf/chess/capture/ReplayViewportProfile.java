package ro.atf.chess.capture;

/** Exact requested display evidence; logical dp never substitutes for native pixels or rotation. */
public record ReplayViewportProfile(String logical, String nativeSize, String density, String font, int rotation) {
    public static ReplayViewportProfile of(String logical, boolean enlargedRotated) {
        String size = switch (logical) {
            case "360x640" -> "720x1280";
            case "740x420" -> "1480x840";
            case "1280x900" -> "1920x1350";
            default -> throw new IllegalArgumentException("Unknown logical replay profile: " + logical);
        };
        return new ReplayViewportProfile(logical, size, logical.equals("1280x900") ? "240" : "320",
                enlargedRotated ? "1.3" : "1.0", enlargedRotated && !logical.equals("360x640") ? 1 : 0);
    }

    public String marker(String locale) {
        if (!locale.equals("en") && !locale.equals("ro")) throw new IllegalArgumentException("Unknown replay locale");
        return nativeSize + "@" + density + ", font=" + font + ", language=" + locale;
    }
}
