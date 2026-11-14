package ro.atf.chess.android;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.List;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestDanger;
import ro.atf.chess.QuestHint;
import ro.atf.chess.QuestPayoff;

final class QuestPayoffDialog {
    private static final int BACKGROUND = 0xff262522;
    private static final int TEXT = 0xfff5f3ec;
    private final Context context;
    private final QuestPayoff payoff;
    private final boolean studyingDefense;
    private final String mateExplanation;
    private final boolean blackBelow;
    private final BoardView board;
    private final TextView move;
    private final TextView material;
    private final TextView floor;
    private final TextView dangerReason;
    private final Button dangerToggle;
    private QuestDanger danger;
    private final Button previous;
    private final Button next;
    private int ply;

    static void open(Context context, QuestPayoff payoff, boolean blackBelow) {
        int title = payoff.defenseStudy() != null ? R.string.tactics_defense_study : R.string.why_tactic;
        new QuestPayoffDialog(context, payoff, blackBelow, context.getString(title), payoff.defenseStudy() != null ? 1 : payoff.plies(), null);
    }

    static void openAlternative(Context context, QuestPayoff payoff, boolean blackBelow, String label) {
        new QuestPayoffDialog(context, payoff, blackBelow, label, 1, null);
    }

    static void openMate(Context context, QuestPayoff payoff, boolean blackBelow, int initialPly, String explanation) {
        new QuestPayoffDialog(context, payoff, blackBelow, context.getString(R.string.mate_replay_title), initialPly, explanation);
    }

