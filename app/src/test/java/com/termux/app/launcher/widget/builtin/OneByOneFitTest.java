package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.RectF;
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
 * The 1×1 Battery and analog Clock fit their card: the ring and the dial fill it to the 12dp
 * inset on its shorter side, and the Battery's number stays inside the ring's inner disc at font
 * scales 1.0, 1.3 and 2.0. The device showed the "100" over the ring's lower arc and the dial at
 * about 60 % of an 89×149dp card.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OneByOneFitTest {
    private static final float DENSITY = 2.625f;
    private static final float[] FONT_SCALES = {1f, 1.3f, 2f};
    /** The 1×1 cards: the design, the device's tall cell, a square one and a short wide one. */
    private static final int[][] CARDS = {{88, 92}, {89, 149}, {120, 120}, {110, 80}};

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        fontScale(1f);
    }

    // ----- the arithmetic -------------------------------------------------------------------

    @Test public void theArtFillsTheShorterSideToTheInset() {
        assertEquals(65, OneByOneFit.artSide(89, 149, 12));
        assertEquals(56, OneByOneFit.artSide(110, 80, 12));
        assertEquals(0, OneByOneFit.artSide(20, 149, 12));
    }

    @Test public void aBoxScaledIntoACircleHasItsCornersOnTheRim() {
        float scale = OneByOneFit.scaleIntoCircle(3f, 4f, 50f);
        assertEquals(10f, scale, 1e-4f);
        assertTrue(OneByOneFit.fitsCircle(30f, 40f, 50f));
        assertTrue(!OneByOneFit.fitsCircle(31f, 40f, 50f));
        assertEquals(0f, OneByOneFit.scaleIntoCircle(3f, 4f, 0f), 0f);
    }

    @Test public void theRingsStackFitsTheInnerDiscLessItsClearBand() {
        // "100" at 1px is about 1.6 wide and 0.72 tall in ink; the bolt 0.5 by 0.55; gap 0.06.
        float inner = 53.5f;
        float size = OneByOneFit.ringNumberSize(inner, 1.6f, 0.72f, 0.5f, 0.55f, 0.06f);
        float clear = inner * (1f - 2f * OneByOneFit.RING_CLEAR);
        float width = 1.6f * size, height = (0.72f + 0.06f + 0.55f) * size;
        assertTrue("fits", OneByOneFit.fitsCircle(width, height, clear));
        assertEquals("fills: its corners on the clear band", clear,
            (float) Math.hypot(width, height), 0.01f);
        float bare = OneByOneFit.ringNumberSize(inner, 1.6f, 0.72f, 0f, 0f, 0.06f);
        assertTrue("without the bolt the number may be larger", bare > size);
    }

    // ----- the widgets ----------------------------------------------------------------------

    @Test public void theBatteryNumberStaysInsideItsRingAtEveryFontScale() {
        for (float scale : FONT_SCALES) {
            fontScale(scale);
            for (int[] card : CARDS) {
                String where = "battery 1x1 " + card[0] + "x" + card[1] + "dp at font " + scale;
                BuiltinWidgetView view = laidOut(BuiltinWidgetKind.BATTERY, card[0], card[1]);
                BatteryRingView ring = (BatteryRingView) only(view, BatteryRingView.class);
                assertNotNull(where + ": a ring", ring);
                int side = px(OneByOneFit.artSide(card[0], card[1], OneByOneFit.INSET_DP));
                assertEquals(where + ": the ring fills the card to the inset", side,
                    ring.getWidth(), 2);
                assertEquals(where + ": square", ring.getWidth(), ring.getHeight());
                assertEquals(where + ": not zoomed", 1f, view.content().getScaleY(), 0f);

                for (boolean bolt : new boolean[] {true, false}) {
                    for (String label : new String[] {"100", "79", "5"}) {
                        ring.set(1f, 0xFF00FF00, label, bolt);
                        RectF ink = ring.stackInk();
                        assertTrue(where + ": \"" + label + "\" bolt=" + bolt + " has ink",
                            ink.width() > 0f && ink.height() > 0f);
                        float inner = ring.getWidth() * BatteryRingView.INNER;
                        assertTrue(where + ": \"" + label + "\" bolt=" + bolt + " " + ink
                                + " inside the inner disc " + inner,
                            OneByOneFit.fitsCircle(ink.width(), ink.height(), inner));
                    }
                }
            }
        }
    }

    @Test public void theBatteryNumberIsSizedByTheRingNotTheFontScale() {
        float[] sizes = new float[FONT_SCALES.length];
        for (int i = 0; i < FONT_SCALES.length; i++) {
            fontScale(FONT_SCALES[i]);
            BuiltinWidgetView view = laidOut(BuiltinWidgetKind.BATTERY, 89, 149);
            BatteryRingView ring = (BatteryRingView) only(view, BatteryRingView.class);
            assertNotNull(ring);
            ring.set(1f, 0xFF00FF00, "100", true);
            ring.stackInk();
            sizes[i] = ring.numberTextSizePx();
        }
        assertEquals(sizes[0], sizes[1], 0.01f);
        assertEquals(sizes[0], sizes[2], 0.01f);
        assertTrue("the number is larger than the old 17sp: " + sizes[0] / DENSITY + "dp",
            sizes[0] > 17f * DENSITY);
    }

    @Test public void theAnalogDialFillsTheCardToTheInset() {
        for (float scale : FONT_SCALES) {
            fontScale(scale);
            for (int[] card : CARDS) {
                String where = "analog 1x1 " + card[0] + "x" + card[1] + "dp at font " + scale;
                android.os.Bundle analog = new android.os.Bundle();
                analog.putString(ClockWidgetSettings.KEY_FACE, ClockFace.ANALOG.key);
                BuiltinWidgetView view = laidOut(BuiltinWidgetKind.CLOCK, card[0], card[1],
                    analog);
                View face = only(view, ClockFaceView.class);
                assertNotNull(where + ": a face", face);
                int side = px(OneByOneFit.artSide(card[0], card[1], OneByOneFit.INSET_DP));
                assertEquals(where + ": the dial fills the card to the inset", side,
                    face.getWidth(), 2);
                assertEquals(where + ": round", face.getWidth(), face.getHeight());
                assertEquals(where + ": not zoomed", 1f, view.content().getScaleY(), 0f);
            }
        }
    }

    // ----- helpers --------------------------------------------------------------------------

    private void fontScale(float scale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = scale;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY * scale;
        metrics.densityDpi = 420;
        context.getResources().updateConfiguration(configuration, metrics);
        metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY * scale;
        metrics.densityDpi = 420;
    }

    private static int px(int dp) { return Math.round(dp * DENSITY); }

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetKind kind, int widthDp,
                                               int heightDp) {
        return laidOut(kind, widthDp, heightDp, null);
    }

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetKind kind, int widthDp,
                                               int heightDp, @Nullable android.os.Bundle config) {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.values()[0], false, null);
        BuiltinWidgetView view = BuiltinWidgetFactory.create(context, kind, services, style);
        view.setPreview(true);
        view.setCells(1, 1);
        view.bind(config);
        int w = px(widthDp);
        int h = px(heightDp);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        services.destroy();
        return view;
    }

    @Nullable private static View only(@NonNull ViewGroup root, @NonNull Class<?> type) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (type.isInstance(child)) return child;
            if (child instanceof ViewGroup) {
                View found = only((ViewGroup) child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
