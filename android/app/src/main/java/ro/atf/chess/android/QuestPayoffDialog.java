package ro.atf.chess.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import ro.atf.chess.QuestReplay;
import ro.atf.chess.ReplayCheckpoint;

final class QuestPayoffDialog {
    private static final int TEXT = 0xffeff2ec;
    private static final int MUTED = 0xffb7c5b4;
    private static final int CAUTION = 0xffffbe89;
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
    private final ImageButton previous;
    private final ImageButton next;
    private QuestReplay replay;
    private final Dialog dialog;
    private String source;

    static void open(Context context, QuestPayoff payoff, boolean blackBelow, int sourceBoardSide) {
        int title = payoff.defenseStudy() != null ? R.string.tactics_defense_study : R.string.why_tactic;
        var initial = QuestReplay.at(payoff, payoff.defenseStudy() != null ? 1 : payoff.plies());
        new QuestPayoffDialog(context, initial, blackBelow, context.getString(title), null, sourceBoardSide);
    }

    static void openAlternative(Context context, QuestPayoff payoff, boolean blackBelow, String label, int sourceBoardSide) {
        new QuestPayoffDialog(context, QuestReplay.at(payoff, 1), blackBelow, label, null, sourceBoardSide);
    }

    static QuestPayoffDialog openMate(Context context, QuestReplay replay, boolean blackBelow, String explanation, int sourceBoardSide,
                                      String source, ReplayCheckpoint restored) {
        if (restored != null) {
            if (!restored.matches(source, ReplayCheckpoint.Kind.MATING, lines(replay.payoff()))) return null;
            replay = QuestReplay.at(replay.payoff(), restored.plies().get(0));
            if (restored.danger()) replay = replay.toggleDanger();
        }
        var result = new QuestPayoffDialog(context, replay, blackBelow, context.getString(R.string.mate_replay_title), explanation, sourceBoardSide);
        result.source = source;
        return result;
    }

    String savedState() {
        return !dialog.isShowing() || source == null ? null : new ReplayCheckpoint(source, ReplayCheckpoint.Kind.MATING, "",
                lines(payoff), 0, List.of(replay.ply()), replay.shownDanger() != null).encode();
    }

    void dismiss() { dialog.dismiss(); }

    private static List<String> lines(QuestPayoff payoff) {
        return List.of(java.util.stream.IntStream.rangeClosed(1, payoff.plies()).mapToObj(ply -> payoff.at(ply).move().uci())
                .collect(java.util.stream.Collectors.joining(" ")));
    }

    private QuestPayoffDialog(Context context, QuestReplay initial, boolean blackBelow, String titleText, String mateExplanation, int sourceBoardSide) {
        this.context = context;
        this.replay = initial;
        this.payoff = initial.payoff();
        this.studyingDefense = payoff.defenseStudy() != null;
        this.mateExplanation = mateExplanation;
        this.blackBelow = blackBelow;
        dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        var screen = LayoutInflater.from(context).inflate(R.layout.puzzle_explain, null);
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        var header = new ChessHeader(context, ro.atf.chess.UiChrome.Page.MATING, R.id.quest_payoff_close, titleText, dialog::dismiss);
        header.title().setId(R.id.quest_payoff_title);
        ((android.view.ViewGroup) screen.findViewById(R.id.quest_payoff_header)).addView(header);
        TextView title = header.title();
        title.setText(titleText);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) title.setAccessibilityHeading(true);
        board = new BoardView(context, ignored -> { });
        board.setId(R.id.quest_payoff_board);
        board.setContentDescription(context.getString(R.string.game_review_board));
        var details = new ScrollView(context);
        details.setId(R.id.quest_payoff_details);
        details.setScrollbarFadingEnabled(false);
        var captions = new LinearLayout(context);
        captions.setOrientation(LinearLayout.VERTICAL);
        captions.setPadding(dp(12), dp(4), dp(12), dp(4));
        move = text("", 16, TEXT);
        move.setTypeface(move.getTypeface(), Typeface.BOLD);
        move.setId(R.id.quest_payoff_move);
        move.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        material = text("", 14, MUTED);
        material.setId(R.id.quest_payoff_material);
        material.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floor = text("", 14, MUTED);
        floor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dangerReason = text("", 14, CAUTION);
        var caution = new GradientDrawable();
        caution.setColor(0xff322a21);
        caution.setCornerRadius(dp(8));
        dangerReason.setBackground(caution);
        dangerReason.setPadding(dp(8), dp(4), dp(8), dp(4));
        dangerReason.setId(R.id.quest_payoff_danger_reason);
        dangerReason.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        captions.addView(dangerReason);
        captions.addView(move);
        captions.addView(material);
        captions.addView(floor);
        if (studyingDefense) {
            addStudyNotes(captions);
        } else if (mateExplanation == null) {
            if (payoff.plies() == 1) captions.addView(text(context.getString(R.string.tactics_key_only), 14, MUTED));
            if (payoff.lesson() != null) captions.addView(text(context.getString(lessonLabel(payoff.lesson())), 15, TEXT));
        }
        details.addView(captions);
        int movePreview = move.getLineHeight() * 2 + move.getPaddingTop() + move.getPaddingBottom() + captions.getPaddingTop();
        FrameLayout body = screen.findViewById(R.id.quest_payoff_body);
        body.addView(new BoardDetails(new BoardPanel(context, null, board, null, null), details, movePreview, sourceBoardSide),
                new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        previous = screen.findViewById(R.id.quest_payoff_previous);
        previous.setOnClickListener(view -> {
            replay = replay.previous();
            renderReplay();
            details.scrollTo(0, 0);
        });
        next = screen.findViewById(R.id.quest_payoff_next);
        next.setOnClickListener(view -> {
            replay = replay.next();
            renderReplay();
            details.scrollTo(0, 0);
        });
        dangerToggle = screen.findViewById(R.id.quest_payoff_danger);
        dangerToggle.setOnClickListener(view -> {
            replay = replay.toggleDanger();
            renderDanger();
            details.scrollTo(0, 0);
        });
        dialog.setContentView(screen);
        renderReplay();
        dialog.show();
        dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private void renderReplay() {
        int ply = replay.ply();
        var frame = replay.frame();
        board.show(frame.position(), blackBelow, -1, List.of(), frame.move(), QuestHint.NONE.cue(frame.move()), false, View.NO_ID);
        renderDanger();
        dangerToggle.setVisibility(replay.hasDanger() ? View.VISIBLE : View.INVISIBLE);
        previous.setEnabled(replay.hasPrevious());
        next.setEnabled(replay.hasNext());
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

    private void renderDanger() {
        var danger = replay.shownDanger();
        dangerToggle.setSelected(danger != null);
        if (danger == null) {
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
        boolean finalFrame = !replay.hasNext();
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
        var lesson = text("ro".equals(language) ? study.lessonRo() : study.lessonEn(), 15, TEXT);
        lesson.setId(R.id.quest_payoff_study_lesson);
        captions.addView(lesson);
        var evidence = text(studyEvidence(context, payoff), 14, MUTED);
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

    private TextView text(String content, int size, int color) {
        var label = new TextView(context);
        label.setGravity(Gravity.START);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(4), dp(4), dp(4), dp(4));
        label.setText(content);
        return label;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
