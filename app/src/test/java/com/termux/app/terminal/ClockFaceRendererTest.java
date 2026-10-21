package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.statusbar.TopPaneClockForm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The status bar's clock draws through {@link ClockFaceRenderer}. Taking the renderer out of
 * {@link TerminalClockWidget} must not move a face by a pixel, so its fit plan at the bar's sizes
 * is held to what the bar's own fit produced before the split.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClockFaceRendererTest {

    /** 2026-07-23 04:05:06 UTC: a fixed time, so the date and minute-track text are fixed too. */
    private static final long WALL = 1_784_779_506_000L;
    private static final String[] FACES = {"flip", "lcd", "minimal", "led", "tape", "slab"};

    /**
     * {face, 12-hour, width budget dp, height budget dp, compact floor, scale, date kept, painted
     * width px}, recorded from {@code TerminalClockWidget}'s private fit plan at commit 280042dd3,
     * before the renderer was taken out, at density 1. 49dp is the bar's 65dp slot less its 8dp air
     * on each side; 30dp is a squeezed slot that drops the date under the compact floor.
     */
    private static final Object[][] BAR_PLANS = {
        {"flip", "false", 340.0f, 49.0f, "false", 0.9521958f, 1.0f, 135.83073f},
        {"flip", "false", 190.0f, 49.0f, "true", 0.9521958f, 1.0f, 135.83073f},
        {"flip", "false", 100.0f, 49.0f, "true", 0.7010165f, 1.0f, 100.0f},
        {"flip", "false", 340.0f, 30.0f, "true", 0.8450704f, 0.0f, 120.54929f},
        {"flip", "false", 340.0f, 30.0f, "false", 0.58297706f, 1.0f, 83.161674f},
        {"flip", "false", 120.0f, 49.0f, "false", 0.8412198f, 1.0f, 120.0f},
        {"flip", "true", 340.0f, 49.0f, "false", 0.9521958f, 1.0f, 135.83073f},
        {"flip", "true", 190.0f, 49.0f, "true", 0.9521958f, 1.0f, 135.83073f},
        {"flip", "true", 100.0f, 49.0f, "true", 0.7010165f, 1.0f, 100.0f},
        {"flip", "true", 340.0f, 30.0f, "true", 0.8450704f, 0.0f, 120.54929f},
        {"flip", "true", 340.0f, 30.0f, "false", 0.58297706f, 1.0f, 83.161674f},
        {"flip", "true", 120.0f, 49.0f, "false", 0.8412198f, 1.0f, 120.0f},
        {"lcd", "false", 340.0f, 49.0f, "false", 0.98f, 1.0f, 111.72f},
        {"lcd", "false", 190.0f, 49.0f, "true", 0.98f, 1.0f, 111.72f},
        {"lcd", "false", 100.0f, 49.0f, "true", 0.877193f, 1.0f, 100.0f},
        {"lcd", "false", 340.0f, 30.0f, "true", 0.88235295f, 0.0f, 100.588234f},
        {"lcd", "false", 340.0f, 30.0f, "false", 0.6f, 1.0f, 68.4f},
        {"lcd", "false", 120.0f, 49.0f, "false", 0.98f, 1.0f, 111.72f},
        {"lcd", "true", 340.0f, 49.0f, "false", 0.98f, 1.0f, 111.72f},
        {"lcd", "true", 190.0f, 49.0f, "true", 0.98f, 1.0f, 111.72f},
        {"lcd", "true", 100.0f, 49.0f, "true", 0.877193f, 1.0f, 100.0f},
        {"lcd", "true", 340.0f, 30.0f, "true", 0.88235295f, 0.0f, 100.588234f},
        {"lcd", "true", 340.0f, 30.0f, "false", 0.6f, 1.0f, 68.4f},
        {"lcd", "true", 120.0f, 49.0f, "false", 0.98f, 1.0f, 111.72f},
        {"minimal", "false", 340.0f, 49.0f, "false", 1.0888889f, 1.0f, 119.77778f},
        {"minimal", "false", 190.0f, 49.0f, "true", 1.0888889f, 1.0f, 119.77778f},
        {"minimal", "false", 100.0f, 49.0f, "true", 0.90909094f, 1.0f, 100.0f},
        {"minimal", "false", 340.0f, 30.0f, "true", 0.9677419f, 0.0f, 106.45161f},
        {"minimal", "false", 340.0f, 30.0f, "false", 0.6666667f, 1.0f, 73.333336f},
        {"minimal", "false", 120.0f, 49.0f, "false", 1.0888889f, 1.0f, 119.77778f},
        {"minimal", "true", 340.0f, 49.0f, "false", 1.0888889f, 1.0f, 119.77778f},
        {"minimal", "true", 190.0f, 49.0f, "true", 1.0888889f, 1.0f, 119.77778f},
        {"minimal", "true", 100.0f, 49.0f, "true", 0.90909094f, 1.0f, 100.0f},
        {"minimal", "true", 340.0f, 30.0f, "true", 0.9677419f, 0.0f, 106.45161f},
        {"minimal", "true", 340.0f, 30.0f, "false", 0.6666667f, 1.0f, 73.333336f},
        {"minimal", "true", 120.0f, 49.0f, "false", 1.0888889f, 1.0f, 119.77778f},
        {"led", "false", 340.0f, 49.0f, "false", 1.1395348f, 1.0f, 140.3907f},
        {"led", "false", 190.0f, 49.0f, "true", 1.1395348f, 1.0f, 140.3907f},
        {"led", "false", 100.0f, 49.0f, "true", 0.8116883f, 1.0f, 100.0f},
        {"led", "false", 340.0f, 30.0f, "true", 0.6976744f, 1.0f, 85.95349f},
        {"led", "false", 340.0f, 30.0f, "false", 0.6976744f, 1.0f, 85.95349f},
        {"led", "false", 120.0f, 49.0f, "false", 0.97402596f, 1.0f, 120.0f},
        {"led", "true", 340.0f, 49.0f, "false", 1.1395348f, 1.0f, 140.3907f},
        {"led", "true", 190.0f, 49.0f, "true", 1.1395348f, 1.0f, 140.3907f},
        {"led", "true", 100.0f, 49.0f, "true", 0.8116883f, 1.0f, 100.0f},
        {"led", "true", 340.0f, 30.0f, "true", 0.6976744f, 1.0f, 85.95349f},
        {"led", "true", 340.0f, 30.0f, "false", 0.6976744f, 1.0f, 85.95349f},
        {"led", "true", 120.0f, 49.0f, "false", 0.97402596f, 1.0f, 120.0f},
        {"tape", "false", 340.0f, 49.0f, "false", 1.1807228f, 1.0f, 212.5301f},
        {"tape", "false", 190.0f, 49.0f, "true", 1.0555556f, 1.0f, 190.0f},
        {"tape", "false", 100.0f, 49.0f, "true", 0.5555556f, 1.0f, 100.00001f},
        {"tape", "false", 340.0f, 30.0f, "true", 1.35f, 0.0f, 137.7f},
        {"tape", "false", 340.0f, 30.0f, "false", 0.72289157f, 1.0f, 130.12048f},
        {"tape", "false", 120.0f, 49.0f, "false", 0.6666667f, 1.0f, 120.0f},
        {"tape", "true", 340.0f, 49.0f, "false", 1.1807228f, 1.0f, 212.5301f},
        {"tape", "true", 190.0f, 49.0f, "true", 1.0555556f, 1.0f, 190.0f},
        {"tape", "true", 100.0f, 49.0f, "true", 0.5555556f, 1.0f, 100.00001f},
        {"tape", "true", 340.0f, 30.0f, "true", 1.35f, 0.0f, 137.7f},
        {"tape", "true", 340.0f, 30.0f, "false", 0.72289157f, 1.0f, 130.12048f},
        {"tape", "true", 120.0f, 49.0f, "false", 0.6666667f, 1.0f, 120.0f},
        {"slab", "false", 340.0f, 49.0f, "false", 1.0888889f, 1.0f, 118.68889f},
        {"slab", "false", 190.0f, 49.0f, "true", 1.0888889f, 1.0f, 118.68889f},
        {"slab", "false", 100.0f, 49.0f, "true", 0.9174312f, 1.0f, 100.0f},
        {"slab", "false", 340.0f, 30.0f, "true", 0.9677419f, 0.0f, 105.48387f},
        {"slab", "false", 340.0f, 30.0f, "false", 0.6666667f, 1.0f, 72.66667f},
        {"slab", "false", 120.0f, 49.0f, "false", 1.0888889f, 1.0f, 118.68889f},
        {"slab", "true", 340.0f, 49.0f, "false", 1.0888889f, 1.0f, 118.68889f},
        {"slab", "true", 190.0f, 49.0f, "true", 1.0888889f, 1.0f, 118.68889f},
        {"slab", "true", 100.0f, 49.0f, "true", 0.9174312f, 1.0f, 100.0f},
        {"slab", "true", 340.0f, 30.0f, "true", 0.9677419f, 0.0f, 105.48387f},
        {"slab", "true", 340.0f, 30.0f, "false", 0.6666667f, 1.0f, 72.66667f},
        {"slab", "true", 120.0f, 49.0f, "false", 1.0888889f, 1.0f, 118.68889f},
    };

    private TimeZone mSavedZone;

    @Before
    public void pinZone() {
        mSavedZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void restoreZone() {
        TimeZone.setDefault(mSavedZone);
    }

    @Test
    public void fitPlanAtBarSizes_equalsWhatTheBarProducedBeforeTheSplit() {
        Context context = ApplicationProvider.getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals("the recorded plans are at density 1", 1f, density, 0f);
        for (Object[] row : BAR_PLANS) {
            String face = (String) row[0];
            boolean amPm = Boolean.parseBoolean((String) row[1]);
            float width = (Float) row[2] * density;
            float height = (Float) row[3] * density;
            boolean floor = Boolean.parseBoolean((String) row[4]);
            ClockFaceRenderer renderer = new ClockFaceRenderer(context.getResources());
            renderer.setStyle(face);
            renderer.setUseAmPm(amPm);
            renderer.updateTime(WALL, 1_000L);
            float[] plan = renderer.fitPlan(width, height, floor);
            String label = face + (amPm ? " 12h " : " 24h ") + row[2] + "x" + row[3]
                + (floor ? " floored" : "");
            assertEquals(label + " scale", (Float) row[5], plan[0], 1e-5f);
            assertEquals(label + " date kept", (Float) row[6], plan[1], 0f);
            assertEquals(label + " painted width", (Float) row[7], plan[2], 1e-3f);
            assertEquals(label + " fit width", (Float) row[7],
                renderer.fitWidthPx(width, height, floor), 1e-3f);
        }
    }

    @Test
    public void theBarReportsTheRenderersPlanAndBounds() {
        Context context = ApplicationProvider.getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(340f * density);
        int height = Math.round(65f * density);
        for (String face : FACES) {
            TerminalClockWidget bar = new TerminalClockWidget(context, null);
            ClockFaceRenderer renderer = new ClockFaceRenderer(context.getResources());
            bar.setStyle(face);
            renderer.setStyle(face);
            bar.updateTime(WALL, 1_000L);
            renderer.updateTime(WALL, 1_000L);
            bar.setFitBudget(width, 49f * density, false);
            renderer.setFitBudget(width, 49f * density, false);
            bar.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,
                    android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height,
                    android.view.View.MeasureSpec.EXACTLY));
            bar.layout(0, 0, width, height);

            assertEquals(face, renderer.fitWidthPx(width, 49f * density, false),
                bar.fitWidthPx(width, 49f * density, false), 0f);
            assertEquals(face, renderer.contentWidth(), bar.contentWidth(), 0f);
            assertEquals(face, renderer.paintedRightPx(width, height), bar.paintedRightPx(), 0f);
            assertEquals(face, renderer.fullBandCenterYPx(height), bar.fullBandCenterYPx(), 0f);
            assertEquals(face, renderer.fullRuleCenterYPx(height), bar.fullRuleCenterYPx(), 0f);
        }
    }

    @Test
    public void everyFaceDrawsInEveryFormIntoAHostsBox() {
        Context context = ApplicationProvider.getApplicationContext();
        ClockFaceRenderer.Palette palette = new ClockFaceRenderer.Palette(Color.WHITE,
            Color.LTGRAY, Color.CYAN, Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY,
            Color.GRAY, Color.LTGRAY);
        int width = 200;
        int height = 80;
        for (String face : FACES) {
            for (TopPaneClockForm form : TopPaneClockForm.values()) {
                ClockFaceRenderer renderer = new ClockFaceRenderer(context.getResources());
                assertTrue(renderer.setPalette(palette));
                renderer.setStyle(face);
                renderer.setForm(form);
                renderer.setLazyMode(true);
                renderer.updateTime(WALL, 1_000L);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                renderer.draw(new Canvas(bitmap), width, height, 2_000L);
                assertTrue(face + " " + form + " painted nothing", hasInk(bitmap));
            }
        }
    }

    @Test
    public void anUnchangedPaletteKeepsWhatItResolved() {
        ClockFaceRenderer renderer = new ClockFaceRenderer(
            ApplicationProvider.getApplicationContext().getResources());
        ClockFaceRenderer.Palette palette = new ClockFaceRenderer.Palette(1, 2, 3, 4, 5, 6, 7, 8,
            9);
        assertTrue(renderer.setPalette(palette));
        assertTrue("the same roles must not re-resolve",
            !renderer.setPalette(new ClockFaceRenderer.Palette(1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertTrue(renderer.setPalette(new ClockFaceRenderer.Palette(1, 2, 3, 4, 5, 6, 7, 8,
            10)));
    }

    private static boolean hasInk(Bitmap bitmap) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) return true;
            }
        }
        return false;
    }
}
