package ro.atf.chess.android;

import android.app.Activity;
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
import ro.atf.chess.MatchSetup;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestHint;
import ro.atf.chess.QuestProgress;
import ro.atf.chess.QuestRewards;
import ro.atf.chess.QuestStep;
import ro.atf.chess.TacticalAnalysis;

public final class ChessActivity extends Activity {
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
    private boolean inMatch;
    private MenuPage menuPage = MenuPage.HOME;
    private boolean botSaveFailed;
    private MatchHistoryView history;
    private MatchAnalysisView matchAnalysis;
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
                new Chapter(R.id.chapter_tactics, R.string.tactics_progress, R.id.chapter_tactics_totals, QuestCatalog.loadTactics()));
        allIds = chapters.stream().flatMap(chapter -> chapter.ids().stream()).collect(Collectors.toList());
        store = new ChessProgressStore(this);
        progress = store.read(allIds);
        rewards = store.readRewards();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::back);
        }
        try {
            match = store.readMatch();
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            Toast.makeText(this, R.string.match_load_failed, Toast.LENGTH_LONG).show();
        }
        if (match != null && store.resumeMatch()) {
            displayMatch(store.matchBlackBelow());
            return;
        }
        if (store.resumeChapter()) for (int index = 0; index < chapters.size(); index++) {
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
        menu.findViewById(R.id.new_match).setOnClickListener(view -> showMatchSetup());
        View resume = menu.findViewById(R.id.resume_match);
        resume.setVisibility(match == null ? View.GONE : View.VISIBLE);
        resume.setOnClickListener(view -> openMatch(match, store.matchBlackBelow()));
        setContentView(menu);
    }

    private LinearLayout openMenu(MenuPage page, int layout) {
        main.removeCallbacks(reply);
        cancelBot();
        if (matchAnalysis != null) matchAnalysis.close();
        matchAnalysis = null;
        inMatch = false;
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
        Runnable start = () -> openMatch(ChessMatch.start(setup), setup.humanSide() == Side.BLACK);
        if (match != null && match.ply() > 0 && match.result() == ChessMatch.Result.IN_PROGRESS) {
            new AlertDialog.Builder(this).setTitle(R.string.match_replace_title).setMessage(R.string.match_replace_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.match_start, (dialog, choice) -> start.run()).show();
        } else start.run();
    }

    private void openMatch(ChessMatch nextMatch, boolean flipped) {
        if (!store.saveMatch(nextMatch, flipped)) {
            showSaveError();
            return;
        }
        match = nextMatch;
        displayMatch(flipped);
        scheduleBot();
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
            if (store.leaveMatch()) showMenu();
            else showSaveError();
        } else leaveChapter();
    }

    private void leaveChapter() {
        if (store.leaveChapter()) showPuzzles();
        else showSaveError();
    }

    private Button menuButton(int label) {
        var button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private void back() {
        if (inMatch || chapterIndex >= 0) leaveBoard();
        else if (menuPage != MenuPage.HOME) showMenu();
        else finish();
    }

    @Override public void onBackPressed() { back(); }

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
            if (inMatch && !store.saveMatch(match, !blackBelow)) {
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
        if (!inMatch) screen.addView(progressPanel());
        topPlayer = playerLabel();
        bottomPlayer = playerLabel();
        board = new BoardView(this, this::tap);
        matchAnalysis = inMatch && match.analysisEnabled() ? new MatchAnalysisView(this, this::notation, this::renderMatch) : null;
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
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(10));
        BoardCaption.reserve(status, inMatch ? 2 : 3);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        if (inMatch) {
            if (matchAnalysis != null) screen.addView(matchAnalysis);
            history = new MatchHistoryView(this, this::notation, this::reviewMatch, matchAnalysis == null ? null : matchAnalysis.bulb());
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
        String from = BoardSquare.name(move.from());
        String to = BoardSquare.name(move.to());
        return move.promotion() == null ? getString(R.string.solution_move, from, to)
                : getString(R.string.solution_promotion, from, to, getString(BoardView.kindLabel(move.promotion())));
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
            if (step.defensePending() || offset > 0 && !progress.canAdvance(questIndex, quests.size())) return;
            int nextIndex = questIndex + offset;
            if (nextIndex < 0) return;
            if (save(progress.select(order.get(nextIndex)))) {
                loadQuest(nextIndex);
                render();
            }
        });
        return button;
    }

    private void openChapter(int index) {
        var chapterIds = chapters.get(index).ids();
        var resumed = QuestProgress.restore(store.remembered(chapterIds.get(0)), progress.solvedIds(), chapterIds);
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
        completedQuest = progress.currentSolved() ? quest.restoreFinish(store.finish(quest.id())) : null;
        earned = 0;
        announceMilestone = false;
        attemptMessage = null;
        hint = QuestHint.NONE;
        selected = -1;
        step = QuestStep.restore(completedQuest == null ? store.ply(quest.id()) : 0, quest);
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
        return reviewedPly < 0 && match.result() == ChessMatch.Result.IN_PROGRESS && !match.awaitingBot();
    }

    private void reviewMatch(int ply) {
        if (ply < 0 || ply > match.ply() || ply == reviewedPly) return;
        if (reviewedPly < 0 && ply == match.ply()) return;
        reviewedPly = ply == match.ply() ? -1 : ply;
        selected = -1;
        options = Collections.emptyList();
        renderMatch();
    }

    private boolean saveMatchMove(Move move) {
        var nextMatch = match.play(move);
        if (!store.saveMatch(nextMatch, blackBelow)) {
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
        if (resumed && inMatch && match.awaitingBot() && botJob == null) main.postDelayed(botReply, DEFENSE_DELAY_MS);
    }

    private void startBotSearch() {
        if (!resumed || !inMatch || !match.awaitingBot()) return;
        var pending = match;
        int generation = botGeneration;
        botJob = bot.submit(() -> {
            Move move = pending.chooseBotMove();
            main.post(() -> {
                if (!resumed || !inMatch || generation != botGeneration || match != pending) return;
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
            botJob.cancel(false);
            botJob = null;
        }
    }

    private void playDefense() {
        if (!resumed || chapterIndex < 0 || !step.defensePending()) return;
        if (saveStep(step.advance())) render();
    }

    private boolean saveStep(QuestStep next) {
        if (!store.save(progress, rewards, next.ply())) {
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
        boolean saved = finish == null ? store.save(nextProgress, nextRewards) : store.saveFinish(nextProgress, nextRewards, finish);
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
        next.setEnabled(progress.canAdvance(questIndex, quests.size()));
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
        var bottom = blackBelow ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
        renderMatchPlayer(topPlayer, bottom.other(), markTurn && shownPosition.turn() == bottom.other());
        renderMatchPlayer(bottomPlayer, bottom, markTurn && shownPosition.turn() == bottom);
        var material = match.materialAt(ply);
        topCaptures.show(bottom.other(), material.forSide(bottom.other()));
        bottomCaptures.show(bottom, material.forSide(bottom));
        recentMove.setText(shownMove == null
                ? getString(R.string.match_history_initial_position)
                : getString(reviewing ? R.string.match_history_showing_move : R.string.match_last_move, notation(shownMove)));
        boolean retry = !reviewing && botSaveFailed;
        status.setOnClickListener(retry ? view -> {
            botSaveFailed = false;
            renderMatch();
            scheduleBot();
        } : null);
        status.setClickable(retry);
        status.setText(reviewing ? getString(R.string.match_history_position, ply, match.ply()) : getString(switch (match.result()) {
            case IN_PROGRESS -> match.awaitingBot() ? R.string.match_bot_thinking
                    : position.isCheck() ? R.string.match_player_check : R.string.match_player_turn;
            case WHITE_WINS -> match.setup().humanSide() == Side.WHITE ? R.string.match_player_wins : R.string.match_bot_wins;
            case BLACK_WINS -> match.setup().humanSide() == Side.BLACK ? R.string.match_player_wins : R.string.match_bot_wins;
            case STALEMATE -> R.string.match_draw_stalemate;
            case MATERIAL_DRAW -> R.string.match_draw_material;
            case REPETITION_DRAW -> R.string.match_draw_repetition;
            case MOVE_COUNT_DRAW -> R.string.match_draw_move_count;
        }));
        if (retry) status.setText(R.string.match_retry_bot);
        int activeLabelId = (shownPosition.turn() == bottom ? bottomPlayer : topPlayer).getId();
        if (matchAnalysis != null) {
            matchAnalysis.evaluationBar().setBlackBelow(blackBelow);
            if (resumed) matchAnalysis.show(match, ply);
        }
        var cue = matchAnalysis == null ? new QuestHint.Cue(-1, -1) : matchAnalysis.cue();
        board.show(shownPosition, blackBelow, selected, options, shownMove, cue, playerCanMove(), activeLabelId);
        history.show(match, ply);
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
        return switch (level) {
            case BASIC -> getString(R.string.match_bot_label, "Alex", getString(R.string.match_basic));
            case ADVANCED -> getString(R.string.match_bot_label, "Mira", getString(R.string.match_advanced));
            case HARD -> getString(R.string.match_bot_label, "Atlas", getString(R.string.match_hard));
        };
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
        bot.shutdown();
        if (matchAnalysis != null) matchAnalysis.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("questId", progress.currentId());
        state.putBoolean("blackBelow", blackBelow);
        state.putString("menuPage", menuPage.name());
        super.onSaveInstanceState(state);
    }
}
