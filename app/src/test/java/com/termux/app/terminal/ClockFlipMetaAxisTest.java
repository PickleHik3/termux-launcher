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
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The full flip's indicator column: the period (AM/PM) and the seconds beside the digits share
 * one vertical centre axis. Measured on the pixels: the ink of each, found as the difference
 * between the face drawn with and without it, must have the same centre x (the developer's
 * screenshot had the period's left edge about 6px left of the seconds').
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockFlipMetaAxisTest {
    private static final float DENSITY = 2.625f;
    /** 2026-07-23 04:05:06 UTC. */
    private static final long WALL = 1_784_779_506_000L;
    private static final long FRAME = 10_000L;
    private static final int INK_ALPHA = 96;
    /** Glyph side bearings differ a little between "PM" and two digits; the bug was ~6px. */
    private static final float TOLERANCE_PX = 1.5f;

    private Context mContext;

    @Before public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
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

    @Test public void theBarsPeriodAndSecondsShareACentreAxis() {
        ClockFaceRenderer renderer = new ClockFaceRenderer(mContext.getResources());
        configure(renderer);
        int width = Math.round(340f * DENSITY), height = Math.round(65f * DENSITY);
        renderer.setFitBudget(width, 49f * DENSITY, false);
        check(renderer, width, height, "bar");
    }

    @Test public void theWidgetsPeriodAndSecondsShareACentreAxis() {
        ClockFaceRenderer renderer = ClockFaceRenderer.forWidget(mContext.getResources());
        configure(renderer);
        int width = Math.round(160f * DENSITY), height = Math.round(70f * DENSITY);
        renderer.setFitBudget(width, height, false);
        check(renderer, width, height, "widget");
    }

    private void configure(@NonNull ClockFaceRenderer renderer) {
        renderer.setPalette(new ClockFaceRenderer.Palette(0xFFE6E1E5, 0xFF958DA5, 0xFF6750A4,
            0xFF1C1B1F, 0xFF2B2930, 0xFF36343B, 0xFF48464F, 0xFF49454F, 0xFFCAC4D0));
        renderer.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
        renderer.setForm(TopPaneClockForm.FULL);
        renderer.setAlignment(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT);
        renderer.setLazyMode(true);
        renderer.setSeconds(true);
        renderer.setPeriodShown(true);
        renderer.setUseAmPm(true);
        renderer.updateTime(WALL, 1_000L);
    }

    private static void check(@NonNull ClockFaceRenderer renderer, int width, int height,
                              @NonNull String where) {
        Bitmap all = render(renderer, width, height);
        renderer.setPeriodShown(false);
        Bitmap noPeriod = render(renderer, width, height);
        renderer.setPeriodShown(true);
        // The seconds are the ink right of the last card (hiding them would refit a widget).
        RectF left = new RectF(), right = new RectF();
        assertTrue(renderer.flipEndPinsPx(width, height, 3, left, right));
        Rect period = inkBounds(all, noPeriod);
        Rect seconds = columnInk(noPeriod, (int) Math.ceil(right.right) + 1,
            Math.round(renderer.fullBandCenterYPx(height) + renderer.fullBandHeightPx(height) / 2f));
        assertFalse(where + ": a period is drawn", period.isEmpty());
        assertFalse(where + ": seconds are drawn", seconds.isEmpty());
        float periodCentre = (period.left + period.right) / 2f;
        float secondsCentre = (seconds.left + seconds.right) / 2f;
        assertEquals(where + ": the period's centre " + periodCentre + " (ink " + period
            + ") is the seconds' " + secondsCentre + " (ink " + seconds + ")", secondsCentre,
            periodCentre, TOLERANCE_PX);
        all.recycle();
        noPeriod.recycle();
    }

    @NonNull private static Bitmap render(@NonNull ClockFaceRenderer renderer, int width,
                                          int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(bitmap), width, height, FRAME);
        return bitmap;
    }

    /** Bounds of the opaque ink from column {@code fromX} on, above row {@code toY}. */
    @NonNull private static Rect columnInk(@NonNull Bitmap bitmap, int fromX, int toY) {
        Rect bounds = new Rect();
        for (int y = 0; y < Math.min(toY, bitmap.getHeight()); y++) {
            for (int x = fromX; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < INK_ALPHA) continue;
                if (bounds.isEmpty()) bounds.set(x, y, x + 1, y + 1);
                else bounds.union(x, y);
            }
        }
        return bounds;
    }

    /** Bounds of the pixels that differ between {@code a} and {@code b}. */
    @NonNull private static Rect inkBounds(@NonNull Bitmap a, @Nullable Bitmap b) {
        Rect bounds = new Rect();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                if (Math.abs(Color.alpha(p) - Color.alpha(q)) < INK_ALPHA
                    && Math.abs(Color.red(p) - Color.red(q)) < INK_ALPHA
                    && Math.abs(Color.green(p) - Color.green(q)) < INK_ALPHA) {
                    continue;
                }
                if (bounds.isEmpty()) bounds.set(x, y, x + 1, y + 1);
                else bounds.union(x, y);
            }
        }
        return bounds;
    }
}
