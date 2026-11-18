package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class NavigationGovernanceTest {
    @Test void titleAndBackFollowTheOpeningHeaderWithoutReplacingContextualMenus(Checks check) throws IOException {
        String markup = source("index.html");
        var header = java.util.regex.Pattern.compile("<main>\\s*<header[^>]*>([\\s\\S]*?)</header>").matcher(markup);
        check.same("The shared page header is present", true, header.find());
        check.same("Back precedes the title and options follow automation at the end of the toolbar", true,
                header.group(1).matches("(?s).*id=\"return-menu\".*id=\"title\".*class=\"header-actions\".*id=\"match-automatic\".*id=\"match-options-anchor\".*"));
        for (String adapter : List.of("study-analysis.mjs", "study-openings.mjs")) {
            String code = source(adapter);
            check.same(adapter + " opts into the title and Back contract", true,
                    code.contains("data-chess-header") && code.contains("data-chess-back"));
        }
        String puzzles = source("study-quests.mjs");
        check.same("Puzzle Back returns to chapters while Home retains its own action", true,
                puzzles.contains("handler('back', () => void showMenu())") && puzzles.contains("handler('home', () => void goHome())"));
        check.same("Game Back has a localized accessible label instead of a visible word", true,
                source("game-review.mjs").contains("find(id).setAttribute('aria-label', words[id])"));
        check.same("The game header reserves separate slots for Back, title, options and automation", true,
                source("game-review.css").contains("grid-template-columns: auto minmax(0, 1fr) auto auto"));
        check.same("Continuation reserves space even while hidden so browsing cannot shift the document", true,
                source("chess-controls.css").contains(".match-actions { min-block-size: var(--play-action-size); }"));
        String game = source("game-review.mjs");
        check.same("Back and Cancel share the setup exit action", true,
                game.contains("if (mode === 'ai' && setupVisible) cancelSetup()") && game.contains("addEventListener('click', cancelSetup)"));
    }

    @Test void sharedControlsKeepContextualVariantsAndOneTitleScale(Checks check) throws IOException {
        String markup = source("index.html");
        check.same("The versioned entry includes shared navigation styling", true, markup.contains("href=\"chess-controls.css\""));
        String css = source("chess-controls.css");
        var heading = java.util.regex.Pattern.compile("\\[data-chess-header] :is\\(h1, h2, h3\\) \\{([^}]+)}").matcher(css);
        check.same("The shared heading contract is present", true, heading.find());
        check.same("Titles stay beside Back even inside a centered puzzle catalog", true, heading.group(1).contains("text-align: start"));
        check.same("Shared options use the end of the toolbar", true,
                css.matches("(?s).*\\[data-chess-header] > \\.options-anchor \\{[^}]*justify-self: end;[^}]*}.*"));
        check.same("Touch targets retain 48 CSS px", true, css.contains("--chess-control-size: 48px"));
        for (String adapter : List.of("study-analysis.mjs", "study-openings.mjs", "study-quests.mjs", "match-inline-analysis.mjs")) {
            String code = source(adapter);
            check.same(adapter + " uses the shared line projection", true, code.contains("renderLinePosition"));
        }
        for (String adapter : List.of("study-analysis.mjs", "study-openings.mjs")) {
            check.same(adapter + " retains its line selector", true, source(adapter).contains("data-chess-lines"));
        }
        check.same("Calculated line retains its selector", true, markup.contains("id=\"match-inline-branches\" class=\"analysis-study-variants\" data-chess-lines"));
        check.same("Shared styling does not hide variant collections", false, css.matches("(?s).*\\[data-chess-lines][^{]*\\{[^}]*display:\\s*none.*"));
        for (String owner : List.of("study-analysis.css", "study-openings.css", "study-quests.css")) {
            check.same(owner + " takes its title scale from the shared control contract", true, source(owner).contains("var(--chess-screen-title)"));
        }
        check.same("Opening lessons retain the shared title scale after the catalog closes", true,
                source("study-openings.css").matches("(?s).*\\.opening-study:has\\(\\.opening-workspace:not\\(\\[hidden]\\)\\) > \\.opening-header h2 \\{\\s*font-size: var\\(--chess-screen-title\\);.*"));
    }

    @Test void pageShellKeepsFormsListsAndStudyWithinTheSameColumn(Checks check) throws IOException {
        String shared = source("game-review.css");
        for (String owner : List.of("study-surface", "ai-setup", "match-list")) {
            check.same(owner + " uses the shared content column", true,
                    shared.matches("(?s).*\\." + owner + " \\{[^}]*max-width: var\\(--play-column\\);[^}]*}.*"));
        }
        check.same("Study headers begin at the page inset without another top offset", true,
                shared.contains(".study-surface { --study-top-gap: 0px;"));
        check.same("Major route changes reset the document origin without resetting move navigation", true,
                source("game-review.mjs").contains("writeModeHash(window, shownRouteHash(), addHistory, routeChanged)"));
        check.same("Switching to AI does not remove the common scrollbar reservation", false, shared.contains("scrollbar-gutter: auto"));
        check.same("List and setup headers share the content column", true,
                source("chess-controls.css").contains("body:not(.home-active) main > [data-chess-header] { max-inline-size: var(--play-column); margin-inline: auto; }"));
        check.same("Free analysis shares the content column", true, source("study-analysis.css").contains("max-width: var(--play-column)"));
        check.same("Opening lessons share the content column", true, source("study-openings.css").contains("max-inline-size: var(--play-column)"));
        check.same("Puzzles reserve only the common vertical page insets", true,
                source("study-quests.css").contains("--quest-insets: var(--chess-page-insets)"));
        check.same("Openings reserve the same page insets", true,
                source("study-openings.css").contains("block-size: calc(100dvh - var(--chess-page-insets))"));
    }

    @Test void nativeGameUsesOneMoveAndAnalysisKeepsItsHistoryStrip(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        String activity = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/ChessActivity.java"));
        String analysis = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/AnalysisActivity.java"));
        String history = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/MatchHistoryView.java"));
        check.same("The native game explicitly selects one current move", true,
                activity.contains("new MatchHistoryView(this, this::notation, this::reviewMatch, true,"));
        check.same("Native analysis retains the strip constructor", true,
                analysis.contains("new MatchHistoryView(this, this::notation, ply -> select(study.seek(ply)), analysis.bulb())"));
        check.same("The native cursor is projected by the shared engine", true, history.contains("MatchHistorySelection.at(match, ply)"));
        check.same("The Web projection uses the same cursor", true, Files.readString(root.resolve("web/src/main/java/ro/atf/chess/WebChessGame.java"))
                .contains("MatchHistorySelection.at(match, viewPly)"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(ProjectRoot.locate().resolve("web/src/main/web/" + name));
    }
}
