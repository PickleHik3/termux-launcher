package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.text.format.DateUtils;
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
import com.termux.ai.TaiBenchStats;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiModelSpec;

import java.util.Locale;

/**
 * The benchmark screens' building blocks, in the Model centre's dress: a tonal card (the
 * {@code tai_centre_shell} tray with a {@code tai_centre_core} surface inside), small-caps
 * section headers, monospace detail lines, the pills, and the number formats every screen shares.
 * Everything takes its colours from the theme attributes through
 * {@link TaiModelCentreAdapter#color}, so light and dark both hold.
 */
final class TaiBenchViews {
    private TaiBenchViews() {
    }

    /** A card: {@link #outer} goes into the list, {@link #core} takes the content. */
    static final class Card {
        @NonNull final FrameLayout outer;
        @NonNull final LinearLayout shell;
        @NonNull final LinearLayout core;

        Card(@NonNull FrameLayout outer, @NonNull LinearLayout shell, @NonNull LinearLayout core) {
            this.outer = outer;
            this.shell = shell;
            this.core = core;
        }
    }

    static int dp(@NonNull Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    static int color(@NonNull Context context, int attr) {
        return TaiModelCentreAdapter.color(context, attr);
    }

    @NonNull
    static Card card(@NonNull Context context) {
        FrameLayout outer = new FrameLayout(context);
        outer.setPadding(dp(context, 16), dp(context, 3), dp(context, 16), dp(context, 3));
        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundResource(R.drawable.tai_centre_shell);
        int pad = dp(context, 4);
        shell.setPadding(pad, pad, pad, pad);
        LinearLayout core = new LinearLayout(context);
        core.setOrientation(LinearLayout.VERTICAL);
        core.setBackgroundResource(R.drawable.tai_centre_core);
        core.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        shell.addView(core, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Card(outer, shell, core);
    }

    /** The Model centre's section header: small caps in the accent, a dim fact at the end. */
    @NonNull
    static View sectionHeader(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence end) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(true);
        row.setPadding(dp(context, 22), dp(context, 16), dp(context, 22), dp(context, 6));
        TextView text = new TextView(context);
        text.setText(title);
        text.setAllCaps(true);
        text.setLetterSpacing(0.16f);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextColor(color(context, com.termux.shared.R.attr.termuxColorPrimary));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) text.setAccessibilityHeading(true);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (end.length() > 0) {
            TextView fact = new TextView(context);
            fact.setText(end);
            fact.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            fact.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(dp(context, 12));
            row.addView(fact, params);
        }
        return row;
    }

    /** A card's title line. */
    @NonNull
    static TextView title(@NonNull Context context, @NonNull CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        return view;
    }

    /** A plain secondary line. */
    @NonNull
    static TextView body(@NonNull Context context, @NonNull CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        return view;
    }

    /** The Model centre's monospace detail line. */
    @NonNull
    static TextView mono(@NonNull Context context, @NonNull CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        return view;
    }

