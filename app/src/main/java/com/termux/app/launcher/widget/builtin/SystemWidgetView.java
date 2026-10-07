package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
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
import com.termux.app.statusbar.SystemStatsController;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;

/**
 * CPU, memory, temperature and storage: three readings at 1×1 and 2×1, four at 2×2 and 4×1, and
 * at 4×2 the CPU over its last 32 samples with the rest underneath. CPU and memory come from the
 * shared {@link SystemStatsController}; the temperature is the battery's, the only device
 * temperature an app can read; storage is the data partition.
 */
public class SystemWidgetView extends BuiltinWidgetView
        implements SystemStatsController.Listener, BatterySource.Listener {
    private static final long SAMPLE_INTERVAL_MS = 2000L;
    /** The narrowest a 2×1 bar is drawn at; a row with less room for it shows only label and value. */
    private static final int BAR_MIN_DP = 24;
    private static final int HISTORY = 32;
    /** A history older than this when the widget comes back on screen starts over. */
    private static final long HISTORY_FRESH_MS = 10_000L;
    /** The temperature bar runs 0..60°C. */
    private static final float TEMPERATURE_SPAN_TENTHS = 600f;

    private static final int[] SAMPLE_CPU = {18, 22, 30, 26, 41, 35, 28, 24, 19, 22, 37, 52, 44, 31,
        26, 22, 20, 25, 33, 29, 24, 27, 35, 48, 39, 30, 24, 21, 26, 29, 23, 24};

    /** {@code cpuinfo_max_freq} in kHz across the cores, read once per process; 0 = unreadable. */
    private static volatile long maxFrequencyKhz = -1L;

    // The views the current span shows; null where it has none.
    @Nullable private TextView cpuValue, ramValue, tempValue, diskValue;
    @Nullable private BuiltinWidgetUi.BarView cpuBar, ramBar, tempBar, diskBar;
    @Nullable private TextView uptime;
    @Nullable private TextView headerDetail;
    @Nullable private SparkBarsView chart;
    @Nullable private TextView ramTail, tempTail, diskTail;

    @Nullable private SystemStatsController stats;
    @Nullable private BatterySource battery;
    private int cpuPercent = -1;
    private long memUsedKb, memTotalKb;
    private int cores = Runtime.getRuntime().availableProcessors();
    private int temperatureTenths = Integer.MIN_VALUE;
    private long diskUsedBytes = -1L, diskTotalBytes = -1L;

    private final int[] history = new int[HISTORY];
    private int historyCount;
    private long lastSampleAtMs;

    public SystemWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                            @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.SYSTEM, services, style);
    }

    // ----- layouts --------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        clearRefs();
        View root;
        switch (span) {
            case TWO_BY_ONE: root = buildTwoByOne(ui); break;
            case TWO_BY_TWO: root = buildTwoByTwo(ui); break;
            case FOUR_BY_ONE: root = buildFourByOne(ui); break;
            case FOUR_BY_TWO: root = buildFourByTwo(ui); break;
            case ONE_BY_ONE:
            default: root = buildOneByOne(ui); break;
        }
        frame.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (isPreview()) loadSample();
        render();
    }

    private void clearRefs() {
        cpuValue = ramValue = tempValue = diskValue = null;
        cpuBar = ramBar = tempBar = diskBar = null;
        uptime = headerDetail = null;
        chart = null;
        ramTail = tempTail = diskTail = null;
    }

    @NonNull private View buildOneByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        cpuValue = ui.numeral("", 16f);
        ramValue = ui.numeral("", 16f);
        tempValue = ui.numeral("", 16f);
        LinearLayout root = ui.column(7,
            glyphRow(ui, StatGlyphs.CPU, s.primary, R.string.bw_device_cpu, cpuValue),
            glyphRow(ui, StatGlyphs.MEMORY, s.onTertiaryContainer, R.string.bw_device_ram,
                ramValue),
            glyphRow(ui, StatGlyphs.TEMPERATURE, s.warm, R.string.bw_device_temperature,
                tempValue));
        root.setGravity(Gravity.CENTER_VERTICAL);
        inset(root, 12, 0, 12, 0, ui);
        return root;
    }

    @NonNull private LinearLayout glyphRow(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                           @ColorInt int color, int name,
                                           @NonNull TextView value) {
        value.setGravity(Gravity.END);
        return ui.row(4, ui.statGlyph(glyph, 13f, color, getContext().getString(name)),
            BuiltinWidgetUi.flex(value));
    }

    @NonNull private View buildTwoByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        cpuValue = shortValue(ui);
        ramValue = shortValue(ui);
        tempValue = shortValue(ui);
        cpuBar = ui.bar(0f, s.primary, 5);
        ramBar = ui.bar(0f, s.onTertiaryContainer, 5);
        tempBar = ui.bar(0f, s.warm, 5);
        LinearLayout root = ui.column(8,
            barRow(ui, StatGlyphs.CPU, R.string.bw_device_cpu, cpuBar, cpuValue),
            barRow(ui, StatGlyphs.MEMORY, R.string.bw_device_ram, ramBar, ramValue),
            barRow(ui, StatGlyphs.TEMPERATURE, R.string.bw_device_temperature, tempBar,
                tempValue));
        root.setGravity(Gravity.CENTER_VERTICAL);
        inset(root, 14, 0, 14, 0, ui);
        return root;
    }

    @NonNull private TextView shortValue(@NonNull BuiltinWidgetUi ui) {
        TextView value = ui.text("", 11f, ui.style.monoMedium, ui.style.onSurface);
        value.setGravity(Gravity.END);
        return value;
    }

    /** The 2×1 row: the reading's glyph, the bar taking the middle, a 36dp right-aligned value. */
    @NonNull private View barRow(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, int label,
                                         @NonNull BuiltinWidgetUi.BarView rowBar,
                                         @NonNull TextView value) {
        TextView name = ui.statGlyph(glyph, 11f, ui.style.onSurfaceVariant,
            getContext().getString(label));
        rowBar.setLayoutParams(new LinearLayout.LayoutParams(0, ui.dp(5), 1f));
        BuiltinWidgetUi.size(value, ui.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT);
        // A bar narrower than 24dp would not read as a bar: where the glyph and the value leave
        // less than that between their gaps, the bar is left out of every row.
        FitStack row = FitStack.row(getContext()).centerAcross()
            .add(name, FitStack.ESSENTIAL, 0)
            .addFlex(rowBar, 10, ui.dp(8), ui.dp(BAR_MIN_DP))
            .add(value, FitStack.ESSENTIAL, ui.dp(8));
        return BuiltinWidgetUi.size(row, ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull private View buildTwoByTwo(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        View caption = ui.caption(getContext().getString(R.string.bw_device_system_caption),
            StatGlyphs.CPU, getContext().getString(R.string.bw_device_system_path));
        cpuValue = groupValue(ui);
        ramValue = groupValue(ui);
        tempValue = groupValue(ui);
        diskValue = groupValue(ui);
        cpuBar = ui.bar(0f, s.primary, 5);
        ramBar = ui.bar(0f, s.onTertiaryContainer, 5);
        tempBar = ui.bar(0f, s.warm, 5);
        diskBar = ui.bar(0f, s.done, 5);
        uptime = ui.mono("", 10.5f);
        // At 115dp the four groups, the caption and the uptime are over a card high: the uptime
        // goes first, then the caption, then the groups from the disk up; the groups share any
        // room that is left between them.
        FitStack root = FitStack.column(getContext())
            .add(caption, 20, 0)
            .add(group(ui, StatGlyphs.CPU, R.string.bw_device_cpu, cpuValue, cpuBar),
                FitStack.ESSENTIAL, ui.dp(10))
            .addElastic(group(ui, StatGlyphs.MEMORY, R.string.bw_device_ram, ramValue, ramBar),
                50, 0)
            .addElastic(group(ui, StatGlyphs.TEMPERATURE, R.string.bw_device_temperature,
                tempValue, tempBar), 40, 0)
            .addElastic(group(ui, StatGlyphs.DISK, R.string.bw_device_storage, diskValue,
                diskBar), 30, 0)
            .add(uptime, 10, ui.dp(10));
        inset(root, 14, 14, 14, 14, ui);
        return root;
    }

    @NonNull private TextView groupValue(@NonNull BuiltinWidgetUi ui) {
        TextView value = ui.text("", 11f, ui.style.monoMedium, ui.style.onSurface);
        value.setGravity(Gravity.END);
        return value;
    }

    /** The 2×2 group: glyph and value on one line, the bar under them. */
    @NonNull private View group(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, int label,
                                @NonNull TextView value,
                                @NonNull BuiltinWidgetUi.BarView groupBar) {
        LinearLayout line = ui.row(8, ui.statGlyph(glyph, 11f, ui.style.onSurfaceVariant,
            getContext().getString(label)), BuiltinWidgetUi.flex(value));
        return BuiltinWidgetUi.size(ui.column(4, BuiltinWidgetUi.size(line,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), groupBar),
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull private View buildFourByOne(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        cpuValue = ui.numeral("", 22f);
        ramValue = ui.numeral("", 22f);
        tempValue = ui.numeral("", 22f);
        diskValue = ui.numeral("", 22f);
        cpuBar = ui.bar(0f, s.primary, 4);
        ramBar = ui.bar(0f, s.onTertiaryContainer, 4);
        tempBar = ui.bar(0f, s.warm, 4);
        diskBar = ui.bar(0f, s.done, 4);
        LinearLayout root = ui.row(12,
            BuiltinWidgetUi.flex(tile(ui, StatGlyphs.CPU, s.primary, R.string.bw_device_cpu,
                cpuValue, cpuBar)),
            BuiltinWidgetUi.flex(tile(ui, StatGlyphs.MEMORY, s.onTertiaryContainer,
                R.string.bw_device_ram, ramValue, ramBar)),
            BuiltinWidgetUi.flex(tile(ui, StatGlyphs.TEMPERATURE, s.warm,
                R.string.bw_device_temperature, tempValue, tempBar)),
            BuiltinWidgetUi.flex(tile(ui, StatGlyphs.DISK, s.done, R.string.bw_device_storage,
                diskValue, diskBar)));
        inset(root, 16, 0, 16, 0, ui);
        return root;
    }

    @NonNull private LinearLayout tile(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                       @ColorInt int color, int label, @NonNull TextView value,
                                       @NonNull BuiltinWidgetUi.BarView tileBar) {
        TextView head = ui.statGlyph(glyph, 12f, color, getContext().getString(label));
        return ui.column(6, head, value, tileBar);
    }

    @NonNull private View buildFourByTwo(@NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        // The glyph stands in for the word "CPU" beside a 30sp reading: sized to read as its
        // peer, not as a caption.
        TextView label = ui.statGlyph(StatGlyphs.CPU, 22f, s.onSurfaceVariant,
            getContext().getString(R.string.bw_device_cpu));
        cpuValue = ui.numeral("", 30f);
        LinearLayout left = baseline(ui.row(8, label, cpuValue));
        left.setBaselineAlignedChildIndex(1);
        headerDetail = ui.mono("", 10.5f);
        headerDetail.setGravity(Gravity.END);
        LinearLayout header = baseline(ui.row(12, left, BuiltinWidgetUi.flex(headerDetail)));

        chart = BuiltinWidgetUi.flexTall(new SparkBarsView(getContext(), s));

        ramTail = tailValue(ui);
        tempTail = tailValue(ui);
        diskTail = tailValue(ui);
        LinearLayout tail = ui.row(12,
            BuiltinWidgetUi.flex(tailPair(ui, StatGlyphs.MEMORY, R.string.bw_device_ram, ramTail)),
            BuiltinWidgetUi.flex(tailPair(ui, StatGlyphs.TEMPERATURE,
                R.string.bw_device_temperature, tempTail)),
            BuiltinWidgetUi.flex(tailPair(ui, StatGlyphs.DISK, R.string.bw_device_storage,
                diskTail)));
        tail.setBaselineAligned(false);

        // Both rows span the column: left at WRAP_CONTENT they shrank to their content, and the
        // equal weights then cut the longer RAM and storage readings short beside free space.
        BuiltinWidgetUi.size(header, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        BuiltinWidgetUi.size(tail, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout root = ui.column(10, header, chart, tail);
        inset(root, 16, 16, 16, 16, ui);
        return root;
    }

    @NonNull private TextView tailValue(@NonNull BuiltinWidgetUi ui) {
        TextView value = ui.text("", 13f, ui.style.monoMedium, ui.style.onSurface);
        value.setGravity(Gravity.END);
        return value;
    }

    @NonNull private LinearLayout tailPair(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                           int label, @NonNull TextView value) {
        return baseline(ui.row(6, ui.statGlyph(glyph, 11f, ui.style.onSurfaceVariant,
            getContext().getString(label)), BuiltinWidgetUi.flex(value)));
    }

    /** A row whose children sit on one text baseline, the design's {@code align-items:baseline}. */
    @NonNull private static LinearLayout baseline(@NonNull LinearLayout row) {
        row.setBaselineAligned(true);
        row.setGravity(Gravity.TOP | Gravity.START);
        return row;
    }

    // ----- data -----------------------------------------------------------------------------

    @Override protected void onStart() {
        if (SystemClock.elapsedRealtime() - lastSampleAtMs > HISTORY_FRESH_MS) historyCount = 0;
        stats = services.acquireStats(this, SAMPLE_INTERVAL_MS);
        battery = BatterySource.get(getContext());
        battery.acquire(this);
        readStorage();
    }

    @Override protected void onStop() {
        services.releaseStats(this);
        stats = null;
        if (battery != null) battery.release(this);
        battery = null;
    }

    @Override public void onStatsUpdated(@NonNull SystemStatsController.Stats value) {
        if (!isStarted()) return;
        take(value);
        if (value.cpuPercent >= 0) {
            push(value.cpuPercent);
            lastSampleAtMs = SystemClock.elapsedRealtime();
        }
        render();
    }

    @Override public void onBatteryChanged(@NonNull BatterySource.State state) {
        if (!isStarted()) return;
        if (state.temperatureTenths == temperatureTenths) return;
        temperatureTenths = state.temperatureTenths;
        render();
    }

    /** A tap re-reads what the widget shows; there is no public screen for these numbers. */
    @Override protected void onTap() {
        if (!isStarted()) return;
        SystemStatsController controller = stats;
        if (controller != null) take(controller.latest());
        readStorage();
        render();
    }

    private void take(@NonNull SystemStatsController.Stats value) {
        cpuPercent = value.cpuPercent;
        memUsedKb = value.memUsedKb;
        memTotalKb = value.memTotalKb;
        if (value.cores > 0) cores = value.cores;
    }

    private void push(int percent) {
        if (historyCount < HISTORY) {
            history[historyCount++] = percent;
        } else {
            System.arraycopy(history, 1, history, 0, HISTORY - 1);
            history[HISTORY - 1] = percent;
        }
    }

    /** Storage, and the CPU's top clock the first time, off the main thread. */
    private void readStorage() {
        try {
            services.io().execute(() -> {
                long total = -1L, used = -1L;
                try {
                    StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                    total = fs.getTotalBytes();
                    used = Math.max(0L, total - fs.getAvailableBytes());
                } catch (RuntimeException ignored) {
                    // Unreadable: the storage reading stays a dash.
                }
                if (maxFrequencyKhz < 0) maxFrequencyKhz = readMaxFrequencyKhz(cores);
                long finalTotal = total, finalUsed = used;
                services.main().post(() -> {
                    if (!isStarted()) return;
                    diskTotalBytes = finalTotal;
                    diskUsedBytes = finalUsed;
                    render();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The page is closing.
        }
    }

    /** The fastest core's {@code cpuinfo_max_freq}; 0 when none can be read. */
    private static long readMaxFrequencyKhz(int cores) {
        long best = 0L;
        for (int i = 0; i < Math.max(1, cores); i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line = reader.readLine();
                if (line != null) best = Math.max(best, Long.parseLong(line.trim()));
            } catch (Exception ignored) {
                // Hidden or absent on this core; the others may still answer.
            }
        }
        return best;
    }

    /** The picker's card: the design's sample readings. */
    private void loadSample() {
        cpuPercent = 24;
        memTotalKb = Math.round(7.7d * 1024 * 1024);
        memUsedKb = Math.round(5.1d * 1024 * 1024);
        cores = 8;
        temperatureTenths = 314;
        diskTotalBytes = 256_000_000_000L;
        diskUsedBytes = 156_000_000_000L;
        System.arraycopy(SAMPLE_CPU, 0, history, 0, HISTORY);
        historyCount = HISTORY;
    }

    // ----- render ---------------------------------------------------------------------------

    private void render() {
        BuiltinWidgetStyle s = style();
        int ram = DeviceWidgetFormats.percent(memUsedKb, memTotalKb);
        int disk = DeviceWidgetFormats.percent(diskUsedBytes, diskTotalBytes);
        boolean tempKnown = temperatureTenths != Integer.MIN_VALUE;

        setValue(cpuValue, DeviceWidgetFormats.percentText(cpuPercent));
        setValue(ramValue, DeviceWidgetFormats.percentText(ram));
        setValue(tempValue, DeviceWidgetFormats.degrees(temperatureTenths));
        setValue(diskValue, DeviceWidgetFormats.percentText(disk));
        setBar(cpuBar, cpuPercent, s.primary);
        setBar(ramBar, ram, s.onTertiaryContainer);
        if (tempBar != null) {
            tempBar.set(tempKnown ? temperatureTenths / TEMPERATURE_SPAN_TENTHS : 0f, s.warm);
        }
        setBar(diskBar, disk, s.done);

        String up = getContext().getString(R.string.bw_device_up, DeviceWidgetFormats.uptime(
            isPreview() ? (3L * 24 + 4) * 3_600_000L : SystemClock.elapsedRealtime()));
        setValue(uptime, up);
        if (headerDetail != null) {
            String detail = getContext().getResources().getQuantityString(
                R.plurals.bw_device_cores, cores, cores);
            String ghz = DeviceWidgetFormats.gigahertz(isPreview() ? 1_800_000L : maxFrequencyKhz);
            if (ghz != null) detail = getContext().getString(R.string.bw_device_join, detail, ghz);
            headerDetail.setText(getContext().getString(R.string.bw_device_join, detail, up));
        }
        setValue(ramTail, DeviceWidgetFormats.memoryPair(memUsedKb, memTotalKb));
        setValue(tempTail, DeviceWidgetFormats.degreesTenths(temperatureTenths));
        setValue(diskTail, DeviceWidgetFormats.storagePair(diskUsedBytes, diskTotalBytes));
        if (chart != null) {
            float[] heights = new float[HISTORY];
            Arrays.fill(heights, -1f);
            int offset = HISTORY - historyCount;
            for (int i = 0; i < historyCount; i++) heights[offset + i] = history[i] / 100f;
            chart.set(heights, SparkBarsView.accentTail(HISTORY, 1, s.primary, s.containerHigh));
        }
        setContentDescription(describe(ram));
    }

    private static void setValue(@Nullable TextView view, @NonNull CharSequence text) {
        if (view != null && !text.toString().contentEquals(view.getText())) view.setText(text);
    }

    private static void setBar(@Nullable BuiltinWidgetUi.BarView view, int percent,
                               @ColorInt int color) {
        if (view != null) view.set(percent < 0 ? 0f : percent / 100f, color);
    }

    @NonNull private String describe(int ram) {
        Context context = getContext();
        String separator = context.getString(R.string.bw_device_desc_separator);
        StringBuilder out = new StringBuilder(cpuPercent >= 0
            ? context.getString(R.string.bw_device_desc_cpu, cpuPercent)
            : context.getString(R.string.bw_device_desc_cpu_unknown));
        if (ram >= 0) {
            out.append(separator).append(context.getString(R.string.bw_device_desc_memory, ram));
        }
        if (temperatureTenths != Integer.MIN_VALUE) {
            out.append(separator).append(context.getString(R.string.bw_device_desc_degrees,
                Math.round(temperatureTenths / 10f)));
        }
        return out.toString();
    }
}
