package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * A command run through the login shell on an interval, with its last output: the status and
 * when it ran at 1×1, the command with a run button at 2×1 and 4×1 (the 4×1 with the first line
 * of output), the output itself at 2×2 and 4×2.
 *
 * <p>It runs on start when the last result is older than the interval, then every interval while
 * the widget is on screen, and whenever the run button is tapped; never while it is off screen.
 * The last result is kept per command, so a restart shows it at once. A tap on the body opens a
 * terminal window running the command, which stays open as a shell afterwards.</p>
 */
public class ShellWidgetView extends BuiltinWidgetView {
    static final String KEY_COMMAND = "command";
    static final String KEY_INTERVAL = "interval";
    static final String KEY_TITLE = "title";
    static final String DEFAULT_COMMAND = "uptime";
    static final int DEFAULT_INTERVAL_MINUTES = 15;
    private static final int MAX_INTERVAL_MINUTES = 7 * 24 * 60;
    /** Lines handed to the output block; more than any span can show. */
    private static final int MAX_LINES = 40;

    private static final String PROMPT = "\uf120";
    private static final String CHECK = "\uf00c";
    private static final String CROSS = "\uf00d";
    private static final String PLAY = "\uf04b";
    private static final String REFRESH = "\uf021";

    private final Runnable scheduled = this::runNow;
    private final BuiltinWidgetServices.TickListener tick = this::refreshAgo;

    @Nullable private FrameLayout frame;
    @Nullable private ShellWidgetRunner.Result last;
    private boolean running;
    private int generation;
    /** The label that says how long ago the command ran, refreshed on the minute tick. */
    @Nullable private TextView agoLabel;

