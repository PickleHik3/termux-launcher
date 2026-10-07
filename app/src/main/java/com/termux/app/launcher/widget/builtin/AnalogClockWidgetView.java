package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.termux.app.launcher.widget.builtin.ClockWidgetSupport.wide;

/**
 * An analog clock, with up to two more cities beside the phone's own at the wide spans.
 *
 * <p>The face is redrawn once a second for its second hand, only while the widget is started
 * and actually visible; the labels change once a minute, on the shared minute tick or when the
 * second hand crosses a minute, whichever comes first.</p>
 */
public class AnalogClockWidgetView extends BuiltinWidgetView {
    /** What a 4×2 zone's time ranks at, less its place in the list; the phone's own never goes. */
    private static final int ZONE_RANK = 90;
    /** The offset lines under the 4×2 times, the first thing left out of a short card. */
    private static final int OFFSET_RANK = 10;

    static final String KEY_ZONE1 = "zone1";
    static final String KEY_ZONE2 = "zone2";
    static final String DEFAULT_ZONE1 = "Europe/London";
    static final String DEFAULT_ZONE2 = "Asia/Kolkata";

    /** One world clock line: a city, its time and how it differs from the phone's. */
    private static final class ZoneRow {
        /** Null for the phone's own zone, which can change under us. */
        @Nullable final ZoneId zone;
        @NonNull final TextView city;
        @NonNull final ClockNumeralView time;
        @NonNull final TextView offset;
        final boolean marker;

        ZoneRow(@Nullable ZoneId zone, @NonNull TextView city, @NonNull ClockNumeralView time,
                @NonNull TextView offset, boolean marker) {
            this.zone = zone; this.city = city; this.time = time; this.offset = offset;
            this.marker = marker;
        }
    }

    private final List<AnalogClockFaceView> faces = new ArrayList<>();
    private final List<ZoneRow> zoneRows = new ArrayList<>();
    @Nullable private AnalogClockFaceView datedFace;
    @Nullable private TextView captionCity;
    @Nullable private ClockNumeralView localTime;
    @Nullable private TextView localDate;

    private final ClockWidgetSupport.SecondTicker ticker;
    private final BuiltinWidgetServices.TickListener minuteTick = this::refresh;
    private boolean shown;
    private long shownMinute = Long.MIN_VALUE;

