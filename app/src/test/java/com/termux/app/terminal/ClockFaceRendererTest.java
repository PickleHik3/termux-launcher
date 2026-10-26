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
 * The status bar's clock draws through {@link ClockFaceRenderer}. Its fit plan at the bar's
 * sizes must keep the face inside the budget the bar hands it, and the bar must report exactly
 * the renderer's plan and bounds.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClockFaceRendererTest {

    /** 2026-07-23 04:05:06 UTC: a fixed time, so the date and minute-track text are fixed too. */
    private static final long WALL = 1_784_779_506_000L;
    private static final String[] FACES = {"flip", "lcd", "minimal", "minimal_medium", "led",
        "tape", "slab"};

    /**
     * {width budget dp, height budget dp, compact floor (1 or 0), date kept (1 or 0)}: the bar's
     * budgets. 49dp is the bar's 65dp slot less its 8dp air on each side; 190dp and 100dp are the
     * run beside media; 30dp is a squeezed slot that drops the date under the compact floor.
     *
     * <p>Until #111 this table held the exact plans recorded before the renderer was split out.
     * The Label-role seconds and date (12sp, no longer scaled with the face) moved every scale,
     * so the table now holds what must stay true instead: the face fits its budget, and the date
     * row stays or goes as it did, except LED in the floored 30dp slot, which now drops its date
     * (its taller date row no longer fits above the compact floor).
     */
    private static final float[][] BAR_BUDGETS = {
        {340f, 49f, 0f, 1f},
        {190f, 49f, 1f, 1f},
        {100f, 49f, 1f, 1f},
        {340f, 30f, 1f, 0f},
        {340f, 30f, 0f, 1f},
        {120f, 49f, 0f, 1f},
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
    public void fitPlanAtBarSizes_keepsTheFaceInsideTheBudget() {
        Context context = ApplicationProvider.getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        int slot = Math.round(65f * density);
        for (String face : FACES) {
            for (boolean amPm : new boolean[] {false, true}) {
                for (float[] row : BAR_BUDGETS) {
                    float width = row[0] * density;
                    float height = row[1] * density;
                    boolean floor = row[2] > 0f;
                    ClockFaceRenderer renderer = new ClockFaceRenderer(context.getResources());
                    renderer.setStyle(face);
                    renderer.setUseAmPm(amPm);
                    renderer.updateTime(WALL, 1_000L);
                    float[] plan = renderer.fitPlan(width, height, floor);
                    String label = face + (amPm ? " 12h " : " 24h ") + row[0] + "x" + row[1]
                        + (floor ? " floored" : "");                    assertTrue(label + " scale " + plan[0],
                        plan[0] > 0f && plan[0] <= ClockFaceRenderer.FIT_MAX_SCALE);
                    assertTrue(label + " painted width " + plan[2], plan[2] <= width + 1e-3f);
                    boolean expectDate = row[3] > 0f
                        && !(face.equals("led") && row[1] == 30f && floor);
                    assertEquals(label + " date kept", expectDate ? 1f : 0f, plan[1], 0f);
                    assertEquals(label + " fit width", plan[2],
                        renderer.fitWidthPx(width, height, floor), 1e-3f);

                    renderer.setFitBudget(width, height, floor);
                    assertTrue(label + " painted column " + renderer.fullColumnPx(slot),
                        renderer.fullColumnPx(slot) <= height + 1e-3f);
                }
            }
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
