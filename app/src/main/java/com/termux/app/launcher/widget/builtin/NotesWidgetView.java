package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A markdown file in the home folder ({@code ~/notes/scratch.md} by default) as a scratchpad:
 * its first lines in the terminal's monospace face, headings in the primary colour, checked
 * items dimmed, and how long ago it was edited. Tapping it opens the file in the user's editor
 * in a terminal window; what the editor saves shows here straight away. The wide card switches
 * between the file and a few more named in settings.
 */
public class NotesWidgetView extends BuiltinWidgetView
    implements MarkdownFileSource.Listener, BuiltinWidgetServices.TickListener {
    static final String KEY_PATH = "path";
    static final String KEY_FILES = "files";
    static final String DEFAULT_PATH = "~/notes/scratch.md";
    /** At most this many files in the wide card's chip row. */
    private static final int MAX_FILES = 4;
    /** More lines than any card can show; the block clips to what fits. */
    private static final int BLOCK_LINES = 40;

    private static final String GLYPH_NOTE = "";

    /** The design's sample note, for the picker card. */
    private static final String SAMPLE = "# today\n"
        + "nix-shell -p ffmpeg\n"
        + "try kitty +kitten icat\n"
        + "dock glass alpha 0.62?\n"
        + "- [x] fix pane rim\n"
        + "- [ ] widget sizes\n";
    private static final String SAMPLE_OTHER_FILE = "ideas.md";
    private static final long SAMPLE_AGE_MS = 2 * 60_000L;

    /** How the card's age label is worded. */
    private enum AgeLabel { NAME_COMPACT, NAME_EDITED, COMPACT }

    @Nullable private MarkdownFileSource source;
    @Nullable private MarkdownFileSource.Snapshot snapshot;
    /** The file a chip of the wide card switched to; view state only. */
    @Nullable private String chosenPath;

    @Nullable private TextView ageView;
    @Nullable private AgeLabel ageLabel;

    public NotesWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                           @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.NOTES, services, style);
    }

    @NonNull @Override public List<ConfigField> configFields() {
        return Arrays.asList(
            new ConfigField(KEY_PATH, getContext().getString(R.string.bw_files_path_label),
                ConfigField.Type.TEXT, DEFAULT_PATH),
            new ConfigField(KEY_FILES, getContext().getString(R.string.bw_files_notes_files_label),
                ConfigField.Type.TEXT, ""));
    }

    // ----- files ----------------------------------------------------------------------------

    @NonNull private String primaryPath() {
        return FilesWidgetPaths.expand(configString(KEY_PATH, DEFAULT_PATH), FilesWidgetPaths.home());
    }

    /** The main file, then the extra ones from settings, without repeats. */
    @NonNull private List<String> files() {
        List<String> out = new ArrayList<>();
        out.add(primaryPath());
        for (String entry : FilesWidgetPaths.splitList(configString(KEY_FILES, ""))) {
            String path = FilesWidgetPaths.expand(entry, FilesWidgetPaths.home());
            if (!out.contains(path)) out.add(path);
            if (out.size() >= MAX_FILES) break;
        }
        return out;
    }

    /** The file this card shows: a chip's choice on the wide card, the main file elsewhere. */
    @NonNull private String shownPath() {
        if (span() == BuiltinWidgetSpan.FOUR_BY_TWO && chosenPath != null && files().contains(chosenPath)) {
            return chosenPath;
        }
        return primaryPath();
    }

    /** The data for the shown file: the sample in the picker, null until the first read lands. */
    @Nullable private MarkdownFileSource.Snapshot current() {
        if (isPreview()) {
            return new MarkdownFileSource.Snapshot(primaryPath(), true, SAMPLE,
                System.currentTimeMillis() - SAMPLE_AGE_MS, false);
        }
        MarkdownFileSource.Snapshot value = snapshot;
        return value != null && value.path.equals(shownPath()) ? value : null;
    }

    // ----- data -----------------------------------------------------------------------------

    @Override protected void onStart() {
        source = MarkdownFileSource.acquire(services, shownPath(), this);
        services.addTickListener(this);
    }

    @Override protected void onStop() {
        services.removeTickListener(this);
        if (source != null) source.release(this);
        source = null;
    }

    @Override public void onMarkdownChanged(@NonNull MarkdownFileSource.Snapshot next) {
        if (!isStarted() || !next.path.equals(shownPath()) || next.sameAs(snapshot)) return;
        boolean sameText = snapshot != null && snapshot.path.equals(next.path)
            && snapshot.exists == next.exists && snapshot.text.equals(next.text);
        snapshot = next;
        if (sameText) updateAge();
        else render();
    }

    @Override public void onTick() { updateAge(); }

    private void render() {
        FrameLayout frame = content();
        frame.removeAllViews();
        build(frame, span(), ui());
    }

    // ----- actions --------------------------------------------------------------------------

    @Override protected void onTap() {
        if (!isPreview()) FilesWidgetEditor.open(services, shownPath());
    }

    private void choose(@NonNull String path) {
        if (isPreview() || path.equals(shownPath())) return;
        chosenPath = path;
        render();
        if (isStarted()) {
            if (source != null) source.release(this);
            source = MarkdownFileSource.acquire(services, shownPath(), this);
        }
    }

    // ----- layout ---------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        build(frame, span, ui);
    }

    private void build(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                       @NonNull BuiltinWidgetUi ui) {
        ageView = null;
        ageLabel = null;
        MarkdownFileSource.Snapshot data = current();
        LinearLayout root;
        switch (span) {
            case ONE_BY_ONE: root = buildOne(ui); break;
            case TWO_BY_ONE: root = buildTwoByOne(ui, data); break;
            case TWO_BY_TWO: root = buildTwoByTwo(ui, data); break;
            case FOUR_BY_ONE: root = buildFourByOne(ui, data); break;
            default: root = buildFourByTwo(ui, data); break;
        }
        frame.addView(root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        updateAge();
    }

    @NonNull private LinearLayout buildOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle style = ui.style;
        FrameLayout square = new FrameLayout(getContext());
        square.setBackground(ui.rounded(style.tertiaryContainer, ui.innerRadius(10)));
        square.addView(ui.glyph(GLYPH_NOTE, 18f, style.onTertiaryContainer),
            new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        square.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        TextView label = ui.mono(getContext().getString(R.string.bw_files_notes_new), 10.5f);
        LinearLayout root = ui.column(6, square, label);
        root.setGravity(Gravity.CENTER);
        inset(root, 8, 0, 8, 0, ui);
        return root;
    }

    @NonNull private LinearLayout buildTwoByOne(@NonNull BuiltinWidgetUi ui,
                                                @Nullable MarkdownFileSource.Snapshot data) {
        BuiltinWidgetStyle style = ui.style;
        TextView header = ui.text(FilesWidgetPaths.fileName(shownPath()), 11f, style.sansBold,
            style.onSurfaceVariant);
        trackAge(header, AgeLabel.NAME_COMPACT);
        LinearLayout root = ui.column(5, header);
        addPreviewLines(ui, root, data, 3, 11.5f, 1.45f);
        inset(root, 14, 12, 14, 12, ui);
        return root;
    }

    @NonNull private LinearLayout buildTwoByTwo(@NonNull BuiltinWidgetUi ui,
                                                @Nullable MarkdownFileSource.Snapshot data) {
        String path = shownPath();
        View caption = ui.caption(FilesWidgetPaths.fileName(path), GLYPH_NOTE,
            FilesWidgetPaths.display(path, FilesWidgetPaths.home()));
        LinearLayout root = ui.column(8, caption, block(ui, data, 11.5f, 1.55f, 7));
        inset(root, 14, 14, 14, 14, ui);
        return root;
    }

    @NonNull private LinearLayout buildFourByOne(@NonNull BuiltinWidgetUi ui,
                                                 @Nullable MarkdownFileSource.Snapshot data) {
        BuiltinWidgetStyle style = ui.style;
        String path = shownPath();
        TextView header = ui.text(FilesWidgetPaths.fileName(path), 11f, style.sansBold,
            style.onSurfaceVariant);
        trackAge(header, AgeLabel.NAME_EDITED);
        TextView line;
        if (data == null) {
            line = ui.text("", 12f, style.mono, style.onSurface);
        } else if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
            line = ui.text(getContext().getString(R.string.bw_files_notes_empty), 12f, style.mono,
                style.onSurfaceVariant);
        } else {
            line = ui.text(TextUtils.join(" · ", NotesWidgetFormats.previewLines(data.text, 6)), 12f,
                style.mono, style.onSurface);
        }
        LinearLayout text = ui.column(5, header, line);

        LinearLayout pill = ui.row(7, ui.glyph(GLYPH_NOTE, 12f, style.onTertiaryContainer),
            ui.text(getContext().getString(R.string.bw_files_notes_note), 12f, style.sansBold,
                style.onTertiaryContainer));
        pill.setBackground(ui.rounded(style.tertiaryContainer, ui.innerRadius(8)));
        pill.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        pill.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(44)));
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        pill.setContentDescription(getContext().getString(R.string.bw_files_notes_open,
            FilesWidgetPaths.fileName(path)));
        pill.setOnClickListener(v -> onTap());

        LinearLayout root = ui.row(12, BuiltinWidgetUi.flex(text), pill);
        inset(root, 16, 0, 12, 0, ui);
        FilesWidgetTapRouter.install(root, ui).control(pill);
        return root;
    }

    @NonNull private LinearLayout buildFourByTwo(@NonNull BuiltinWidgetUi ui,
                                                 @Nullable MarkdownFileSource.Snapshot data) {
        BuiltinWidgetStyle style = ui.style;
        String shown = shownPath();
        List<String> paths = files();
        List<String> names = new ArrayList<>();
        for (String path : paths) names.add(FilesWidgetPaths.fileName(path));
        if (isPreview() && paths.size() == 1) {
            paths.add("");
            names.add(SAMPLE_OTHER_FILE);
        }
        List<View> chips = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            chips.add(chip(ui, paths.get(i), names.get(i), paths.get(i).equals(shown)));
        }
        LinearLayout chipRow = ui.row(6, chips.toArray(new View[0]));
        TextView age = ui.mono("", 10.5f);
        trackAge(age, AgeLabel.COMPACT);
        LinearLayout header = ui.row(8, BuiltinWidgetUi.flex(chipRow), age);
        LinearLayout root = ui.column(8, header, block(ui, data, 12f, 1.6f, 6));
        inset(root, 16, 14, 16, 14, ui);
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        if (chips.size() > 1) for (View chip : chips) router.control(chip);
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    @NonNull private TextView chip(@NonNull BuiltinWidgetUi ui, @NonNull String path,
                                   @NonNull String name, boolean selected) {
        BuiltinWidgetStyle style = ui.style;
        TextView chip = ui.text(name, 11f, selected ? style.monoMedium : style.mono,
            selected ? style.onPrimaryContainer : style.onSurfaceVariant);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(ui.rounded(selected ? style.primaryContainer : style.container, ui.dp(12)));
        chip.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        chip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(24)));
        chip.setSelected(selected);
        chip.setContentDescription(getContext().getString(R.string.bw_files_notes_show, name));
        chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> { if (!path.isEmpty()) choose(path); });
        return chip;
    }

    /**
     * Up to {@code max} one-line previews of the note under {@code column}, each ellipsised, one
     * line height ({@code spacing} times the text size) apart.
     */
    private void addPreviewLines(@NonNull BuiltinWidgetUi ui, @NonNull LinearLayout column,
                                 @Nullable MarkdownFileSource.Snapshot data, int max, float sp,
                                 float spacing) {
        BuiltinWidgetStyle style = ui.style;
        if (data == null) return;
        List<TextView> lines = new ArrayList<>();
        if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
            lines.add(ui.text(getContext().getString(R.string.bw_files_notes_empty), sp, style.mono,
                style.onSurfaceVariant));
        } else {
            for (String line : NotesWidgetFormats.previewLines(data.text, max)) {
                lines.add(ui.text(line, sp, style.mono,
                    NotesWidgetFormats.isDoneItem(line) ? style.onSurfaceVariant : style.onSurface));
            }
        }
        // A one-line TextView is as tall as its font's line; the rest of the design's line
        // height goes between the lines.
        float textPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
            getResources().getDisplayMetrics());
        int natural = lines.isEmpty() ? 0 : lines.get(0).getPaint().getFontMetricsInt(null);
        int gap = Math.max(0, Math.round(textPx * spacing) - natural);
        for (int i = 0; i < lines.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // The header sits 5dp above the first line (the column's gap); the rest are line-height apart.
            params.topMargin = i == 0 ? ui.dp(5) : gap;
            column.addView(lines.get(i), params);
        }
    }

    /**
     * The note as a wrapping block that fills the rest of the card: headings in primary, checked
     * items dimmed, as many lines as fit and an ellipsis on the last.
     */
    @NonNull private TextView block(@NonNull BuiltinWidgetUi ui,
                                    @Nullable MarkdownFileSource.Snapshot data, float sp,
                                    float spacing, int initialLines) {
        BuiltinWidgetStyle style = ui.style;
        TextView view = ui.text("", sp, style.mono, style.onSurface);
        view.setSingleLine(false);
        view.setHorizontallyScrolling(false);
        view.setLineSpacing(0f, spacing);
        view.setMaxLines(initialLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.TOP | Gravity.START);
        if (data != null) {
            if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
                view.setText(getContext().getString(R.string.bw_files_notes_empty));
                view.setTextColor(style.onSurfaceVariant);
            } else {
                view.setText(styled(data.text, style));
            }
        }
        // Keep to the lines that fit, so the last visible one ends in an ellipsis rather than
        // half a line cut by the card. Only the height decides it, and the height is the
        // column's, so this settles in one pass.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                        oldBottom) -> {
            int lineHeight = view.getLineHeight();
            if (lineHeight <= 0) return;
            int height = bottom - top - view.getPaddingTop() - view.getPaddingBottom();
            int lines = Math.max(1, height / lineHeight);
            if (view.getMaxLines() != lines) view.post(() -> view.setMaxLines(lines));
        });
        return BuiltinWidgetUi.flexTall(view);
    }

    @NonNull private static CharSequence styled(@NonNull String text, @NonNull BuiltinWidgetStyle style) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        List<String> lines = NotesWidgetFormats.head(text, BLOCK_LINES);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            String line = lines.get(i);
            int start = out.length();
            out.append(line);
            int color = NotesWidgetFormats.isHeading(line) ? style.primary
                : NotesWidgetFormats.isDoneItem(line) ? style.onSurfaceVariant : 0;
            if (color != 0 && out.length() > start) {
                out.setSpan(new ForegroundColorSpan(color), start, out.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }

    // ----- age ------------------------------------------------------------------------------

    private void trackAge(@NonNull TextView view, @NonNull AgeLabel label) {
        ageView = view;
        ageLabel = label;
    }

    /** Rewords the age label and the description; called on build, on a new read and each minute. */
    private void updateAge() {
        MarkdownFileSource.Snapshot data = current();
        String name = FilesWidgetPaths.fileName(shownPath());
        NotesWidgetFormats.Age age = data != null && data.exists
            ? NotesWidgetFormats.age(data.lastModified, System.currentTimeMillis()) : null;
        TextView view = ageView;
        if (view != null && ageLabel != null) {
            String compact = age == null ? null : compact(age);
            switch (ageLabel) {
                case NAME_COMPACT:
                    view.setText(compact == null ? name
                        : getContext().getString(R.string.bw_files_notes_name_age, name, compact));
                    break;
                case NAME_EDITED:
                    view.setText(age == null ? name
                        : getContext().getString(R.string.bw_files_notes_name_age, name, edited(age)));
                    break;
                default:
                    view.setText(compact == null ? "" : compact);
                    break;
            }
        }
        String label = getContext().getString(kind.label);
        if (data == null) {
            setContentDescription(label + " " + name);
        } else if (age == null || NotesWidgetFormats.isBlank(data.text)) {
            setContentDescription(getContext().getString(R.string.bw_files_notes_description_empty,
                label, name));
        } else {
            setContentDescription(getContext().getString(R.string.bw_files_notes_description,
                label, name, spoken(age)));
        }
    }

    @NonNull private String compact(@NonNull NotesWidgetFormats.Age age) {
        return NotesWidgetFormats.compact(age, getContext().getString(R.string.bw_files_age_now),
            getContext().getString(R.string.bw_files_age_minutes),
            getContext().getString(R.string.bw_files_age_hours), locale(), TimeZone.getDefault());
    }

    @NonNull private String edited(@NonNull NotesWidgetFormats.Age age) {
        switch (age.unit) {
            case NOW: return getContext().getString(R.string.bw_files_edited_now);
            case MINUTES:
            case HOURS: return getContext().getString(R.string.bw_files_edited_ago, compact(age));
            default: return getContext().getString(R.string.bw_files_edited_on, compact(age));
        }
    }

    @NonNull private String spoken(@NonNull NotesWidgetFormats.Age age) {
        switch (age.unit) {
            case NOW: return getContext().getString(R.string.bw_files_edited_now);
            case MINUTES: return getResources().getQuantityString(
                R.plurals.bw_files_edited_minutes_spoken, age.amount, age.amount);
            case HOURS: return getResources().getQuantityString(
                R.plurals.bw_files_edited_hours_spoken, age.amount, age.amount);
            default: return getContext().getString(R.string.bw_files_edited_on,
                NotesWidgetFormats.spokenDay(age, locale(), TimeZone.getDefault()));
        }
    }

    @NonNull private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }
}
