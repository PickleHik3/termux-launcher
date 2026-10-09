package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * What a frame's border is made of. A lone pane wears the shared rim, so none of its pixels may
 * be the Material active colour; the focused pane of a split wears exactly that colour; a pane
 * asking for the user wears the attention colour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaneRimBorderTest {

    private static final int SIZE = 200;
    private static final float RADIUS = 24f;

    private static Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return activity;
    }

    private static Bitmap draw(FrameLayout frame) {
        Drawable border = frame.getForeground();
        assertNotNull(border);
        border.setBounds(0, 0, SIZE, SIZE);
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        border.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** Whether any drawn pixel carries this colour's RGB. */
    private static boolean hasColour(Bitmap bitmap, int colour) {
        int rgb = colour & 0xFFFFFF;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = bitmap.getPixel(x, y);
                if ((p >>> 24) > 0 && (p & 0xFFFFFF) == rgb) return true;
            }
        }
        return false;
    }

    @Test
    public void aLonePaneFrameNoLongerUsesTheActiveColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        for (boolean glass : new boolean[] {false, true}) {
            FrameLayout frame = new FrameLayout(activity);
            new PaneRim().apply(frame, glass, RADIUS,
                PaneBorderStyle.decide(1, true, false, palette), null);
            assertFalse("glass=" + glass, hasColour(draw(frame), palette.focus));
        }
    }

    @Test
    public void theFocusedPaneOfASplitWearsTheActiveColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        // Plain only: the lit glass rim shades its tint, so its pixels are not exact.
        FrameLayout frame = new FrameLayout(activity);
        new PaneRim().apply(frame, false, RADIUS,
            PaneBorderStyle.decide(2, true, false, palette), null);
        assertTrue(hasColour(draw(frame), palette.focus));
    }

    @Test
    public void aPaneAskingForAttentionWearsTheAttentionColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        FrameLayout frame = new FrameLayout(activity);
        new PaneRim().apply(frame, false, RADIUS,
            PaneBorderStyle.decide(1, true, true, palette), null);
        assertTrue(hasColour(draw(frame), palette.attention));
        assertFalse(palette.attention == palette.focus);
    }

    @Test
    public void clearingTheBorderTakesTheForegroundOff() {
        Activity activity = activity();
        FrameLayout frame = new FrameLayout(activity);
        PaneRim rim = new PaneRim();
        rim.apply(frame, false, RADIUS, PaneBorderStyle.lone(), null);
        assertNotNull(frame.getForeground());
        rim.clear(frame);
        assertEquals(null, frame.getForeground());
    }
}
