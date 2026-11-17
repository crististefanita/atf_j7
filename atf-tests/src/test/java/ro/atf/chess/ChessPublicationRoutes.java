package ro.atf.chess;

final class ChessPublicationRoutes {
    private static final String OWNER = "crististefanita";
    private static final String REPOSITORY_NAME = "chess";

    static final String REPOSITORY = OWNER + "/" + REPOSITORY_NAME;
    static final String RELEASES = "https://github.com/" + REPOSITORY + "/releases/";
    static final String PAGES_HOST = OWNER + ".github.io";
    static final String PAGES_PATH = "/" + REPOSITORY_NAME + "/";
    static final String HOME_URL = "https://" + PAGES_HOST + PAGES_PATH;

    private static final String PREVIOUS_RELEASES = "https://github.com/" + OWNER + "/atf_j7/releases/";

    private ChessPublicationRoutes() { }

    /** Checks a preserved receipt against the current URL for the same tag or APK. */
    static boolean archivedReleaseUrlMatches(String recordedUrl, String currentUrl) {
        return currentUrl.startsWith(RELEASES) && (currentUrl.equals(recordedUrl)
                || (PREVIOUS_RELEASES + currentUrl.substring(RELEASES.length())).equals(recordedUrl));
    }
}
