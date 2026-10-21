package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.WidgetAppGroup;
import com.termux.app.launcher.widget.WidgetGridDefinition;
import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.launcher.widget.WidgetProviderItem;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Content;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Item;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Kind;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Max spans (#121): each kind declares the largest span that still adds content, the planners
 * read it from the kind, and past it a widget only gains room; and the picker lists each kind
 * once, with its sizes grouped and a description that starts with a verb.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetMaxSpanTest {
    private static final WidgetLayoutPlan.Measure MEASURE =
        (text, role, numeric, sizeDp) -> text.length() * sizeDp * 0.55f;

    @Test public void everyKindDeclaresAMaxSpanNoSmallerThanItsMinimum() {
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            assertTrue(kind.id, kind.maxSpan.columns >= kind.minSpan.columns);
            assertTrue(kind.id, kind.maxSpan.rows >= kind.minSpan.rows);
            assertFalse(kind.id + " offers a size", kind.sizes().isEmpty());
        }
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetKind.CLOCK.maxSpan);
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetKind.GLANCE.maxSpan);
    }

    @Test public void thePlannersTakeTheirMaxSpanFromTheKind() {
        assertSame(BuiltinWidgetKind.CLOCK.maxSpan, ClockLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.GLANCE.maxSpan, GlanceLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.DEVICE.maxSpan, DeviceLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.CALENDAR.maxSpan, CalendarLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.WEATHER.maxSpan, WeatherLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.MEDIA.maxSpan, MediaLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.TASKS.maxSpan, TasksLayoutPlanner.MAX_SPAN);
        assertSame(BuiltinWidgetKind.NOTIFICATIONS.maxSpan, NotificationsLayoutPlanner.MAX_SPAN);
    }

    @Test public void growingGlancePastItsMaxSpanAddsNoParts() {
        Item item = new Item(Kind.EVENT_SOON, "Design review · 15:00 in 40 min",
            GlanceLayoutPlanner.GLYPH_EVENT, Ink.STRONG, null);
        Content content = new Content("Wed, Oct 21", "21°", "", Ink.SUN, item);
        WidgetLayoutPlan atMax = glance(BuiltinWidgetKind.GLANCE.maxSpan, content);
        WidgetLayoutPlan taller = glance(BuiltinWidgetSpan.FOUR_BY_TWO, content);
        assertEquals(ids(atMax), ids(taller));
        assertSame(BuiltinWidgetKind.GLANCE.maxSpan, taller.maxSpan);
    }

    @NonNull private static WidgetLayoutPlan glance(@NonNull BuiltinWidgetSpan span,
                                                    @NonNull Content content) {
        return GlanceLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f, content, MEASURE);
    }

    @NonNull private static List<String> ids(@NonNull WidgetLayoutPlan plan) {
        List<String> ids = new ArrayList<>();
        for (WidgetLayoutPlan.Part part : plan.parts) ids.add(part.id);
        return ids;
    }

    @Test public void theSizesAreGroupedFromTheMinimumToTheMaximum() {
        assertEquals("2 × 1 · 4 × 1",
            BuiltinWidgetHost.sizesText(BuiltinWidgetKind.GLANCE));
        assertEquals("2 × 1 · 2 × 2 · 4 × 1 · 4 × 2",
            BuiltinWidgetHost.sizesText(BuiltinWidgetKind.NOTIFICATIONS));
        assertEquals("1 × 1 · 2 × 1 · 2 × 2 · 4 × 1 · 4 × 2",
            BuiltinWidgetHost.sizesText(BuiltinWidgetKind.CLOCK));
    }

    @Test public void thePickerListsEachNewKindOnceWithItsSizesAndAVerbDescription() {
        Context context = RuntimeEnvironment.getApplication();
        BuiltinWidgetServices services = new BuiltinWidgetServices(context,
            new BuiltinWidgetServices.Host() {
                @Override public void requestCalendarPermission() { }
                @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                           String title) { return false; }
                @Override public android.graphics.Typeface monoTypeface() { return null; }
            });
        BuiltinWidgetHost host = new BuiltinWidgetHost(context, services,
            () -> BuiltinWidgetStyle.Numerals.SANS);
        try {
            WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, 400, 600), 0, 0, 0,
                new WidgetGridDefinition(5, 4), false);
            WidgetAppGroup group = host.group(metrics);
            Set<String> seen = new HashSet<>();
            List<String> expected = new ArrayList<>();
            for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) expected.add(kind.id);
            assertEquals("Clock, Glance, Device, Calendar, Weather, Media, Notifications, "
                + "Tasks, Notes, Terminal", 10, expected.size());
            for (WidgetProviderItem item : group.providers) {
                assertTrue("once: " + item.builtinKind, seen.add(item.builtinKind));
                BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(item.builtinKind);
                assertNotNull(kind);
                assertEquals(BuiltinWidgetHost.sizesText(kind), item.sizes);
                String first = item.description.split(" ")[0];
                assertTrue(kind.id + " starts with a verb: " + item.description,
                    java.util.Arrays.asList("See", "Keep", "Glance", "Watch", "Control").contains(first));
                assertTrue(item.description.endsWith("."));
            }
            assertEquals(new HashSet<>(expected), seen);
            for (String old : new String[] {"battery", "system", "agenda", "calendar.month",
                "clock.analog", "clock.digital"}) {
                assertFalse("the picker leaves out " + old, seen.contains(old));
            }
        } finally {
            host.destroy();
        }
    }
}
