package com.termux.app.launcher.widget.builtin;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Clock (ticket #112, spec #101): one widget on eight clock faces — the station dial, the
 * Libron numerals, and the status bar's six through the shared renderer. It grows by span: the
 * time at 1×1, the date from 2×1, the next alarm from 2×2. The face, Minimal's weight, the seconds
 * and 24-hour time are set in its gear; a new Clock starts on the bar's face and 12/24-hour
 * setting ({@link ClockWidgetSettings#initial}), and the two are independent after that. The old
 * Analog and Digital clocks open as it ({@link BuiltinWidgetAliases}).
 *
 * <p>The face repaints on the minute, and on the second only while Seconds is on and the widget is
 * seen; a digit change animates on the minute alone. The date and the alarm are the widget tokens'
 * parts of its layout plan ({@link ClockLayoutPlanner}), rebuilt only when their text changes. A
 * tap opens the clock app.</p>
 */
public class ClockWidgetView extends BuiltinWidgetView {
    private final ClockWidgetSupport.SecondTicker ticker;
    private final BuiltinWidgetServices.TickListener minuteTick = this::onMinute;
    @Nullable private ClockFaceView faceView;
    @Nullable private WidgetPlanView planView;
    /** The date and the alarm the current layout was planned with. */
    @NonNull private String shownDate = "";
    @NonNull private String shownAlarm = "";
    /** The zone columns the current layout was planned with, flattened to one string. */
    @NonNull private String shownZones = "";
    private boolean shown;

    public ClockWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                           @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.CLOCK, services, style);
        ticker = new ClockWidgetSupport.SecondTicker(services.main(), this::onSecond);
    }

    /** Bare is this widget's own choice, off by default (its Glass row). */
    @Override protected boolean asksBare() { return bareChosen(); }

    // ----- settings -------------------------------------------------------------------------

    @NonNull private Bundle settings() { return config(); }

    @NonNull ClockFace face() { return ClockWidgetSettings.face(settings()); }

    private boolean seconds() { return ClockWidgetSettings.seconds(settings()); }

    private boolean is24() {
        return ClockWidgetSettings.is24Hour(settings(), DateFormat.is24HourFormat(getContext()));
    }

    /**
     * The gear: the faces as a strip of live previews, Minimal's weight (only while Minimal is
     * the face), Seconds, 24-hour time and the Time zones row (two extra zones, shown at 4×1
     * and 4×2).
     */
    @NonNull @Override public List<ConfigField> configFields() {
        Context context = getContext();
        List<ConfigField> fields = new ArrayList<>();
        List<ConfigField.Option> faces = new ArrayList<>();
        for (ClockFace face : ClockFace.values()) {
            faces.add(new ConfigField.Option(face.key, context.getString(face.label)));
        }
        fields.add(ConfigField.choice(ClockWidgetSettings.KEY_FACE,
            context.getString(R.string.bw_clock_face), face().key, faces,
            (tileContext, value) -> previewTile(tileContext, value)));
        List<ConfigField.Option> weights = new ArrayList<>();
        weights.add(new ConfigField.Option(ClockWidgetSettings.WEIGHT_THIN,
            context.getString(R.string.bw_clock_weight_thin)));
        weights.add(new ConfigField.Option(ClockWidgetSettings.WEIGHT_MEDIUM,
            context.getString(R.string.bw_clock_weight_medium)));
        fields.add(ConfigField.choice(ClockWidgetSettings.KEY_WEIGHT,
            context.getString(R.string.bw_clock_weight), ClockWidgetSettings.WEIGHT_THIN,
            weights, null).shownWhen(ClockWidgetSettings.KEY_FACE, ClockFace.MINIMAL.key));
        fields.add(ConfigField.toggle(ClockWidgetSettings.KEY_SECONDS,
            context.getString(R.string.bw_clock_seconds), false));
        fields.add(ConfigField.toggle(ClockWidgetSettings.KEY_24_HOUR,
            context.getString(R.string.bw_clock_24_hour), is24()));
        fields.add(ConfigField.zones(ClockWidgetSettings.KEY_ZONE1,
            ClockWidgetSettings.KEY_ZONE2, context.getString(R.string.bw_clock_zones)));
        return fields;
    }

    /** The look of a gear chip: the widget's numerals on the sheet's own theme and surface. */
    @NonNull BuiltinWidgetStyle chipStyle(@NonNull Context sheetContext) {
        return BuiltinWidgetStyle.resolve(sheetContext, style().numeralsChoice, false,
            services.host().monoTypeface());
    }

    /** One face of the gear's strip, drawn as itself at the sample time. */
    @NonNull private View previewTile(@NonNull Context context, @NonNull String value) {
        ClockFace face = ClockFace.fromKey(value);
        // The chip stands on the sheet's own surface, not on the wallpaper the widget is Bare on:
        // its ink comes from the sheet's theme, so it reads on the chip in either theme.
        ClockFaceView tile = new ClockFaceView(context, chipStyle(context));
        tile.configure(face == null ? ClockWidgetSettings.FALLBACK_FACE : face,
            ClockWidgetSettings.medium(settings()), false, is24(), true, true, sampleNow());
        return tile;
    }

    // ----- layouts --------------------------------------------------------------------------

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        render(frame, span);
    }

    private void render(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span) {
        ZonedDateTime now = now();
        ClockFace face = face();
        boolean is24 = is24();
        ClockFaceView faceView = new ClockFaceView(getContext(), style());
        faceView.configure(face, ClockWidgetSettings.medium(settings()), seconds(), is24,
            span == BuiltinWidgetSpan.ONE_BY_ONE, lazy(), now);
        faceView.setGround(groundSurface());
        String date = ClockWidgetSupport.date("EEEdMMM", now);
        // The date's shorter forms, for a line too narrow for the whole one: never cut.
        List<String> dates = java.util.Arrays.asList(date,
            ClockWidgetSupport.date("dMMM", now), ClockWidgetSupport.date("EEEd", now));
        String alarm = span.isTall() ? alarmText(now, is24) : "";
        List<ClockLayoutPlanner.ZoneColumn> zones = zoneColumns(now, is24, span);
        shownDate = date;
        shownAlarm = alarm;
        shownZones = zonesKey(zones);
        float fontScale = getResources().getConfiguration().fontScale;
        float density = getResources().getDisplayMetrics().density;
        ClockLayoutPlanner.FaceMeasure faceMeasure = (widthDp, maxHeightDp) ->
            faceView.heightPxFor(widthDp * density, maxHeightDp * density) / density;
        WidgetPlanView planView = new WidgetPlanView(getContext(), style(),
            (widthDp, heightDp, measure) -> ClockLayoutPlanner.plan(span, widthDp, heightDp,
                fontScale, face, dates, alarm, zones, faceMeasure, measure));
        ClockCard card = new ClockCard(getContext(), planView, faceView);
        this.faceView = faceView;
        this.planView = planView;
        frame.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        describe(now, date, alarm, zones, is24);
    }

    /** "Clock, 13:40, Tuesday 6 October", with the alarm when it shows. */
    private void describe(@NonNull ZonedDateTime now, @NonNull String date, @NonNull String alarm,
                          @NonNull List<ClockLayoutPlanner.ZoneColumn> zones, boolean is24) {
        String time = ClockWidgetFormats.hoursMinutes(now.toLocalTime(), is24) + " "
            + ClockWidgetFormats.amPm(now.toLocalTime(), is24);
        StringBuilder text = new StringBuilder(getContext().getString(R.string.bw_clocks_description,
            time.trim(), ClockWidgetSupport.date("EEEEdMMMM", now)));
        if (!alarm.isEmpty()) {
            text.append(". ").append(getContext().getString(R.string.bw_clock_alarm, alarm));
        }
        for (ClockLayoutPlanner.ZoneColumn zone : zones) {
            text.append(". ").append(getContext().getString(R.string.bw_clocks_description_zone,
                zone.city, zone.time));
        }
        setContentDescription(text);
    }

    /** The zones to show at {@code span}: none below 4×1, else the set ones, in the 24-hour habit. */
    @NonNull private List<ClockLayoutPlanner.ZoneColumn> zoneColumns(@NonNull ZonedDateTime now,
                                                                     boolean is24,
                                                                     @NonNull BuiltinWidgetSpan span) {
        List<ClockLayoutPlanner.ZoneColumn> columns = new ArrayList<>();
        if (!span.isWide()) return columns;
        Context context = getContext();
        for (ZoneId zone : ClockWidgetSettings.zones(settings())) {
            ZonedDateTime there = now.withZoneSameInstant(zone);
            String time = (ClockWidgetFormats.hoursMinutes(there.toLocalTime(), is24) + " "
                + ClockWidgetFormats.amPm(there.toLocalTime(), is24)).trim();
            String offset = ClockWidgetFormats.offsetLabel(
                ClockWidgetFormats.offsetMinutes(now, zone), ClockWidgetFormats.dayDelta(now, zone),
                context.getString(R.string.bw_clocks_today),
                context.getString(R.string.bw_clocks_offset_tomorrow),
                context.getString(R.string.bw_clocks_offset_yesterday));
            columns.add(new ClockLayoutPlanner.ZoneColumn(
                ClockWidgetFormats.cityName(zone.getId()), time, offset));
        }
        return columns;
    }

    @NonNull private static String zonesKey(@NonNull List<ClockLayoutPlanner.ZoneColumn> zones) {
        StringBuilder key = new StringBuilder();
        for (ClockLayoutPlanner.ZoneColumn zone : zones) {
            key.append(zone.city).append('|').append(zone.time).append('|').append(zone.offset)
                .append(';');
        }
        return key.toString();
    }

    @Override protected void onCardChanged() {
        if (faceView != null) faceView.setGround(groundSurface());
    }

    // ----- time -----------------------------------------------------------------------------

    @NonNull private ZonedDateTime now() {
        return isPreview() ? sampleNow() : ZonedDateTime.now(ZoneId.systemDefault());
    }

    /** The design's sample moment, today at 13:40 in the phone's zone. */
    @NonNull static ZonedDateTime sampleNow() {
        return ClockWidgetSupport.sampleNow();
    }

    private boolean lazy() {
        if (isPreview()) return true;
        TermuxAppSharedPreferences preferences = preferences();
        return preferences != null && preferences.isLazyModeEnabled();
    }

    /** The next alarm the platform holds, as the 2×2 shows it; empty for none. */
    @NonNull private String alarmText(@NonNull ZonedDateTime now, boolean is24) {
        String time;
        if (isPreview()) {
            time = is24 ? "07:00" : "7:00 AM";
        } else {
            time = ClockWidgetSettings.alarmTime(now, nextAlarm(now.getZone()), is24);
        }
        return time.isEmpty() ? "" : getContext().getString(R.string.bw_clock_alarm, time);
    }

    @Nullable private ZonedDateTime nextAlarm(@NonNull ZoneId zone) {
        try {
            AlarmManager alarms = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo next = alarms == null ? null : alarms.getNextAlarmClock();
            return next == null ? null
                : ZonedDateTime.ofInstant(Instant.ofEpochMilli(next.getTriggerTime()), zone);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The minute: the face moves on; the date or the alarm, when theirs changed, re-plan. */
    private void onMinute() {
        if (!isStarted()) return;
        ZonedDateTime now = now();
        String date = ClockWidgetSupport.date("EEEdMMM", now);
        String alarm = span().isTall() ? alarmText(now, is24()) : "";
        List<ClockLayoutPlanner.ZoneColumn> zones = zoneColumns(now, is24(), span());
        if (!date.equals(shownDate) || !alarm.equals(shownAlarm)
            || !zonesKey(zones).equals(shownZones)) {
            FrameLayout frame = content();
            frame.removeAllViews();
            render(frame, span());
            return;
        }
        if (faceView != null) faceView.setTime(now);
        describe(now, date, alarm, zones, is24());
    }

    private void onSecond() {
        if (faceView == null) return;
        ZonedDateTime now = now();
        if (now.getSecond() == 0) onMinute();
        else faceView.setTime(now);
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        services.addTickListener(minuteTick);
        shown = isShown() && getWindowVisibility() == View.VISIBLE;
        onMinute();
        updateTicker();
    }

    @Override protected void onStop() {
        services.removeTickListener(minuteTick);
        ticker.setRunning(false);
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        shown = isVisible;
        updateTicker();
    }

    /** The seconds tick only while Seconds is on and someone can see them. */
    private void updateTicker() {
        boolean run = isStarted() && shown && !isPreview() && seconds();
        if (run && !ticker.isRunning() && faceView != null) faceView.setTime(now());
        ticker.setRunning(run);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        ClockWidgetSupport.openClockApp(getContext());
    }

    // ----- for tests ------------------------------------------------------------------------

    @VisibleForTesting @Nullable ClockFaceView faceView() { return faceView; }

    @VisibleForTesting @Nullable WidgetLayoutPlan plan() {
        return planView == null ? null : planView.plan();
    }

    /**
     * The card: the plan's parts over the whole card, and the face laid out in the plan's face
     * box once the plan is made at the card's size.
     */
    private static final class ClockCard extends FrameLayout {
        @NonNull private final WidgetPlanView planView;
        @NonNull private final ClockFaceView faceView;

        ClockCard(@NonNull Context context, @NonNull WidgetPlanView planView,
                  @NonNull ClockFaceView faceView) {
            super(context);
            this.planView = planView;
            this.faceView = faceView;
            addView(planView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            addView(faceView, new LayoutParams(0, 0));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right,
                                          int bottom) {
            int width = right - left, height = bottom - top;
            planView.layout(0, 0, width, height);
            WidgetLayoutPlan plan = planView.plan();
            WidgetLayoutPlan.Part face = plan == null ? null : plan.part(ClockLayoutPlanner.FACE);
            if (face == null) {
                faceView.layout(0, 0, 0, 0);
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            int w = Math.round(face.box.width * density);
            int h = Math.round(face.box.height * density);
            int x = Math.round(face.box.left * density);
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) x = width - x - w;
            int y = Math.round(face.box.top * density);
            faceView.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            faceView.layout(x, y, x + w, y + h);
        }
    }
}
