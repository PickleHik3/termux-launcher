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
 * A markdown file in the home folder ({@code ~/notes/scratch.md} by default) as the Notes widget:
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
    private enum AgeLabel { AGE }

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

    /**
     * A card of two rows is never zoomed: its body block fills the height with more lines at the
     * Body role. Zoomed, a tall cell drew the empty note's "Tap to write" near twice Body.
     */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) {
        return !span.isTall();
    }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        build(frame, span, ui);
    }

    private void build(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                       @NonNull BuiltinWidgetUi ui) {
        ageView = null;
        ageLabel = null;
        MarkdownFileSource.Snapshot data = current();
        ViewGroup root;
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
        square.setBackground(ui.innerRounded(style.tertiaryContainer, 10));
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

    @NonNull private FitStack buildTwoByOne(@NonNull BuiltinWidgetUi ui,
                                            @Nullable MarkdownFileSource.Snapshot data) {
        // One row: the header (file, age) always; the title and then body lines where they fit,
        // each line that does not fit whole left out from the last.
        FitStack root = FitStack.column(getContext())
            .add(headerRow(ui, null, true), FitStack.ESSENTIAL, 0);
        addTitle(ui, root, data, WidgetTokens.GAP_TIGHT_DP);
        addBodyLines(ui, root, data, 2);
        inset(root, WidgetTokens.INSET_COMPACT_DP, WidgetTokens.INSET_COMPACT_DP,
            WidgetTokens.INSET_COMPACT_DP, WidgetTokens.INSET_COMPACT_DP, ui);
        return root;
    }

    @NonNull private LinearLayout buildTwoByTwo(@NonNull BuiltinWidgetUi ui,
                                                @Nullable MarkdownFileSource.Snapshot data) {
        return tallCard(ui, data, headerRow(ui, null, false));
    }

    /** Header, title, body and age: the card of every span of two rows. */
    @NonNull private LinearLayout tallCard(@NonNull BuiltinWidgetUi ui,
                                           @Nullable MarkdownFileSource.Snapshot data,
                                           @NonNull View header) {
        BuiltinWidgetStyle style = ui.style;
        LinearLayout root = ui.column(WidgetTokens.GAP_DP, header);
        String title = data == null ? null : NotesWidgetFormats.title(data.text);
        if (title != null) root.addView(titleView(ui, title));
        root.addView(block(ui, data));
        TextView age = ui.text("", WidgetTokens.textSp(WidgetTokens.Role.LABEL), style.sansMedium, style.onSurfaceVariant);
        trackAge(age, AgeLabel.AGE);
        root.addView(age);
        inset(root, WidgetTokens.INSET_DP, WidgetTokens.INSET_DP, WidgetTokens.INSET_DP,
            WidgetTokens.INSET_DP, ui);
        return root;
    }

    /** The file glyph and its name as a small label; the age on the right where {@code withAge}. */
    @NonNull private LinearLayout headerRow(@NonNull BuiltinWidgetUi ui, @Nullable View nameOrChips,
                                            boolean withAge) {
        BuiltinWidgetStyle style = ui.style;
        View name = nameOrChips;
        if (name == null) {
            TextView label = ui.text(FilesWidgetPaths.fileName(shownPath()),
                WidgetTokens.textSp(WidgetTokens.Role.LABEL), style.sansMedium, style.onSurfaceVariant);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            name = label;
        }
        LinearLayout row = ui.row(WidgetTokens.GAP_DP,
            ui.glyph(GLYPH_NOTE, WidgetTokens.textSp(WidgetTokens.Role.BODY), style.primary),
            BuiltinWidgetUi.flex(name));
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (withAge) {
            TextView age = ui.text("", WidgetTokens.textSp(WidgetTokens.Role.LABEL), style.sansMedium,
                style.onSurfaceVariant);
            trackAge(age, AgeLabel.AGE);
            row.addView(age);
        }
        return row;
    }

    @NonNull private TextView titleView(@NonNull BuiltinWidgetUi ui, @NonNull String title) {
        TextView view = ui.text(title, WidgetTokens.textSp(WidgetTokens.Role.TITLE), ui.style.sansBold,
            ui.style.onSurface);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private void addTitle(@NonNull BuiltinWidgetUi ui, @NonNull FitStack column,
                          @Nullable MarkdownFileSource.Snapshot data, int gapDp) {
        String title = data == null ? null : NotesWidgetFormats.title(data.text);
        if (title != null) column.addRow(titleView(ui, title), ui.dp(gapDp));
    }

    /** Up to {@code max} body lines, one per row, each ellipsised; the empty note's hint if blank. */
    private void addBodyLines(@NonNull BuiltinWidgetUi ui, @NonNull FitStack column,
                              @Nullable MarkdownFileSource.Snapshot data, int max) {
        BuiltinWidgetStyle style = ui.style;
        if (data == null) return;
        List<TextView> lines = new ArrayList<>();
        if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
            lines.add(ui.text(getContext().getString(R.string.bw_files_notes_empty),
                WidgetTokens.textSp(WidgetTokens.Role.BODY), style.mono, style.onSurfaceVariant));
        } else {
            for (String line : NotesWidgetFormats.bodyLines(data.text, max)) {
                lines.add(ui.text(line, WidgetTokens.textSp(WidgetTokens.Role.BODY), style.mono,
                    NotesWidgetFormats.isDoneItem(line) ? style.onSurfaceVariant : style.onSurface));
            }
        }
        for (TextView line : lines) {
            line.setSingleLine(true);
            line.setEllipsize(TextUtils.TruncateAt.END);
            column.addRow(line, ui.dp(WidgetTokens.GAP_TIGHT_DP));
        }
    }

    @NonNull private LinearLayout buildFourByOne(@NonNull BuiltinWidgetUi ui,
                                                 @Nullable MarkdownFileSource.Snapshot data) {
        BuiltinWidgetStyle style = ui.style;
        String path = shownPath();
        String title = data == null ? null : NotesWidgetFormats.title(data.text);
        TextView line;
        float bodySp = WidgetTokens.textSp(WidgetTokens.Role.BODY);
        if (data == null) {
            line = ui.text("", bodySp, style.mono, style.onSurface);
        } else if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
            line = ui.text(getContext().getString(R.string.bw_files_notes_empty), bodySp, style.mono,
                style.onSurfaceVariant);
        } else {
            List<String> body = NotesWidgetFormats.bodyLines(data.text, 6);
            line = ui.text(body.isEmpty() ? "" : TextUtils.join(" · ", body), bodySp, style.mono,
                style.onSurface);
        }
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout text = ui.column(WidgetTokens.GAP_TIGHT_DP, headerRow(ui, null, true));
        if (title != null) text.addView(titleView(ui, title));
        text.addView(line);

        LinearLayout pill = ui.row(7, ui.glyph(GLYPH_NOTE, 12f, style.onTertiaryContainer),
            ui.text(getContext().getString(R.string.bw_files_notes_note), 12f, style.sansBold,
                style.onTertiaryContainer));
        pill.setBackground(ui.innerRounded(style.tertiaryContainer, 8));
        pill.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        pill.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(44)));
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        pill.setContentDescription(getContext().getString(R.string.bw_files_notes_open,
            FilesWidgetPaths.fileName(path)));
        pill.setOnClickListener(v -> onTap());

        LinearLayout root = ui.row(12, BuiltinWidgetUi.flex(text), pill);
        inset(root, WidgetTokens.INSET_COMPACT_DP, 0, WidgetTokens.INSET_COMPACT_DP, 0, ui);
        FilesWidgetTapRouter.install(root, ui).control(pill);
        return root;
    }

    @NonNull private LinearLayout buildFourByTwo(@NonNull BuiltinWidgetUi ui,
                                                 @Nullable MarkdownFileSource.Snapshot data) {
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
        LinearLayout root = tallCard(ui, data, headerRow(ui, chipRow, false));
        FilesWidgetTapRouter router = FilesWidgetTapRouter.install(root, ui);
        if (chips.size() > 1) for (View chip : chips) router.control(chip);
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    @NonNull private TextView chip(@NonNull BuiltinWidgetUi ui, @NonNull String path,
                                   @NonNull String name, boolean selected) {
        BuiltinWidgetStyle style = ui.style;
        TextView chip = ui.text(name, WidgetTokens.textSp(WidgetTokens.Role.LABEL_FORCED), selected ? style.monoMedium : style.mono,
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
     * The body as a wrapping block that fills the rest of the card, in mono at Body: checked
     * items dimmed, as many lines as fit and an ellipsis on the last.
     */
    @NonNull private TextView block(@NonNull BuiltinWidgetUi ui,
                                    @Nullable MarkdownFileSource.Snapshot data) {
        BuiltinWidgetStyle style = ui.style;
        TextView view = ui.text("", WidgetTokens.textSp(WidgetTokens.Role.BODY), style.mono, style.onSurface);
        view.setSingleLine(false);
        view.setHorizontallyScrolling(false);
        view.setLineSpacing(0f, 1.25f);
        view.setMaxLines(6);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.TOP | Gravity.START);
        if (data != null) {
            if (!data.exists || NotesWidgetFormats.isBlank(data.text)) {
                view.setText(getContext().getString(R.string.bw_files_notes_empty));
                view.setTextColor(style.onSurfaceVariant);
            } else {
                view.setText(styled(NotesWidgetFormats.bodyLines(data.text, BLOCK_LINES), style));
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

    @NonNull private static CharSequence styled(@NonNull List<String> lines,
                                                @NonNull BuiltinWidgetStyle style) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            String line = lines.get(i);
            int start = out.length();
            out.append(line);
            int color = NotesWidgetFormats.isDoneItem(line) ? style.onSurfaceVariant : 0;
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
            view.setText(age == null ? "" : ageText(age));
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

    /** The card's age line: "2 h ago", "5 min ago", "just now", or the day it was edited. */
    @NonNull private String ageText(@NonNull NotesWidgetFormats.Age age) {
        switch (age.unit) {
            case NOW: return getContext().getString(R.string.bw_files_age_label_now);
            case MINUTES: return getContext().getString(R.string.bw_files_age_label_minutes, age.amount);
            case HOURS: return getContext().getString(R.string.bw_files_age_label_hours, age.amount);
            default: return compact(age);
        }
    }

    @NonNull private String compact(@NonNull NotesWidgetFormats.Age age) {
        return NotesWidgetFormats.compact(age, getContext().getString(R.string.bw_files_age_now),
            getContext().getString(R.string.bw_files_age_minutes),
            getContext().getString(R.string.bw_files_age_hours), locale(), TimeZone.getDefault());
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
