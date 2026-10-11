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
 * A card taller than its bucket's design is filled, not banded: the cards here are the 4×5 grid's
 * on a 411dp-wide phone, whose rows run from about 92dp to 128dp tall as the status bar and the
 * keyboard come and go, while each column stays about 92dp wide.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetFillTest {
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

    @Test public void oneRowCardsTallerThanTheDesignAreZoomedToFill() {
        for (BuiltinWidgetKind kind : new BuiltinWidgetKind[] {
            BuiltinWidgetKind.SYSTEM, BuiltinWidgetKind.CLOCK_DIGITAL}) {
            for (int columns : new int[] {2, 4}) {
                BuiltinWidgetView view = laidOut(kind, columns, 1, columns * 92 - 4, 118);
                View content = view.content();
                String where = kind + " " + columns + "x1 at 118dp";
                assertTrue(where + " is zoomed: " + content.getScaleY(),
                    content.getScaleY() > 1.2f);
                assertEquals(where + " fills the card's height", view.getHeight(),
                    content.getHeight() * content.getScaleY(), 2f);
                assertEquals(where + " fills the card's width", view.getWidth(),
                    content.getWidth() * content.getScaleX(), 2f);
            }
        }
    }

    @Test public void aCardNoTallerThanTheDesignIsNotZoomed() {
        BuiltinWidgetView view = laidOut(BuiltinWidgetKind.SYSTEM, 2, 1, 184, 92);
        assertEquals(1f, view.content().getScaleY(), 0f);
        assertEquals(view.getHeight(), view.content().getHeight());
    }

    @Test public void theFourByTwoArtStaysInATallCard() {
        // 246dp is the 4x2 card with the status bar compact: square to that height, the art was
        // wider than the row could give it beside the deck, and it was left out.
        for (int height : new int[] {180, 220, 246, 300}) {
            BuiltinWidgetView view = laidOut(BuiltinWidgetKind.MEDIA, 4, 2, 366, height);
            View art = shownArt(view);
            assertNotNull("4x2 art at 366x" + height + "dp", art);
            assertEquals("4x2 art at 366x" + height + "dp keeps the card's height",
                view.getHeight() - 2 * Math.round(12 * DENSITY), art.getHeight(), 2);
        }
    }

    @Test public void theOneRowArtGrowsWithTheCard() {
        for (int columns : new int[] {2, 4}) {
            BuiltinWidgetView design = laidOut(BuiltinWidgetKind.MEDIA, columns, 1,
                columns * 92 - 4, 92);
            BuiltinWidgetView tall = laidOut(BuiltinWidgetKind.MEDIA, columns, 1,
                columns * 92 - 4, 118);
            View designArt = shownArt(design);
            View tallArt = shownArt(tall);
            assertNotNull(columns + "x1 art at the design height", designArt);
            assertNotNull(columns + "x1 art at 118dp", tallArt);
            assertEquals(columns + "x1 art at 118dp keeps 12dp above and below",
                tall.getHeight() - 2 * Math.round(12 * DENSITY), tallArt.getHeight(), 2);
            assertTrue(columns + "x1 art grows with the card",
                tallArt.getHeight() > designArt.getHeight());
        }
    }

    @Test public void theTwoByOneKeepsBothControlsBesideATallArt() {
        BuiltinWidgetView view = laidOut(BuiltinWidgetKind.MEDIA, 2, 1, 180, 118);
        int controls = 0;
        for (View button : find(view, "ControlButton")) {
            if (button.getParent() != null && button.getWidth() > 0) controls++;
        }
        assertEquals("play and next both shown", 2, controls);
    }

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetKind kind, int columns,
                                               int rows, int widthDp, int heightDp) {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Direction.values()[0], false, null);
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
