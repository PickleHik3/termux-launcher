package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * The modifier marks, the locked fill and the press glow as the bar shows them: a page attached to
 * a window and laid out by a real traversal, so each cap has been through the pre-draw pass that
 * scrolls a single-line label into view. A cap built and drawn on its own never goes through that
 * pass, which is how a mark drawn far off the cap once passed its test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class,
    qualifiers = "w411dp-h914dp-420dpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeysPageMarkTest {

    private static final int W = 1080;
    /** One row of 38 dp at 2.625. */
    private static final int H = 100;
    /** The light theme's bar and the ink the activity hands a page on it. */
    private static final int LIGHT_BAR = Color.rgb(0xE8, 0xE9, 0xEE);
    private static final int LIGHT_INK = Color.rgb(0x1B, 0x1B, 0x1F);
    private static final int DARK_BAR = Color.rgb(0x1E, 0x1F, 0x24);
    private static final int DARK_INK = Color.rgb(0xE4, 0xE2, 0xE6);
    /** A pale wallpaper accent, close to the light bar. */
    private static final int ACCENT = Color.rgb(0xC5, 0xCB, 0xE0);

    private static final String ROW =
        "[['CTRL', {key: 'ALT', color: 'primary_container'}, 'SHIFT', 'FN', 'LEFT', 'RIGHT']]";

    private Context context;
    private FrameLayout root;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        context = new ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight);
        root = new FrameLayout(activity);
        activity.setContentView(root);
    }

    private ExtraKeysPageView page(int ink, boolean deck) throws Exception {
        assertEquals(2.625f, context.getResources().getDisplayMetrics().density, 0f);
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(new ExtraKeysInfo(ROW,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES));
        page.setModifiers(new ExtraKeyModifiers());
        // What TermuxActivity.styleExtraKeysPage hands a page.
        page.setButtonTextAllCaps(true);
        page.setButtonTextColor(ink);
        page.refreshKeyStyles();
        page.setKeyPressFeedbackColor(ACCENT);
        page.setKeyPressFeedbackBlurAvailable(false);
        if (deck) {
            page.setDeckLook(true, Color.rgb(0x30, 0x30, 0x34), Color.rgb(0x3A, 0x3A, 0x3E),
                Color.rgb(0x48, 0x48, 0x4C), Color.rgb(0x55, 0x55, 0x5A), Color.rgb(0x22, 0x22, 0x25),
                Color.rgb(0xFF, 0xB3, 0x00), Color.BLACK);
        }
        root.addView(page, new FrameLayout.LayoutParams(W, H));
        // A real traversal: measure, layout and the pre-draw pass.
        ShadowLooper.idleMainLooper();
        assertEquals(W, page.getWidth());
        assertEquals(H, page.getHeight());
        return page;
    }

    private static Bitmap render(ExtraKeysPageView page, int bar) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bar);
        page.draw(canvas);
        return bitmap;
    }

    private static void touch(ExtraKeysPageView page, View cap, int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action,
            cap.getLeft() + cap.getWidth() / 2f, cap.getTop() + cap.getHeight() / 2f, 0);
        page.dispatchTouchEvent(event);
        event.recycle();
    }

    private static int differingIn(Bitmap a, Bitmap b, View cap, int fromY) {
        int n = 0;
        for (int y = Math.max(cap.getTop(), fromY); y < cap.getBottom(); y++) {
            for (int x = cap.getLeft(); x < cap.getRight(); x++) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) n++;
            }
        }
        return n;
    }

    private static int labelBottom(ExtraKeyCapView cap) {
        android.graphics.Paint.FontMetrics m = cap.getPaint().getFontMetrics();
        float baseline = cap.getHeight() / 2f - (m.ascent + m.descent) / 2f;
        return cap.getTop() + Math.round(baseline + m.descent);
    }

    /** Tap arms, hold locks, both through the page's own touch handling, on the given key. */
    private void checkMarks(int ink, int bar, boolean deck, String key)
        throws Exception {
        ExtraKeysPageView page = page(ink, deck);
        ExtraKeyCapView cap = page.capForKey(key);
        int under = labelBottom(cap);
        Bitmap off = render(page, bar);

        touch(page, cap, MotionEvent.ACTION_DOWN);
        touch(page, cap, MotionEvent.ACTION_UP);
        assertEquals(ExtraKeyModifiers.State.ARMED, cap.modifierState());
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS); // the glow fades out
        Bitmap armed = render(page, bar);
        int dot = differingIn(off, armed, cap, under);
        assertTrue(key + ": armed shows a dot under the label (" + dot + " px, cap scrollX "
            + cap.getScrollX() + ")", dot > 30);

        // A tap lets the armed key go; a hold from off locks it.
        touch(page, cap, MotionEvent.ACTION_DOWN);
        touch(page, cap, MotionEvent.ACTION_UP);
        assertEquals(ExtraKeyModifiers.State.OFF, cap.modifierState());
        touch(page, cap, MotionEvent.ACTION_DOWN);
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        touch(page, cap, MotionEvent.ACTION_UP);
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals(ExtraKeyModifiers.State.LOCKED, cap.modifierState());
        Bitmap locked = render(page, bar);

        // A filled cap: a point inside the cap, left of the label, changes; the cell's corner,
        // outside the rounded cap, does not.
        float d = context.getResources().getDisplayMetrics().density;
        int insideX = cap.getLeft() + Math.round(8 * d);
        int midY = cap.getTop() + cap.getHeight() / 2;
        assertTrue(key + ": locked fills the cap",
            off.getPixel(insideX, midY) != locked.getPixel(insideX, midY));
        assertEquals(key + ": the fill is a rounded cap inside the cell",
            off.getPixel(cap.getLeft(), cap.getTop()), locked.getPixel(cap.getLeft(), cap.getTop()));
        int fill = locked.getPixel(insideX, midY);
        int barPx = 0;
        for (int y = under; y < cap.getBottom(); y++) {
            int p = locked.getPixel(cap.getLeft() + cap.getWidth() / 2, y);
            if (p != fill) barPx++;
        }
        assertTrue(key + ": locked shows a bar under the label on its fill", barPx > 0);
        root.removeView(page);
    }

    @Test
    public void aPlainKeyOnTheLightThemeShowsItsMarks() throws Exception {
        checkMarks(LIGHT_INK, LIGHT_BAR, false, "CTRL");
    }

    @Test
    public void aPlainKeyOnTheDarkThemeShowsItsMarks() throws Exception {
        checkMarks(DARK_INK, DARK_BAR, false, "CTRL");
    }

    @Test
    public void aColourRoleKeyShowsItsMarks() throws Exception {
        checkMarks(LIGHT_INK, LIGHT_BAR, false, "ALT");
    }

    @Test
    public void aDeckKeyShowsItsMarks() throws Exception {
        checkMarks(DARK_INK, DARK_BAR, true, "CTRL");
    }

    @Test
    public void aPressedKeyGlowsOnItsCap() throws Exception {
        ExtraKeysPageView page = page(LIGHT_INK, false);
        ExtraKeyCapView cap = page.capForKey("LEFT");
        Bitmap off = render(page, LIGHT_BAR);
        touch(page, cap, MotionEvent.ACTION_DOWN);
        Bitmap pressed = render(page, LIGHT_BAR);
        int glow = differingIn(off, pressed, cap, 0);
        assertTrue("a pressed key glows across its cap (" + glow + " px)",
            glow > cap.getWidth() * cap.getHeight() / 4);
        touch(page, cap, MotionEvent.ACTION_UP);
    }
}
