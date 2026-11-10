package ro.atf.chess.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestHint;
import ro.atf.chess.QuestProgress;
import ro.atf.chess.QuestStep;

public final class ChessActivity extends Activity {
    private static final int BACKGROUND = 0xff262522;
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int PROGRESS = 0xffb8d88a;
    private static final int[] CHAPTER_BUTTONS = {R.id.chapter_one, R.id.chapter_two, R.id.chapter_three};
    private static final int[] CHAPTER_PROGRESS = {R.string.quest_progress, R.string.mate_two_progress, R.string.mate_three_progress};
    private static final long DEFENSE_DELAY_MS = 650;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reply = this::playDefense;
    private List<List<QuestCatalog.Quest>> chapters;
    private List<QuestCatalog.Quest> quests;
    private List<String> order;
    private QuestProgressStore store;
    private QuestProgress progress;
    private ChessPosition position;
    private int questIndex;
    private boolean solved;
    private QuestStep step;
    private boolean resumed;
    private int chapterIndex = -1;
    private LinearLayout questScreen;
    private String attemptMessage;
    private BoardView board;
    private TextView status;
    private TextView topPlayer;
    private TextView bottomPlayer;
    private TextView recentMove;
    private TextView solvedCount;
    private TextView milestone;
    private ProgressBar progressTrack;
    private Button guidance;
    private QuestHint hint = QuestHint.NONE;
    private Button previous;
    private Button next;
    private boolean blackBelow;
    private int selected = -1;
    private Move last;
    private List<Move> options = Collections.emptyList();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        chapters = List.of(QuestCatalog.load(), QuestCatalog.loadMateInTwo(), QuestCatalog.loadMateInThree());
        var allIds = new ArrayList<String>();
        for (var collection : chapters) allIds.addAll(ids(collection));
        store = new QuestProgressStore(this);
        progress = store.read(allIds);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::back);
        }
        if (store.resumeChapter()) for (int index = 0; index < chapters.size(); index++) {
            var chapterIds = ids(chapters.get(index));
            if (!chapterIds.contains(progress.currentId())) continue;
            displayChapter(index, chapterIds);
            if (state != null && progress.currentId().equals(state.getString("questId"))) blackBelow = state.getBoolean("blackBelow", blackBelow);
            render();
            return;
        }
        showMenu();
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
        main.removeCallbacks(reply);
        chapterIndex = -1;
        var menu = screen();
        getLayoutInflater().inflate(R.layout.main_menu, menu);
        for (int index = 0; index < CHAPTER_BUTTONS.length; index++) {
            int chapter = index;
            menu.findViewById(CHAPTER_BUTTONS[index]).setOnClickListener(view -> openChapter(chapter));
        }
        setContentView(menu);
    }

    private void leaveChapter() {
        if (store.leaveChapter()) showMenu();
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
        if (chapterIndex < 0) finish();
        else leaveChapter();
    }

    @Override public void onBackPressed() { back(); }

    private LinearLayout buildQuestScreen() {
        var screen = screen();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(8), 0);
        TextView title = text(R.string.app_name, 21, PRIMARY);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button flip = new Button(this);
        flip.setText(R.string.flip);
        flip.setAllCaps(false);
        flip.setOnClickListener(view -> { blackBelow = !blackBelow; render(); });
        header.addView(flip);
        var menu = menuButton(R.string.main_menu);
        menu.setOnClickListener(view -> leaveChapter());
        header.addView(menu);
        screen.addView(header);
        recentMove = text(R.string.mate_objective, 14, MUTED);
        recentMove.setGravity(Gravity.CENTER);
        screen.addView(recentMove);
        screen.addView(progressPanel());
        topPlayer = playerLabel();
        bottomPlayer = playerLabel();
        board = new BoardView(this, this::tap);
        screen.addView(new BoardPanel(this, topPlayer, board, bottomPlayer), new LinearLayout.LayoutParams(-1, 0, 1));
        status = text(R.string.mate_objective, 16, PRIMARY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(10));
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBaselineAligned(false);
        previous = questButton(R.string.previous_quest, -1);
        next = questButton(R.string.next_quest, 1);
        guidance = new Button(this);
        guidance.setAllCaps(false);
        guidance.setOnClickListener(view -> revealGuidance());
        addControl(navigation, previous, 1);
        addControl(navigation, guidance, 1.25f);
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
        milestone.setGravity(Gravity.END);
        milestone.setPadding(dp(8), 0, 0, 0);
        milestone.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        row.addView(solvedCount, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(milestone, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(row);
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
        if (solved || hint == QuestHint.SOLUTION) {
            showSolution(quest);
            return;
        }
        hint = hint.advance();
        attemptMessage = null;
        selected = -1;
        options = Collections.emptyList();
        render();
    }

    private void showSolution(QuestCatalog.Quest quest) {
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
        var destination = chapters.get(index);
        String first = destination.get(0).id();
        String remembered = store.remembered(first);
        var chapterIds = ids(destination);
        String target = chapterIds.contains(remembered) ? remembered : first;
        if (!save(progress.select(target))) return;
        displayChapter(index, chapterIds);
        render();
        scheduleDefense();
    }

    private void displayChapter(int index, List<String> chapterIds) {
        chapterIndex = index;
        quests = chapters.get(index);
        order = chapterIds;
        loadQuest(order.indexOf(progress.currentId()));
        if (questScreen == null) questScreen = buildQuestScreen();
        setContentView(questScreen);
    }

    private static ArrayList<String> ids(List<QuestCatalog.Quest> collection) {
        var ids = new ArrayList<String>(collection.size());
        for (var quest : collection) ids.add(quest.id());
        return ids;
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
        blackBelow = quest.position().turn() == ChessPosition.Side.BLACK;
        solved = progress.currentSolved();
        attemptMessage = null;
        hint = QuestHint.NONE;
        selected = -1;
        step = QuestStep.restore(solved ? 0 : store.ply(quest.id()), quest);
        last = solved ? quest.matingMove() : step.lastMove(quest);
        position = solved ? quest.solvedPosition() : step.position(quest);
        options = Collections.emptyList();
    }

    private TextView playerLabel() {
        TextView label = text(R.string.white, 16, PRIMARY);
        label.setId(View.generateViewId());
        label.setPadding(dp(14), dp(6), dp(14), dp(6));
        return label;
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
        if (solved || step.defensePending()) return;
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
        if (chapterIndex < 0 || solved || step.defensePending()) return;
        var quest = quests.get(questIndex);
        int stage = step.stage();
        int remaining = quest.mateLength() - stage;
        var result = quest.assess(move, stage);
        boolean mate = result.outcome() == ChessPosition.Outcome.CHECKMATE;
        if (mate && !save(progress.solveCurrent())) return;
        if (result.continues() && !saveStep(step.advance())) return;
        solved = mate;
        if (mate) {
            position = result.position();
            last = move;
        }
        if (mate || result.continues()) hint = QuestHint.NONE;
        if (result.continues() || mate) attemptMessage = null;
        else if (remaining > 1) attemptMessage = getString(remaining == 3 ? R.string.mate_three_try_again : R.string.mate_two_try_again);
        else attemptMessage = switch (result.outcome()) {
            case CHECKMATE -> null;
            case CHECK -> getString(R.string.quest_check_reply, notation(result.defense()));
            case STALEMATE -> getString(R.string.quest_stalemate);
            case PLAYING -> getString(R.string.quest_try_again);
        };
        selected = -1;
        options = Collections.emptyList();
        render();
        if (result.continues()) scheduleDefense();
    }

    private void scheduleDefense() {
        main.removeCallbacks(reply);
        if (resumed && chapterIndex >= 0 && step.defensePending()) main.postDelayed(reply, DEFENSE_DELAY_MS);
    }

    private void playDefense() {
        if (!resumed || chapterIndex < 0 || !step.defensePending()) return;
        if (saveStep(step.advance())) render();
    }

    private boolean saveStep(QuestStep next) {
        if (!store.save(progress, next.ply())) {
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
        if (!store.save(nextProgress)) {
            showSaveError();
            return false;
        }
        progress = nextProgress;
        return true;
    }

    private void showSaveError() { Toast.makeText(this, R.string.progress_save_failed, Toast.LENGTH_SHORT).show(); }

    private void render() {
        var bottom = blackBelow ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
        renderPlayer(topPlayer, bottom.other());
        renderPlayer(bottomPlayer, bottom);
        recentMove.setText(getString(CHAPTER_PROGRESS[chapterIndex], questIndex + 1, quests.size()));
        renderProgress();
        boolean pending = step.defensePending();
        previous.setEnabled(!pending && questIndex > 0);
        next.setEnabled(progress.canAdvance(questIndex, quests.size()));
        guidance.setEnabled(!pending);
        guidance.setText(solved ? R.string.why_mate : switch (hint) {
            case NONE -> R.string.hint;
            case PIECE -> R.string.show_solution;
            case SOLUTION -> R.string.why_mate;
        });
        var quest = quests.get(questIndex);
        int stage = step.stage();
        Move suggestion = quest.playerMove(stage);
        if (solved) status.setText(R.string.quest_solved);
        else if (pending) status.setText(R.string.opponent_reply_pending);
        else if (attemptMessage != null) status.setText(attemptMessage);
        else if (hint == QuestHint.PIECE) {
            var piece = position.pieceAt(suggestion.from());
            status.setText(getString(R.string.hint_description, BoardSquare.name(suggestion.from()),
                    getString(BoardView.sideLabel(piece.side())), getString(BoardView.kindLabel(piece.kind()))));
        } else if (hint == QuestHint.SOLUTION) status.setText(getString(R.string.suggested_move, notation(suggestion)));
        else if (stage > 0) {
            int label = stage == quest.mateLength() - 1 ? R.string.mate_two_reply : R.string.mate_three_reply;
            status.setText(getString(label, notation(quest.defenseAfter(stage - 1))));
        }
        else status.setText(R.string.find_mate);
        int activeLabelId = (position.turn() == bottom ? bottomPlayer : topPlayer).getId();
        board.show(position, blackBelow, selected, options, last, hint.cue(suggestion), !pending, activeLabelId);
    }

    private void renderPlayer(TextView label, ChessPosition.Side side) {
        boolean active = !solved && position.turn() == side;
        String color = getString(BoardView.sideLabel(side));
        label.setText(active ? getString(R.string.to_move, color) : color);
        label.setTextColor(active ? PROGRESS : PRIMARY);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
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
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        scheduleDefense();
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(reply);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("questId", progress.currentId());
        state.putBoolean("blackBelow", blackBelow);
        super.onSaveInstanceState(state);
    }
}
