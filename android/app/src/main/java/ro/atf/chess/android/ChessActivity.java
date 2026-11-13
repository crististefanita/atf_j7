package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview;
import ro.atf.chess.MatchSetup;
import ro.atf.chess.MatchSlots;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestHint;
import ro.atf.chess.QuestProgress;
import ro.atf.chess.QuestRewards;
import ro.atf.chess.QuestStep;
import ro.atf.chess.TacticalAnalysis;

public final class ChessActivity extends BackNavigableActivity {
    private static final int BACKGROUND = 0xff262522;
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int PROGRESS = 0xffb8d88a;
    private static final int FLAME = 0xffffbc78;
    private enum MenuPage { HOME, PUZZLES, MATCH_SETUP }
    private record Chapter(int entry, int label, int totals, List<QuestCatalog.Quest> quests, List<String> ids) {
        Chapter(int entry, int label, int totals, List<QuestCatalog.Quest> quests) {
            this(entry, label, totals, quests, quests.stream().map(QuestCatalog.Quest::id).collect(Collectors.toList()));
        }
    }
    private static final long DEFENSE_DELAY_MS = 650;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reply = this::playDefense;
    private final Runnable botReply = this::startBotSearch;
    private final ExecutorService bot = Executors.newSingleThreadExecutor();
    private Future<?> botJob;
    private int botGeneration;
    private List<Chapter> chapters;
    private List<String> allIds;
    private List<QuestCatalog.Quest> quests;
    private List<String> order;
    private ChessProgressStore store;
    private ChessMatch match;
    private int matchSlot = 1;
    private ro.atf.chess.MatchStore.ArchivedMatch archivedMatch;
    private boolean manualAi;
    private CheckBox manualAiCheck;
    private boolean inMatch;
    private MenuPage menuPage = MenuPage.HOME;
    private boolean botSaveFailed;
    private MatchHistoryView history;
    private ImageButton matchActions;
    private MatchAnalysisView matchAnalysis;
    private android.app.Dialog reviewDialog;
    private boolean choosingContinuation;
    private GameReview completedReview;
    private int reviewedPly = -1;
    private QuestProgress progress;
    private QuestRewards rewards;
    private int earned;
    private ChessPosition position;
    private int questIndex;
    private QuestCatalog.Quest completedQuest;
    private QuestStep step;
    private boolean resumed;
    private int chapterIndex = -1;
    private String attemptMessage;
    private BoardView board;
    private TextView status;
    private TextView topPlayer;
    private TextView bottomPlayer;
    private MatchCapturesView topCaptures;
    private MatchCapturesView bottomCaptures;
    private TextView recentMove;
    private TextView solvedCount;
    private TextView milestone;
    private TextView chapterPoints;
    private TextView streakBadge;
    private boolean announceMilestone;
    private ProgressBar progressTrack;
    private ImageButton guidance;
    private QuestHint hint = QuestHint.NONE;
    private Button previous;
    private Button next;
    private boolean blackBelow;
    private int selected = -1;
    private Move last;
    private List<Move> options = Collections.emptyList();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        chapters = List.of(
                new Chapter(R.id.chapter_one, R.string.quest_progress, R.id.chapter_one_totals, QuestCatalog.load()),
                new Chapter(R.id.chapter_two, R.string.mate_two_progress, R.id.chapter_two_totals, QuestCatalog.loadMateInTwo()),
                new Chapter(R.id.chapter_three, R.string.mate_three_progress, R.id.chapter_three_totals, QuestCatalog.loadMateInThree()),
                new Chapter(R.id.chapter_tactics, R.string.tactics_progress, R.id.chapter_tactics_totals, QuestCatalog.loadTactics()),
                new Chapter(R.id.chapter_training, R.string.training_progress, R.id.chapter_training_totals, QuestCatalog.loadTraining()),
                new Chapter(R.id.chapter_intermezzo, R.string.intermezzo_progress, R.id.chapter_intermezzo_totals, QuestCatalog.loadIntermezzo()));
        allIds = chapters.stream().flatMap(chapter -> chapter.ids().stream()).collect(Collectors.toList());
        store = new ChessProgressStore(this);
        store.matches().migrateCompletedMatches();
        progress = store.quests().read(allIds);
        rewards = store.quests().readRewards();
        try {
            matchSlot = store.matches().activeMatchSlot();
            match = store.matches().readMatch();
            manualAi = store.matches().matchManualAi(matchSlot);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            Toast.makeText(this, R.string.match_load_failed, Toast.LENGTH_LONG).show();
        }
        String archiveId = state == null ? null : state.getString("archive-id");
        if (archiveId != null && openArchivedMatch(archiveId)) return;
        if (match != null && store.matches().resumeMatch()) {
            displayMatch(store.matches().matchBlackBelow());
            return;
        }
        if (store.quests().resumeChapter()) for (int index = 0; index < chapters.size(); index++) {
            var chapterIds = chapters.get(index).ids();
            if (!chapterIds.contains(progress.currentId())) continue;
            displayChapter(index);
            if (state != null && progress.currentId().equals(state.getString("questId"))) blackBelow = state.getBoolean("blackBelow", blackBelow);
            render();
            return;
        }
        switch (state == null ? MenuPage.HOME : MenuPage.valueOf(state.getString("menuPage", MenuPage.HOME.name()))) {
            case HOME -> showMenu();
            case PUZZLES -> showPuzzles();
            case MATCH_SETUP -> showMatchSetup();
        }
    }

    private LinearLayout screen() {
        var screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BACKGROUND);
        screen.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        return screen;
    }

    private void showMenu() {
        var menu = openMenu(MenuPage.HOME, R.layout.main_menu);
        ((TextView) menu.findViewById(R.id.home_totals)).setText(totals(R.string.all_quest_totals, allIds));
        var profile = (Button) menu.findViewById(R.id.edit_player_name);
        profile.setText(getString(R.string.profile_name, humanName()));
        profile.setOnClickListener(view -> PlayerNameEditor.show(this, store.playerName(), store::savePlayerName,
                () -> profile.setText(getString(R.string.profile_name, humanName()))));
        menu.findViewById(R.id.open_puzzles).setOnClickListener(view -> showPuzzles());
        menu.findViewById(R.id.open_openings).setOnClickListener(view -> startActivity(new android.content.Intent(this, OpeningActivity.class)));
        menu.findViewById(R.id.open_analysis).setOnClickListener(view -> AnalysisActivity.open(this, ChessMatch.start(), 0, false));
        menu.findViewById(R.id.new_match).setOnClickListener(view -> showMatchSetup());
        menu.findViewById(R.id.match_archive).setOnClickListener(view -> MatchListActivity.openFinished(this));
        Button resume = menu.findViewById(R.id.resume_match);
        resume.setVisibility(store.matches().savedMatchSlots().isEmpty() ? View.GONE : View.VISIBLE);
        resume.setOnClickListener(view -> MatchListActivity.openSaved(this));
        setContentView(menu);
    }

    private LinearLayout openMenu(MenuPage page, int layout) {
        main.removeCallbacks(reply);
        cancelBot();
        if (matchAnalysis != null) matchAnalysis.close();
        matchAnalysis = null;
        inMatch = false;
        archivedMatch = null;
        menuPage = page;
        chapterIndex = -1;
        var menu = screen();
        getLayoutInflater().inflate(layout, menu);
        return menu;
    }

    private void showPuzzles() {
        var menu = openMenu(MenuPage.PUZZLES, R.layout.puzzle_menu);
        for (int index = 0; index < chapters.size(); index++) {
            int destination = index;
            var chapter = chapters.get(index);
            menu.findViewById(chapter.entry()).setOnClickListener(view -> openChapter(destination));
            ((TextView) menu.findViewById(chapter.totals())).setText(totals(R.string.chapter_totals, chapter.ids()));
        }
        ((TextView) menu.findViewById(R.id.quest_totals)).setText(totals(R.string.all_quest_totals, allIds));
        renderStreak(menu.findViewById(R.id.menu_streak), View.GONE);
        menu.findViewById(R.id.puzzles_home).setOnClickListener(view -> showMenu());
        setContentView(menu);
    }

    private void showMatchSetup() {
        var menu = openMenu(MenuPage.MATCH_SETUP, R.layout.match_setup);
        Spinner colors = menu.findViewById(R.id.match_color);
        Spinner levels = menu.findViewById(R.id.match_level);
        CheckBox analysis = menu.findViewById(R.id.match_analysis_option);
        menu.findViewById(R.id.match_replacement_note).setVisibility(store.matches().freeMatchSlot() == 0 ? View.VISIBLE : View.GONE);
        menu.findViewById(R.id.match_start).setOnClickListener(view -> {
            var color = MatchSetup.ColorChoice.values()[colors.getSelectedItemPosition()];
            var level = MatchSetup.Level.values()[levels.getSelectedItemPosition()];
            newMatch(new MatchSetup(color.resolve(ThreadLocalRandom.current()::nextBoolean), level, analysis.isChecked()));
        });
        menu.findViewById(R.id.setup_home).setOnClickListener(view -> showMenu());
        setContentView(menu);
    }

    private String humanName() {
        String name = store.playerName();
        return name.isEmpty() ? getString(R.string.profile_default) : name;
    }

    private void newMatch(MatchSetup setup) {
        openMatch(store.matches().slotForNewMatch(), ChessMatch.start(setup), setup.humanSide() == Side.BLACK, false, false, true);
    }

    private void selectMatch(int slot) {
        var saved = store.matches().readSavedMatch(slot);
        if (saved == null) {
            Toast.makeText(this, R.string.match_load_failed, Toast.LENGTH_LONG).show();
            return;
        }
        openMatch(slot, saved, store.matches().matchBlackBelow(slot), store.matches().matchManualAi(slot), store.matches().matchManualUsed(slot), false);
    }

    private void openMatch(int slot, ChessMatch nextMatch, boolean flipped, boolean manual, boolean manualUsed, boolean fresh) {
        boolean saved = fresh ? store.matches().startMatch(slot, nextMatch, flipped, manual, manualUsed)
                : store.matches().saveMatch(slot, nextMatch, flipped, manual, manualUsed);
        if (!saved) {
            showSaveError();
            return;
        }
        if (match != nextMatch) completedReview = null;
        cancelBot();
        if (matchAnalysis != null) matchAnalysis.close();
        matchSlot = slot;
        archivedMatch = null;
        manualAi = manual;
        match = nextMatch;
        displayMatch(flipped);
        scheduleBot();
    }

    private boolean openArchivedMatch(String id) {
        var archived = store.matches().readArchivedMatch(id);
        if (archived == null) return false;
        cancelBot();
        if (matchAnalysis != null) matchAnalysis.close();
        archivedMatch = archived;
        match = archived.match();
        manualAi = false;
        completedReview = store.matches().readReview(archived.id(), match);
        displayMatch(archived.blackBelow());
        return true;
    }

    private void displayMatch(boolean flipped) {
        inMatch = true;
        reviewedPly = -1;
        botSaveFailed = false;
        chapterIndex = -1;
        blackBelow = flipped;
        selected = -1;
        options = Collections.emptyList();
        position = match.position();
        last = match.lastMove();
        setContentView(buildBoardScreen());
        renderMatch();
    }

    private void leaveBoard() {
        if (inMatch) {
            if (archivedMatch != null || store.matches().leaveMatch()) showMenu();
            else showSaveError();
        } else leaveChapter();
    }

    private void leaveChapter() {
        if (store.quests().leaveChapter()) showPuzzles();
        else showSaveError();
    }

    private Button menuButton(int label) {
        var button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    @Override protected void back() {
        if (inMatch || chapterIndex >= 0) leaveBoard();
        else if (menuPage != MenuPage.HOME) showMenu();
        else finish();
    }

    private LinearLayout buildBoardScreen() {
        var screen = screen();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(8), 0);
        TextView title = text(R.string.app_name, 21, PRIMARY);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button flip = new Button(this);
        flip.setText(R.string.flip);
        flip.setAllCaps(false);
        flip.setOnClickListener(view -> {
            if (inMatch && archivedMatch == null && !store.matches().saveMatch(matchSlot, match, !blackBelow, manualAi)) {
                showSaveError();
                return;
            }
            blackBelow = !blackBelow;
            render();
        });
        header.addView(flip);
        var menu = menuButton(R.string.main_menu);
        menu.setOnClickListener(view -> leaveBoard());
        header.addView(menu);
        screen.addView(header);
        recentMove = text(R.string.mate_objective, 14, MUTED);
        recentMove.setGravity(Gravity.CENTER);
        BoardCaption.reserve(recentMove, 1);
        screen.addView(recentMove);
        if (inMatch) {
            manualAiCheck = new CheckBox(this);
            manualAiCheck.setId(R.id.match_manual_ai);
            manualAiCheck.setText(R.string.match_manual_ai);
            manualAiCheck.setTextColor(PRIMARY);
            manualAiCheck.setMinHeight(dp(48));
            manualAiCheck.setOnClickListener(view -> setManualAi(manualAiCheck.isChecked()));
            screen.addView(manualAiCheck);
        }
        if (!inMatch) screen.addView(progressPanel());
        topPlayer = playerLabel();
        bottomPlayer = playerLabel();
        board = new BoardView(this, this::tap);
        matchAnalysis = inMatch && match.analysisEnabled() ? new MatchAnalysisView(this, this::notation, () -> {
            selected = -1;
            options = List.of();
            renderMatch();
        }) : null;
        View topSeat = topPlayer;
        View bottomSeat = bottomPlayer;
        if (inMatch) {
            topCaptures = new MatchCapturesView(this, R.id.match_top_captures);
            bottomCaptures = new MatchCapturesView(this, R.id.match_bottom_captures);
            topSeat = matchSeat(topPlayer, topCaptures);
            bottomSeat = matchSeat(bottomPlayer, bottomCaptures);
        }
        screen.addView(new BoardPanel(this, topSeat, board, bottomSeat, matchAnalysis == null ? null : matchAnalysis.evaluationBar()),
                new LinearLayout.LayoutParams(-1, 0, 1));
        status = text(R.string.mate_objective, 16, PRIMARY);
        if (inMatch) status.setId(R.id.match_status);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(10));
        BoardCaption.reserve(status, inMatch ? 2 : 3);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (inMatch) {
            var statusRow = new LinearLayout(this);
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            statusRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
            matchActions = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
            matchActions.setId(R.id.match_actions);
            matchActions.setImageResource(R.drawable.match_more);
            matchActions.setImageTintList(ColorStateList.valueOf(MUTED));
            matchActions.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            matchActions.setContentDescription(getString(R.string.match_actions));
            matchActions.setOnClickListener(this::showMatchActions);
            statusRow.addView(matchActions, new LinearLayout.LayoutParams(dp(48), dp(48)));
            screen.addView(statusRow);
        } else screen.addView(status);
        if (inMatch) {
            if (matchAnalysis != null) screen.addView(matchAnalysis);
            var study = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
            study.setImageResource(R.drawable.match_study);
            study.setImageTintList(ColorStateList.valueOf(MUTED));
            study.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            study.setId(R.id.match_open_analysis);
            study.setContentDescription(getString(R.string.manual_analysis));
            study.setOnClickListener(view -> {
                cancelBot();
                String id = archivedMatch == null ? store.matches().archivedId(matchSlot) : archivedMatch.id();
                boolean manualUsed = archivedMatch == null ? store.matches().matchManualUsed(matchSlot) : archivedMatch.manualUsed();
                AnalysisActivity.open(this, match, reviewedPly < 0 ? match.ply() : reviewedPly, blackBelow,
                        id, manualUsed, humanName(), botName(match.setup().level()));
            });
            history = new MatchHistoryView(this, this::notation, this::reviewMatch, matchAnalysis == null ? null : matchAnalysis.bulb(), study);
            screen.addView(history, new LinearLayout.LayoutParams(-1, -2));
            return screen;
        }
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBaselineAligned(false);
        previous = questButton(R.string.previous_quest, -1);
        next = questButton(R.string.next_quest, 1);
        guidance = (ImageButton) getLayoutInflater().inflate(R.layout.quest_guidance, navigation, false);
        guidance.setOnClickListener(view -> revealGuidance());
        addControl(navigation, previous, 1);
        navigation.addView(guidance);
        addControl(navigation, next, 1);
        screen.addView(navigation);
        return screen;
    }

    private LinearLayout progressPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        solvedCount = text(R.string.mate_objective, 13, PRIMARY);
        milestone = text(R.string.mate_objective, 12, MUTED);
        BoardCaption.reserve(solvedCount, 2);
        BoardCaption.reserve(milestone, 2);
        milestone.setGravity(Gravity.END);
        milestone.setPadding(dp(8), 0, 0, 0);
        row.addView(solvedCount, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(milestone, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(row);
        var rewardRow = new LinearLayout(this);
        rewardRow.setGravity(Gravity.CENTER_VERTICAL);
        chapterPoints = text(R.string.mate_objective, 13, PROGRESS);
        streakBadge = text(R.string.mate_objective, 13, FLAME);
        BoardCaption.reserve(chapterPoints, 1);
        BoardCaption.reserve(streakBadge, 1);
        rewardRow.addView(chapterPoints, new LinearLayout.LayoutParams(0, -2, 1));
        rewardRow.addView(streakBadge);
        panel.addView(rewardRow);
        progressTrack = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressTrack.setProgressTintList(ColorStateList.valueOf(PROGRESS));
        progressTrack.setProgressBackgroundTintList(ColorStateList.valueOf(0xff51534b));
        progressTrack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        var track = new LinearLayout.LayoutParams(-1, dp(6));
        track.topMargin = dp(3);
        panel.addView(progressTrack, track);
        return panel;
    }

    private void revealGuidance() {
        if (step.defensePending()) return;
        var quest = quests.get(questIndex);
        if (completedQuest != null || hint == QuestHint.SOLUTION) {
            showSolution(completedQuest == null ? quest : completedQuest);
            return;
        }
        if (!save(progress, rewards.interrupt(quest.id()))) return;
        hint = hint.advance();
        attemptMessage = null;
        selected = -1;
        options = Collections.emptyList();
        render();
    }

    private void showSolution(QuestCatalog.Quest quest) {
        if (quest.objective() == QuestCatalog.Objective.MATERIAL) {
            var best = TacticalAnalysis.analyze(quest.position(), 3).best().get(0);
            var line = new StringJoiner(" · ");
            for (var move : best.line()) line.add(notation(move));
            String gain = NumberFormat.getNumberInstance().format(best.materialChange() / 100.0);
            new AlertDialog.Builder(this).setTitle(R.string.why_tactic)
                    .setMessage(getString(R.string.tactics_explanation, line.toString(), gain))
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        int explanation = switch (quest.matingPattern()) {
            case DIRECT -> R.string.mate_direct;
            case DISCOVERED -> R.string.mate_discovered;
            case CASTLING -> R.string.mate_castling;
            case EN_PASSANT -> R.string.mate_en_passant;
            case PROMOTION -> R.string.mate_promotion;
        };
        var mate = quest.explanation();
        var line = new StringJoiner(" · ");
        for (var move : quest.line()) line.add(notation(move));
        var message = new StringBuilder(line.toString());
        if (quest.mateLength() > 1) message.append('#');
        message.append("\n\n").append(getString(explanation))
                .append("\n\n").append(getString(R.string.mate_checkers, BoardSquare.name(mate.king()), threats(mate.checkers())));
        for (var exit : mate.exits()) {
            String square = BoardSquare.name(exit.square());
            String detail = exit.ownBlocker() == null ? getString(R.string.mate_exit_attacked, square, threats(exit.attackers()))
                    : getString(R.string.mate_exit_occupied, square, getString(BoardView.kindLabel(exit.ownBlocker().kind())));
            message.append('\n').append(detail);
        }
        new AlertDialog.Builder(this).setTitle(R.string.why_mate).setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }

    private String notation(Move move) {
        return notation(this, move);
    }

    static String notation(android.content.Context context, Move move) {
        String from = BoardSquare.name(move.from());
        String to = BoardSquare.name(move.to());
        return move.promotion() == null ? context.getString(R.string.solution_move, from, to)
                : context.getString(R.string.solution_promotion, from, to, context.getString(BoardView.kindLabel(move.promotion())));
    }

    static String compactNotation(Move move) {
        String uci = move.uci();
        String squares = uci.substring(0, 2) + "–" + uci.substring(2, 4);
        return move.promotion() == null ? squares : squares + "=" + Character.toUpperCase(uci.charAt(4));
    }

    private String threats(List<ChessPosition.Threat> attackers) {
        List<String> labels = new ArrayList<>(attackers.size());
        for (var attacker : attackers) {
            labels.add(getString(R.string.piece_at_square, getString(BoardView.kindLabel(attacker.piece().kind())), BoardSquare.name(attacker.square())));
        }
        return String.join(", ", labels);
    }

    private Button questButton(int label, int offset) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(view -> {
            if (step.defensePending()) return;
            int nextIndex = offset > 0 ? progress.nextIndex(questIndex, order) : questIndex + offset;
            if (nextIndex < 0) return;
            if (save(progress.select(order.get(nextIndex)))) {
                loadQuest(nextIndex);
                render();
                scheduleDefense();
            }
        });
        return button;
    }

    private void openChapter(int index) {
        var chapterIds = chapters.get(index).ids();
        var resumed = QuestProgress.restore(store.quests().remembered(chapterIds.get(0)), progress.solvedIds(), chapterIds);
        if (!save(resumed)) return;
        displayChapter(index);
        render();
        scheduleDefense();
    }

    private void displayChapter(int index) {
        inMatch = false;
        chapterIndex = index;
        var chapter = chapters.get(index);
        quests = chapter.quests();
        order = chapter.ids();
        loadQuest(order.indexOf(progress.currentId()));
        blackBelow = quests.get(questIndex).position().turn() == ChessPosition.Side.BLACK;
        setContentView(buildBoardScreen());
    }

    private void addControl(LinearLayout row, Button button, float weight) {
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(6), button.getPaddingTop(), dp(6), button.getPaddingBottom());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, weight));
    }

    private void loadQuest(int index) {
        questIndex = index;
        var quest = quests.get(index);
        completedQuest = progress.currentSolved() ? quest.restoreFinish(store.quests().finish(quest.id())) : null;
        earned = 0;
        announceMilestone = false;
        attemptMessage = null;
        hint = QuestHint.NONE;
        selected = -1;
        step = QuestStep.restore(completedQuest == null ? store.quests().ply(quest.id()) : 0, quest);
        last = completedQuest == null ? step.lastMove(quest) : completedQuest.finishingMove();
        position = completedQuest == null ? step.position(quest) : completedQuest.solvedPosition();
        options = Collections.emptyList();
    }

    private TextView playerLabel() {
        TextView label = text(R.string.white, 16, PRIMARY);
        label.setId(View.generateViewId());
        label.setPadding(dp(14), dp(6), dp(14), dp(6));
        BoardCaption.reserve(label, 1);
        return label;
    }

    private View matchSeat(TextView name, MatchCapturesView captures) {
        var seat = new LinearLayout(this);
        seat.setOrientation(LinearLayout.VERTICAL);
        name.setPadding(dp(14), dp(4), dp(14), 0);
        seat.addView(name, new LinearLayout.LayoutParams(-2, -2));
        seat.addView(captures, new LinearLayout.LayoutParams(-2, -2));
        return seat;
    }

    private TextView text(int label, int size, int color) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void tap(int square) {
        if (inMatch ? !playerCanMove() : completedQuest != null || step.defensePending()) return;
        var guided = inMatch && matchAnalysis != null ? matchAnalysis.moveForTap(position, selected, square) : null;
        if (guided != null) {
            play(guided);
            return;
        }
        List<Move> targets = new ArrayList<>();
        for (Move move : options) if (move.to() == square) targets.add(move);
        if (targets.size() == 1) {
            play(targets.get(0));
        } else if (targets.size() > 1) {
            CharSequence[] labels = new CharSequence[targets.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = getText(BoardView.kindLabel(targets.get(i).promotion()));
            new AlertDialog.Builder(this).setTitle(R.string.promote)
                    .setItems(labels, (dialog, choice) -> play(targets.get(choice))).show();
        } else {
            ChessPosition.Piece piece = position.pieceAt(square);
            selected = selected != square && piece != null && piece.side() == position.turn() ? square : -1;
            options = position.legalMovesFrom(selected);
            render();
        }
    }

    private void play(Move move) {
        if (inMatch) {
            if (playerCanMove() && saveMatchMove(move)) scheduleBot();
            return;
        }
        if (chapterIndex < 0 || completedQuest != null || step.defensePending()) return;
        var quest = quests.get(questIndex);
        int stage = step.stage();
        int remaining = quest.playerMoves() - stage;
        var result = quest.assess(move, stage);
        if (result.solved()) {
            var award = rewards.complete(progress);
            String finish = quest.objective() == QuestCatalog.Objective.MATE ? step.finishCheckpoint(move) : null;
            if (!save(progress.solveCurrent(), award.rewards(), finish)) return;
            earned = award.amount();
            completedQuest = quest.restoreFinish(finish);
            position = result.position();
            last = move;
            announceMilestone = progress.milestone(order).reached();
        } else if (result.continues()) {
            if (!saveStep(step.advance())) return;
        } else if (!save(progress, rewards.interrupt(quest.id()))) {
            return;
        }
        if (result.solved() || result.continues()) {
            hint = QuestHint.NONE;
            attemptMessage = null;
        } else if (quest.objective() == QuestCatalog.Objective.MATERIAL) {
            attemptMessage = getString(R.string.tactics_try_again);
        } else attemptMessage = switch (result.outcome()) {
            case CHECKMATE -> null;
            case CHECK -> checkingReply(result.replyAnalysis());
            case STALEMATE -> getString(R.string.quest_stalemate);
            case PLAYING -> getString(remaining == 3 ? R.string.mate_three_try_again
                    : remaining == 2 ? R.string.mate_two_try_again : R.string.quest_try_again);
        };
        selected = -1;
        options = Collections.emptyList();
        render();
        if (result.continues()) scheduleDefense();
    }

    private String checkingReply(TacticalAnalysis.Result analysis) {
        var best = analysis.best();
        var reply = best.get(0);
        var line = new StringJoiner(" · ");
        for (Move move : reply.line()) line.add(notation(move));
        String preview = getString(R.string.quest_check_reply, line.toString());
        String effect = switch (reply.ending()) {
            case CHECKMATE -> getString(reply.signedMatePlies() > 0 ? R.string.quest_reply_mates : R.string.quest_reply_allows_mate);
            case STALEMATE -> getString(R.string.quest_reply_stalemate);
            case HORIZON -> reply.materialChange() > 0
                    ? getString(R.string.quest_reply_gain, NumberFormat.getNumberInstance(
                            getResources().getConfiguration().getLocales().get(0)).format(reply.materialChange() / 100.0))
                    : "";
        };
        if (!effect.isEmpty()) preview += " · " + effect;
        if (best.size() > 1) preview += " · " + getString(R.string.quest_reply_equal, best.size(), notation(best.get(1).move()));
        return preview;
    }

    private void scheduleDefense() {
        main.removeCallbacks(reply);
        if (resumed && chapterIndex >= 0 && step.defensePending()) main.postDelayed(reply, DEFENSE_DELAY_MS);
    }

    private boolean playerCanMove() {
        return MatchSlots.canMove(match, manualAi, reviewedPly >= 0);
    }

    private void setManualAi(boolean enabled) {
        if (!store.matches().saveMatch(matchSlot, match, blackBelow, enabled)) {
            manualAiCheck.setChecked(manualAi);
            showSaveError();
            return;
        }
        cancelBot();
        manualAi = enabled;
        botSaveFailed = false;
        selected = -1;
        options = List.of();
        renderMatch();
        scheduleBot();
    }

    private void reviewMatch(int ply) {
        if (ply < 0 || ply > match.ply() || ply == reviewedPly) return;
        if (reviewedPly < 0 && ply == match.ply()) return;
        reviewedPly = ply == match.ply() ? -1 : ply;
        selected = -1;
        options = Collections.emptyList();
        renderMatch();
    }

    private void confirmContinuation(int ply) {
        if (!inMatch || choosingContinuation || ply < 0 || ply >= match.ply()) return;
        var automatic = new CheckBox(this);
        automatic.setText(R.string.match_automatic_ai);
        automatic.setChecked(!manualAi);
        choosingContinuation = true;
        cancelBot();
        new AlertDialog.Builder(this).setTitle(R.string.match_continue_from_here)
                .setMessage(getString(archivedMatch == null ? R.string.match_continue_confirm : R.string.manual_continue_confirm, ply))
                .setView(automatic)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    choosingContinuation = false;
                    scheduleBot();
                })
                .setPositiveButton(R.string.match_confirm_continue, (dialog, which) -> continueMatch(ply, !automatic.isChecked())).show();
    }

    private void continueMatch(int ply, boolean manual) {
        var continuation = match.continueFrom(ply);
        if (archivedMatch != null) {
            int free = store.matches().freeMatchSlot();
            if (free == 0) Toast.makeText(this, R.string.manual_no_free_slot, Toast.LENGTH_LONG).show();
            else openMatch(free, continuation, blackBelow, manual, archivedMatch.manualUsed(), true);
            return;
        }
        boolean saved = match.result() == ChessMatch.Result.IN_PROGRESS ? store.matches().saveMatch(matchSlot, continuation, blackBelow, manual)
                : store.matches().startMatch(matchSlot, continuation, blackBelow, manual, store.matches().matchManualUsed(matchSlot));
        if (!saved) {
            showSaveError();
            return;
        }
        cancelBot();
        completedReview = null;
        reviewedPly = -1;
        botSaveFailed = false;
        manualAi = manual;
        match = continuation;
        position = match.position();
        last = match.lastMove();
        selected = -1;
        options = Collections.emptyList();
        renderMatch();
        scheduleBot();
    }

    private void showGameReview() {
        if (reviewDialog != null) return;
        var snapshot = match;
        if (snapshot.result() == ChessMatch.Result.IN_PROGRESS) {
            if (!snapshot.analysisEnabled() || snapshot.ply() == 0) return;
            cancelBot();
            reviewDialog = GameReviewDialog.showLine(this, snapshot, bot, this::notation, GameReviewDialog.Mode.LIVE_MATCH,
                    humanName(), botName(snapshot.setup().level()), this::reviewMatch, this::confirmContinuation, () -> {
                        reviewDialog = null;
                        scheduleBot();
                    });
            return;
        }
        String id = archivedMatch == null ? store.matches().archivedId(matchSlot) : archivedMatch.id();
        boolean manualUsed = archivedMatch == null ? store.matches().matchManualUsed(matchSlot) : archivedMatch.manualUsed();
        if (completedReview == null) completedReview = store.matches().readReview(id, snapshot);
        reviewDialog = GameReviewDialog.show(this, snapshot, completedReview, bot, this::notation, manualUsed, humanName(),
                botName(match.setup().level()), result -> {
                    if (match == snapshot) completedReview = result;
                    if (store.matches().hasArchived(id) && !store.matches().saveReview(id, snapshot, result)) showSaveError();
                }, this::reviewMatch, this::confirmContinuation, () -> reviewDialog = null);
    }

    private void showMatchActions(View anchor) {
        var menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.match_saved_games).setOnMenuItemClickListener(item -> {
            MatchListActivity.openSaved(this);
            return true;
        });
        boolean finished = match.result() != ChessMatch.Result.IN_PROGRESS;
        if (finished || match.analysisEnabled()) {
            menu.getMenu().add(R.string.match_game_review).setEnabled(finished || match.ply() > 0).setOnMenuItemClickListener(item -> {
                showGameReview();
                return true;
            });
        }
        if (finished) {
            menu.show();
            return;
        }
        var draw = menu.getMenu().add(R.string.match_offer_draw);
        draw.setEnabled(playerCanMove() && !match.awaitingBot());
        draw.setOnMenuItemClickListener(item -> {
            if (playerCanMove() && !match.awaitingBot()) {
                var outcome = match.offerDraw();
                if (outcome == match) Toast.makeText(this, R.string.match_draw_declined, Toast.LENGTH_SHORT).show();
                else saveMatchEnding(outcome);
            }
            return true;
        });
        menu.getMenu().add(R.string.match_resign).setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this).setTitle(R.string.match_resign).setMessage(R.string.match_resign_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.match_confirm_resign, (dialog, which) -> {
                        if (inMatch && reviewedPly < 0 && match.result() == ChessMatch.Result.IN_PROGRESS) saveMatchEnding(match.resign());
                    }).show();
            return true;
        });
        menu.show();
    }

    private void saveMatchEnding(ChessMatch ending) {
        if (!store.matches().saveMatch(matchSlot, ending, blackBelow, manualAi)) {
            showSaveError();
            return;
        }
        cancelBot();
        match = ending;
        botSaveFailed = false;
        selected = -1;
        options = List.of();
        renderMatch();
    }

    private boolean saveMatchMove(Move move) {
        var nextMatch = match.play(move);
        boolean manualUsed = store.matches().matchManualUsed(matchSlot) || manualAi && match.awaitingBot();
        if (!store.matches().saveMatch(matchSlot, nextMatch, blackBelow, manualAi, manualUsed)) {
            showSaveError();
            return false;
        }
        match = nextMatch;
        botSaveFailed = false;
        position = match.position();
        last = match.lastMove();
        selected = -1;
        options = Collections.emptyList();
        renderMatch();
        return true;
    }

    private void scheduleBot() {
        main.removeCallbacks(botReply);
        if (canStartBot() && botJob == null) main.postDelayed(botReply, DEFENSE_DELAY_MS);
    }

    private boolean canStartBot() {
        return resumed && inMatch && reviewDialog == null && !choosingContinuation && MatchSlots.needsBot(match, manualAi);
    }

    private void startBotSearch() {
        if (!canStartBot()) return;
        var pending = new MatchSlots.BotTurn(matchSlot, botGeneration, match);
        botJob = bot.submit(() -> {
            Move move = pending.match().chooseBotMove();
            main.post(() -> {
                if (!resumed || !inMatch || !pending.isCurrent(matchSlot, botGeneration, match, manualAi)) return;
                botJob = null;
                if (!saveMatchMove(move)) {
                    botSaveFailed = true;
                    renderMatch();
                }
            });
        });
    }

    private void cancelBot() {
        main.removeCallbacks(botReply);
        botGeneration++;
        if (botJob != null) {
            botJob.cancel(true);
            botJob = null;
        }
    }

    private void playDefense() {
        if (!resumed || chapterIndex < 0 || !step.defensePending()) return;
        if (saveStep(step.advance())) render();
    }

    private boolean saveStep(QuestStep next) {
        if (!store.quests().save(progress, rewards, next.ply())) {
            showSaveError();
            return false;
        }
        var quest = quests.get(questIndex);
        step = next;
        position = step.position(quest);
        last = step.lastMove(quest);
        return true;
    }

    private boolean save(QuestProgress nextProgress) {
        return save(nextProgress, rewards);
    }

    private boolean save(QuestProgress nextProgress, QuestRewards nextRewards) {
        return save(nextProgress, nextRewards, null);
    }

    private boolean save(QuestProgress nextProgress, QuestRewards nextRewards, String finish) {
        boolean saved = finish == null ? store.quests().save(nextProgress, nextRewards) : store.quests().saveFinish(nextProgress, nextRewards, finish);
        if (!saved) {
            showSaveError();
            return false;
        }
        progress = nextProgress;
        rewards = nextRewards;
        return true;
    }

    private void showSaveError() { Toast.makeText(this, R.string.progress_save_failed, Toast.LENGTH_SHORT).show(); }

    private void render() {
        if (inMatch) {
            renderMatch();
            return;
        }
        var bottom = blackBelow ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
        boolean solved = completedQuest != null;
        renderPlayer(topPlayer, bottom.other(), !solved && position.turn() == bottom.other());
        renderPlayer(bottomPlayer, bottom, !solved && position.turn() == bottom);
        recentMove.setText(getString(chapters.get(chapterIndex).label(), questIndex + 1, quests.size()));
        renderProgress();
        boolean pending = step.defensePending();
        previous.setEnabled(!pending && questIndex > 0);
        int nextIndex = progress.nextIndex(questIndex, order);
        next.setText(nextIndex >= 0 && nextIndex < questIndex ? R.string.first_unsolved_quest : R.string.next_quest);
        next.setEnabled(!pending && nextIndex >= 0);
        guidance.setEnabled(!pending);
        var quest = quests.get(questIndex);
        int explanation = quest.objective() == QuestCatalog.Objective.MATE ? R.string.why_mate : R.string.why_tactic;
        guidance.setContentDescription(getString(solved ? explanation : switch (hint) {
            case NONE -> R.string.hint;
            case PIECE -> R.string.show_solution;
            case SOLUTION -> explanation;
        }));
        guidance.setImageTintList(ColorStateList.valueOf(!pending && hint != QuestHint.NONE ? PROGRESS : MUTED));
        int stage = step.stage();
        Move suggestion = quest.playerMove(stage);
        renderStatus(quest, stage, suggestion, pending);
        int activeLabelId = (position.turn() == bottom ? bottomPlayer : topPlayer).getId();
        board.show(position, blackBelow, selected, options, last, hint.cue(suggestion), !pending, activeLabelId);
    }

    private void renderStatus(QuestCatalog.Quest quest, int stage, Move suggestion, boolean pending) {
        String visible;
        String spoken = null;
        if (completedQuest != null) {
            visible = getString(quest.objective() == QuestCatalog.Objective.MATE ? R.string.quest_solved : R.string.tactics_solved);
            if (announceMilestone) spoken = visible + ". " + milestone.getText();
        } else if (pending) visible = getString(R.string.opponent_reply_pending);
        else if (attemptMessage != null) visible = attemptMessage;
        else if (hint != QuestHint.NONE) {
            var piece = position.pieceAt(suggestion.from());
            String square = BoardSquare.name(suggestion.from());
            String side = getString(BoardView.sideLabel(piece.side()));
            String kind = getString(BoardView.kindLabel(piece.kind()));
            String turn = getString(R.string.to_move, side);
            String pieceHint = getString(R.string.hint_description, square, side, kind);
            if (hint == QuestHint.PIECE) {
                visible = pieceHint;
                spoken = turn + ". " + pieceHint;
            } else {
                visible = getString(R.string.suggested_move, notation(suggestion));
                spoken = getString(R.string.full_hint_announcement, turn, pieceHint, BoardSquare.name(suggestion.to()));
                if (suggestion.promotion() != null) spoken += " " + notation(suggestion);
            }
        } else if (stage > 0) {
            int label = stage == quest.mateLength() - 1 ? R.string.mate_two_reply : R.string.mate_three_reply;
            visible = getString(label, notation(quest.defenseAfter(stage - 1)));
        } else visible = getString(quest.objective() == QuestCatalog.Objective.MATE ? R.string.find_mate : R.string.find_tactic);
        if (!android.text.TextUtils.equals(spoken, status.getContentDescription())) status.setContentDescription(spoken);
        if (!visible.contentEquals(status.getText())) status.setText(visible);
        String details = attemptMessage;
        status.setOnClickListener(details == null ? null : view -> new AlertDialog.Builder(this)
                .setMessage(details).setPositiveButton(android.R.string.ok, null).show());
        status.setClickable(details != null);
    }

    private void renderMatch() {
        boolean reviewing = reviewedPly >= 0;
        int ply = reviewing ? reviewedPly : match.ply();
        var shownPosition = match.positionAt(ply);
        var shownMove = match.moveAt(ply);
        boolean markTurn = reviewing || match.result() == ChessMatch.Result.IN_PROGRESS;
        manualAiCheck.setChecked(manualAi);
        manualAiCheck.setEnabled(!reviewing && match.result() == ChessMatch.Result.IN_PROGRESS);
        var bottom = blackBelow ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
        renderMatchPlayer(topPlayer, bottom.other(), markTurn && shownPosition.turn() == bottom.other());
        renderMatchPlayer(bottomPlayer, bottom, markTurn && shownPosition.turn() == bottom);
        var material = match.materialAt(ply);
        topCaptures.show(bottom.other(), material.forSide(bottom.other()));
        bottomCaptures.show(bottom, material.forSide(bottom));
        recentMove.setText(shownMove == null
                ? getString(R.string.match_history_initial_position)
                : getString(reviewing ? R.string.match_history_showing_move : R.string.match_last_move, notation(shownMove)));
        renderMatchStatus(reviewing, ply);
        int activeLabelId = (shownPosition.turn() == bottom ? bottomPlayer : topPlayer).getId();
        if (matchAnalysis != null) {
            matchAnalysis.evaluationBar().setBlackBelow(blackBelow);
            if (resumed) matchAnalysis.show(match, ply);
        }
        var cue = matchAnalysis == null ? new QuestHint.Cue(-1, -1) : matchAnalysis.cue();
        board.show(shownPosition, blackBelow, selected, options, shownMove, cue, playerCanMove(), activeLabelId);
        history.show(match, ply);
    }

    private void renderMatchStatus(boolean reviewing, int ply) {
        String label = getString(switch (match.result()) {
            case IN_PROGRESS -> match.awaitingBot() ? manualAi ? R.string.match_manual_ai_turn : R.string.match_bot_thinking
                    : position.isCheck() ? R.string.match_player_check : R.string.match_player_turn;
            case WHITE_WINS -> match.setup().humanSide() == Side.WHITE ? R.string.match_player_wins : R.string.match_bot_wins;
            case BLACK_WINS -> match.setup().humanSide() == Side.BLACK ? R.string.match_player_wins : R.string.match_bot_wins;
            case STALEMATE -> R.string.match_draw_stalemate;
            case MATERIAL_DRAW -> R.string.match_draw_material;
            case REPETITION_DRAW -> R.string.match_draw_repetition;
            case MOVE_COUNT_DRAW -> R.string.match_draw_move_count;
            case AGREED_DRAW -> R.string.match_agreed_draw;
        });
        if (match.resigned()) label = getString(R.string.match_resigned);
        View.OnClickListener action = null;
        String spokenLabel = null;
        if (reviewing) {
            label = getString(R.string.match_history_position, ply, match.ply()) + "\n" + getString(R.string.match_continue_from_here);
            action = view -> confirmContinuation(ply);
        } else if (botSaveFailed) {
            label = getString(R.string.match_retry_bot);
            action = view -> {
                botSaveFailed = false;
                renderMatch();
                scheduleBot();
            };
        } else if (match.result() != ChessMatch.Result.IN_PROGRESS) {
            spokenLabel = label + ". " + getString(R.string.match_game_review);
            label += "\n📊  " + getString(R.string.match_game_review);
            action = view -> showGameReview();
        }
        matchActions.setVisibility(reviewing ? View.INVISIBLE : View.VISIBLE);
        status.setText(label);
        status.setContentDescription(spokenLabel);
        status.setTextColor(action == null ? PRIMARY : PROGRESS);
        status.setOnClickListener(action);
        status.setClickable(action != null);
        status.setFocusable(action != null);
    }

    private void renderPlayer(TextView label, ChessPosition.Side side, boolean active) {
        String color = getString(BoardView.sideLabel(side));
        label.setText(active ? getString(R.string.to_move, color) : color);
        label.setTextColor(active ? PROGRESS : PRIMARY);
    }

    private void renderMatchPlayer(TextView label, Side side, boolean active) {
        String name = side == match.setup().humanSide() ? humanName() : botName(match.setup().level());
        String color = getString(BoardView.sideLabel(side));
        label.setText(active ? "●  " + name : name);
        label.setTextColor(active ? PROGRESS : PRIMARY);
        label.setContentDescription(getString(R.string.match_seat_description, name, active ? getString(R.string.to_move, color) : color));
    }

    private String botName(MatchSetup.Level level) {
        return MatchLabels.botName(this, level);
    }

    private void renderProgress() {
        var goal = progress.milestone(order);
        solvedCount.setText(getString(R.string.solved_count, goal.solved(), goal.total()));
        String label = goal.complete() ? getString(R.string.collection_complete)
                : goal.reached() ? getString(R.string.milestone_reached, goal.solved(), goal.next())
                : getString(R.string.next_milestone, goal.next());
        if (!label.contentEquals(milestone.getText())) milestone.setText(label);
        milestone.setTextColor(goal.reached() ? PROGRESS : MUTED);
        progressTrack.setMax(goal.total());
        if (progressTrack.getProgress() != goal.solved()) progressTrack.setProgress(goal.solved(), true);
        int points = rewards.summarize(order, progress).points();
        chapterPoints.setText(earned == 0 ? getString(R.string.chapter_points, points) : getString(R.string.earned_points, earned, points));
        renderStreak(streakBadge, View.INVISIBLE);
    }

    private String totals(int label, List<String> ids) {
        var totals = rewards.summarize(ids, progress);
        return getString(label, totals.solved(), totals.total(), totals.points());
    }

    private void renderStreak(TextView badge, int hiddenVisibility) {
        badge.setVisibility(rewards.showsStreak() ? View.VISIBLE : hiddenVisibility);
        badge.setText(getString(R.string.reward_streak, rewards.streak()));
        badge.setTextColor(FLAME);
        badge.setCompoundDrawablePadding(dp(4));
        badge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.quest_flame, 0, 0, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        scheduleDefense();
        scheduleBot();
        if (inMatch && matchAnalysis != null) renderMatch();
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(reply);
        cancelBot();
        if (inMatch && matchAnalysis != null) matchAnalysis.cancel();
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelBot();
        if (reviewDialog != null) reviewDialog.dismiss();
        bot.shutdown();
        if (matchAnalysis != null) matchAnalysis.close();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == MatchListActivity.SAVED_REQUEST) {
            selectMatch(data.getIntExtra(MatchListActivity.SAVED_SLOT, 0));
            return;
        }
        if (request == MatchListActivity.FINISHED_REQUEST) {
            if (!openArchivedMatch(data.getStringExtra(MatchListActivity.ARCHIVE_ID))) {
                Toast.makeText(this, R.string.match_load_failed, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (request != AnalysisActivity.CONTINUATION_REQUEST) return;
        String checkpoint = data.getStringExtra(AnalysisActivity.CONTINUATION);
        if (checkpoint == null) return;
        int free = store.matches().freeMatchSlot();
        if (free == 0) {
            Toast.makeText(this, R.string.manual_no_free_slot, Toast.LENGTH_LONG).show();
            return;
        }
        var continuation = ChessMatch.restore(checkpoint);
        boolean flipped = data.getBooleanExtra(AnalysisActivity.ORIENTATION, false);
        boolean automatic = data.getBooleanExtra(AnalysisActivity.AI_AUTOMATIC, true);
        openMatch(free, continuation, flipped, !automatic, continuation.ply() > 0, true);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (archivedMatch != null) state.putString("archive-id", archivedMatch.id());
        state.putString("questId", progress.currentId());
        state.putBoolean("blackBelow", blackBelow);
        state.putString("menuPage", menuPage.name());
        super.onSaveInstanceState(state);
    }
}
