package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.InsetGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The page bar's cues by style, drawn for real ({@link GraphicsMode.Mode#NATIVE}): the well's
 * shade and rim, and the concave glass's inverted rim alone over a flat ground. The lens itself
 * is AGSL, which does not run off a device: {@code GlassConcaveLensRenderTest} draws its mapping
 * on the CPU. Where {@code ~/.cache/window-restructure/rounds/concave} exists, the pictures are
 * written there to look at.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageBarGlassPainterTest {

    private static final float DENSITY = 2.75f;
    private static final int DARK_GROUND = 0xFF2A3038;
    private static final int LIGHT_GROUND = 0xFFD6DBE0;

    /** The slab: 30 dp tall, 200 dp wide, a capsule, 10 dp of air round it, on whole px as laid out. */
    private static final float MARGIN = Math.round(10f * DENSITY);
    private static final float SLAB_W = Math.round(200f * DENSITY);
    private static final float SLAB_H = Math.round(30f * DENSITY);
    private static final int W = Math.round(SLAB_W + 2f * MARGIN);
    private static final int H = Math.round(SLAB_H + 2f * MARGIN);

    @Test
    public void theWellShadesAndTheConcaveGlassDrawsOnlyItsRim() throws IOException {
        Bitmap well = draw(PageBarGlassStyle.WELL, true, DARK_GROUND);
        Bitmap concave = draw(PageBarGlassStyle.CONCAVE, true, DARK_GROUND);
        int cx = W / 2;
        int top = Math.round(MARGIN);
        int bottom = Math.round(MARGIN + SLAB_H);
        // Inside, just under the top edge: the well's floor and contact shadow darken it; the
        // concave glass paints nothing there.
        int under = top + Math.round(2f * DENSITY);
        assertTrue(luma(well.getPixel(cx, under)) < luma(DARK_GROUND) - 3);
        assertEquals(DARK_GROUND, concave.getPixel(cx, under));
        assertEquals(DARK_GROUND, concave.getPixel(cx, (top + bottom) / 2));
        // The inverted rim: dark just above the top edge, light just under the bottom one.
        assertTrue(luma(concave.getPixel(cx, top - 1)) < luma(DARK_GROUND) - 5);
        assertTrue(luma(concave.getPixel(cx, bottom)) > luma(DARK_GROUND) + 20);
        // Stronger than the well's halved rim, as it is the only cue.
        assertTrue(luma(concave.getPixel(cx, top - 1)) < luma(well.getPixel(cx, top - 1)));
        assertTrue(luma(concave.getPixel(cx, bottom)) > luma(well.getPixel(cx, bottom)));
        write("rim_concave_dark.png", scaled(concave));
        write("rim_well_dark.png", scaled(well));
        write("rim_concave_light.png", scaled(draw(PageBarGlassStyle.CONCAVE, false, LIGHT_GROUND)));
        write("rim_well_light.png", scaled(draw(PageBarGlassStyle.WELL, false, LIGHT_GROUND)));
    }

    @Test
    public void theStyleRebuildsOnlyOnAChange() {
        PageBarGlassPainter painter = new PageBarGlassPainter();
        assertTrue(painter.sync(DENSITY, 0f, 0f, SLAB_W, SLAB_H, true, PageBarGlassStyle.WELL));
        assertTrue(painter.drawsShade());
        assertFalse(painter.sync(DENSITY, 0f, 0f, SLAB_W, SLAB_H, true, PageBarGlassStyle.WELL));
        assertTrue(painter.sync(DENSITY, 0f, 0f, SLAB_W, SLAB_H, true, PageBarGlassStyle.CONCAVE));
        assertFalse(painter.drawsShade());
        assertEquals(InsetGlass.concaveRimShadow(true), painter.rimShadowColor());
    }

    // ------------------------------------------------------------------------------- helpers

    private static Bitmap draw(PageBarGlassStyle style, boolean dark, int ground) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ground);
        PageBarGlassPainter painter = new PageBarGlassPainter();
        painter.sync(DENSITY, MARGIN, MARGIN, SLAB_W, SLAB_H, dark, style);
        painter.draw(canvas, MARGIN, MARGIN, MARGIN + SLAB_W, MARGIN + SLAB_H, SLAB_H / 2f);
        return bitmap;
    }

    private static Bitmap scaled(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 2, bitmap.getHeight() * 2, false);
    }

    private static int luma(int color) {
        return (Color.red(color) * 2126 + Color.green(color) * 7152 + Color.blue(color) * 722) / 10000;
    }

    private static void write(String name, Bitmap bitmap) throws IOException {
        File dir = new File(System.getProperty("user.home"), ".cache/window-restructure/rounds/concave");
        if (!dir.isDirectory()) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
