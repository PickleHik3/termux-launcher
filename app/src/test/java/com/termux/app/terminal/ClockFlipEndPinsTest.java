package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The flip card's end pins (back by decision, 2026-10-25): two small tabs on the rim at the ends
 * of the split line, sized with the card, and nothing drawn across the digits. The geometry is
 * held here, and the pixels of the status bar's Full Flip; the Clock widget's are probed by
 * {@code ClockWidgetFlipPinsTest} with {@link #checkEndPins}.
 */
@RunWith(RobolectricTestRunner.class)
// Native graphics: real pixels to probe.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockFlipEndPinsTest {
    private static final float DENSITY = 2.625f;
    /** 2026-07-23 04:05:06 UTC. */
    private static final long WALL = 1_784_779_506_000L;
    private static final long FRAME = 10_000L;
    /** A channel that moves by more than this is a pixel the pins painted. */
    private static final int DIFF = 6;
    /** Antialiasing may touch the pixel row or column around a pin's rect, no further. */
    private static final float FRINGE = 1.5f;

    private static final ClockFaceRenderer.Palette DARK = new ClockFaceRenderer.Palette(
        0xFFE6E1E5, 0xFF958DA5, 0xFF6750A4, 0xFF1C1B1F, 0xFF2B2930, 0xFF36343B, 0xFF48464F,
        0xFF49454F, 0xFFCAC4D0);
    private static final ClockFaceRenderer.Palette LIGHT = new ClockFaceRenderer.Palette(
        0xFF1C1B1F, 0xFF625B71, 0xFF6750A4, 0xFFFFFBFE, 0xFFF7F2FA, 0xFFECE6F0, 0xFFE6E0E9,
        0xFFCAC4D0, 0xFF49454F);

    private Context mContext;

    @Before public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        Configuration configuration =
            new Configuration(mContext.getResources().getConfiguration());
        configuration.fontScale = 1f;
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
        mContext.getResources().updateConfiguration(configuration, metrics);
        metrics = mContext.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
    }

    // ----- geometry -----------------------------------------------------------------------------

    @Test public void thePinsStandOnTheRimAtTheEndsOfTheSplitLine() {
        RectF card = new RectF(10f, 20f, 36f, 55.5f);
        RectF left = new RectF(), right = new RectF();
        ClockFaceRenderer.flipEndPins(card, .5f, 5f, left, right);
        float height = ClockFaceRenderer.FLIP_PIN_HEIGHT_FRACTION * card.height();
        float width = ClockFaceRenderer.FLIP_PIN_WIDTH_FRACTION * card.height();
        assertEquals(height, left.height(), 1e-4f);
        assertEquals(width, left.width(), 1e-4f);
        assertEquals(card.centerY(), left.centerY(), 1e-4f);
        assertEquals(card.centerY(), right.centerY(), 1e-4f);
        assertEquals("the left pin starts at the card's edge", card.left, left.left, 1e-4f);
        assertEquals("the right pin ends at the card's edge", card.right, right.right, 1e-4f);
        assertEquals(left.width(), right.width(), 1e-4f);
        assertEquals(left.height(), right.height(), 1e-4f);
    }

    @Test public void thePinsGrowWithTheCard() {
        RectF small = new RectF(0f, 0f, 26f, 35.5f), large = new RectF(0f, 0f, 104f, 142f);
        RectF smallLeft = new RectF(), smallRight = new RectF();
        RectF largeLeft = new RectF(), largeRight = new RectF();
        ClockFaceRenderer.flipEndPins(small, .5f, 5f, smallLeft, smallRight);
        ClockFaceRenderer.flipEndPins(large, 2f, 20f, largeLeft, largeRight);
        assertEquals(4f * smallLeft.width(), largeLeft.width(), 1e-4f);
        assertEquals(4f * smallLeft.height(), largeLeft.height(), 1e-4f);
    }

    @Test public void aPinIsNeverNarrowerThanTheRimNorWiderThanTheRoomBesideTheDigit() {
        RectF card = new RectF(0f, 0f, 26f, 35.5f);
        RectF left = new RectF(), right = new RectF();
        // A floored rim (a small face) is wider than the share: the pin reaches past it.
        ClockFaceRenderer.flipEndPins(card, 1.4f, 5f, left, right);
        assertEquals(1.4f, left.width(), 1e-4f);
        // Little room between the edge and the digit: the pin keeps to it.
        ClockFaceRenderer.flipEndPins(card, 1.4f, .4f, left, right);
        assertEquals(.4f, left.width(), 1e-4f);
        assertEquals(.4f, right.width(), 1e-4f);
        // No room at all: no pin.
        ClockFaceRenderer.flipEndPins(card, 1.4f, -2f, left, right);
        assertTrue(left.isEmpty());
        assertTrue(right.isEmpty());
    }

    @Test public void onlyTheFullFlipHasPins() {
        ClockFaceRenderer renderer = barRenderer(DARK);
        RectF left = new RectF(), right = new RectF();
        renderer.setForm(TopPaneClockForm.COMPACT);
        assertFalse(renderer.flipEndPinsPx(900, 170, 0, left, right));
        renderer.setForm(TopPaneClockForm.FULL);
        renderer.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        assertFalse(renderer.flipEndPinsPx(900, 170, 0, left, right));
    }

    // ----- the status bar's Full Flip -----------------------------------------------------------

    @Test public void theBarsFullFlipDrawsItsEndPinsAndNothingElse() {
        for (ClockFaceRenderer.Palette palette : new ClockFaceRenderer.Palette[] {DARK, LIGHT}) {
            for (boolean amPm : new boolean[] {false, true}) {
                ClockFaceRenderer renderer = barRenderer(palette);
                renderer.setUseAmPm(amPm);
                renderer.updateTime(WALL, 1_000L);
                int width = Math.round(340f * DENSITY), height = Math.round(65f * DENSITY);
                renderer.setFitBudget(width, 49f * DENSITY, false);
                checkEndPins(renderer, width, height, "bar " + (palette == DARK ? "dark" : "light")
                    + (amPm ? " 12h" : " 24h"));
            }
        }
    }

    @NonNull private ClockFaceRenderer barRenderer(@NonNull ClockFaceRenderer.Palette palette) {
        ClockFaceRenderer renderer = new ClockFaceRenderer(mContext.getResources());
        renderer.setPalette(palette);
        renderer.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
        renderer.setForm(TopPaneClockForm.FULL);
        renderer.setLazyMode(true);
        renderer.updateTime(WALL, 1_000L);
        return renderer;
    }

    /**
     * Draws the face with its end pins and without them, and holds the difference to the pins:
     * every card shows ink inside both of its pins, at the two ends of its split line, and no
     * pixel outside the pins moves — so nothing is drawn across a digit.
     */
    public static void checkEndPins(@NonNull ClockFaceRenderer renderer, int width, int height,
                                    @NonNull String where) {
        Bitmap with = render(renderer, width, height, true);
        Bitmap without = render(renderer, width, height, false);
        RectF[][] pins = new RectF[4][];
        for (int digit = 0; digit < 4; digit++) {
            RectF left = new RectF(), right = new RectF();
            assertTrue(where + ": card " + digit + " has pins",
                renderer.flipEndPinsPx(width, height, digit, left, right));
            assertFalse(where + ": card " + digit + " pins are not empty", left.isEmpty());
            assertTrue(where + ": card " + digit + " pins lie inside the box " + left + right,
                left.left >= 0f && right.right <= width && left.top >= 0f
                    && left.bottom <= height);
            pins[digit] = new RectF[] {left, right};
        }
        for (int digit = 0; digit < 4; digit++) {
            for (RectF pin : pins[digit]) {
                assertTrue(where + ": card " + digit + " paints its pin at " + pin,
                    differs(with, without, pin));
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!moved(with.getPixel(x, y), without.getPixel(x, y))) continue;
                boolean inPin = false;
                for (RectF[] card : pins) {
                    for (RectF pin : card) {
                        inPin |= x + 1 > pin.left - FRINGE && x < pin.right + FRINGE
                            && y + 1 > pin.top - FRINGE && y < pin.bottom + FRINGE;
                    }
                }
                assertTrue(where + ": the pins painted outside themselves at " + x + "," + y,
                    inPin);
            }
        }
        with.recycle();
        without.recycle();
    }

    @NonNull private static Bitmap render(@NonNull ClockFaceRenderer renderer, int width,
                                          int height, boolean pins) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.setFlipEndPinsDrawn(pins);
        renderer.draw(new Canvas(bitmap), width, height, FRAME);
        renderer.setFlipEndPinsDrawn(true);
        return bitmap;
    }

    private static boolean differs(@NonNull Bitmap with, @NonNull Bitmap without,
                                   @NonNull RectF pin) {
        int left = Math.max(0, (int) Math.floor(pin.left));
        int top = Math.max(0, (int) Math.floor(pin.top));
        int right = Math.min(with.getWidth(), (int) Math.ceil(pin.right));
        int bottom = Math.min(with.getHeight(), (int) Math.ceil(pin.bottom));
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if (moved(with.getPixel(x, y), without.getPixel(x, y))) return true;
            }
        }
        return false;
    }

    private static boolean moved(int a, int b) {
        return Math.abs(Color.alpha(a) - Color.alpha(b)) > DIFF
            || Math.abs(Color.red(a) - Color.red(b)) > DIFF
            || Math.abs(Color.green(a) - Color.green(b)) > DIFF
            || Math.abs(Color.blue(a) - Color.blue(b)) > DIFF;
    }
}
