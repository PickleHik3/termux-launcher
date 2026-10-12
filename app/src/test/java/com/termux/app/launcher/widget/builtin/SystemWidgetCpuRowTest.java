package com.termux.app.launcher.widget.builtin;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The System widget without privileged access: every span is built without its CPU row, and what
 * is left (memory, temperature, storage where the span has it) is laid out whole inside the card.
 * The privileged backend is never initialized here, so it reads as unavailable, as on a phone
 * before Shizuku or su has answered.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SystemWidgetCpuRowTest {
    private static final float DENSITY = 2.625f;
    /** A cell's pitch on the home grid, and the gutter a card leaves of it (as the fill test). */
    private static final int CELL_DP = 92;
    private static final int GUTTER_DP = 4;

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
    }

    @Test public void withoutAccessNoSpanShowsTheCpuAndTheRestFits() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            BuiltinWidgetView view = laidOut(span, false);
            String where = span + " without access";
            assertEquals(where + ": the size picks the span", span, view.span());
            assertNull(where + ": no CPU row", shown(view, R.string.bw_device_cpu));
            for (int label : new int[] {R.string.bw_device_ram, R.string.bw_device_temperature}) {
                View glyph = shown(view, label);
                assertNotNull(where + ": " + context.getString(label) + " shown", glyph);
                assertInside(where + ": " + context.getString(label), view.content(), glyph);
            }
        }
    }

    @Test public void thePickersCardStillShowsTheCpu() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            assertNotNull(span + " preview shows the CPU",
                shown(laidOut(span, true), R.string.bw_device_cpu));
        }
    }

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetSpan span, boolean preview) {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Direction.values()[0], false, null);
        BuiltinWidgetView view = BuiltinWidgetFactory.create(context, BuiltinWidgetKind.SYSTEM,
            services, style);
        view.setPreview(preview);
        view.setCells(span.columns, span.rows);
        view.bind(null);
        int w = Math.round((span.columns * CELL_DP - GUTTER_DP) * DENSITY);
        int h = Math.round((span.rows * CELL_DP - GUTTER_DP) * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        services.destroy();
        return view;
    }

    /** The attached, laid-out glyph named {@code label}, or null when the layout has none. */
    @Nullable private View shown(@NonNull View view, int label) {
        String name = context.getString(label);
        if (name.contentEquals(String.valueOf(view.getContentDescription()))
            && view.getWidth() > 0 && view.getHeight() > 0) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = shown(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    private static void assertInside(@NonNull String what, @NonNull View content,
                                     @NonNull View child) {
        int left = 0, top = 0;
        for (View at = child; at != content; at = (View) at.getParent()) {
            left += at.getLeft();
            top += at.getTop();
        }
        assertTrue(what + " starts inside the card", left >= 0 && top >= 0);
        assertTrue(what + " ends inside the card",
            left + child.getWidth() <= content.getWidth()
                && top + child.getHeight() <= content.getHeight());
    }
}
