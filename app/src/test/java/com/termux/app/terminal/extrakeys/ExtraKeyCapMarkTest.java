package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * An armed modifier shows a dot and a locked one a bar on a filled cap, drawn so they read against
 * the bar whatever the accent is: on a teal wallpaper with Material You the accent is teal too, and
 * a mark in the accent alone vanishes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeyCapMarkTest {

    private static final int W = 160;
    private static final int H = 100;
    private static final int TEAL = Color.rgb(0x3F, 0xAE, 0xA8);
    private static final int PALE_INK = Color.rgb(0xF2, 0xF2, 0xF2);
    private static final int DARK_INK = Color.rgb(0x1A, 0x1A, 0x1A);

    private ExtraKeyCapView cap(int ink, Integer accent) throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo("[['CTRL']]",
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        ExtraKeyButton key = info.getMatrix()[0][0];
        ExtraKeyCapView cap = new ExtraKeyCapView(RuntimeEnvironment.getApplication(), key,
            new KeyId(key.getKey(), null, 0));
        cap.setLabel("CTRL", true);
        cap.setLabelColor(ink);
        if (accent != null) cap.setAccent(accent);
        cap.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        cap.layout(0, 0, W, H);
        return cap;
    }

    private static Bitmap render(ExtraKeyCapView cap, ExtraKeyModifiers.State state, int bar) {
        cap.setModifierState(state);
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bar);
        cap.draw(canvas);
        return bitmap;
    }

    private static int differing(Bitmap a, Bitmap b) {
        int n = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) if (a.getPixel(x, y) != b.getPixel(x, y)) n++;
        }
        return n;
    }

    private void checkEveryState(int ink, Integer accent, int bar) throws Exception {
        ExtraKeyCapView cap = cap(ink, accent);
        Bitmap off = render(cap, ExtraKeyModifiers.State.OFF, bar);
        Bitmap armed = render(cap, ExtraKeyModifiers.State.ARMED, bar);
        Bitmap locked = render(cap, ExtraKeyModifiers.State.LOCKED, bar);
        assertTrue("armed draws a dot", differing(off, armed) > 20);
        // The fill covers the cap: pixels well inside it, away from the label and the bar.
        assertNotEquals("locked fills the cap", bar, locked.getPixel(W / 2 + 40, H / 2));
        assertNotEquals("locked fills the cap", bar, locked.getPixel(12, H / 2));
        assertTrue("locked differs from armed", differing(armed, locked) > 500);
    }

    @Test
    public void accentLikeTheBarStillShowsTheMarks() throws Exception {
        checkEveryState(PALE_INK, TEAL, TEAL);
    }

    @Test
    public void anAccentThatContrastsShowsTheMarks() throws Exception {
        checkEveryState(PALE_INK, Color.rgb(0xB0, 0x20, 0x20), TEAL);
        checkEveryState(DARK_INK, Color.rgb(0xFF, 0xEB, 0x3B), Color.rgb(0xF4, 0xF0, 0xE8));
    }

    @Test
    public void theViewsDefaultsShowTheMarks() throws Exception {
        checkEveryState(Color.WHITE, null, Color.rgb(0x20, 0x20, 0x20));
        checkEveryState(DARK_INK, null, Color.rgb(0xF0, 0xF0, 0xF0));
    }

    @Test
    public void lockedBarIsSeenOnItsFill() throws Exception {
        ExtraKeyCapView cap = cap(PALE_INK, TEAL);
        Bitmap locked = render(cap, ExtraKeyModifiers.State.LOCKED, TEAL);
        int fill = locked.getPixel(12, H / 2);
        int distinct = 0;
        for (int y = H / 2; y < H; y++) {
            int p = locked.getPixel(W / 2, y);
            if (p != fill && p != TEAL) distinct++;
        }
        assertTrue("a bar of another colour than the fill sits under the label", distinct > 0);
    }
}
