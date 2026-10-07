package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * The battery: a ring at 1×1, the level and what the charger is doing at 2×1 and 2×2, the phone,
 * its temperature and its power draw side by side at 4×1, and the last 24 hours as a bar chart
 * at 4×2. Live from {@link BatterySource}; a tap opens the system's battery screen.
 */
public class BatteryWidgetView extends BuiltinWidgetView implements BatterySource.Listener {
    private static final String BOLT = "";
    private static final String PLUG = "";
    private static final String BATTERY = "";
    private static final String PHONE = "";
    private static final String THERMOMETER = "";

    /** Below this the level is drawn in the error colour. */
    private static final int LOW_PERCENT = 15;
    /** The shortest the 4×2 history chart is drawn at; a card with less room leaves it out. */
    private static final int CHART_MIN_DP = 20;
    /** The temperature tile's bar runs 0..60°C, the power tile's 0..30 W. */
    private static final float TEMPERATURE_SPAN_TENTHS = 600f;
    private static final float POWER_SPAN_WATTS = 30f;
    /** How many of the newest hours the chart draws in the accent. */
    private static final int ACCENTED_HOURS = 4;

    /** The picker's card: the design's sample reading and day. */
    private static final BatterySource.State SAMPLE = new BatterySource.State(
        79, true, false, true, 314, 4200, 18d, 42 * 60_000L);
    private static final int[] SAMPLE_HISTORY = {96, 92, 88, 84, 80, 76, 72, 70, 68, 66, 64, 63, 62,
        61, 60, 58, 56, 52, 48, 44, 52, 62, 71, 79};
    private static final BatterySource.State NO_READING = new BatterySource.State(
        -1, false, false, false, Integer.MIN_VALUE, -1, Double.NaN, -1);

    // The views the current span shows; null where it has none.
    @Nullable private BatteryRingView ring;
    @Nullable private TextView ringGlyph;
    @Nullable private TextView level;
    @Nullable private TextView levelUnit;
    @Nullable private BuiltinWidgetUi.BarView bar;
    @Nullable private TextView statusGlyph;
    @Nullable private TextView status;
    @Nullable private TextView detail;
    @Nullable private TextView temperature;
    @Nullable private TextView power;
    @Nullable private BuiltinWidgetUi.BarView temperatureBar;
    @Nullable private BuiltinWidgetUi.BarView powerBar;
    @Nullable private TextView readings;
    @Nullable private SparkBarsView chart;
    @NonNull private final List<TextView> axis = new ArrayList<>();
    /** How the status line reads at this span: the state, + the time left, + "full in". */
    private int statusForm;
    private static final int STATUS_PLAIN = 0, STATUS_TIME = 1, STATUS_FULL_IN = 2;

    @Nullable private BatterySource source;

