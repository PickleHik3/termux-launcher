package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * A card that comes back to a size it has had before is zoomed as it was then. The Appearance
 * editor's Margin slider walks a Home widget's cell through many sizes and Undo brings it back to
 * the first: the zoom search measured the layout at the very sizes it had measured before, the
 * framework answered those measures from its measure cache without measuring the labels inside,
 * every trial read the losses the last size had left behind, and the clock came back at the
 * largest zoom, its date cut short and its numerals clipped, until a restart.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetZoomReturnTest {
    private static final float DENSITY = 2f;
    private static final String LABEL = "Wednesday, September 30";

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** One padded, single-line, ellipsising label: cut short past some zoom. */
    private static final class Labelled extends BuiltinWidgetView {
        TextView label;

        Labelled(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
            super(context, BuiltinWidgetKind.BATTERY, services, style);
        }

        @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                         @NonNull BuiltinWidgetUi ui) {
            label = ui.text(LABEL, 12f, style().sansMedium, style().onSurface);
            FrameLayout column = new FrameLayout(getContext());
            column.setPadding(ui.dp(14), 0, ui.dp(14), 0);
            column.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
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
        style = BuiltinWidgetStyle.resolve(context, BuiltinWidgetStyle.Direction.values()[0],
            false, null);
    }

    private static void settle(@NonNull View view, int widthPx, int heightPx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
    }

    /** The Margin slider's walk: the cell narrows and shortens a step at a time, then Undo. */
    private static void walkAndReturn(@NonNull View view, int widthPx, int heightPx) {
        for (int step = 1; step <= 30; step++) settle(view, widthPx - 2 * step, heightPx - 2 * step);
        settle(view, widthPx, heightPx);
    }

    private static boolean cutShort(@NonNull TextView view) {
        Layout layout = view.getLayout();
        int lines = layout == null ? 0 : layout.getLineCount();
        return lines > 0 && layout.getEllipsisCount(lines - 1) > 0;
    }

    @Test public void aLabelledCardComesBackAtItsZoom() {
        BuiltinWidgetUi ui = new BuiltinWidgetUi(context, style);
        TextView probe = ui.text(LABEL, 12f, style.sansMedium, style.onSurface);
        int natural = (int) Math.ceil(probe.getPaint().measureText(LABEL));
        // Room for the label at zoom 1.4, a card tall enough to ask for 1.85: the search stops
        // between them, where the label still fits.
        int width = Math.round((natural + 2 * 28 + 4) * 1.4f);
        int height = 340;
        assertEquals("a 2x1 card", BuiltinWidgetSpan.TWO_BY_ONE,
            BuiltinWidgetSpan.forSize(width / DENSITY, height / DENSITY, 2, 1));

        Labelled widget = new Labelled(context, services, style);
        widget.setCells(2, 1);
        widget.bind(null);
        settle(widget, width, height);
        float before = widget.content().getScaleX();
        assertTrue("zoomed, not to the most: " + before, before > 1.02f && before < 1.8f);
        assertTrue("the label fits at first", !cutShort(widget.label));

        walkAndReturn(widget, width, height);

        assertEquals("the zoom it had at this size", before, widget.content().getScaleX(), 0.001f);
        assertTrue("the label is not cut short on return", !cutShort(widget.label));
    }

    @Test public void theDigitalClockComesBackAtItsZoom() {
        int width = 380, height = 298;
        DigitalClockWidgetView clock = new DigitalClockWidgetView(context, services, style);
        clock.setCells(2, 1);
        clock.bind(null);
        settle(clock, width, height);
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, clock.span());
        float before = clock.content().getScaleX();

        DigitalClockWidgetView fresh = new DigitalClockWidgetView(context, services, style);
        fresh.setCells(2, 1);
        fresh.bind(null);
        settle(fresh, width, height);
        assertEquals("a fresh clock agrees", before, fresh.content().getScaleX(), 0.001f);

        walkAndReturn(clock, width, height);

        assertEquals("the zoom it had at this size", before, clock.content().getScaleX(), 0.001f);
    }
}
