package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import com.termux.app.launcher.widget.builtin.BuiltinWidgetKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Per-kind minimum spans (#121, #118): the resize policy respects them without forcing a resize. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class BuiltinMinimumSpanTest {
    private static WidgetGridMetrics metrics() {
        return new WidgetGridMetrics(new Rect(0, 0, 400, 600), 0, 0, 0,
            new WidgetGridDefinition(5, 4), false);
    }

    private static WidgetGridMetrics.Span minimum(BuiltinWidgetKind kind, int columns, int rows) {
        return WidgetEditPolicy.builtinMinimum(metrics(), kind.minSpan, 1f, columns, rows);
    }

    @Test public void notificationsAndGlanceStopAtTwoColumnsOnAFullSizedWidget() {
        WidgetGridMetrics.Span notifications = minimum(BuiltinWidgetKind.NOTIFICATIONS, 4, 2);
        assertEquals(2, notifications.columns);
        assertEquals(1, notifications.rows);
        assertEquals(2, minimum(BuiltinWidgetKind.GLANCE, 4, 1).columns);
    }

    @Test public void everyOtherKindStillShrinksToOneByOne() {
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            if (kind == BuiltinWidgetKind.NOTIFICATIONS || kind == BuiltinWidgetKind.GLANCE) continue;
            WidgetGridMetrics.Span span = minimum(kind, 2, 2);
            assertEquals(kind.id, 1, span.columns);
            assertEquals(kind.id, 1, span.rows);
        }
    }

    @Test public void aPlacementBelowTheMinimumKeepsItsSizeAndIsNotForcedUp() {
        WidgetGridMetrics.Span old = minimum(BuiltinWidgetKind.NOTIFICATIONS, 1, 1);
        assertEquals(1, old.columns);
        assertEquals(1, old.rows);
        assertEquals(1, minimum(BuiltinWidgetKind.GLANCE, 1, 1).columns);
    }
}
