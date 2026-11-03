package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * Media at pong sizes; a card taller than its bucket's design is filled, not banded: the cards here are the 4×5 grid's
 * on a 411dp-wide phone, whose rows run from about 92dp to 128dp tall as the status bar and the
 * keyboard come and go, while each column stays about 92dp wide.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MediaCardAtPongSizeTest {
    private static final float DENSITY = 2.625f;

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

    private static String dump(View v, String indent, float d) {
        StringBuilder out = new StringBuilder();
        out.append('\n').append(indent).append(v.getClass().getSimpleName()).append(" [")
            .append(v.getLeft() / d).append(',').append(v.getTop() / d).append(' ')
            .append(v.getWidth() / d).append('x').append(v.getHeight() / d).append("] vis=")
            .append(v.getVisibility());
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) out.append(dump(g.getChildAt(i), indent + "  ", d));
        }
        return out.toString();
    }

    private void checkCard(int columns, int rows, int w, int h, int insetDp) {
        BuiltinWidgetView view = laidOut(BuiltinWidgetKind.MEDIA, columns, rows, w, h);
        // A second pass at the same size, as any later requestLayout (a tick, a rebind) causes.
        relayout(view);
        String tree = columns + "x" + rows + " " + w + "x" + h + dump(view, "", DENSITY);
        View art = shownArt(view);
        assertNotNull("art " + tree, art);
        float inset = insetDp * DENSITY;
        int[] at = new int[2];
        float left = 0, top = 0;
        for (View p = art; p != view; p = (View) p.getParent()) { left += p.getLeft(); top += p.getTop(); }
        assertTrue("art inside the inset " + left / DENSITY + "," + top / DENSITY + tree,
            left >= inset - 1 && top >= inset - 1
                && left + art.getWidth() <= view.getWidth() - inset + 1
                && top + art.getHeight() <= view.getHeight() - inset + 1);
        int controls = find(view, "ControlButton").size();
        if (rows == 2) assertEquals("three controls " + tree, 3, controls);
    }

    private static void relayout(BuiltinWidgetView view) {
        int w = view.getWidth(), h = view.getHeight();
        for (View host : find(view, "WidgetPlanHost")) host.requestLayout();
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    @Test public void largeCardAtPongSize() { checkCard(4, 2, 376, 208, 16); }

    @Test public void shortCardAtPongSize() { checkCard(3, 2, 255, 160, 16); }

    @Test public void oneRowCardKeepsItsInsetAfterASecondPass() { checkCard(3, 1, 255, 160, 12); }

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetKind kind, int columns,
                                               int rows, int widthDp, int heightDp) {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.values()[0], false, null);
        BuiltinWidgetView view = BuiltinWidgetFactory.create(context, kind, services, style);
        view.setPreview(true);
        view.setCells(columns, rows);
        view.bind(null);
        int w = Math.round(widthDp * DENSITY);
        int h = Math.round(heightDp * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        services.destroy();
        return view;
    }

    /** The art well that is attached and drawn, or null when the layout left it out. */
    @Nullable private static View shownArt(@NonNull ViewGroup root) {
        for (View art : find(root, "ArtFrame")) {
            if (art.getParent() != null && art.getWidth() > 0 && art.getHeight() > 0) return art;
        }
        return null;
    }

    @NonNull private static List<View> find(@NonNull ViewGroup root, @NonNull String simpleName) {
        List<View> out = new java.util.ArrayList<>();
        collect(root, simpleName, out);
        return out;
    }

    private static void collect(@NonNull ViewGroup parent, @NonNull String simpleName,
                                @NonNull List<View> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            if (child.getClass().getSimpleName().equals(simpleName)) out.add(child);
            if (child instanceof ViewGroup) collect((ViewGroup) child, simpleName, out);
        }
    }
}
