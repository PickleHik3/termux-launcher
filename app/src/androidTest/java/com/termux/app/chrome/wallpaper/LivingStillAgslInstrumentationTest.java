package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RuntimeShader;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.termux.app.chrome.wallpaper.living.LivingRecipe;
import com.termux.app.chrome.wallpaper.living.Manifest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The AGSL programs on a real device compiler (unit tests only read the source): every built-in
 * background compiles, and a living still compiles both its programs, binds the composite's six
 * children, draws the photo unchanged at rest (without an effects map), draws a moving frame for
 * each water mode without the driver refusing it, draws the effects program, and draws the moving
 * composite again with that map bound, which changes the picture.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class LivingStillAgslInstrumentationTest {

    private static final int W = 216, H = 482;

    @Test
    public void everyBuiltInBackgroundCompiles() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            assertNotNull(w.id(), new RuntimeShader(w.agsl()));
        }
    }

    @Test
    public void aLivingStillIsThePhotoAtRestAndMovesForEveryWaterMode() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String water : new String[] {LivingRecipe.WATER_LAKE, LivingRecipe.WATER_POOL,
                LivingRecipe.WATER_REFLECTION}) {
            File dir = new File(context.getCacheDir(), "living-agsl-test-" + water);
            Manifest manifest = writeManifest(dir, water);
            LivingStill still = new LivingStill(manifest);
            RuntimeShader shader = WallpaperUniforms.newShader(still);
            int[] palette = still.ownPalette();
            Bitmap photo = android.graphics.BitmapFactory.decodeFile(manifest.image().getPath());

            WallpaperUniforms.applyRest(shader, palette, W, H);
            Bitmap rest = draw(shader);
            float restDiff = meanDiff(rest, photo);
            assertTrue(water + ": rest frame is the photo, mean diff " + restDiff, restDiff < 2f);

            WallpaperDirector.Frame moving = new WallpaperDirector.Frame(30, 3.7f, 3.7f, 1f, 0f, palette,
                null, false, 0.5f);
            WallpaperUniforms.apply(shader, moving, W, H);
            Bitmap live = draw(shader);
            float liveDiff = meanDiff(live, photo);
            assertTrue(water + ": a moving frame differs from the photo, mean diff " + liveDiff, liveDiff > 0.5f);

            // The effects program compiles, draws something other than the neutral map, and the
            // composite with that map bound (a quarter-size copy, as the renderer's) is not the
            // composite with the neutral one.
            RuntimeShader effects = WallpaperUniforms.newEffectsShader(still);
            WallpaperUniforms.applyEffects(effects, moving, W, H);
            Bitmap map = draw(effects);
            assertTrue(water + ": the effects map is not neutral", maxDeviationFromNeutral(map) > 3);
            Bitmap quarter = Bitmap.createScaledBitmap(map, W / 4, H / 4, true);
            WallpaperUniforms.setEffects(shader, WallpaperUniforms.linearShader(quarter), W / 4, H / 4);
            float mapDiff = meanDiff(draw(shader), live);
            assertTrue(water + ": the effects map changes the composite, mean diff " + mapDiff, mapDiff > 0.02f);

            // A non-neutral map is ignored at rest: the photo exactly.
            WallpaperUniforms.applyRest(shader, palette, W, H);
            float restWithMap = meanDiff(draw(shader), photo);
            assertTrue(water + ": rest ignores the map, mean diff " + restWithMap, restWithMap < 2f);
        }
    }

    /** Draws through a recorded picture, which renders on the GPU path (a software canvas refuses RuntimeShader). */
    private static Bitmap draw(RuntimeShader shader) {
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(W, H);
        Paint paint = new Paint();
        paint.setShader(shader);
        canvas.drawRect(0, 0, W, H, paint);
        picture.endRecording();
        return Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false);
    }

    /** The largest distance of any pixel's rgb from the neutral map (128, 128, 0), in 0..255 units. */
    private static int maxDeviationFromNeutral(Bitmap b) {
        int max = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p = b.getPixel(x, y);
                max = Math.max(max, Math.max(Math.abs(Color.red(p) - 128),
                    Math.max(Math.abs(Color.green(p) - 128), Color.blue(p))));
            }
        }
        return max;
    }

    /** Mean absolute channel difference in 0..255 units. */
    private static float meanDiff(Bitmap a, Bitmap b) {
        long sum = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                sum += Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q))
                    + Math.abs(Color.blue(p) - Color.blue(q));
            }
        }
        return sum / (3f * W * H);
    }

    /** A small synthetic still with every region present and every effect switched on. */
    private static Manifest writeManifest(File dir, String water) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir " + dir);
        Bitmap image = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Bitmap depth = Bitmap.createBitmap(W / 4, H / 4, Bitmap.Config.ARGB_8888);
        Bitmap a = Bitmap.createBitmap(W / 4, H / 4, Bitmap.Config.ARGB_8888);
        Bitmap b = Bitmap.createBitmap(W / 4, H / 4, Bitmap.Config.ARGB_8888);
        Bitmap c = Bitmap.createBitmap(W / 4, H / 4, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean check = ((x / 12) + (y / 12)) % 2 == 0;
                int r = 40 + 180 * x / W, g = 60 + 150 * y / H, bl = check ? 200 : 90;
                image.setPixel(x, y, Color.rgb(r, g, bl));
            }
        }
        int mw = W / 4, mh = H / 4;
        for (int y = 0; y < mh; y++) {
            float t = y / (float) mh;
            for (int x = 0; x < mw; x++) {
                depth.setPixel(x, y, Color.rgb(Math.round(255 * t), Math.round(255 * t), Math.round(255 * t)));
                int waterM = t > 0.6f ? 255 : 0, sway = t > 0.35f && t < 0.6f ? 200 : 0, sky = t < 0.3f ? 255 : 0;
                a.setPixel(x, y, Color.rgb(waterM, sway, sky));
                int fall = x > mw / 2 - 2 && x < mw / 2 + 2 && t > 0.3f && t < 0.6f ? 255 : 0;
                int subject = Math.abs(x - mw / 2) < mw / 6 && Math.abs(t - 0.5f) < 0.1f ? 255 : 0;
                int glow = (x + y) % 9 == 0 ? 255 : 0;
                b.setPixel(x, y, Color.rgb(fall, subject, glow));
                c.setPixel(x, y, Color.rgb(subject, t < 0.5f ? 160 : 0, 255));
            }
        }
        save(image, new File(dir, Manifest.IMAGE));
        save(depth, new File(dir, Manifest.DEPTH));
        save(a, new File(dir, Manifest.MASK_A));
        save(b, new File(dir, Manifest.MASK_B));
        save(c, new File(dir, Manifest.MASK_C));
        LivingRecipe recipe = new LivingRecipe();
        recipe.swaySpeed = 1.1f;
        recipe.swayAmp = 0.005f;
        recipe.waterMode = water;
        recipe.skyFlow = 0.035f;
        recipe.pour = 1f;
        recipe.glowMode = LivingRecipe.GLOW_FLICKER;
        recipe.glowGain = 1.4f;
        recipe.mistAmount = 0.4f;
        recipe.particles = LivingRecipe.PARTICLES_GLINTS;
        Manifest.writeRecipe(dir, recipe);
        Manifest manifest = Manifest.load(dir);
        if (manifest == null) throw new IOException("manifest did not load from " + dir);
        return manifest;
    }

    private static void save(Bitmap bitmap, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
