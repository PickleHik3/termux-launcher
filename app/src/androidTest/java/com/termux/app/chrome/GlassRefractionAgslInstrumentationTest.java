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
 * The glass edge program on a real device compiler: it compiles with the bevel light and the
 * seven-sample dispersion, Clear's look (bend 28, edge 32, light 85, specular 0, dispersion 40)
 * changes the rim while leaving the middle of the pane as it was, and the bevel light lands on
 * the top-left corner and not the top-right.
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
        Bitmap clear = draw(new GlassRefraction.Look(28, 32, 85, 0, 40), content);
        float rim = meanDiff(plain, clear, 2, 2, 30, H - 4);
        float middle = meanDiff(plain, clear, W / 2 - 20, H / 2 - 20, W / 2 + 20, H / 2 + 20);
        assertTrue("the rim changes, mean diff " + rim, rim > 4f);
        assertTrue("the middle stays, mean diff " + middle, middle < 2f);
    }

    @Test
    public void theBevelLightCatchesOneDiagonalAndNotTheOther() {
        // Same bend, only the bevel light differs, so any change is the light alone.
        Bitmap content = checker();
        Bitmap dark = draw(new GlassRefraction.Look(28, 32, 85, 0, 0), content);
        Bitmap lit = draw(new GlassRefraction.Look(28, 32, 85, 100, 0), content);
        float topLeft = meanDiff(dark, lit, 8, 8, 24, 24);
        float topRight = meanDiff(dark, lit, W - 24, 8, W - 8, 24);
        float middle = meanDiff(dark, lit, W / 2 - 20, H / 2 - 20, W / 2 + 20, H / 2 + 20);
        assertTrue("the top-left catches the light, mean diff " + topLeft, topLeft > 20f);
        assertTrue("the top-right stays dark, mean diff " + topRight + " against " + topLeft,
            topRight < topLeft / 3f);
        assertTrue("the middle stays, mean diff " + middle, middle < 1f);
    }

    @Test
    public void aStraightEdgeKeepsOnlyAFaintBevelLight() {
        // The middle of the top edge faces the light half-on. The plain cosine gave it 71% of a
        // corner's light, so every edge glowed; the corner glint leaves it a faint trace. Measured
        // at the same depth from the rim as the corner box above.
        Bitmap content = checker();
        Bitmap dark = draw(new GlassRefraction.Look(28, 32, 85, 0, 0), content);
        Bitmap lit = draw(new GlassRefraction.Look(28, 32, 85, 100, 0), content);
        float topLeft = meanDiff(dark, lit, 8, 8, 24, 24);
        float topEdge = meanDiff(dark, lit, W / 2 - 20, 4, W / 2 + 20, 17);
        assertTrue("the top edge keeps a faint light, mean diff " + topEdge + " against " + topLeft,
            topEdge < topLeft / 2f);
    }

    @Test
    public void theRimIsBrightestWhereItFacesTheLightAndFaintAlongTheSides() {
        // Same bend, only the hairline differs, so any change is the rim light alone. The light
        // stands above and a little to the left: the top edge catches it, the left side runs
        // nearly edge-on to it and keeps a faint line, never none.
        Bitmap content = checker();
        Bitmap dark = draw(new GlassRefraction.Look(28, 32, 0, 0, 0), content);
        Bitmap lit = draw(new GlassRefraction.Look(28, 32, 100, 0, 0), content);
        float top = meanDiff(dark, lit, W / 2 - 20, 0, W / 2 + 20, 5);
        float left = meanDiff(dark, lit, 0, H / 2 - 20, 5, H / 2 + 20);
        float bottom = meanDiff(dark, lit, W / 2 - 20, H - 5, W / 2 + 20, H);
        assertTrue("the top edge is lit, mean diff " + top, top > 8f);
        assertTrue("the side is fainter than the top, " + left + " against " + top, left < top * 0.7f);
        assertTrue("the side keeps a line, mean diff " + left, left > 1f);
        assertTrue("the bottom answers with a softer light, " + bottom + " between " + left + " and " + top,
            bottom > left && bottom < top);
    }

    @Test
    public void clarityShowsTheSharpPictureThroughTheBentEdgeAndLeavesTheMiddle() {
        // The sharp input is a flat colour nothing like the checker, so wherever it shows the
        // pixels move a long way; the middle, outside the band, never reads it.
        Bitmap content = checker();
        // Held at twice the content's size, as a frame at a lower blur is, to prove the aim scales.
        Bitmap sharp = Bitmap.createBitmap(W * 2, H * 2, Bitmap.Config.ARGB_8888);
        sharp.eraseColor(Color.rgb(250, 250, 250));
        Bitmap frosted = draw(new GlassRefraction.Look(28, 32, 0, 0, 0, 0), content, sharp, null);
        Bitmap clear = draw(new GlassRefraction.Look(28, 32, 0, 0, 0, 100), content, sharp, null);
        float rim = meanDiff(frosted, clear, 2, 2, 24, H - 4);
        float middle = meanDiff(frosted, clear, W / 2 - 20, H / 2 - 20, W / 2 + 20, H / 2 + 20);
        assertTrue("the rim shows the sharp picture, mean diff " + rim, rim > 20f);
        assertTrue("the middle stays, mean diff " + middle, middle < 1f);
    }

    @Test
    public void withNoSharpInputClarityIsTheFrostItself() {
        // setInput binds the frost as its own clear picture, so clarity at 100 changes nothing.
        Bitmap content = checker();
        Bitmap frosted = draw(new GlassRefraction.Look(28, 32, 0, 0, 0, 0), content);
        Bitmap clear = draw(new GlassRefraction.Look(28, 32, 0, 0, 0, 100), content);
        assertTrue(meanDiff(frosted, clear, 0, 0, W, H) < 0.5f);
    }

    @Test
    public void aFingerLightsTheGlassUnderItAndNotTheFarCorner() {
        Bitmap content = checker();
        float[] touch = {W / 2f, H / 2f, 40f, 1f};
        Bitmap rest = draw(new GlassRefraction.Look(28, 32, 60, 0, 0), content);
        Bitmap held = draw(new GlassRefraction.Look(28, 32, 60, 0, 0), content, null, touch);
        float under = meanDiff(rest, held, W / 2 - 6, H / 2 - 6, W / 2 + 6, H / 2 + 6);
        float far = meanDiff(rest, held, W - 30, H - 30, W - 8, H - 8);
        assertTrue("the glass glows under the finger, mean diff " + under, under > 3f);
        assertTrue("the far corner rests, mean diff " + far, far < 0.5f);
    }

    private static Bitmap draw(GlassRefraction.Look look, Bitmap content) {
        return draw(look, content, null, null);
    }

    /** {@code touch} is {@code {x, y, radiusPx, intensity}} or null for no finger. */
    private static Bitmap draw(GlassRefraction.Look look, Bitmap content, Bitmap sharp, float[] touch) {
        GlassRefraction.Program program = GlassRefraction.Program.create(2f);
        assertNotNull(program);
        program.setInput(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        if (sharp != null)
            program.setClarityInput(new BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                sharp.getWidth() / (float) content.getWidth(), sharp.getHeight() / (float) content.getHeight());
        program.setAim(1f, 1f, 0f, 0f);
        program.setRect(0f, 0f, W, H, 32f);
        program.setLook(look);
        if (touch != null) program.setTouch(touch[0], touch[1], touch[2], touch[3]);
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
