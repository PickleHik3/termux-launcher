package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The concave lens's mapping made visible: AGSL does not run off a device, so a grid is drawn
 * through the program's own arithmetic in Java ({@link GlassRefraction#concaveOffset}) on the page
 * bar's slab, beside the old convex bend on the same slab. Where
 * {@code ~/.cache/window-restructure/rounds/concave} exists, the pictures are written there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GlassConcaveLensRenderTest {

    private static final float DENSITY = 2.75f;
    private static final float MARGIN = Math.round(10f * DENSITY);
    private static final float SLAB_W = Math.round(200f * DENSITY);
    private static final float SLAB_H = Math.round(30f * DENSITY);
    private static final int W = Math.round(SLAB_W + 2f * MARGIN);
    private static final int H = Math.round(SLAB_H + 2f * MARGIN);

    /** The concave mapping over a grid, and the old convex bend on the same slab beside it. */
    @Test
    public void theLensDrawsTheGridInTowardTheMiddleWithNoStreaks() throws IOException {
        // Down the slab's middle column the concave sample only ever moves on with the pixel (no
        // fold, so no row of the picture is smeared down the column); the old bend folds back.
        assertTrue(monotoneDownTheMiddle(true));
        assertFalse(monotoneDownTheMiddle(false));
        write("lens_grid_concave.png", scaled(Bitmap.createBitmap(lensOver(true), W, H, Bitmap.Config.ARGB_8888)));
        write("lens_grid_convex_old.png", scaled(Bitmap.createBitmap(lensOver(false), W, H, Bitmap.Config.ARGB_8888)));
        write("lens_grid_flat.png", scaled(Bitmap.createBitmap(lensOver(null), W, H, Bitmap.Config.ARGB_8888)));
    }

    // ------------------------------------------------------------------------------- helpers

    private static final int GRID = 0xFF101010;
    private static final int PAPER = 0xFFF4F1E8;
    private static final int STRIPE = 0xFFE0563C;
    /** Samples per pixel along each axis: the picture is averaged, as the blurred frame is smooth. */
    private static final int SUPER = 4;

    /** A grid every 3 dp, lines 2 px wide, with a red stripe every 9 dp so the rows can be told apart. */
    private static int pattern(float x, float y) {
        float cell = 3f * DENSITY;
        float gx = ((x % cell) + cell) % cell;
        float gy = ((y % cell) + cell) % cell;
        if (gx < 2f || gy < 2f) {
            int row = (int) Math.floor(y / cell);
            return gy < 2f && Math.floorMod(row, 3) == 0 ? STRIPE : GRID;
        }
        return PAPER;
    }

    private static boolean monotoneDownTheMiddle(boolean concave) {
        float bx = SLAB_W / 2f;
        float by = SLAB_H / 2f;
        float[] off = new float[2];
        float last = -Float.MAX_VALUE;
        for (float py = -by + 0.05f; py < by; py += 0.05f) {
            offset(off, 0f, py, bx, by, concave);
            float sample = py + off[1];
            if (sample < last) return false;
            last = sample;
        }
        return true;
    }

    private static void offset(float[] off, float px, float py, float bx, float by, boolean concave) {
        if (concave) {
            GlassRefraction.concaveOffset(off, px, py, bx, by, by, DENSITY);
        } else {
            GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
            GlassRefraction.restBendOffset(off, px, py, bx, by, by, look.bandPx(DENSITY),
                look.strengthPx(DENSITY), 0f, 0f, GlassRefraction.Look.rimWidthPx(DENSITY));
        }
    }

    /** The grid seen through the slab (null: no glass at all), averaged over {@link #SUPER}². */
    private static int[] lensOver(Boolean concave) {
        int[] out = new int[W * H];
        float bx = SLAB_W / 2f;
        float by = SLAB_H / 2f;
        float cx = MARGIN + bx;
        float cy = MARGIN + by;
        float[] off = new float[2];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SUPER; sy++) {
                    for (int sx = 0; sx < SUPER; sx++) {
                        float fx = x + (sx + 0.5f) / SUPER;
                        float fy = y + (sy + 0.5f) / SUPER;
                        float px = fx - cx;
                        float py = fy - cy;
                        off[0] = 0f;
                        off[1] = 0f;
                        if (concave != null && GlassRefraction.insidePx(px, py, bx, by, by) >= 0f) {
                            offset(off, px, py, bx, by, concave);
                        }
                        int c = pattern(fx + off[0], fy + off[1]);
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                int n = SUPER * SUPER;
                out[y * W + x] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        outline(out);
        return out;
    }

    /** A faint blue hairline on the slab's outline, so where the glass is can be seen. */
    private static void outline(int[] pixels) {
        float bx = SLAB_W / 2f;
        float by = SLAB_H / 2f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float inside = GlassRefraction.insidePx(x + 0.5f - MARGIN - bx, y + 0.5f - MARGIN - by, bx, by, by);
                if (inside < 0f && inside > -1f) pixels[y * W + x] = 0xFF2F6FD0;
            }
        }
    }

    private static Bitmap scaled(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 2, bitmap.getHeight() * 2, false);
    }

    private static void write(String name, Bitmap bitmap) throws IOException {
        File dir = new File(System.getProperty("user.home"), ".cache/window-restructure/rounds/concave");
        if (!dir.isDirectory()) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