    public ShellWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                           @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.SHELL, services, style);
    }

    @NonNull @Override public List<ConfigField> configFields() {
        Resources res = getResources();
        return Arrays.asList(
            new ConfigField(KEY_COMMAND, res.getString(R.string.bw_signals_shell_field_command),
                ConfigField.Type.TEXT, DEFAULT_COMMAND),
            new ConfigField(KEY_INTERVAL, res.getString(R.string.bw_signals_shell_field_interval),
                ConfigField.Type.NUMBER, String.valueOf(DEFAULT_INTERVAL_MINUTES)),
            new ConfigField(KEY_TITLE, res.getString(R.string.bw_signals_shell_field_title),
                ConfigField.Type.TEXT, ""));
    }

    // ----- settings -------------------------------------------------------------------------

    @NonNull private String command() {
        if (isPreview()) return sampleCommand(span());
        String value = configString(KEY_COMMAND, DEFAULT_COMMAND).trim();
        return value.isEmpty() ? DEFAULT_COMMAND : value;
    }

    /** Minutes between runs; 0 runs it only when the run button is tapped. */
    private int intervalMinutes() {
        if (isPreview()) return span() == BuiltinWidgetSpan.TWO_BY_TWO ? 60 : 15;
        int value = configInt(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES);
        return Math.max(0, Math.min(MAX_INTERVAL_MINUTES, value));
    }

    /** The user's label, or null to show the command itself. */
    @Nullable private String title() {
        if (isPreview()) return span() == BuiltinWidgetSpan.ONE_BY_ONE ? "backup.sh" : null;
        String value = configString(KEY_TITLE, "").trim();
        return value.isEmpty() ? null : value;
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        this.frame = frame;
        if (isPreview()) {
            last = sampleResult(span);
        } else if (last != null && !last.command.equals(command())) {
            last = null;
        }
        render();
    }

    @Override protected void onStart() {
        generation++;
        services.addTickListener(tick);
        final int ticket = generation;
        final String command = command();
        final Context context = services.context();
        try {
            services.io().execute(() -> {
                ShellWidgetRunner.Result stored = ShellWidgetRunner.load(context, command);
                services.main().post(() -> {
                    if (ticket != generation || !isStarted()) return;
                    if (stored != null && (last == null || stored.finishedAt > last.finishedAt)) {
                        last = stored;
                        render();
                    }
                    schedule();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The page is going away.
        }
    }

    @Override protected void onStop() {
        generation++;
        services.removeTickListener(tick);
        services.main().removeCallbacks(scheduled);
        if (running) {
            // The run finishes and is saved; this view hears of it on its next start.
            running = false;
            render();
        }
    }

    /** The body opens a terminal window running the command, left as a shell when it ends. */
    @Override protected void onTap() {
        if (isPreview()) return;
        String command = command();
        String label = title();
        services.host().openCommandWindow(
            Arrays.asList("bash", "-lc", command + "\nexec \"${SHELL:-bash}\" -l"),
            label != null ? label : command);
    }

    // ----- running --------------------------------------------------------------------------

    /** Queues the next automatic run: now when the last one is older than the interval. */
    private void schedule() {
        services.main().removeCallbacks(scheduled);
        int minutes = intervalMinutes();
        if (!isStarted() || isPreview() || minutes <= 0 || running) return;
        long interval = minutes * 60_000L;
        long age = last == null ? Long.MAX_VALUE : System.currentTimeMillis() - last.finishedAt;
        long delay = age >= interval || age < 0 ? 0L : interval - age;
        services.main().postDelayed(scheduled, delay);
    }

    private void runNow() {
        services.main().removeCallbacks(scheduled);
        if (!isStarted() || isPreview() || running) return;
        running = true;
        render();
        final int ticket = generation;
        final String command = command();
        final Context context = services.context();
        try {
            ShellWidgetRunner.executor().execute(() -> {
                ShellWidgetRunner.Result result = ShellWidgetRunner.run(context, command);
                services.main().post(() -> {
                    if (ticket != generation || !isStarted()) return;
                    running = false;
                    last = result;
                    render();
                    schedule();
                });
            });
        } catch (RejectedExecutionException e) {
            running = false;
            render();
        }
    }

    private void refreshAgo() {
        TextView label = agoLabel;
        if (label != null && last != null && !running) {
            label.setText(span() == BuiltinWidgetSpan.FOUR_BY_TWO ? agoEvery() : agoText());
        }
        setContentDescription(describe());
    }

    // ----- layouts --------------------------------------------------------------------------

    private void render() {
        FrameLayout target = frame;
        if (target == null) return;
        target.removeAllViews();
        agoLabel = null;
        BuiltinWidgetUi ui = ui();
        View root;
        switch (span()) {
            case ONE_BY_ONE: root = oneByOne(ui); break;
            case TWO_BY_ONE: root = twoByOne(ui); break;
            case TWO_BY_TWO: root = tall(ui, false); break;
            case FOUR_BY_ONE: root = fourByOne(ui); break;
            default: root = tall(ui, true); break;
        }
        target.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentDescription(describe());
    }

    @NonNull
    private View oneByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        View spacer = new View(getContext());
        LinearLayout top = ui.row(0, ui.glyph(PROMPT, 16f, s.primary), BuiltinWidgetUi.flex(spacer),
            statusGlyph(ui, 12f));
        String label = title();
        TextView name = ui.text(label != null ? label : command(), 11.5f, SignalsWidgetKit.monoBold(s),
            s.onSurface);
        TextView ago = ui.text(agoText(), 10f, s.monoMedium, s.onSurfaceVariant);
        agoLabel = ago;
        LinearLayout bottom = ui.column(2, name, ago);
        View gap = new View(getContext());
        LinearLayout column = ui.column(0, wide(top), BuiltinWidgetUi.flexTall(gap), bottom);
        inset(column, 12, 12, 12, 12, ui);
        return column;
    }

    @NonNull
    private View twoByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        TextView label = labelView(ui, 12f);
        TextView ago = ui.text(agoText(), 10.5f, s.monoMedium, s.onSurfaceVariant);
        agoLabel = ago;
        LinearLayout lines = ui.column(5, label, statusRow(ui, 10.5f, false), ago);
        View run = runDisc(ui, PLAY, s.primary, s.onPrimary);
        // The 48dp touch frame overhangs the 36dp disc by 6dp: take it back off the gap and inset.
        LinearLayout row = ui.row(4, BuiltinWidgetUi.flex(lines), run);
        inset(row, 14, 0, 6, 0, ui);
        return row;
    }

    @NonNull
    private View fourByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        TextView label = labelView(ui, 11.5f);
        TextView ago = ui.text(agoText(), 11.5f, s.monoMedium, s.onSurfaceVariant);
        agoLabel = ago;
        LinearLayout head = ui.row(8, BuiltinWidgetUi.flex(label), ago);
        TextView first = ui.text(outputLine(), 12f, s.mono, s.onSurface);
        LinearLayout lines = ui.column(6, wide(head), first);
        View run = runDisc(ui, REFRESH, s.container, s.onSurface);
        LinearLayout row = ui.row(6, BuiltinWidgetUi.flex(lines), run);
        inset(row, 16, 0, 6, 0, ui);
        return row;
    }

    /** 2×2 ({@code wide} false) and 4×2: header, output block, footer. */
    @NonNull
    private View tall(@NonNull BuiltinWidgetUi ui, boolean wide) {
        BuiltinWidgetStyle s = ui.style;
        TextView label = labelView(ui, wide ? 12f : 11.5f);
        TextView refresh = ui.glyph(REFRESH, 11f, running ? s.outlineVariant : s.onSurfaceVariant);
        LinearLayout header = ui.row(8, BuiltinWidgetUi.flex(label), refresh);

        ShellOutputView output = new ShellOutputView(getContext());
        output.setTypeface(s.mono);
        output.setTextColor(s.onSurface);
        float sp = wide ? 11.5f : 10.5f;
        output.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        SignalsWidgetKit.lineHeight(output, sp, wide ? 1.6f : 1.55f);
        output.setText(wide ? colouredOutput(s) : headedOutput(s));
        if (wide) {
            output.setBackground(ui.rounded(s.container, ui.innerRadius(12)));
            output.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        }

        LinearLayout footer;
        if (wide) {
            TextView when = ui.text(agoEvery(), 10.5f, s.monoMedium, s.onSurfaceVariant);
            agoLabel = when;
            footer = ui.row(8, BuiltinWidgetUi.flex(statusRow(ui, 10.5f, true)), when);
        } else {
            footer = ui.row(8, BuiltinWidgetUi.flex(statusText(ui, 10f)),
                ui.text(everyText(), 10f, s.monoMedium, s.onSurfaceVariant));
        }

        LinearLayout column = ui.column(8, wide(header), BuiltinWidgetUi.flexTall(output), wide(footer));
        if (wide) inset(column, 16, 14, 16, 14, ui);
        else inset(column, 14, 14, 14, 14, ui);
        if (!isPreview()) {
            refresh.setClickable(true);
            refresh.setFocusable(true);
            refresh.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            refresh.setContentDescription(getResources().getString(R.string.bw_signals_shell_run));
            refresh.setOnClickListener(v -> runNow());
            SignalsWidgetKit.expandTouch(column, refresh, ui);
        }
        return column;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /** "$ command" with the prompt in primary, or the user's label on its own. */
    @NonNull
    private TextView labelView(@NonNull BuiltinWidgetUi ui, float sp) {
        BuiltinWidgetStyle s = ui.style;
        Typeface bold = SignalsWidgetKit.monoBold(s);
        String label = title();
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (label == null) {
            text.append("$ ");
            text.setSpan(new ForegroundColorSpan(s.primary), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(command());
        } else {
            text.append(label);
        }
        return ui.text(text, sp, bold, s.onSurface);
    }

    /** The round run button in its 48dp touch frame; a plain disc in the picker. */
    @NonNull
    private View runDisc(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, @ColorInt int fill,
                         @ColorInt int onFill) {
        TextView disc = ui.roundButton(glyph, 36, 12f, fill, running ? ui.style.outlineVariant : onFill,
            getResources().getString(R.string.bw_signals_shell_run));
        if (isPreview()) {
            disc.setClickable(false);
            FrameLayout holder = new FrameLayout(getContext());
            holder.addView(disc, new FrameLayout.LayoutParams(ui.dp(36), ui.dp(36), Gravity.CENTER));
            holder.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(SignalsWidgetKit.TOUCH_DP),
                ui.dp(SignalsWidgetKit.TOUCH_DP)));
            return holder;
        }
        return SignalsWidgetKit.touchFrame(getContext(), disc, 36, ui, v -> runNow());
    }

    @NonNull
    private TextView statusGlyph(@NonNull BuiltinWidgetUi ui, float sp) {
        BuiltinWidgetStyle s = ui.style;
        ShellWidgetRunner.Result result = last;
        if (running || result == null) return ui.glyph(running ? REFRESH : CHECK, sp, s.outlineVariant);
        return result.succeeded() ? ui.glyph(CHECK, sp, s.done) : ui.glyph(CROSS, sp, s.error);
    }

    /** "✓ exit 0", optionally with " · 0.18 s"; "running…" and "not run yet" in the variant colour. */
    @NonNull
    private View statusRow(@NonNull BuiltinWidgetUi ui, float sp, boolean withDuration) {
        TextView text = statusText(ui, sp);
        ShellWidgetRunner.Result result = last;
        if (running || result == null) return text;
        if (withDuration && !result.failed) {
            text.setText(getResources().getString(R.string.bw_signals_shell_status_duration,
                text.getText(), SignalsWidgetFormats.duration(result.durationMs)));
        }
        return ui.row(4, statusGlyph(ui, sp), text);
    }

    @NonNull
    private TextView statusText(@NonNull BuiltinWidgetUi ui, float sp) {
        BuiltinWidgetStyle s = ui.style;
        ShellWidgetRunner.Result result = last;
        int color = running || result == null ? s.onSurfaceVariant
            : result.succeeded() ? s.done : s.error;
        return ui.text(statusWords(), sp, s.monoMedium, color);
    }

    @NonNull
    private String statusWords() {
        Resources res = getResources();
        ShellWidgetRunner.Result result = last;
        if (running) return res.getString(R.string.bw_signals_shell_running);
        if (result == null) return res.getString(R.string.bw_signals_shell_not_run);
        if (result.failed) return res.getString(R.string.bw_signals_shell_failed);
        if (result.timedOut) return res.getString(R.string.bw_signals_shell_timed_out);
        return res.getString(R.string.bw_signals_shell_exit, result.exitCode);
    }

    /** "12m ago" for the one-line layouts; while running, "running…". */
    @NonNull
    private String agoText() {
        ShellWidgetRunner.Result result = last;
        if (running) return getResources().getString(R.string.bw_signals_shell_running);
        if (result == null) return getResources().getString(R.string.bw_signals_shell_not_run);
        return SignalsWidgetFormats.ago(result.finishedAt, System.currentTimeMillis());
    }

    /** "4m ago · every 15m" for the 4×2 footer. */
    @NonNull
    private String agoEvery() {
        ShellWidgetRunner.Result result = last;
        if (result == null || running) return everyText();
        return getResources().getString(R.string.bw_signals_shell_ago_every,
            SignalsWidgetFormats.ago(result.finishedAt, System.currentTimeMillis()), everyText());
    }

    @NonNull
    private String everyText() {
        int minutes = intervalMinutes();
        return minutes <= 0 ? getResources().getString(R.string.bw_signals_shell_manual)
            : SignalsWidgetFormats.every(minutes);
    }

    @NonNull
    private String output() {
        ShellWidgetRunner.Result result = last;
        if (result == null) return "";
        String[] lines = result.output.split("\n", -1);
        if (lines.length <= MAX_LINES) return result.output;
        return String.join("\n", Arrays.asList(lines).subList(0, MAX_LINES));
    }

    @NonNull
    private String outputLine() {
        return ShellWidgetRunner.firstLine(output());
    }

    /** 2×2: the first line (a table's header, usually) in the variant colour. */
    @NonNull
    private CharSequence headedOutput(@NonNull BuiltinWidgetStyle s) {
        String text = output();
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        int end = text.indexOf('\n');
        if (end < 0) end = text.length();
        if (end > 0) {
            out.setSpan(new ForegroundColorSpan(s.onSurfaceVariant), 0, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    /** 4×2: git-status colouring, line by line. */
    @NonNull
    private CharSequence colouredOutput(@NonNull BuiltinWidgetStyle s) {
        String text = output();
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            for (ShellOutputColouring.Range range : ShellOutputColouring.line(line)) {
                out.setSpan(new ForegroundColorSpan(roleColor(s, range.role)), offset + range.start,
                    offset + range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            offset += line.length() + 1;
        }
        return out;
    }

    @ColorInt
    private static int roleColor(@NonNull BuiltinWidgetStyle s, @NonNull ShellOutputColouring.Role role) {
        switch (role) {
            case DONE: return s.done;
            case ERROR: return s.error;
            case WARM: return s.warm;
            default: return s.primary;
        }
    }

    @NonNull
    private static <V extends View> V wide(@NonNull V view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    @NonNull
    private CharSequence describe() {
        Resources res = getResources();
        String label = title();
        if (label == null) label = command();
        ShellWidgetRunner.Result result = last;
        if (running) return res.getString(R.string.bw_signals_shell_description_running, label);
        if (result == null) return res.getString(R.string.bw_signals_shell_description_none, label);
        CharSequence when = DateUtils.getRelativeTimeSpanString(result.finishedAt,
            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return res.getString(R.string.bw_signals_shell_description, label, statusWords(), when);
    }

    // ----- picker sample --------------------------------------------------------------------

    @NonNull
    private static String sampleCommand(@NonNull BuiltinWidgetSpan span) {
        switch (span) {
            case ONE_BY_ONE: return "~/bin/backup.sh";
            case TWO_BY_ONE: return "tlstore update";
            case TWO_BY_TWO: return "df -h";
            case FOUR_BY_ONE: return "uptime";
            default: return "git -C ~/termux-launcher status -sb";
        }
    }

    /** The design's sample run for each span. */
    @NonNull
    private static ShellWidgetRunner.Result sampleResult(@NonNull BuiltinWidgetSpan span) {
        long now = System.currentTimeMillis();
        String output;
        long ago;
        switch (span) {
            case ONE_BY_ONE: output = "done"; ago = 2 * 60 * 60_000L; break;
            case TWO_BY_ONE: output = "Up to date"; ago = 12 * 60_000L; break;
            case TWO_BY_TWO:
                output = "mount  used  avail\n/data   156G  100G\n/sdcard 156G  100G\n"
                    + "/system 6.1G    0B\n/cache  1.2M  442M";
                ago = 20 * 60_000L;
                break;
            case FOUR_BY_ONE: output = "up 3 days, 4:12 · load 1.08 0.94 0.88"; ago = 30_000L; break;
            default:
                output = "## dev...origin/dev [ahead 2]\n M .../WidgetPaneView.java\n"
                    + " M .../WidgetCellView.java\n?? docs/widgets.md";
                ago = 4 * 60_000L;
                break;
        }
        return new ShellWidgetRunner.Result(sampleCommand(span), output, 0, 180L, now - ago, false, false);
    }
}