    private QuestPayoffDialog(Context context, QuestPayoff payoff, boolean blackBelow, String titleText, int initialPly, String mateExplanation) {
        this.context = context;
        this.payoff = payoff;
        this.studyingDefense = payoff.defenseStudy() != null;
        this.mateExplanation = mateExplanation;
        this.blackBelow = blackBelow;
        var dialog = new Dialog(context);
        var screen = new LinearLayout(context);
        screen.setId(R.id.quest_payoff_replay);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BACKGROUND);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));
        var title = text(titleText, 19);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) title.setAccessibilityHeading(true);
        screen.addView(title);
        var panel = new LinearLayout(context);
        panel.setGravity(Gravity.CENTER);
        board = new BoardView(context, ignored -> { });
        board.setId(R.id.quest_payoff_board);
        board.setContentDescription(context.getString(R.string.game_review_board));
        panel.addView(board, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        screen.addView(panel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 2));
        var details = new ScrollView(context);
        details.setId(R.id.quest_payoff_details);
        var captions = new LinearLayout(context);
        captions.setOrientation(LinearLayout.VERTICAL);
        move = text("", 16);
        move.setId(R.id.quest_payoff_move);
        move.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        material = text("", 15);
        material.setId(R.id.quest_payoff_material);
        material.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floor = text("", 15);
        floor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dangerReason = text("", 15);
        dangerReason.setId(R.id.quest_payoff_danger_reason);
        dangerReason.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        captions.addView(dangerReason);
        captions.addView(move);
        captions.addView(material);
        captions.addView(floor);
        if (studyingDefense) {
            addStudyNotes(captions);
        } else if (mateExplanation == null) {
            if (payoff.plies() == 1) captions.addView(text(context.getString(R.string.tactics_key_only), 15));
            if (payoff.lesson() != null) captions.addView(text(context.getString(lessonLabel(payoff.lesson())), 15));
        }
        details.addView(captions);
        screen.addView(details, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        var buttons = new LinearLayout(context);
        previous = button(R.string.game_review_previous);
        previous.setId(R.id.quest_payoff_previous);
        previous.setContentDescription(context.getString(R.string.opening_previous_move));
        previous.setOnClickListener(view -> {
            show(ply - 1);
            details.scrollTo(0, 0);
        });
        next = button(R.string.game_review_next);
        next.setId(R.id.quest_payoff_next);
        next.setContentDescription(context.getString(R.string.opening_next_move));
        next.setOnClickListener(view -> {
            show(ply + 1);
            details.scrollTo(0, 0);
        });
        dangerToggle = button(R.string.quest_danger);
        dangerToggle.setId(R.id.quest_payoff_danger);
        dangerToggle.setPadding(0, 0, 0, 0);
        dangerToggle.setOnClickListener(view -> {
            showDanger(!view.isSelected());
            details.scrollTo(0, 0);
        });
        var close = button(android.R.string.ok);
        close.setOnClickListener(view -> dialog.dismiss());
        for (var button : List.of(previous, next, dangerToggle, close)) {
            buttons.addView(button, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        screen.addView(buttons);
        dialog.setContentView(screen);
        show(initialPly);
        dialog.show();
        dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private void show(int target) {
        ply = target;
        var frame = payoff.at(ply);
        board.show(frame.position(), blackBelow, -1, List.of(), frame.move(), QuestHint.NONE.cue(frame.move()), false, View.NO_ID);
        danger = payoff.dangerAt(ply);
        showDanger(false);
        dangerToggle.setVisibility(danger == null ? View.INVISIBLE : View.VISIBLE);
        previous.setEnabled(ply > 0);
        next.setEnabled(ply < payoff.plies());
        String label = context.getString(R.string.tactics_replay_start);
        if (frame.move() != null) {
            String side = context.getString(BoardView.sideLabel(frame.position().turn().other()));
            label = context.getString(R.string.tactics_replay_move, ply, payoff.plies(), side, ChessActivity.notation(context, frame.move()));
            if (frame.capture() != null) {
                String piece = context.getString(BoardView.kindLabel(frame.capture().piece().kind()));
                label += "\n" + context.getString(R.string.tactics_replay_capture, piece, BoardSquare.name(frame.capture().square()));
            }
        }
        if (mateExplanation != null) {
            showMatePosition(label, frame);
            return;
        }
        String netMaterial = studyingDefense ? materialAtStep(context, frame.materialChange())
                : material(context, frame.materialChange(), payoff.plies() == 1);
        Integer minimum = studyingDefense && ply == payoff.plies() ? payoff.immediateMaterialFloor() : null;
        String minimumMaterial = minimum == null || minimum == frame.materialChange() ? null
                : materialFloor(context, minimum);
        move.setContentDescription(label + ". " + netMaterial + (minimumMaterial == null ? "" : ". " + minimumMaterial));
        move.setText(label);
        material.setText(netMaterial);
        floor.setText(minimumMaterial);
        floor.setVisibility(minimumMaterial == null ? View.GONE : View.VISIBLE);
    }

    private void showDanger(boolean visible) {
        dangerToggle.setSelected(visible);
        if (!visible) {
            board.markDanger(-1, blackBelow, null);
            dangerReason.setVisibility(View.GONE);
            dangerReason.setText(null);
            return;
        }
        String coordinate = BoardSquare.name(danger.square());
        String reason = danger.kind() == QuestDanger.Kind.MATE
                ? context.getString(R.string.quest_danger_mate, coordinate)
                : context.getString(R.string.quest_danger_capture, coordinate, signedPawnUnits(context, danger.retainedCp()));
        board.markDanger(danger.square(), blackBelow, reason);
        dangerReason.setVisibility(View.VISIBLE);
        dangerReason.setText(reason);
    }

    private void showMatePosition(String label, QuestPayoff.Frame frame) {
        var position = frame.position();
        boolean finalFrame = ply == payoff.plies();
        String side = context.getString(BoardView.sideLabel(position.turn()));
        int status = finalFrame ? R.string.mate_replay_done : position.isCheck() ? R.string.mate_replay_check : R.string.to_move;
        String caption = finalFrame ? context.getString(status) : context.getString(status, side);
        move.setText(label);
        move.setContentDescription(label + ". " + caption);
        material.setText(caption);
        floor.setText(finalFrame ? mateExplanation : null);
        floor.setVisibility(finalFrame ? View.VISIBLE : View.GONE);
        floor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    static String material(Context context, int change, boolean oneMove) {
        if (oneMove) return context.getString(R.string.tactics_material_after_move, signedPawnUnits(context, change));
        int label = change > 0 ? R.string.tactics_material_gain : change < 0 ? R.string.tactics_material_loss : R.string.tactics_material_equal;
        return context.getString(label, pawnUnits(context, change));
    }

    private void addStudyNotes(LinearLayout captions) {
        var study = payoff.defenseStudy();
        var language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        var lesson = text("ro".equals(language) ? study.lessonRo() : study.lessonEn(), 15);
        lesson.setId(R.id.quest_payoff_study_lesson);
        captions.addView(lesson);
        var evidence = text(studyEvidence(context, payoff), 14);
        evidence.setId(R.id.quest_payoff_study_evidence);
        captions.addView(evidence);
    }

    static String studyEvidence(Context context, QuestPayoff payoff) {
        var study = payoff.defenseStudy();
        Integer mate = study.mateIn();
        String estimate = mate == null
                ? context.getString(R.string.tactics_study_position_score, signedPawnUnits(context, study.evaluationCp()))
                : context.getString(mate > 0 ? R.string.tactics_study_mate_for_solver : R.string.tactics_study_mate_for_defender, Math.abs(mate));
        var side = context.getString(BoardView.sideLabel(payoff.at(0).position().turn()));
        return context.getString(R.string.tactics_study_stockfish, study.depth(), side, estimate);
    }

    static String materialAtStep(Context context, int change) {
        return context.getString(R.string.tactics_study_material, signedPawnUnits(context, change));
    }

    static String materialFloor(Context context, int change) {
        return context.getString(R.string.tactics_study_material_floor, signedPawnUnits(context, change));
    }

    private static String signedPawnUnits(Context context, int value) {
        return (value > 0 ? "+" : value < 0 ? "−" : "") + pawnUnits(context, value);
    }

    private static String pawnUnits(Context context, int value) {
        var format = NumberFormat.getNumberInstance(context.getResources().getConfiguration().getLocales().get(0));
        return format.format(Math.abs((long) value) / 100.0);
    }

    private static int lessonLabel(QuestCatalog.Lesson lesson) {
        return switch (lesson) {
            case FORK -> R.string.tactics_lesson_fork;
            case DEFLECTION -> R.string.tactics_lesson_deflection;
            case PIN -> R.string.tactics_lesson_pin;
            case INTERMEZZO -> R.string.tactics_lesson_intermezzo;
            case CLEARANCE -> R.string.tactics_lesson_clearance;
            case DISCOVERY -> R.string.tactics_lesson_discovery;
            case PROMOTION -> R.string.tactics_lesson_promotion;
            case REMOVE_DEFENDER -> R.string.tactics_lesson_remove_defender;
            case DEFENSE -> R.string.tactics_lesson_defense;
            case QUIET_THREAT -> R.string.tactics_lesson_quiet_threat;
            case XRAY -> R.string.tactics_lesson_xray;
            case SKEWER -> R.string.tactics_lesson_skewer;
            case SACRIFICE -> R.string.tactics_lesson_sacrifice;
            case TRAPPED_PIECE -> R.string.tactics_lesson_trapped_piece;
        };
    }

    private TextView text(String content, int size) {
        var label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(TEXT);
        label.setTextSize(size);
        label.setPadding(dp(4), dp(4), dp(4), dp(4));
        label.setText(content);
        return label;
    }

    private Button button(int label) {
        var control = new Button(context);
        control.setAllCaps(false);
        control.setMinWidth(0);
        control.setMinHeight(dp(48));
        control.setText(label);
        return control;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
