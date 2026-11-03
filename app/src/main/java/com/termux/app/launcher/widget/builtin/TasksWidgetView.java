package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
 * A markdown checklist in the home folder ({@code ~/notes/tasks.md} by default) as a widget: an
 * open count in the header, the tasks themselves as 48 dp checkbox rows that rewrite the file,
 * and, with none, an invitation that names the file it writes to. The file stays the user's —
 * any other line in it is left alone — and a change made in a terminal shows here as soon as the
 * editor saves. The view draws its layout plan ({@link TasksLayoutPlanner}) and puts a
 * transparent target in each of its touch parts.
 */
public class TasksWidgetView extends BuiltinWidgetView implements MarkdownFileSource.Listener {
    static final String KEY_PATH = "path";
    static final String DEFAULT_PATH = "~/notes/tasks.md";

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

    private void render() {
        FrameLayout frame = content();
        frame.removeAllViews();
        build(frame, span(), ui());
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

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        build(frame, span, ui);
    }

    private void build(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                       @NonNull BuiltinWidgetUi ui) {
        List<TaskListParser.Task> all = currentTasks();
        List<TaskListParser.Task> ordered = TaskListParser.ordered(all);
        List<TasksLayoutPlanner.Row> rows = new ArrayList<>(ordered.size());
        for (TaskListParser.Task task : ordered) {
            rows.add(new TasksLayoutPlanner.Row(task.text, task.due, task.done));
        }
        int open = TaskListParser.openCount(all);
        boolean loaded = loaded();
        String shownPath = configString(KEY_PATH, DEFAULT_PATH);
        float fontScale = WidgetTokens.typeScale(getResources());
        TasksLayoutPlanner.Words words = words();
        describe(loaded, all, open);
        frame.addView(new WidgetPlanHost(getContext(), style(), (widthDp, heightDp, measure) ->
            TasksLayoutPlanner.plan(span, widthDp, heightDp, fontScale, loaded, rows, open,
                shownPath, words, measure), new WidgetPlanHost.ViewSource() {
                @Nullable @Override public View view(@NonNull WidgetLayoutPlan.Part part) {
                    return target(ui, part, ordered);
                }
                @Override public void onPlanned(@NonNull WidgetLayoutPlan plan) { }
            }), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** The transparent, rippling, accessible target for a touch part: a row's, or the "+". */
    @Nullable private View target(@NonNull BuiltinWidgetUi ui, @NonNull WidgetLayoutPlan.Part part,
                                  @NonNull List<TaskListParser.Task> ordered) {
        View view = new View(getContext());
        view.setClickable(true);
        view.setFocusable(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        GradientDrawable mask = ui.rounded(0xFFFFFFFF, ui.dp(8));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(ui.style.onSurface, 0x1F)), null, mask));
        if (part.id.equals("add.touch")) {
            view.setContentDescription(getContext().getString(R.string.bw_files_tasks_add_description));
            view.setOnClickListener(v -> showAddDialog());
            return view;
        }
        if (!part.id.startsWith("row.")) return null;
        int index;
        try {
            index = Integer.parseInt(part.id.split("\\.")[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 0 || index >= ordered.size()) return null;
        TaskListParser.Task task = ordered.get(index);
        view.setContentDescription(getContext().getString(
            task.done ? R.string.bw_files_task_done : R.string.bw_files_task_open, task.text));
        view.setOnClickListener(v -> toggle(task));
        return view;
    }

    private void describe(boolean loaded, @NonNull List<TaskListParser.Task> all, int open) {
        int done = all.size() - open;
        if (!loaded) {
            setContentDescription(getContext().getString(kind.label));
        } else if (all.isEmpty()) {
            setContentDescription(getContext().getString(R.string.bw_files_tasks_empty));
        } else {
            String left = getResources().getQuantityString(R.plurals.bw_files_tasks_description, open, open);
            setContentDescription(getContext().getString(R.string.bw_files_tasks_description_done, left, done));
        }
    }

    @NonNull private TasksLayoutPlanner.Words words() {
        Context context = getContext();
        return new TasksLayoutPlanner.Words() {
            @NonNull @Override public String header(int open) {
                return context.getString(R.string.bw_files_tasks_header_open, open);
            }
            @NonNull @Override public String headerShort(int open) {
                return context.getString(R.string.bw_files_tasks_header_open_short, open);
            }
            @NonNull @Override public String openLabel(int open) {
                return context.getString(R.string.bw_files_tasks_open_label);
            }
            @NonNull @Override public String addTask() {
                return context.getString(R.string.bw_files_tasks_add_description);
            }
            @NonNull @Override public String writesTo(@NonNull String path) {
                return context.getString(R.string.bw_files_tasks_writes_to, path);
            }
        };
    }
}
