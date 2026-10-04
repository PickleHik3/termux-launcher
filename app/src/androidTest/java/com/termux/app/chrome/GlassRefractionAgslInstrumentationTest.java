package com.termux.app.chrome;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The glass edge program on a real device compiler: it compiles with the bevel and dispersion
 * terms, and Clear's look (bend 28, edge 32, light 85, specular 45, dispersion 40) changes the
 * rim while leaving the middle of the pane as it was.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class GlassRefractionAgslInstrumentationTest {

    private static final int W = 240, H = 320;

    @Test
    public void theProgramCompiles() {
        assertNotNull(new RuntimeShader(GlassRefraction.AGSL));
        assertNotNull(GlassRefraction.Program.create(2f));
    }

    @Test
    public void clearBendsAndLightsTheRimAndLeavesTheMiddle() {
        Bitmap content = checker();
        Bitmap plain = draw(new GlassRefraction.Look(0, 1, 0, 0, 0), content);
        Bitmap clear = draw(new GlassRefraction.Look(28, 32, 85, 45, 40), content);
        float rim = meanDiff(plain, clear, 2, 2, 30, H - 4);
        float middle = meanDiff(plain, clear, W / 2 - 20, H / 2 - 20, W / 2 + 20, H / 2 + 20);
        assertTrue("the rim changes, mean diff " + rim, rim > 4f);
        assertTrue("the middle stays, mean diff " + middle, middle < 2f);
    }

    private static Bitmap draw(GlassRefraction.Look look, Bitmap content) {
        GlassRefraction.Program program = GlassRefraction.Program.create(2f);
        assertNotNull(program);
        program.setInput(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        program.setAim(1f, 1f, 0f, 0f);
        program.setRect(0f, 0f, W, H, 32f);
        program.setLook(look);
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(W, H);
        Paint paint = new Paint();
        paint.setShader(program.shader());
        canvas.drawRect(0, 0, W, H, paint);
        picture.endRecording();
        return Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false);
    }

    private static Bitmap checker() {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean on = ((x / 10) + (y / 10)) % 2 == 0;
                b.setPixel(x, y, on ? Color.rgb(220, 60, 60) : Color.rgb(40, 90, 200));
            }
        }
        return b;
    }

    /** Mean absolute channel difference over a rectangle, in 0..255 units. */
    private static float meanDiff(Bitmap a, Bitmap b, int l, int t, int r, int bottom) {
        long sum = 0;
        int n = 0;
        for (int y = t; y < bottom; y++) {
            for (int x = l; x < r; x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                sum += Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q))
                    + Math.abs(Color.blue(p) - Color.blue(q));
                n++;
            }
        }
        return sum / (3f * n);
    }
}
