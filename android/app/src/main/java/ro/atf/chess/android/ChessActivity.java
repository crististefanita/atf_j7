package ro.atf.chess.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
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
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestHint;
import ro.atf.chess.QuestProgress;

public final class ChessActivity extends Activity {
    private static final int BACKGROUND = 0xff262522;
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int PROGRESS = 0xffb8d88a;
    private List<QuestCatalog.Quest> mateInOne;
    private List<QuestCatalog.Quest> mateInTwo;
    private List<QuestCatalog.Quest> quests;
    private List<String> order;
    private QuestProgressStore store;
    private QuestProgress progress;
    private ChessPosition position;
    private int questIndex;
    private boolean solved;
    private boolean awaitingMate;
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
    private Button chapter;
    private QuestHint hint = QuestHint.NONE;
    private Button previous;
    private Button next;
    private boolean blackBelow;
    private int selected = -1;
    private Move last;
    private List<Move> options = Collections.emptyList();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mateInOne = QuestCatalog.load();
        mateInTwo = QuestCatalog.loadMateInTwo();
        var allIds = ids(mateInOne);
        allIds.addAll(ids(mateInTwo));
        store = new QuestProgressStore(this);
        progress = store.read(allIds);
        selectChapter(progress.currentId().startsWith("mate2-") ? mateInTwo : mateInOne, progress.currentId());
        if (state != null && progress.currentId().equals(state.getString("questId"))) {
            blackBelow = state.getBoolean("blackBelow", blackBelow);
        }
        LinearLayout screen = new LinearLayout(this);
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
        screen.addView(header);
        recentMove = text(R.string.mate_objective, 14, MUTED);
        recentMove.setGravity(Gravity.CENTER);
        LinearLayout chapterRow = new LinearLayout(this);
        chapterRow.setGravity(Gravity.CENTER_VERTICAL);
        chapterRow.addView(recentMove, new LinearLayout.LayoutParams(0, -2, 1));
        chapter = new Button(this);
        chapter.setAllCaps(false);
        chapter.setOnClickListener(view -> switchChapter());
        chapterRow.addView(chapter);
        screen.addView(chapterRow);
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
        setContentView(screen);
        render();
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
        String line = quest.mateInTwo() ? getString(R.string.mate_two_line, notation(quest.solution()), notation(quest.line().defense()), notation(quest.matingMove()))
                : notation(quest.solution());
        var message = new StringBuilder(line).append("\n\n").append(getString(explanation))
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
            int nextIndex = questIndex + offset;
            if (save(progress.select(order.get(nextIndex)))) {
                loadQuest(nextIndex);
                render();
            }
        });
        return button;
    }

    private void switchChapter() {
        var destination = quests == mateInOne ? mateInTwo : mateInOne;
        String first = destination.get(0).id();
        String remembered = store.remembered(first);
        String target = ids(destination).contains(remembered) ? remembered : first;
        if (save(progress.select(target))) {
            selectChapter(destination, target);
            render();
        }
    }

    private void selectChapter(List<QuestCatalog.Quest> collection, String id) {
        quests = collection;
        order = ids(collection);
        loadQuest(order.indexOf(id));
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
        position = quest.position();
        blackBelow = position.turn() == ChessPosition.Side.BLACK;
        solved = progress.currentSolved();
        attemptMessage = null;
        hint = QuestHint.NONE;
        selected = -1;
        awaitingMate = !solved && quest.mateInTwo() && store.awaitingMate(quest.id());
        last = solved ? quest.matingMove() : awaitingMate ? quest.line().defense() : null;
        if (solved) position = quest.solvedPosition();
        else if (awaitingMate) position = quest.continuationPosition();
        options = Collections.emptyList();
    }

    private TextView playerLabel() {
        TextView label = text(R.string.white, 16, PRIMARY);
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
        if (solved) return;
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
        var quest = quests.get(questIndex);
        boolean opening = quest.mateInTwo() && !awaitingMate;
        var result = quest.assess(move, awaitingMate);
        boolean mate = result.outcome() == ChessPosition.Outcome.CHECKMATE;
        if (mate && !save(progress.solveCurrent())) return;
        if (result.continues() && !save(progress, true)) return;
        solved = mate;
        if (mate || result.continues()) {
            position = result.position();
            last = mate ? move : result.defense();
            awaitingMate = result.continues();
            hint = QuestHint.NONE;
        }
        if (result.continues() || mate) attemptMessage = null;
        else if (opening) attemptMessage = getString(R.string.mate_two_try_again);
        else attemptMessage = switch (result.outcome()) {
            case CHECKMATE -> null;
            case CHECK -> getString(R.string.quest_check_reply, notation(result.defense()));
            case STALEMATE -> getString(R.string.quest_stalemate);
            case PLAYING -> getString(R.string.quest_try_again);
        };
        selected = -1;
        options = Collections.emptyList();
        render();
    }

    private boolean save(QuestProgress nextProgress) { return save(nextProgress, false); }

    private boolean save(QuestProgress nextProgress, boolean awaitingMate) {
        if (!store.save(nextProgress, awaitingMate)) {
            Toast.makeText(this, R.string.progress_save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        progress = nextProgress;
        return true;
    }

    private void render() {
        var bottom = blackBelow ? ChessPosition.Side.BLACK : ChessPosition.Side.WHITE;
        renderPlayer(topPlayer, bottom.other());
        renderPlayer(bottomPlayer, bottom);
        boolean two = quests == mateInTwo;
        recentMove.setText(getString(two ? R.string.mate_two_progress : R.string.quest_progress, questIndex + 1, quests.size()));
        chapter.setText(two ? R.string.open_mate_one : R.string.open_mate_two);
        renderProgress();
        previous.setEnabled(questIndex > 0);
        next.setEnabled(questIndex + 1 < quests.size());
        guidance.setText(solved ? R.string.why_mate : switch (hint) {
            case NONE -> R.string.hint;
            case PIECE -> R.string.show_solution;
            case SOLUTION -> R.string.why_mate;
        });
        var quest = quests.get(questIndex);
        Move suggestion = awaitingMate ? quest.matingMove() : quest.solution();
        if (solved) status.setText(R.string.quest_solved);
        else if (attemptMessage != null) status.setText(attemptMessage);
        else if (hint == QuestHint.PIECE) {
            var piece = position.pieceAt(suggestion.from());
            status.setText(getString(R.string.hint_description, BoardSquare.name(suggestion.from()),
                    getString(BoardView.sideLabel(piece.side())), getString(BoardView.kindLabel(piece.kind()))));
        } else if (hint == QuestHint.SOLUTION) status.setText(notation(suggestion));
        else if (awaitingMate) status.setText(getString(R.string.mate_two_reply, notation(quest.line().defense())));
        else status.setText(R.string.find_mate);
        board.show(position, blackBelow, selected, options, last, hint.cue(suggestion));
    }

    private void renderPlayer(TextView label, ChessPosition.Side side) {
        boolean active = !solved && position.turn() == side;
        String color = getString(BoardView.sideLabel(side));
        label.setText(active ? getString(R.string.to_move, color) : color);
        label.setTextColor(active ? PROGRESS : PRIMARY);
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

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("questId", progress.currentId());
        state.putBoolean("blackBelow", blackBelow);
        super.onSaveInstanceState(state);
    }
}
