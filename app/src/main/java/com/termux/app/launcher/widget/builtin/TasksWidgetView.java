package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A markdown checklist in the home folder ({@code ~/notes/tasks.md} by default) as a widget: the
 * count of open tasks, the tasks themselves with a checkbox that rewrites the file, and a "+"
 * that appends one. The file stays the user's — any other line in it is left alone — and a change
 * made in a terminal shows here as soon as the editor saves.
 *
 * <p>A toggle rebinds the rows already on screen rather than rebuilding the card, so the row
 * keeps accessibility focus and the tap does not flash the layout.</p>
 */
public class TasksWidgetView extends BuiltinWidgetView implements MarkdownFileSource.Listener {
    static final String KEY_PATH = "path";
    static final String DEFAULT_PATH = "~/notes/tasks.md";

    private static final String GLYPH_CAPTION = "";
    private static final String GLYPH_ADD = "";
    private static final String GLYPH_CHECK = "";

    /** The design's sample list, for the picker card. */
    private static final String SAMPLE = "- [ ] Review dock PR @today\n"
        + "- [ ] Pay electricity bill @today\n"
        + "- [ ] Call Ahmed re: invoice @16:00\n"
        + "- [ ] Back up ~/notes @Wed\n"
        + "- [x] Update tlstore\n";

    @Nullable private MarkdownFileSource source;
    /** The file the tasks below were read from; null until the first read lands. */
    @Nullable private String dataPath;
    @NonNull private String text = "";
    @NonNull private List<TaskListParser.Task> tasks = Collections.emptyList();

    // What the last build put on screen, so a change of data can rebind it in place.
    @Nullable private String builtShape;
    private final List<Row> rows = new ArrayList<>();
    @Nullable private TextView countView;
    @Nullable private TextView countLabel;
    @Nullable private TextView summaryView;

    /** One checklist row on the card and the task it shows. */
    private final class Row {
        final LinearLayout view;
        final TextView box;
        final TextView label;
        @Nullable final TextView due;
        final int radiusDp;
        @Nullable TaskListParser.Task task;

