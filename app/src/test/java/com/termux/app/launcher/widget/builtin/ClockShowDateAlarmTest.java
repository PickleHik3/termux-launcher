package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Clock sheet's Show date and Show next alarm: each hides only its own line, both default on,
 * and the alarm can stand without the date.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockShowDateAlarmTest {
    private static final WidgetLayoutPlan.Measure MEASURE =
        (text, role, numeric, sizeDp) -> text.length() * sizeDp * 0.55f;
    private static final ClockLayoutPlanner.FaceMeasure FACE =
        (widthDp, maxHeightDp) -> Math.min(maxHeightDp, widthDp * 0.3f);
    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public Typeface monoTypeface() { return null; }
    };

    // ----- the settings ---------------------------------------------------------------------

    @Test public void bothDefaultOnAndOnlyAnExplicitOffTurnsOneOff() {
        Bundle none = new Bundle();
        assertTrue(ClockWidgetSettings.showDate(none));
        assertTrue(ClockWidgetSettings.showAlarm(none));
        Bundle date = new Bundle();
        date.putString(ClockWidgetSettings.KEY_SHOW_DATE, ClockWidgetSettings.OFF);
        assertFalse(ClockWidgetSettings.showDate(date));
        assertTrue(ClockWidgetSettings.showAlarm(date));
        Bundle alarm = new Bundle();
        alarm.putString(ClockWidgetSettings.KEY_SHOW_ALARM, ClockWidgetSettings.OFF);
        assertTrue(ClockWidgetSettings.showDate(alarm));
        assertFalse(ClockWidgetSettings.showAlarm(alarm));
        Bundle on = new Bundle();
        on.putString(ClockWidgetSettings.KEY_SHOW_DATE, ClockWidgetSettings.ON);
        on.putString(ClockWidgetSettings.KEY_SHOW_ALARM, ClockWidgetSettings.ON);
        assertTrue(ClockWidgetSettings.showDate(on));
        assertTrue(ClockWidgetSettings.showAlarm(on));
    }

    // ----- the plan -------------------------------------------------------------------------

    private static WidgetLayoutPlan plan(BuiltinWidgetSpan span, ClockFace face, boolean date,
                                         boolean alarm) {
        List<String> dates = date ? Arrays.asList("Wed 21 Oct", "21 Oct", "Wed 21")
            : Collections.<String>emptyList();
        return ClockLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f, face, dates,
            alarm ? "Alarm 07:00" : "", new ArrayList<ClockLayoutPlanner.ZoneColumn>(), FACE,
            MEASURE);
    }

    @Test public void eachLineHidesOnlyItself() {
        for (ClockFace face : new ClockFace[] {ClockFace.FLIP, ClockFace.ANALOG}) {
            for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_TWO,
                BuiltinWidgetSpan.FOUR_BY_TWO}) {
                String where = face + " " + span;
                WidgetLayoutPlan both = plan(span, face, true, true);
                assertTrue(where + ": date", both.has(ClockLayoutPlanner.DATE));
                assertTrue(where + ": alarm", both.has(ClockLayoutPlanner.ALARM));
                WidgetLayoutPlan noDate = plan(span, face, false, true);
                assertFalse(where + ": no date", noDate.has(ClockLayoutPlanner.DATE));
                assertTrue(where + ": alarm stays without the date",
                    noDate.has(ClockLayoutPlanner.ALARM));
                assertTrue(where + ": alarm glyph stays", noDate.has(ClockLayoutPlanner.ALARM_GLYPH));
                assertTrue(where + ": the face stays", noDate.has(ClockLayoutPlanner.FACE));
                WidgetLayoutPlan noAlarm = plan(span, face, true, false);
                assertTrue(where + ": date stays", noAlarm.has(ClockLayoutPlanner.DATE));
                assertFalse(where + ": no alarm", noAlarm.has(ClockLayoutPlanner.ALARM));
                assertFalse(where + ": no alarm glyph", noAlarm.has(ClockLayoutPlanner.ALARM_GLYPH));
                WidgetLayoutPlan neither = plan(span, face, false, false);
                assertFalse(where + ": nothing", neither.has(ClockLayoutPlanner.DATE)
                    || neither.has(ClockLayoutPlanner.ALARM));
                assertTrue(where + ": still a face", neither.has(ClockLayoutPlanner.FACE));
            }
        }
    }

    @Test public void theAlarmAloneSitsInsideTheCard() {
        WidgetLayoutPlan alone = plan(BuiltinWidgetSpan.TWO_BY_TWO, ClockFace.FLIP, false, true);
        WidgetLayoutPlan.Part alarm = alone.part(ClockLayoutPlanner.ALARM);
        WidgetLayoutPlan.Part face = alone.part(ClockLayoutPlanner.FACE);
        assertNotNull(alarm);
        assertNotNull(face);
        assertTrue("the alarm " + alarm.box + " is inside " + alone.inset,
            alone.inset.contains(alarm.box));
        assertTrue("the alarm is under the face", alarm.box.top >= face.box.bottom() - 0.01f);
    }

    // ----- through the widget ---------------------------------------------------------------

    @Test public void theWidgetFollowsItsSettings() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(true, planOf(context, null, null).has(ClockLayoutPlanner.DATE));
        assertTrue(planOf(context, null, null).has(ClockLayoutPlanner.ALARM));
        WidgetLayoutPlan noDate = planOf(context, ClockWidgetSettings.OFF, null);
        assertFalse(noDate.has(ClockLayoutPlanner.DATE));
        assertTrue(noDate.has(ClockLayoutPlanner.ALARM));
        WidgetLayoutPlan noAlarm = planOf(context, null, ClockWidgetSettings.OFF);
        assertTrue(noAlarm.has(ClockLayoutPlanner.DATE));
        assertFalse(noAlarm.has(ClockLayoutPlanner.ALARM));
    }

    @NonNull private WidgetLayoutPlan planOf(@NonNull Context context, String date, String alarm) {
        Bundle config = new Bundle();
        config.putString(ClockWidgetSettings.KEY_FACE, ClockFace.FLIP.key);
        if (date != null) config.putString(ClockWidgetSettings.KEY_SHOW_DATE, date);
        if (alarm != null) config.putString(ClockWidgetSettings.KEY_SHOW_ALARM, alarm);
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_TWO;
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        ClockWidgetView view = new ClockWidgetView(context, services, style);
        view.setPreview(true);
        view.setCells(span.columns, span.rows);
        view.bind(config);
        float density = context.getResources().getDisplayMetrics().density;
        int w = (int) Math.ceil(span.widthDp * density);
        int h = (int) Math.ceil(span.heightDp * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        WidgetLayoutPlan plan = view.plan();
        services.destroy();
        assertNotNull(plan);
        return plan;
    }
}