    public AnalogClockWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.CLOCK_ANALOG, services, style);
        ticker = new ClockWidgetSupport.SecondTicker(services.main(), this::onSecond);
    }

    @NonNull @Override public List<ConfigField> configFields() {
        Context context = getContext();
        return Arrays.asList(
            new ConfigField(KEY_ZONE1, context.getString(R.string.bw_clocks_zone1_label),
                ConfigField.Type.TEXT, DEFAULT_ZONE1),
            new ConfigField(KEY_ZONE2, context.getString(R.string.bw_clocks_zone2_label),
                ConfigField.Type.TEXT, DEFAULT_ZONE2));
    }

    // ----- layouts --------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        faces.clear();
        zoneRows.clear();
        datedFace = null;
        captionCity = null;
        localTime = null;
        localDate = null;
        shownMinute = Long.MIN_VALUE;

        switch (span) {
            case ONE_BY_ONE: buildOneByOne(frame, ui); break;
            case TWO_BY_ONE: buildTwoByOne(frame, ui); break;
            case TWO_BY_TWO: buildTwoByTwo(frame, ui); break;
            case FOUR_BY_ONE: buildFourByOne(frame, ui); break;
            case FOUR_BY_TWO: buildFourByTwo(frame, ui); break;
        }
        refresh();
    }

    /**
     * Air between the face and the card: the face measures to the room it is given, and a cell
     * narrower than the design (a 2x2 on a six-column grid) would otherwise put the dial's rim on
     * the card's own, under its corners.
     */
    private static final int FACE_AIR_DP = 10;

    private void buildOneByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        frame.addView(aired(face(70, 0.12f), ui), fill());
    }

    /** {@code face} centred inside a frame that keeps {@link #FACE_AIR_DP} on every side. */
    @NonNull private FrameLayout aired(@NonNull AnalogClockFaceView face, @NonNull BuiltinWidgetUi ui) {
        FrameLayout room = new FrameLayout(getContext());
        inset(room, FACE_AIR_DP, FACE_AIR_DP, FACE_AIR_DP, FACE_AIR_DP, ui);
        room.addView(face, centred());
        return room;
    }

    private void buildTwoByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        localTime = wide(numeral(22f, -0.02f, Gravity.START));
        localDate = ui.text("", 11f, style().sansMedium, style().onSurfaceVariant);
        LinearLayout column = ui.column(4, caption(ui), localTime, localDate);
        LinearLayout root = ui.row(12, face(68, 0.12f), BuiltinWidgetUi.flex(column));
        inset(root, 12, 0, 12, 0, ui);
        frame.addView(root, fill());
    }

    private void buildTwoByTwo(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        AnalogClockFaceView face = face(160, 0.08f);
        datedFace = face;
        frame.addView(aired(face, ui), fill());
    }

    private void buildFourByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        List<ZoneId> zones = zones();
        View[] columns = new View[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            TextView city = ui.text("", 11f, style().sansBold, style().onSurfaceVariant);
            ClockNumeralView time = wide(numeral(20f, 0f, Gravity.START));
            TextView offset = ui.mono("", 10.5f);
            zoneRows.add(new ZoneRow(zones.get(i), city, time, offset, false));
            columns[i] = BuiltinWidgetUi.flex(ui.column(3, city, time, offset));
        }
        LinearLayout grid = ui.row(8, columns);
        grid.setGravity(Gravity.TOP);
        LinearLayout root = ui.row(14, face(68, 0.12f), BuiltinWidgetUi.flex(grid));
        inset(root, 14, 0, 14, 0, ui);
        frame.addView(root, fill());
    }

    private void buildFourByTwo(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        List<ZoneId> zones = zones();
        // Three zones with their offset lines are about 35dp taller than the 115dp minimum
        // gives: the offset lines go, all together, and the three times stay.
        FitStack column = FitStack.column(getContext());
        for (int i = 0; i < zones.size(); i++) {
            TextView city = ui.text("", 12f, style().sansBold, style().onSurface);
            TextView offset = ui.mono("", 10.5f);
            ClockNumeralView time = numeral(22f, 0f, Gravity.END);
            zoneRows.add(new ZoneRow(zones.get(i), city, time, offset, true));
            // Only the city and the time share a baseline, like the design: the time sits on the
            // city's line. The offset is its own line below, so no nested column's baseline can
            // push it out of the row.
            LinearLayout head = wide(ui.row(8, BuiltinWidgetUi.flex(city), time));
            head.setGravity(Gravity.TOP);
            head.setBaselineAligned(true);
            column.add(head, i == 0 ? FitStack.ESSENTIAL : ZONE_RANK - i, i == 0 ? 0 : ui.dp(12));
            column.add(wide(offset), OFFSET_RANK, ui.dp(2));
        }
        LinearLayout root = ui.row(20, face(160, 0.08f), BuiltinWidgetUi.flex(column));
        inset(root, 16, 0, 16, 0, ui);
        frame.addView(root, fill());
    }

    /** The phone's zone (as null, resolved at each refresh), then the two chosen in settings. */
    @NonNull private List<ZoneId> zones() {
        List<ZoneId> zones = new ArrayList<>(3);
        zones.add(null);
        zones.add(configuredZone(KEY_ZONE1, DEFAULT_ZONE1));
        zones.add(configuredZone(KEY_ZONE2, DEFAULT_ZONE2));
        return zones;
    }

    @NonNull private ZoneId configuredZone(@NonNull String key, @NonNull String fallback) {
        ZoneId zone = ClockWidgetFormats.resolveZone(configString(key, fallback));
        return zone != null ? zone : ZoneId.of(fallback);
    }

    /** The 2×1 caption: the phone's city (Tonal), or a clock glyph and the city as a path (Pane). */
    @NonNull private View caption(@NonNull BuiltinWidgetUi ui) {
        if (!style().isPane()) {
            TextView label = ui.caption("");
            captionCity = label;
            return label;
        }
        TextView label = ui.text("", 10.5f, style().monoMedium, style().onSurfaceVariant);
        captionCity = label;
        return ui.row(5, ui.glyph(ClockWidgetSupport.GLYPH_CLOCK, 10.5f, style().primary),
            BuiltinWidgetUi.flex(label));
    }

    @NonNull private AnalogClockFaceView face(int sizeDp, float dotShare) {
        AnalogClockFaceView face = new AnalogClockFaceView(getContext(), style(), sizeDp, dotShare);
        faces.add(face);
        return face;
    }

    @NonNull private ClockNumeralView numeral(float sp, float letterSpacing, int gravity) {
        ClockNumeralView view = new ClockNumeralView(getContext(), style().numerals, sp,
            style().onSurface, letterSpacing);
        view.setTailStyle(Math.max(10f, sp * 0.4f), style().onSurfaceVariant);
        view.setHorizontalGravity(gravity);
        return view;
    }

    @NonNull private static FrameLayout.LayoutParams centred() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }

    @NonNull private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    }

    // ----- time -----------------------------------------------------------------------------

    /** Everything the clock shows, for the current minute. */
    private void refresh() {
        ZonedDateTime now = ClockWidgetSupport.now(isPreview());
        shownMinute = now.toEpochSecond() / 60L;
        boolean is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        Context context = getContext();
        LocalTime local = now.toLocalTime();
        setHands(now);

        String deviceZone = now.getZone().getId();
        if (datedFace != null) {
            datedFace.setLabel(ClockWidgetSupport.date("EEEdd", now).toUpperCase(Locale.getDefault()));
        }
        if (captionCity != null) {
            captionCity.setText(style().isPane() ? ClockWidgetFormats.paneCity(deviceZone)
                : ClockWidgetFormats.cityName(deviceZone));
        }
        String localText = ClockWidgetFormats.hoursMinutes(local, is24);
        String localMarker = marker(local, is24);
        if (localTime != null) localTime.setText(localText, localMarker);
        if (localDate != null) localDate.setText(ClockWidgetSupport.date("EEEdMMM", now));

        StringBuilder description = new StringBuilder(context.getString(
            R.string.bw_clocks_description, (localText + localMarker).trim(),
            ClockWidgetSupport.date("EEEEdMMMM", now)));
        String today = context.getString(R.string.bw_clocks_today);
        String tomorrow = context.getString(R.string.bw_clocks_offset_tomorrow);
        String yesterday = context.getString(R.string.bw_clocks_offset_yesterday);
        for (ZoneRow row : zoneRows) {
            ZoneId zone = row.zone != null ? row.zone : now.getZone();
            LocalTime there = now.withZoneSameInstant(zone).toLocalTime();
            String city = ClockWidgetFormats.cityName(zone.getId());
            String time = ClockWidgetFormats.hoursMinutes(there, is24);
            row.city.setText(city);
            row.time.setText(time, row.marker ? marker(there, is24) : "");
            row.offset.setText(row.zone == null ? today : ClockWidgetFormats.offsetLabel(
                ClockWidgetFormats.offsetMinutes(now, zone), ClockWidgetFormats.dayDelta(now, zone),
                today, tomorrow, yesterday));
            if (row.zone != null) {
                description.append(". ").append(context.getString(R.string.bw_clocks_description_zone,
                    city, (time + " " + ClockWidgetFormats.amPm(there, is24)).trim()));
            }
        }
        setContentDescription(description);
    }

    /** " PM" for a 12-hour clock, nothing for a 24-hour one. */
    @NonNull private static String marker(@NonNull LocalTime time, boolean is24) {
        String marker = ClockWidgetFormats.amPm(time, is24);
        return marker.isEmpty() ? "" : " " + marker;
    }

    private void setHands(@NonNull ZonedDateTime now) {
        for (AnalogClockFaceView face : faces) {
            face.setTime(now.getHour(), now.getMinute(), now.getSecond());
        }
    }

    private void onSecond() {
        ZonedDateTime now = ClockWidgetSupport.now(false);
        if (now.toEpochSecond() / 60L != shownMinute) refresh();
        else setHands(now);
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        services.addTickListener(minuteTick);
        shown = isShown() && getWindowVisibility() == View.VISIBLE;
        refresh();
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

    /** The second hand moves only while someone can see it. */
    private void updateTicker() {
        boolean run = isStarted() && shown && !isPreview();
        // Coming back into view: catch the hands (and a missed minute) up before the next second.
        if (run && !ticker.isRunning()) onSecond();
        ticker.setRunning(run);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        ClockWidgetSupport.openClockApp(getContext());
    }
}