        Row(@NonNull BuiltinWidgetUi ui, int boxDp, int radiusDp, float textSp, int gapDp,
            boolean withDue) {
            this.radiusDp = radiusDp;
            box = ui.glyph("", 9f, ui.style.onPrimary);
            box.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(boxDp), ui.dp(boxDp)));
            label = ui.text("", textSp, ui.style.sansMedium, ui.style.onSurface);
            due = withDue ? ui.mono("", 10.5f) : null;
            view = due != null
                ? ui.row(gapDp, box, BuiltinWidgetUi.flex(label), due)
                : ui.row(gapDp, box, BuiltinWidgetUi.flex(label));
            view.setClickable(true);
            view.setFocusable(true);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            GradientDrawable mask = ui.rounded(0xFFFFFFFF, ui.dp(6));
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(ui.style.onSurface, 0x1F)), null, mask));
            view.setOnClickListener(v -> { if (task != null) toggle(task); });
        }

        void bind(@NonNull BuiltinWidgetUi ui, @NonNull TaskListParser.Task value) {
            task = value;
            BuiltinWidgetStyle style = ui.style;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(ui.dp(radiusDp));
            if (value.done) {
                shape.setColor(style.primary);
                box.setText(GLYPH_CHECK);
            } else {
                shape.setColor(0);
                shape.setStroke(Math.max(1, Math.round(1.5f * style.density)), style.onSurfaceVariant);
                box.setText("");
            }
            box.setBackground(shape);
            label.setText(value.text);
            label.setTextColor(value.done ? style.onSurfaceVariant : style.onSurface);
            label.setPaintFlags(value.done
                ? label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : label.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            if (due != null) {
                String dueText = value.due != null ? value.due
                    : value.done ? getContext().getString(R.string.bw_files_task_done_label) : "";
                due.setText(dueText);
                due.setVisibility(dueText.isEmpty() ? View.GONE : View.VISIBLE);
            }
            view.setContentDescription(getContext().getString(
                value.done ? R.string.bw_files_task_done : R.string.bw_files_task_open, value.text));
        }
    }

    public TasksWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                           @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.TASKS, services, style);
    }

    @NonNull @Override public List<ConfigField> configFields() {
        return Collections.singletonList(new ConfigField(KEY_PATH,
            getContext().getString(R.string.bw_files_path_label), ConfigField.Type.TEXT, DEFAULT_PATH));
    }

    @NonNull private String path() {
        return FilesWidgetPaths.expand(configString(KEY_PATH, DEFAULT_PATH), FilesWidgetPaths.home());
    }

    // ----- data -----------------------------------------------------------------------------

    @Override protected void onStart() {
        source = MarkdownFileSource.acquire(services, path(), this);
    }

    @Override protected void onStop() {
        if (source != null) source.release(this);
        source = null;
    }

    @Override public void onMarkdownChanged(@NonNull MarkdownFileSource.Snapshot snapshot) {
        if (!isStarted() || !snapshot.path.equals(path())) return;
        if (snapshot.path.equals(dataPath) && snapshot.text.equals(text)) return;
        dataPath = snapshot.path;
        text = snapshot.text;
        tasks = TaskListParser.parse(text);
        render();
    }

    private boolean loaded() { return isPreview() || path().equals(dataPath); }

    @NonNull private List<TaskListParser.Task> currentTasks() {
        if (isPreview()) return TaskListParser.parse(SAMPLE);
        return loaded() ? tasks : Collections.emptyList();
    }

    private static int visibleCount(@NonNull BuiltinWidgetSpan span) {
        switch (span) {
            case ONE_BY_ONE: return 0;
            case TWO_BY_ONE: return 2;
            case FOUR_BY_TWO: return 5;
            default: return 4;
        }
    }

    @NonNull private List<TaskListParser.Task> visibleTasks(@NonNull List<TaskListParser.Task> all) {
        List<TaskListParser.Task> ordered = TaskListParser.ordered(all);
        int count = Math.min(ordered.size(), visibleCount(span()));
        return ordered.subList(0, count);
    }

    /** What decides the layout's structure; a change of anything else is a rebind. */
    @NonNull private String shape(@NonNull List<TaskListParser.Task> all) {
        return span().name() + '|' + loaded() + '|' + all.isEmpty() + '|' + visibleTasks(all).size();
    }

    private void render() {
        List<TaskListParser.Task> all = currentTasks();
        if (shape(all).equals(builtShape)) {
            bindData(ui(), all);
        } else {
            FrameLayout frame = content();
            frame.removeAllViews();
            build(frame, span(), ui(), all);
        }
    }

    // ----- actions --------------------------------------------------------------------------

    @Override protected void onTap() {
        if (!isPreview()) FilesWidgetEditor.open(services, path());
    }

    private void toggle(@NonNull TaskListParser.Task task) {
        if (isPreview()) return;
        final int line = task.line;
        final String raw = task.raw;
        edit(current -> TaskListParser.toggle(current, line, raw));
    }

    private void add(@Nullable CharSequence value) {
        if (isPreview() || value == null) return;
        final String entry = value.toString().trim();
        if (entry.isEmpty()) return;
        edit(current -> TaskListParser.append(current, entry));
    }

    private void edit(@NonNull MarkdownFileSource.Edit edit) {
        Runnable failed = () -> Toast.makeText(services.context(), R.string.bw_files_tasks_save_failed,
            Toast.LENGTH_SHORT).show();
        if (source != null) source.edit(edit, failed);
        else MarkdownFileSource.editDetached(services, path(), edit, failed);
    }

    private void showAddDialog() {
        if (isPreview()) return;
        Context context = getContext();
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density);
        TextInputLayout box = new TextInputLayout(context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(context.getString(R.string.bw_files_tasks_new_hint));
        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout holder = new FrameLayout(context);
        holder.setPadding(pad, pad / 3, pad, 0);
        holder.addView(box);
        services.host().onSystemImeRequested();
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.bw_files_tasks_new_title)
            .setView(holder)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.bw_files_save, (d, which) -> add(input.getText()))
            .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            add(input.getText());
            dialog.dismiss();
            return true;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        input.requestFocus();
        dialog.setOnDismissListener(d -> services.host().onSystemImeReleased());
        dialog.show();
    }

    // ----- layout ---------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        build(frame, span, ui, currentTasks());
    }

    private void build(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                       @NonNull BuiltinWidgetUi ui, @NonNull List<TaskListParser.Task> all) {
        rows.clear();
        countView = null;
        countLabel = null;
        summaryView = null;
        builtShape = shape(all);
        boolean empty = loaded() && all.isEmpty();
        int shown = visibleTasks(all).size();
        LinearLayout root;
        switch (span) {
            case ONE_BY_ONE: root = buildOne(ui, empty); break;
            case TWO_BY_ONE: root = buildTwoByOne(ui, empty, shown); break;
            case TWO_BY_TWO: root = buildTwoByTwo(ui, empty, shown); break;
            case FOUR_BY_ONE: root = buildFourByOne(ui, empty, shown); break;
            default: root = buildFourByTwo(ui, empty, shown); break;
        }
        frame.addView(root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        bindData(ui, all);
    }

    /** Fills the rows and counts already on screen from {@code all}. */
    private void bindData(@NonNull BuiltinWidgetUi ui, @NonNull List<TaskListParser.Task> all) {
        List<TaskListParser.Task> visible = visibleTasks(all);
        for (int i = 0; i < rows.size() && i < visible.size(); i++) rows.get(i).bind(ui, visible.get(i));
        int open = TaskListParser.openCount(all);
        int done = all.size() - open;
        boolean loaded = loaded();
        if (countView != null) countView.setText(loaded ? String.valueOf(open) : "");
        if (countLabel != null) {
            countLabel.setText(getResources().getQuantityString(R.plurals.bw_files_tasks_left_label, open));
        }
        if (summaryView != null) {
            summaryView.setText(loaded ? getContext().getString(R.string.bw_files_tasks_header, open, done) : "");
        }
        if (!loaded) {
            setContentDescription(getContext().getString(kind.label));
        } else if (all.isEmpty()) {
            setContentDescription(getContext().getString(R.string.bw_files_tasks_empty));
        } else {
            String left = getResources().getQuantityString(R.plurals.bw_files_tasks_description, open, open);
            setContentDescription(getContext().getString(R.string.bw_files_tasks_description_done, left, done));
        }
    }

    @NonNull private LinearLayout buildOne(@NonNull BuiltinWidgetUi ui, boolean empty) {
        BuiltinWidgetStyle style = ui.style;
        LinearLayout root;
        if (empty) {
            TextView add = addDisc(ui, 32, 12f);
            TextView label = ui.text(getContext().getString(R.string.bw_files_tasks_empty), 11f,
                style.sansMedium, style.onSurfaceVariant);
            root = ui.column(6, add, label);
            FilesWidgetTapRouter.install(root, ui).control(add);
        } else {
            countView = ui.numeral("", 34f);
            countLabel = ui.text("", 11f, style.sansMedium, style.onSurfaceVariant);
            root = ui.column(4, countView, countLabel);
        }
        root.setGravity(Gravity.CENTER);
        inset(root, 8, 0, 8, 0, ui);
        return root;
    }

    @NonNull private LinearLayout buildTwoByOne(@NonNull BuiltinWidgetUi ui, boolean empty, int shown) {
        LinearLayout counts = countColumn(ui, 34, 28f);
        LinearLayout list;
        TextView add = null;
        if (empty) {
            add = addDisc(ui, 28, 11f);
            list = ui.row(8, BuiltinWidgetUi.flex(emptyLabel(ui)), add);
        } else {
            list = ui.column(6, rowViews(ui, shown, 14, 4, 12f, 7, false));
            list.setGravity(Gravity.CENTER_VERTICAL);
        }
        list.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout root = ui.row(12, counts, list);
        inset(root, 14, 0, 14, 0, ui);
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        if (add != null) router.control(add);
        else router.rows(list, rowList());
        return root;
    }

    @NonNull private LinearLayout buildTwoByTwo(@NonNull BuiltinWidgetUi ui, boolean empty, int shown) {
        View caption = ui.caption(getContext().getString(R.string.bw_files_tasks_today), GLYPH_CAPTION,
            FilesWidgetPaths.baseName(path()));
        TextView add = addDisc(ui, 22, 10f);
        LinearLayout header = ui.row(8, BuiltinWidgetUi.flex(caption), add);
        // The list shows the tasks that fit whole, from the first; at 115dp that is two or three.
        View list = empty ? ui.column(0, emptyLabel(ui))
            : taskList(ui, rowViews(ui, shown, 16, 5, 12.5f, 8, false), 10);
        LinearLayout root = ui.column(10, header, BuiltinWidgetUi.flexTall(list));
        inset(root, 14, 14, 14, 14, ui);
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        router.control(add);
        if (!empty) router.rows(list, rowList());
        return root;
    }

    @NonNull private LinearLayout buildFourByOne(@NonNull BuiltinWidgetUi ui, boolean empty, int shown) {
        LinearLayout counts = countColumn(ui, 40, 30f);
        LinearLayout grid;
        if (empty) {
            grid = ui.column(0, emptyLabel(ui));
        } else {
            View[] cells = rowViews(ui, shown, 16, 5, 12.5f, 7, false);
            List<View> lines = new ArrayList<>();
            for (int i = 0; i < cells.length; i += 2) {
                View right = i + 1 < cells.length ? cells[i + 1] : new View(getContext());
                lines.add(ui.row(14, BuiltinWidgetUi.flex(cells[i]), BuiltinWidgetUi.flex(right)));
            }
            grid = ui.column(8, lines.toArray(new View[0]));
        }
        grid.setGravity(Gravity.CENTER_VERTICAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView add = addDisc(ui, 32, 12f);
        LinearLayout root = ui.row(14, counts, grid, add);
        inset(root, 16, 0, 16, 0, ui);
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        router.control(add);
        if (!empty) router.rows(grid, rowList());
        return root;
    }

    @NonNull private LinearLayout buildFourByTwo(@NonNull BuiltinWidgetUi ui, boolean empty, int shown) {
        BuiltinWidgetStyle style = ui.style;
        TextView title = ui.sans(getContext().getString(R.string.bw_files_tasks_today), 13f, true);
        summaryView = ui.mono("", 10.5f);
        LinearLayout titles = ui.row(8, title, summaryView);
        // Top gravity is what lets a horizontal LinearLayout line its children up by baseline.
        titles.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout pill = ui.row(5, ui.glyph(GLYPH_ADD, 10f, style.onPrimaryContainer),
            ui.text(getContext().getString(R.string.bw_files_tasks_add), 11f, style.sansBold,
                style.onPrimaryContainer));
        pill.setBackground(ui.rounded(style.primaryContainer, ui.dp(13)));
        pill.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        pill.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(26)));
        makeAddControl(pill);
        LinearLayout header = ui.row(8, BuiltinWidgetUi.flex(titles), pill);

        View list = empty ? ui.column(0, emptyLabel(ui))
            : taskList(ui, rowViews(ui, shown, 16, 5, 12.5f, 10, true), 9);
        LinearLayout root = ui.column(9, header, BuiltinWidgetUi.flexTall(list));
        inset(root, 16, 14, 16, 14, ui);
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        router.control(pill);
        if (!empty) router.rows(list, rowList());
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /** The open-task count over "left", centred in a column {@code widthDp} wide. */
    @NonNull private LinearLayout countColumn(@NonNull BuiltinWidgetUi ui, int widthDp, float numeralSp) {
        countView = ui.numeral("", numeralSp);
        TextView left = ui.mono(getContext().getString(R.string.bw_files_tasks_left_short), 10f);
        LinearLayout column = ui.column(0, countView, left);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(widthDp),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    @NonNull private View[] rowViews(@NonNull BuiltinWidgetUi ui, int count, int boxDp, int radiusDp,
                                     float textSp, int gapDp, boolean withDue) {
        View[] views = new View[count];
        for (int i = 0; i < count; i++) {
            Row row = new Row(ui, boxDp, radiusDp, textSp, gapDp, withDue);
            rows.add(row);
            views[i] = row.view;
        }
        return views;
    }

    /** The task rows as a column that keeps the first {@code n} that fit whole. */
    @NonNull private FitStack taskList(@NonNull BuiltinWidgetUi ui, @NonNull View[] views,
                                       int gapDp) {
        FitStack list = FitStack.column(getContext());
        for (View view : views) list.addRow(view, ui.dp(gapDp));
        return list;
    }

    @NonNull private List<View> rowList() {
        List<View> views = new ArrayList<>(rows.size());
        for (Row row : rows) views.add(row.view);
        return views;
    }

    @NonNull private TextView emptyLabel(@NonNull BuiltinWidgetUi ui) {
        return ui.text(getContext().getString(R.string.bw_files_tasks_empty), 12.5f,
            ui.style.sansMedium, ui.style.onSurfaceVariant);
    }

    @NonNull private TextView addDisc(@NonNull BuiltinWidgetUi ui, int sizeDp, float glyphSp) {
        TextView disc = ui.roundButton(GLYPH_ADD, sizeDp, glyphSp, ui.style.primaryContainer,
            ui.style.onPrimaryContainer, getContext().getString(R.string.bw_files_tasks_add_description));
        makeAddControl(disc);
        return disc;
    }

    private void makeAddControl(@NonNull View view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setContentDescription(getContext().getString(R.string.bw_files_tasks_add_description));
        view.setOnClickListener(v -> showAddDialog());
    }
}
