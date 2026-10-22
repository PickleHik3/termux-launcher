package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.statusbar.SystemStatsController;
import com.termux.app.theme.SchemeTone;
import com.termux.privileged.PrivilegedAvailabilitySignal;
import com.termux.privileged.PrivilegedBackendManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.RejectedExecutionException;

/**
 * The phone's own state (ticket #117, which merged the Battery and System widgets): the battery
 * first, then storage, memory and temperature as it grows, and the CPU's history only where the
 * launcher has privileged access. The view draws its layout plan ({@link DeviceLayoutPlanner});
 * the ring, the bolt, the bars and the charts are views in the plan's boxes, fed live without
 * planning again. The plan is made again only when a word or a value on it changes.
 *
 * <p>Colour means something: the ring is in a warning colour only while charging (the done
 * green) or low (the error red), and in the soft ink otherwise. Every bar, the ring's arc and the
 * charts are toned until they hold 3:1 against what they sit on ({@link #onTrack}). The CPU
 * never shows as a dash: without access, or before its first reading, its history is absent.</p>
 */
public class DeviceWidgetView extends BuiltinWidgetView
        implements BatterySource.Listener, SystemStatsController.Listener {
    /** Below this, off the charger, the battery is low: the ring goes red. */
    static final int LOW_PERCENT = 20;
    /** The contrast a bar, the ring's arc or a chart keeps against its track: WCAG 1.4.11. */
    static final double GRAPHIC_CONTRAST = 3d;

    private static final long SAMPLE_INTERVAL_MS = 2000L;
    private static final int CPU_HISTORY = 32;
    /** A CPU history older than this when the widget comes back on screen starts over. */
    private static final long HISTORY_FRESH_MS = 10_000L;
    /** The Temp bar runs 0..60 °C. */
    private static final float TEMPERATURE_SPAN_TENTHS = 600f;

    /** The picker's card: the design's sample reading and day. */
    private static final BatterySource.State SAMPLE = new BatterySource.State(
        84, true, false, true, 340, 4200, 18d, 70 * 60_000L);
    private static final int[] SAMPLE_HISTORY = {96, 92, 88, 84, 80, 76, 72, 70, 68, 66, 64, 63, 62,
        61, 60, 58, 56, 52, 48, 44, 52, 62, 71, 84};
    private static final int[] SAMPLE_CPU = {18, 22, 30, 26, 41, 35, 28, 24, 19, 22, 37, 52, 44, 31,
        26, 22, 20, 25, 33, 29, 24, 27, 35, 48, 39, 30, 24, 21, 26, 29, 23, 24};
    private static final BatterySource.State NO_READING = new BatterySource.State(
        -1, false, false, false, Integer.MIN_VALUE, -1, Double.NaN, -1);

    @Nullable private BatterySource battery;
    @Nullable private SystemStatsController stats;
    /** Whether this start holds the stats sampler and the access listener. */
    private boolean systemHeld;
    private long memUsedKb, memTotalKb;
    private long diskUsedBytes = -1L, diskTotalBytes = -1L;
    private int cpuPercent = -1;
    private final int[] cpuHistory = new int[CPU_HISTORY];
    private int cpuCount;
    private long lastSampleAtMs;
    private final PrivilegedAvailabilitySignal.Listener availabilityListener =
        available -> refresh();

    // The views the current plan placed; null or absent where it has none.
    @Nullable private BatteryRingView ring;
    @Nullable private GlyphView bolt;
    private final Map<String, BuiltinWidgetUi.BarView> bars = new HashMap<>();
    @Nullable private SparkBarsView chart;
    @Nullable private SparkBarsView cpuChart;
    @Nullable private TextView cpuValue;
    /** What the plan on screen was made from; see {@link DeviceLayoutPlanner.Content#signature}. */
    @Nullable private String shownSignature;

    public DeviceWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                            @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.DEVICE, services, style);
    }

    // ----- layouts --------------------------------------------------------------------------

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        if (isPreview()) loadSample();
        render(frame, span, planned(span));
    }

    private void render(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                        @NonNull DeviceLayoutPlanner.Content shown) {
        frame.removeAllViews();
        ring = null; bolt = null; chart = null; cpuChart = null; cpuValue = null;
        bars.clear();
        shownSignature = shown.signature();
        float fontScale = WidgetTokens.typeScale(getResources());
        frame.addView(new WidgetPlanHost(getContext(), style(), (widthDp, heightDp, measure) ->
            DeviceLayoutPlanner.plan(span, widthDp, heightDp, fontScale, shown, measure),
            new WidgetPlanHost.ViewSource() {
                @Nullable @Override public View view(@NonNull Part part) {
                    return viewFor(part, fontScale);
                }
                @Override public void onPlanned(@NonNull WidgetLayoutPlan plan) { bindLive(); }
            }), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentDescription(describe());
    }

    @Nullable private View viewFor(@NonNull Part part, float fontScale) {
        Context context = getContext();
        BuiltinWidgetStyle s = style();
        switch (part.id) {
            case "ring": ring = new BatteryRingView(context, s); return ring;
            case "bolt": bolt = new GlyphView(context, s); return bolt;
            case "chart": chart = new SparkBarsView(context, s); return chart;
            case "cpu": cpuChart = new SparkBarsView(context, s); return cpuChart;
            case "cpu.value": {
                TextView value = new TextView(context);
                value.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                    WidgetTokens.sizeDp(WidgetTokens.Role.LABEL, fontScale));
                value.setTypeface(s.monoNumerals() ? s.monoMedium : s.sansMedium);
                if (!s.monoNumerals()) value.setFontFeatureSettings(WidgetTokens.TABULAR_FIGURES);
                value.setTextColor(s.onSurfaceVariant);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                value.setIncludeFontPadding(false);
                value.setMaxLines(1);
                value.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                cpuValue = value;
                return value;
            }
            default:
                if (part.id.endsWith(".bar")) {
                    BuiltinWidgetUi.BarView bar = new BuiltinWidgetUi.BarView(context, s);
                    bar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    bars.put(part.id.substring(0, part.id.length() - ".bar".length()), bar);
                    return bar;
                }
                return null;
        }
    }

    /** What the plan shows at {@code span}, in the app's words. */
    @NonNull private DeviceLayoutPlanner.Content planned(@NonNull BuiltinWidgetSpan span) {
        Context context = getContext();
        BatterySource.State state = batteryState();
        String[] detail = detail(state);
        List<DeviceLayoutPlanner.Reading> readings = new ArrayList<>();
        if (wantsSystem(span)) {
            readings.add(new DeviceLayoutPlanner.Reading("storage", StatGlyphs.DISK,
                context.getString(R.string.bw_device_storage),
                DeviceWidgetFormats.storagePair(diskUsedBytes, diskTotalBytes)));
            readings.add(new DeviceLayoutPlanner.Reading("ram", StatGlyphs.MEMORY,
                context.getString(R.string.bw_device_ram),
                DeviceWidgetFormats.memoryPair(memUsedKb, memTotalKb)));
            readings.add(new DeviceLayoutPlanner.Reading("temp", StatGlyphs.TEMPERATURE,
                context.getString(R.string.bw_device_temp),
                DeviceWidgetFormats.celsiusWhole(state.temperatureTenths)));
        }
        List<String> axis = new ArrayList<>();
        if (span == BuiltinWidgetSpan.FOUR_BY_TWO) {
            long now = System.currentTimeMillis();
            axis.addAll(Arrays.asList(DeviceWidgetFormats.hourAxis(
                DeviceWidgetFormats.localHour(now, TimeZone.getDefault()),
                BatteryHistoryStore.SLOTS)));
            axis.add(context.getString(R.string.bw_device_now));
        }
        String lastDay = context.getString(R.string.bw_device_last_day);
        return new DeviceLayoutPlanner.Content(
            state.known() ? state.level + "%" : "", state.known() && onCharger(state),
            detail[0], detail[1], readings,
            context.getString(R.string.bw_device_join,
                context.getString(R.string.bw_device_battery), lastDay), lastDay, axis,
            cpuShown() && cpuCount > 0 ? context.getString(R.string.bw_device_cpu) : null);
    }

    /** The line under the level, and its short form: "Full in 1 h 10 m" and "1 h 10 m". */
    @NonNull private String[] detail(@NonNull BatterySource.State state) {
        Context context = getContext();
        if (!state.known()) return pair(context.getString(R.string.bw_device_no_battery));
        if (state.full) return pair(context.getString(R.string.bw_device_charged));
        if (state.charging) {
            if (state.chargeTimeMs <= 0) return pair(context.getString(R.string.bw_device_charging));
            String left = DeviceWidgetFormats.duration(state.chargeTimeMs);
            return new String[] {context.getString(R.string.bw_device_full_in, left), left};
        }
        if (state.plugged) return pair(context.getString(R.string.bw_device_plugged));
        int drain = BatteryHistoryStore.drainPerHour(levels());
        if (drain > 0) {
            return new String[] {context.getString(R.string.bw_device_drain, drain),
                context.getString(R.string.bw_device_drain_short, drain)};
        }
        if (!Double.isNaN(state.powerWatts)) {
            String watts = DeviceWidgetFormats.watts(state.powerWatts);
            return new String[] {context.getString(R.string.bw_device_using, watts), watts};
        }
        return pair(context.getString(low(state) ? R.string.bw_device_low
            : R.string.bw_device_on_battery));
    }

    @NonNull private static String[] pair(@NonNull String word) { return new String[] {word, word}; }

    /** The plan's views take the live values: no planning, only paint. */
    private void bindLive() {
        BuiltinWidgetStyle s = style();
        BatterySource.State state = batteryState();
        boolean known = state.known();
        int track = s.containerHigh;
        if (ring != null) {
            ring.set(known ? state.level / 100f : 0f, onTrack(ringAccent(state, s), track),
                known ? String.valueOf(state.level) : "", known && onCharger(state));
        }
        if (bolt != null) bolt.set(StatGlyphs.POWER, onTrack(s.done, s.card));
        @ColorInt int fill = onTrack(s.primary, track);
        setBar("storage", DeviceWidgetFormats.percent(diskUsedBytes, diskTotalBytes) / 100f, fill);
        setBar("ram", DeviceWidgetFormats.percent(memUsedKb, memTotalKb) / 100f, fill);
        setBar("temp", state.temperatureTenths == Integer.MIN_VALUE ? 0f
            : state.temperatureTenths / TEMPERATURE_SPAN_TENTHS, fill);
        @ColorInt int ink = onTrack(s.primary, s.card);
        if (chart != null) {
            int[] levels = levels();
            float[] heights = new float[levels.length];
            for (int i = 0; i < levels.length; i++) heights[i] = levels[i] < 0 ? -1f : levels[i] / 100f;
            chart.setArea(heights, ink);
        }
        if (cpuChart != null) {
            float[] heights = new float[CPU_HISTORY];
            Arrays.fill(heights, -1f);
            int offset = CPU_HISTORY - cpuCount;
            for (int i = 0; i < cpuCount; i++) heights[offset + i] = cpuHistory[i] / 100f;
            cpuChart.set(heights, SparkBarsView.accentTail(CPU_HISTORY, CPU_HISTORY, ink, ink));
        }
        if (cpuValue != null) {
            String text = cpuPercent >= 0 ? cpuPercent + "%" : "";
            if (!text.contentEquals(cpuValue.getText())) cpuValue.setText(text);
        }
    }

    private void setBar(@NonNull String id, float fraction, @ColorInt int fill) {
        BuiltinWidgetUi.BarView bar = bars.get(id);
        if (bar != null) bar.set(Math.max(0f, fraction), fill);
    }

    // ----- colour ---------------------------------------------------------------------------

    /**
     * {@code accent} moved along its own tone until it holds {@link #GRAPHIC_CONTRAST} against
     * {@code track}; unchanged where it already does.
     */
    @ColorInt static int onTrack(@ColorInt int accent, @ColorInt int track) {
        return SchemeTone.contrastTone(ColorUtils.setAlphaComponent(accent, 255),
            ColorUtils.setAlphaComponent(track, 255), GRAPHIC_CONTRAST);
    }

    /** The ring's colour before toning: done while charging, error while low, else soft. */
    @ColorInt static int ringAccent(@NonNull BatterySource.State state,
                                    @NonNull BuiltinWidgetStyle style) {
        if (state.known() && onCharger(state)) return style.done;
        if (low(state)) return style.error;
        return style.onSurfaceVariant;
    }

    /** Charging, or full and still on the charger. */
    static boolean onCharger(@NonNull BatterySource.State state) {
        return state.charging || state.full;
    }

    static boolean low(@NonNull BatterySource.State state) {
        return state.known() && !state.plugged && state.level < LOW_PERCENT;
    }

    // ----- data -----------------------------------------------------------------------------

    /** Storage, memory and the CPU are read only where the span shows them. */
    private static boolean wantsSystem(@NonNull BuiltinWidgetSpan span) {
        return span != BuiltinWidgetSpan.ONE_BY_ONE && span != BuiltinWidgetSpan.TWO_BY_ONE;
    }

    private boolean cpuShown() {
        return isPreview() || PrivilegedBackendManager.getInstance().isPrivilegedAvailableForUi();
    }

    @Override protected void onStart() {
        battery = BatterySource.get(getContext());
        battery.acquire(this);
        if (wantsSystem(span())) {
            systemHeld = true;
            PrivilegedBackendManager.getInstance().addAvailabilityListener(availabilityListener);
            if (SystemClock.elapsedRealtime() - lastSampleAtMs > HISTORY_FRESH_MS) cpuCount = 0;
            stats = services.acquireStats(this, SAMPLE_INTERVAL_MS);
            readStorage();
        }
        refresh();
    }

    @Override protected void onStop() {
        if (battery != null) battery.release(this);
        battery = null;
        if (systemHeld) {
            systemHeld = false;
            PrivilegedBackendManager.getInstance().removeAvailabilityListener(availabilityListener);
            services.releaseStats(this);
            stats = null;
        }
    }

    @Override public void onBatteryChanged(@NonNull BatterySource.State state) {
        if (isStarted()) refresh();
    }

    @Override public void onStatsUpdated(@NonNull SystemStatsController.Stats value) {
        if (!isStarted()) return;
        memUsedKb = value.memUsedKb;
        memTotalKb = value.memTotalKb;
        cpuPercent = value.cpuPercent;
        if (value.cpuPercent >= 0) {
            push(value.cpuPercent);
            lastSampleAtMs = SystemClock.elapsedRealtime();
        }
        refresh();
    }

    @Override protected void onTap() {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // No battery screen on this build; the widget stays as it is.
        }
    }

    /** Plans again when a word or a value on the card changed, else only repaints the views. */
    private void refresh() {
        if (!isStarted()) return;
        DeviceLayoutPlanner.Content next = planned(span());
        if (next.signature().equals(shownSignature)) {
            bindLive();
            setContentDescription(describe());
        } else {
            render(content(), span(), next);
        }
    }

    private void push(int percent) {
        if (cpuCount < CPU_HISTORY) {
            cpuHistory[cpuCount++] = percent;
        } else {
            System.arraycopy(cpuHistory, 1, cpuHistory, 0, CPU_HISTORY - 1);
            cpuHistory[CPU_HISTORY - 1] = percent;
        }
    }

    /** The data partition, off the main thread. */
    private void readStorage() {
        try {
            services.io().execute(() -> {
                long total = -1L, used = -1L;
                try {
                    StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                    total = fs.getTotalBytes();
                    used = Math.max(0L, total - fs.getAvailableBytes());
                } catch (RuntimeException ignored) {
                    // Unreadable: the Storage bar keeps its label and an empty track.
                }
                long finalTotal = total, finalUsed = used;
                services.main().post(() -> {
                    if (!isStarted()) return;
                    diskTotalBytes = finalTotal;
                    diskUsedBytes = finalUsed;
                    refresh();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The page is closing.
        }
    }

    /** The picker's card: the design's sample readings. */
    private void loadSample() {
        memTotalKb = 8L * 1024 * 1024;
        memUsedKb = Math.round(3.1d * 1024 * 1024);
        diskTotalBytes = 128_000_000_000L;
        diskUsedBytes = 41_000_000_000L;
        cpuPercent = SAMPLE_CPU[CPU_HISTORY - 1];
        System.arraycopy(SAMPLE_CPU, 0, cpuHistory, 0, CPU_HISTORY);
        cpuCount = CPU_HISTORY;
    }

    @NonNull private BatterySource.State batteryState() {
        if (isPreview()) return SAMPLE;
        BatterySource.State latest = BatterySource.get(getContext()).latest();
        return latest == null ? NO_READING : latest;
    }

    /** The last day hour by hour, oldest first. */
    @NonNull private int[] levels() {
        if (isPreview()) return SAMPLE_HISTORY;
        return BatterySource.get(getContext()).history().levels(System.currentTimeMillis());
    }

    // ----- description ----------------------------------------------------------------------

    @NonNull private String describe() {
        Context context = getContext();
        BatterySource.State state = batteryState();
        String separator = context.getString(R.string.bw_device_desc_separator);
        List<String> parts = new ArrayList<>();
        if (!state.known()) {
            parts.add(context.getString(R.string.bw_device_desc_battery_unknown));
        } else {
            parts.add(context.getString(R.string.bw_device_desc_battery, state.level));
            if (state.full) {
                parts.add(context.getString(R.string.bw_device_desc_charged));
            } else if (state.charging) {
                parts.add(context.getString(R.string.bw_device_desc_charging));
                if (state.chargeTimeMs > 0) {
                    int minutes = DeviceWidgetFormats.minutesRoundedUp(state.chargeTimeMs);
                    parts.add(context.getResources().getQuantityString(
                        R.plurals.bw_device_desc_full_in, minutes, minutes));
                }
            } else if (state.plugged) {
                parts.add(context.getString(R.string.bw_device_desc_plugged));
            } else {
                parts.add(context.getString(R.string.bw_device_desc_discharging));
            }
        }
        if (wantsSystem(span())) {
            int disk = DeviceWidgetFormats.percent(diskUsedBytes, diskTotalBytes);
            int ram = DeviceWidgetFormats.percent(memUsedKb, memTotalKb);
            if (disk >= 0) parts.add(context.getString(R.string.bw_device_desc_storage, disk));
            if (ram >= 0) parts.add(context.getString(R.string.bw_device_desc_memory, ram));
            if (state.temperatureTenths != Integer.MIN_VALUE) {
                parts.add(context.getString(R.string.bw_device_desc_degrees,
                    Math.round(state.temperatureTenths / 10f)));
            }
            if (span() == BuiltinWidgetSpan.FOUR_BY_TWO && cpuShown() && cpuPercent >= 0) {
                parts.add(context.getString(R.string.bw_device_desc_cpu, cpuPercent));
            }
        }
        return String.join(separator, parts);
    }

    // ----- the bolt -------------------------------------------------------------------------

    /** A Nerd glyph fit to its box, centred, in one colour: the charging bolt. */
    static final class GlyphView extends View {
        private static final float PROBE_PX = 100f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect bounds = new Rect();
        @NonNull private String glyph = "";

        GlyphView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
            super(context);
            if (style.nerd != null) paint.setTypeface(style.nerd);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void set(@NonNull String glyph, @ColorInt int color) {
            this.glyph = glyph;
            paint.setColor(color);
            invalidate();
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            if (glyph.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
            paint.setTextSize(PROBE_PX);
            paint.getTextBounds(glyph, 0, glyph.length(), bounds);
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            float scale = Math.min(getWidth() / (float) bounds.width(),
                getHeight() / (float) bounds.height());
            paint.setTextSize(PROBE_PX * scale);
            paint.getTextBounds(glyph, 0, glyph.length(), bounds);
            canvas.drawText(glyph, getWidth() / 2f - (bounds.left + bounds.right) / 2f,
                getHeight() / 2f - (bounds.top + bounds.bottom) / 2f, paint);
        }
    }
}
