package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * A measure only measures. The Home column measures the widget grid twice a pass while the status
 * bar overlays Home: at the bar's height first, then at its full height. A widget that rebuilt
 * from its measure flipped to the 1×1 and back every pass and restarted its live sources each
 * time, a new receiver in the system server per pass, until Android killed the launcher.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetMeasureTest {
    private static final float DENSITY = 2f;

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** A widget that counts its builds and its starts and stops. */
    private static final class Counting extends BuiltinWidgetView {
        int builds, starts, stops;
        BuiltinWidgetSpan lastBuilt;

        Counting(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
            super(context, BuiltinWidgetKind.DEVICE, services, style);
        }

        @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                         @NonNull BuiltinWidgetUi ui) {
            builds++;
            lastBuilt = span;
            TextView label = new TextView(getContext());
            label.setText(span.name());
            frame.addView(label);
        }

        @Override protected void onStart() { starts++; }
        @Override protected void onStop() { stops++; }

        void reset() { builds = 0; starts = 0; stops = 0; }
    }

    private Context context;
    private BuiltinWidgetServices services;
    private BuiltinWidgetStyle style;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 320;
        services = new BuiltinWidgetServices(context, HOST);
        style = BuiltinWidgetStyle.resolve(context, BuiltinWidgetStyle.Numerals.values()[0],
            false, null);
    }

    private static int px(int dp) { return Math.round(dp * DENSITY); }

    private static void measure(@NonNull View view, int widthPx, int heightPx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
    }

    /** A 4×2 widget, attached to a window and laid out once at its card's size. */
    @NonNull private Counting attachedAndSettled(int widthPx, int heightPx) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout holder = new FrameLayout(activity);
        activity.setContentView(holder);
        Counting widget = new Counting(activity, services, style);
        widget.setCells(4, 2);
        widget.bind(null);
        holder.addView(widget, new FrameLayout.LayoutParams(widthPx, heightPx));
        assertTrue("attached", widget.isAttachedToWindow());
        measure(widget, widthPx, heightPx);
        widget.layout(0, 0, widthPx, heightPx);
        return widget;
    }

    @Test public void measuresAtAThrowAwaySizeNeitherRebuildNorRestart() {
        int width = px(380), full = px(200), barHigh = px(28);
        assertEquals("the full card is the 4x2", BuiltinWidgetSpan.FOUR_BY_TWO,
            BuiltinWidgetSpan.forSize(380, 200, 4, 2));
        assertNotEquals("the bar-high measure asks for another bucket",
            BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(380, 28, 4, 2));

        Counting widget = attachedAndSettled(width, full);
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, widget.span());
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, widget.lastBuilt);
        assertTrue("running", widget.isStarted());
        widget.reset();

        for (int pass = 0; pass < 120; pass++) {
            measure(widget, width, barHigh);
            measure(widget, width, full);
            widget.layout(0, 0, width, full);
        }
        assertEquals("no rebuild across 120 passes", 0, widget.builds);
        assertEquals("sources never restarted", 0, widget.starts);
        assertEquals("sources never stopped", 0, widget.stops);
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, widget.span());
    }

    @Test public void theFirstLayoutBuildsTheMeasuredBucketAndStartsOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout holder = new FrameLayout(activity);
        activity.setContentView(holder);
        Counting widget = new Counting(activity, services, style);
        widget.setCells(4, 2);
        widget.bind(null);
        assertEquals("bound at the smallest", BuiltinWidgetSpan.ONE_BY_ONE, widget.lastBuilt);
        int width = px(380), full = px(200);
        holder.addView(widget, new FrameLayout.LayoutParams(width, full));
        assertEquals("started on attach", 1, widget.starts);

        measure(widget, width, full);
        assertEquals("a measure builds nothing", 1, widget.builds);
        widget.layout(0, 0, width, full);
        assertEquals("built for its card at layout", 2, widget.builds);
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, widget.lastBuilt);
        assertEquals("the content is measured for the new layout", width,
            widget.content().getMeasuredWidth() * widget.content().getScaleX(), 2f);
        assertTrue(widget.isStarted());
        assertEquals("one stop for the one rebuild", 1, widget.stops);
    }

    @Test public void aRealResizeRebuildsOnceAtLayout() {
        int width = px(380), full = px(200), oneRow = px(100);
        Counting widget = attachedAndSettled(width, full);
        widget.reset();
        measure(widget, width, oneRow);
        widget.layout(0, 0, width, oneRow);
        for (int pass = 0; pass < 10; pass++) {
            measure(widget, width, oneRow);
            widget.layout(0, 0, width, oneRow);
        }
        assertEquals(BuiltinWidgetSpan.forSize(380, 100, 4, 2), widget.span());
        assertEquals("one rebuild for the new size", 1, widget.builds);
        assertEquals(1, widget.starts);
        assertEquals(1, widget.stops);
    }

    @Test public void detachStopsAndAttachStartsOnce() {
        int width = px(380), full = px(200);
        Counting widget = attachedAndSettled(width, full);
        widget.reset();
        FrameLayout holder = (FrameLayout) widget.getParent();
        holder.removeView(widget);
        assertEquals(1, widget.stops);
        holder.addView(widget, new FrameLayout.LayoutParams(width, full));
        for (int pass = 0; pass < 10; pass++) {
            measure(widget, width, px(28));
            measure(widget, width, full);
            widget.layout(0, 0, width, full);
        }
        assertEquals(1, widget.starts);
        assertEquals(0, widget.builds);
    }
}