    /** A big figure, for a tile or the Result headline. */
    @NonNull
    static TextView figure(@NonNull Context context, @NonNull CharSequence text, float sp) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        return view;
    }

    /** A small pill in a tone; empty text hides it. */
    @NonNull
    static TextView pill(@NonNull Context context, @NonNull CharSequence text, @NonNull TaiModelCentreRows.Tone tone) {
        TextView pill = new TextView(context);
        pill.setText(text);
        pill.setBackgroundResource(R.drawable.tai_centre_pill);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        pill.setPadding(dp(context, 9), dp(context, 3), dp(context, 9), dp(context, 3));
        pill.setSingleLine(true);
        TaiModelCentreAdapter.tonePill(pill, tone);
        pill.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
        return pill;
    }

    /** The quiet backend pill ("LiteRT", "MNN"). */
    @NonNull
    static TextView backendPill(@NonNull Context context, @NonNull String backend) {
        TextView pill = pill(context, backendLabel(context, backend), TaiModelCentreRows.Tone.NEUTRAL);
        TaiModelCentreAdapter.backendPill(pill);
        return pill;
    }

    /** The one loud button: Run a benchmark, Start, Run this model again. */
    @NonNull
    static TextView goButton(@NonNull Context context, @NonNull CharSequence text) {
        TextView button = actionButton(context, text);
        TaiModelCentreAdapter.goPill(button);
        return button;
    }

    /** The quiet button: Show why, Skip the wait, Stop. */
    @NonNull
    static TextView ghostButton(@NonNull Context context, @NonNull CharSequence text) {
        TextView button = actionButton(context, text);
        TaiModelCentreAdapter.ghostPill(button);
        return button;
    }

    /** A button in the error tone: Stop, Delete results. */
    @NonNull
    static TextView errorButton(@NonNull Context context, @NonNull CharSequence text) {
        TextView button = actionButton(context, text);
        button.setBackgroundTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorErrorContainer)));
        button.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnErrorContainer));
        return button;
    }

    @NonNull
    private static TextView actionButton(@NonNull Context context, @NonNull CharSequence text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setBackgroundResource(R.drawable.tai_centre_pill);
        button.setClickable(true);
        button.setFocusable(true);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        button.setMinHeight(dp(context, 36));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        return button;
    }

    /** Dims a button that cannot be tapped now, and says so to accessibility. */
    static void setEnabled(@NonNull View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
    }

    /** A horizontal row of views with a gap between them. */
    @NonNull
    static LinearLayout row(@NonNull Context context, int gapDp, @NonNull View... children) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < children.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.setMarginStart(dp(context, gapDp));
            row.addView(children[i], params);
        }
        return row;
    }

    /** {@link LinearLayout.LayoutParams} for a block with a top margin. */
    @NonNull
    static LinearLayout.LayoutParams block(@NonNull Context context, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topDp);
        return params;
    }

    // ---- words and numbers ----

    @NonNull
    static String backendLabel(@NonNull Context context, @Nullable String backend) {
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(backend)) return context.getString(R.string.tai_centre_pill_backend_litert);
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return context.getString(R.string.tai_centre_pill_backend_mnn);
        return backend == null ? "" : backend;
    }

    /** "CPU", "GPU". */
    @NonNull
    static String processorLabel(@NonNull String accelerator) {
        return accelerator.toUpperCase(Locale.ROOT);
    }

    /** "21.3 tok/s"; "—" with nothing measured. */
    @NonNull
    static String tps(@NonNull Context context, double value) {
        if (Double.isNaN(value) || value <= 0.0) return context.getString(R.string.tai_bench_none);
        return context.getString(R.string.tai_bench_tps, value >= 100.0 ? String.format(Locale.US, "%.0f", value)
            : String.format(Locale.US, "%.1f", value));
    }

    /** "380 ms" or "1.4 s"; "—" with nothing measured. */
    @NonNull
    static String millis(@NonNull Context context, double value) {
        if (Double.isNaN(value) || value <= 0.0) return context.getString(R.string.tai_bench_none);
        if (value < 1000.0) return context.getString(R.string.tai_bench_ms, Math.round(value));
        return context.getString(R.string.tai_bench_seconds, String.format(Locale.US, "%.1f", value / 1000.0));
    }

    /** "1.2 GB"; "—" with nothing measured. */
    @NonNull
    static String bytes(@NonNull Context context, long value) {
        if (value <= 0L) return context.getString(R.string.tai_bench_none);
        return TaiModelCentreRows.formatBytes(value);
    }

    /** "about 2 min", "about 12 min", "about 1 h 10 min". */
    @NonNull
    static String duration(@NonNull Context context, long ms) {
        long minutes = Math.max(1L, Math.round(ms / 60_000.0));
        if (minutes < 60L) return context.getString(R.string.tai_bench_about_minutes, minutes);
        return context.getString(R.string.tai_bench_about_hours, minutes / 60L, minutes % 60L);
    }

    /** "3:42" for a countdown. */
    @NonNull
    static String clock(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** "2 hours ago", "yesterday"; never a future time. */
    @NonNull
    static CharSequence ago(long timestamp) {
        long now = System.currentTimeMillis();
        return DateUtils.getRelativeTimeSpanString(Math.min(timestamp, now), now, DateUtils.MINUTE_IN_MILLIS);
    }

    /** The verdict's word and tone: Smooth, Usable, Slow, Broken. */
    @NonNull
    static String verdictLabel(@NonNull Context context, @Nullable String verdict) {
        if (verdict == null) return "";
        switch (verdict) {
            case TaiBenchStats.VERDICT_SMOOTH: return context.getString(R.string.tai_bench_verdict_smooth);
            case TaiBenchStats.VERDICT_USABLE: return context.getString(R.string.tai_bench_verdict_usable);
            case TaiBenchStats.VERDICT_SLOW: return context.getString(R.string.tai_bench_verdict_slow);
            case TaiBenchStats.VERDICT_BROKEN: return context.getString(R.string.tai_bench_verdict_broken);
            default: return "";
        }
    }

    @NonNull
    static TaiModelCentreRows.Tone verdictTone(@Nullable String verdict) {
        if (verdict == null) return TaiModelCentreRows.Tone.NEUTRAL;
        switch (verdict) {
            case TaiBenchStats.VERDICT_SMOOTH: return TaiModelCentreRows.Tone.ACCENT;
            case TaiBenchStats.VERDICT_USABLE: return TaiModelCentreRows.Tone.NEUTRAL;
            case TaiBenchStats.VERDICT_SLOW: return TaiModelCentreRows.Tone.WARN;
            default: return TaiModelCentreRows.Tone.ERROR;
        }
    }

    /** The phase's word for the stepper. */
    @NonNull
    static String phaseLabel(@NonNull Context context, @NonNull String phase) {
        switch (phase) {
            case TaiBenchSuite.PHASE_LOAD: return context.getString(R.string.tai_bench_phase_load);
            case TaiBenchSuite.PHASE_WARMUP: return context.getString(R.string.tai_bench_phase_warmup);
            case TaiBenchSuite.PHASE_READING: return context.getString(R.string.tai_bench_phase_reading);
            case TaiBenchSuite.PHASE_FIRST_WORD: return context.getString(R.string.tai_bench_phase_first_word);
            case TaiBenchSuite.PHASE_WRITING: return context.getString(R.string.tai_bench_phase_writing);
            case TaiBenchSuite.PHASE_SUSTAINED: return context.getString(R.string.tai_bench_phase_sustained);
            case TaiBenchSuite.PHASE_CHECK: return context.getString(R.string.tai_bench_phase_check);
            default: return phase;
        }
    }

    @NonNull
    static String presetLabel(@NonNull Context context, @Nullable TaiBenchSuite.Preset preset) {
        if (preset == null) return "";
        switch (preset) {
            case QUICK: return context.getString(R.string.tai_bench_preset_quick);
            case THOROUGH: return context.getString(R.string.tai_bench_preset_thorough);
            default: return context.getString(R.string.tai_bench_preset_standard);
        }
    }

    /** "Cool", "Warm", "Hot" for a thermal status name; "—" when unknown. */
    @NonNull
    static String heatLabel(@NonNull Context context, @Nullable String thermalStatus) {
        if (thermalStatus == null) return context.getString(R.string.tai_bench_none);
        switch (thermalStatus) {
            case "none": return context.getString(R.string.tai_bench_heat_cool);
            case "light": return context.getString(R.string.tai_bench_heat_light);
            case "moderate": return context.getString(R.string.tai_bench_heat_warm);
            default: return context.getString(R.string.tai_bench_heat_hot);
        }
    }

    /** "72%", "72% · charging"; "—" when unknown. */
    @NonNull
    static String batteryLabel(@NonNull Context context, int percent, boolean charging) {
        if (percent < 0) return context.getString(R.string.tai_bench_none);
        return charging ? context.getString(R.string.tai_bench_battery_charging, percent)
            : context.getString(R.string.tai_bench_battery, percent);
    }
}