    public BatteryWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                             @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.BATTERY, services, style);
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
        apply(currentState());
    }

    private void clearRefs() {
        ring = null; ringGlyph = null; level = null; levelUnit = null; bar = null;
        statusGlyph = null; status = null; detail = null; temperature = null; power = null;
        temperatureBar = null; powerBar = null; readings = null; chart = null;
        axis.clear();
        statusForm = STATUS_PLAIN;
    }

    @NonNull private View buildOneByOne(@NonNull BuiltinWidgetUi ui) {
        FrameLayout root = new FrameLayout(getContext());
        ring = new BatteryRingView(getContext(), ui.style, 68);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ringParams.gravity = Gravity.CENTER;
        root.addView(ring, ringParams);

        ringGlyph = ui.glyph(BOLT, 13f, ui.style.done);
        level = ui.numeral("", 17f);
        level.setGravity(Gravity.CENTER);
        LinearLayout middle = ui.column(1, ringGlyph, level);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams middleParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        middleParams.gravity = Gravity.CENTER;
        root.addView(middle, middleParams);
        return root;
    }

    @NonNull private View buildTwoByOne(@NonNull BuiltinWidgetUi ui) {
        level = ui.numeral("", 30f);
        LinearLayout statusRow = statusRow(ui, 11.5f, 3);
        LinearLayout top = baseline(ui.row(8, level, BuiltinWidgetUi.flex(statusRow)));
        bar = ui.bar(0f, ui.style.primary, 8);
        detail = ui.mono("", 10.5f);
        // At 56dp the temperature line and bar fill the card: the line under the bar goes.
        FitStack root = FitStack.column(getContext()).centerAlong()
            .add(top, FitStack.ESSENTIAL, 0)
            .add(bar, 20, ui.dp(8))
            .add(detail, 10, ui.dp(8));
        inset(root, 14, 0, 14, 0, ui);
        return root;
    }

    @NonNull private View buildTwoByTwo(@NonNull BuiltinWidgetUi ui) {
        View caption = ui.caption(getContext().getString(R.string.bw_device_battery_caption),
            BATTERY, getContext().getString(R.string.bw_device_battery_path));
        level = ui.numeral("", 46f);
        level.setLetterSpacing(-0.03f);
        levelUnit = ui.text("%", 20f, ui.style.numerals, ui.style.onSurfaceVariant);
        LinearLayout levelRow = baseline(ui.row(6, level, levelUnit));
        bar = ui.bar(0f, ui.style.primary, 8);
        statusForm = STATUS_TIME;
        LinearLayout statusRow = statusRow(ui, 12f, 4);

        temperature = ui.text("", 12f, ui.style.monoMedium, ui.style.onSurface);
        power = ui.text("", 12f, ui.style.monoMedium, ui.style.onSurface);
        LinearLayout footer = ui.row(6,
            BuiltinWidgetUi.flex(ui.column(2,
                ui.mono(getContext().getString(R.string.bw_device_temp), 10.5f), temperature)),
            BuiltinWidgetUi.flex(ui.column(2,
                ui.mono(getContext().getString(R.string.bw_device_power), 10.5f), power)));
        footer.setGravity(Gravity.TOP);

        // At 115dp the footer, the status and the caption are left out, in that order, before
        // the bar; the level stays. The level sits 4dp lower than the 8dp gap under the caption.
        FitStack root = FitStack.column(getContext())
            .add(caption, 30, 0)
            .add(levelRow, FitStack.ESSENTIAL, ui.dp(12))
            .add(bar, 40, ui.dp(8))
            .add(statusRow, 20, ui.dp(8))
            .addElastic(footer, 10, ui.dp(16));
        inset(root, 14, 14, 14, 14, ui);
        return root;
    }

    @NonNull private View buildFourByOne(@NonNull BuiltinWidgetUi ui) {
        level = ui.numeral("", 22f);
        bar = ui.bar(0f, ui.style.primary, 5);
        temperature = ui.numeral("", 22f);
        temperatureBar = ui.bar(0f, ui.style.warm, 5);
        power = ui.numeral("", 22f);
        powerBar = ui.bar(0f, ui.style.primary, 5);
        LinearLayout root = ui.row(12,
            BuiltinWidgetUi.flex(tile(ui, PHONE, R.string.bw_device_phone, level, bar)),
            BuiltinWidgetUi.flex(tile(ui, THERMOMETER, R.string.bw_device_temperature,
                temperature, temperatureBar)),
            BuiltinWidgetUi.flex(tile(ui, BOLT, R.string.bw_device_power, power, powerBar)));
        inset(root, 16, 0, 16, 0, ui);
        return root;
    }

    @NonNull private FitStack tile(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                       int label, @NonNull TextView value,
                                       @NonNull BuiltinWidgetUi.BarView tileBar) {
        TextView name = ui.text(getContext().getString(label), 11.5f, ui.style.sansBold,
            ui.style.onSurfaceVariant);
        LinearLayout head = ui.row(6, ui.glyph(glyph, 11.5f, ui.style.onSurface),
            BuiltinWidgetUi.flex(name));
        // At 56dp a tile is a pixel taller than the card: its bar goes, then its label.
        return FitStack.column(getContext())
            .add(head, 30, 0)
            .add(value, FitStack.ESSENTIAL, ui.dp(6))
            .add(tileBar, 10, ui.dp(6));
    }

    @NonNull private View buildFourByTwo(@NonNull BuiltinWidgetUi ui) {
        level = ui.numeral("", 40f);
        level.setLetterSpacing(-0.03f);
        levelUnit = ui.text("%", 18f, ui.style.numerals, ui.style.onSurfaceVariant);
        statusForm = STATUS_FULL_IN;
        LinearLayout levelRow = baseline(ui.row(4, level, levelUnit));
        LinearLayout statusRow = statusRow(ui, 12f, 4);

        readings = ui.mono("", 10.5f);
        readings.setGravity(Gravity.END);
        TextView window = ui.mono(getContext().getString(R.string.bw_device_last_day), 10.5f);
        window.setGravity(Gravity.END);
        LinearLayout right = ui.column(3, readings, window);
        right.setGravity(Gravity.END);

        LinearLayout header = ui.row(12, BuiltinWidgetUi.flex(levelRow), right);
        header.setBaselineAligned(false);
        header.setGravity(Gravity.TOP);

        chart = new SparkBarsView(getContext(), ui.style);
        chart.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        FitStack axisRow = FitStack.row(getContext()).centerAcross();
        for (int i = 0; i < 5; i++) {
            TextView label = ui.mono("", 10f);
            axis.add(label);
            axisRow.addElastic(label, FitStack.ESSENTIAL, 0);
        }

        // At 115dp the level and the history chart share the card: the hour axis goes first,
        // then the status line under the level; the chart is left out only if it would be
        // under 20dp tall.
        FitStack root = FitStack.column(getContext())
            .add(BuiltinWidgetUi.size(header, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), FitStack.ESSENTIAL, 0)
            .add(BuiltinWidgetUi.size(statusRow, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), 20, ui.dp(6))
            .addFlex(chart, 40, ui.dp(10), ui.dp(CHART_MIN_DP))
            .add(BuiltinWidgetUi.size(axisRow, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), 30, ui.dp(10));
        inset(root, 16, 16, 16, 16, ui);
        return root;
    }

    /** The glyph + state line, in the state's colour; its text is the row's baseline. */
    @NonNull private LinearLayout statusRow(@NonNull BuiltinWidgetUi ui, float sp, int gapDp) {
        statusGlyph = ui.glyph(BOLT, sp, ui.style.done);
        status = ui.text("", sp, ui.style.sansBold, ui.style.done);
        LinearLayout row = ui.row(gapDp, statusGlyph, BuiltinWidgetUi.flex(status));
        row.setBaselineAlignedChildIndex(1);
        return row;
    }

    /** A row whose children sit on one text baseline, the design's {@code align-items:baseline}. */
    @NonNull private static LinearLayout baseline(@NonNull LinearLayout row) {
        row.setBaselineAligned(true);
        row.setGravity(Gravity.TOP | Gravity.START);
        return row;
    }

    // ----- data -----------------------------------------------------------------------------

    @Override protected void onStart() {
        source = BatterySource.get(getContext());
        source.acquire(this);
    }

    @Override protected void onStop() {
        if (source != null) source.release(this);
        source = null;
    }

    @Override public void onBatteryChanged(@NonNull BatterySource.State state) {
        if (!isStarted()) return;
        apply(state);
    }

    @Override protected void onTap() {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // No battery screen on this build; the widget stays as it is.
        }
    }

    @NonNull private BatterySource.State currentState() {
        if (isPreview()) return SAMPLE;
        BatterySource.State latest = BatterySource.get(getContext()).latest();
        return latest == null ? NO_READING : latest;
    }

    private void apply(@NonNull BatterySource.State state) {
        boolean known = state.known();
        boolean powered = state.charging || state.full;
        boolean low = known && !state.plugged && state.level < LOW_PERCENT;
        BuiltinWidgetStyle s = style();
        @ColorInt int accent = powered ? s.done : low ? s.error : s.primary;
        float fraction = known ? state.level / 100f : 0f;
        String levelText = known ? String.valueOf(state.level) : DeviceWidgetFormats.UNKNOWN;
        String temp = DeviceWidgetFormats.celsius(state.temperatureTenths);
        String watts = DeviceWidgetFormats.watts(state.powerWatts);

        if (ring != null) ring.set(fraction, accent);
        if (ringGlyph != null) {
            ringGlyph.setVisibility(powered ? VISIBLE : GONE);
            ringGlyph.setTextColor(accent);
        }
        if (level != null) {
            boolean withPercent = levelUnit == null && ring == null && known;
            level.setText(withPercent ? state.level + "%" : levelText);
        }
        if (levelUnit != null) levelUnit.setVisibility(known ? VISIBLE : GONE);
        if (bar != null) bar.set(fraction, accent);

        if (status != null && statusGlyph != null) applyStatus(state, low);

        if (detail != null) {
            detail.setText(state.charging && state.chargeTimeMs > 0
                ? getContext().getString(R.string.bw_device_full_in,
                    DeviceWidgetFormats.duration(state.chargeTimeMs))
                : getContext().getString(R.string.bw_device_join, temp, watts));
        }
        if (temperature != null) temperature.setText(temp);
        if (power != null) power.setText(watts);
        if (temperatureBar != null) {
            temperatureBar.set(state.temperatureTenths == Integer.MIN_VALUE ? 0f
                : state.temperatureTenths / TEMPERATURE_SPAN_TENTHS, s.warm);
        }
        if (powerBar != null) {
            powerBar.set(Double.isNaN(state.powerWatts) ? 0f
                : (float) (state.powerWatts / POWER_SPAN_WATTS), s.primary);
        }
        if (readings != null) {
            readings.setText(getContext().getString(R.string.bw_device_join, temp, watts));
        }
        if (chart != null) applyHistory(powered ? s.done : s.primary);

        setContentDescription(describe(state));
    }

    private void applyStatus(@NonNull BatterySource.State state, boolean low) {
        BuiltinWidgetStyle s = style();
        String text;
        String glyph = null;
        @ColorInt int color;
        if (!state.known()) {
            text = getContext().getString(R.string.bw_device_no_battery);
            color = s.onSurfaceVariant;
        } else if (state.full) {
            text = getContext().getString(R.string.bw_device_charged);
            glyph = PLUG;
            color = s.done;
        } else if (state.charging) {
            text = getContext().getString(R.string.bw_device_charging);
            glyph = BOLT;
            color = s.done;
            if (state.chargeTimeMs > 0 && statusForm != STATUS_PLAIN) {
                String left = DeviceWidgetFormats.duration(state.chargeTimeMs);
                if (statusForm == STATUS_FULL_IN) {
                    left = getContext().getString(R.string.bw_device_full_in, left);
                }
                text = getContext().getString(R.string.bw_device_join, text, left);
            }
        } else if (state.plugged) {
            text = getContext().getString(R.string.bw_device_plugged);
            glyph = PLUG;
            color = s.onSurfaceVariant;
        } else if (low) {
            text = getContext().getString(R.string.bw_device_low);
            color = s.error;
        } else {
            text = getContext().getString(R.string.bw_device_discharging);
            color = s.onSurfaceVariant;
        }
        TextView label = status, mark = statusGlyph;
        if (label == null || mark == null) return;
        label.setText(text);
        label.setTextColor(color);
        mark.setVisibility(glyph == null ? GONE : VISIBLE);
        if (glyph != null) {
            mark.setText(glyph);
            mark.setTextColor(color);
        }
    }

    private void applyHistory(@ColorInt int accent) {
        SparkBarsView view = chart;
        if (view == null) return;
        long now = System.currentTimeMillis();
        int[] levels = isPreview() ? SAMPLE_HISTORY
            : BatterySource.get(getContext()).history().levels(now);
        float[] heights = new float[levels.length];
        for (int i = 0; i < levels.length; i++) {
            heights[i] = levels[i] < 0 ? -1f : levels[i] / 100f;
        }
        BuiltinWidgetStyle s = style();
        view.set(heights, SparkBarsView.accentTail(levels.length, ACCENTED_HOURS, accent,
            s.containerHigh));
        String[] hours = DeviceWidgetFormats.hourAxis(
            DeviceWidgetFormats.localHour(now, TimeZone.getDefault()), levels.length);
        for (int i = 0; i < axis.size(); i++) {
            axis.get(i).setText(i < hours.length ? hours[i]
                : getContext().getString(R.string.bw_device_now));
        }
    }

    @NonNull private String describe(@NonNull BatterySource.State state) {
        Context context = getContext();
        if (!state.known()) return context.getString(R.string.bw_device_desc_battery_unknown);
        String separator = context.getString(R.string.bw_device_desc_separator);
        StringBuilder out = new StringBuilder(
            context.getString(R.string.bw_device_desc_battery, state.level));
        if (state.full) {
            out.append(separator).append(context.getString(R.string.bw_device_desc_charged));
        } else if (state.charging) {
            out.append(separator).append(context.getString(R.string.bw_device_desc_charging));
            if (state.chargeTimeMs > 0) {
                int minutes = DeviceWidgetFormats.minutesRoundedUp(state.chargeTimeMs);
                out.append(separator).append(context.getResources().getQuantityString(
                    R.plurals.bw_device_desc_full_in, minutes, minutes));
            }
        } else if (state.plugged) {
            out.append(separator).append(context.getString(R.string.bw_device_desc_plugged));
        } else {
            out.append(separator).append(context.getString(R.string.bw_device_desc_discharging));
        }
        return out.toString();
    }
}
