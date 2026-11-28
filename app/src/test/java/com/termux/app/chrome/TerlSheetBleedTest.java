package com.termux.app.chrome;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.DockGlassRendering;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertTrue;

/**
 * What a Terl sheet lets through of the windows under it, measured in pixels (round 5).
 *
 * <p>Each sheet is drawn the way it is built (the window menu, Search, the A–Z callout, Overview
 * and the hint card through {@link GlassStack#build}, the drawer through
 * {@link DockGlassRendering#createGlassSurface}) over a black half and a white half: the
 * difference between the two halves under the sheet is what a terminal's text under it keeps of
 * its contrast. At the old 96 % floor that was 10 of 255 (4 %), which read on the flat dark
 * sheet as ghost text on the emulator (round5/a-menu-zoom, a-search, a-drawer measure the same
 * 5 to 10 levels). The windows under a sheet are not blurred (the blur is the wallpaper's), so
 * the sheet stands opaque over them.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerlSheetBleedTest {

    private static final int SIZE = 40;

    /** The most of 255 a sheet may pass through of the contrast under it. */
    private static final int MAX_PASSED = 0;

    /** The Look's opacities a sheet may be asked for: clear, the shipped default, near opaque. */
    private static final int[] LOOK_OPACITIES = {0, 15, 46, 60, 90, 96};

    @Test
    public void aSheetBuiltThroughTheGlassStackPassesNothingOfTheWindowsUnderIt() {
        Context context = RuntimeEnvironment.getApplication();
        GlassSurfaceFactory glass = new GlassSurfaceFactory(new FakeChromeSurfaces(context));
        for (int look : LOOK_OPACITIES) {
            float alpha = SheetGlass.terlSheetOpacityPercent(look) / 100f;
            // As terlGlass builds it: no blur of the windows under it, the Look's tint, a rim.
            GlassStack.Spec spec = GlassStack.Spec.of(0, alpha, 0, 0f, null)
                .withRim(false).withOwnOutline(false);
            Drawable sheet = GlassStack.build(glass, spec, 1f, null);
            int passed = passedThrough(sheet);
            assertTrue("Look " + look + "%: the sheet passes " + passed + " of 255", passed <= MAX_PASSED);
        }
    }

    @Test
    public void theDrawerPassesNothingOfTheWindowsUnderIt() {
        Context context = RuntimeEnvironment.getApplication();
        for (int look : LOOK_OPACITIES) {
            float opacity = SheetGlass.terlSheetOpacityPercent(SheetGlass.opacityPercent(look)) / 100f;
            Drawable plane = DockGlassRendering.createGlassSurface(context.getResources(),
                0xFF1B1B1F, 0xFFA8C7FA, opacity, 0, true);
            int passed = passedThrough(plane);
            assertTrue("Look " + look + "%: the drawer passes " + passed + " of 255", passed <= MAX_PASSED);
        }
    }

    @Test
    public void theHintCardsPlainGroundPassesNothing() {
        int alpha = Math.round(SheetGlass.BARE_OPACITY_FLOOR_PERCENT * 255f / 100f);
        android.graphics.drawable.ColorDrawable plain = new android.graphics.drawable.ColorDrawable(
            (alpha << 24) | 0x1B1B1F);
        int passed = passedThrough(plain);
        assertTrue("the hint passes " + passed + " of 255", passed <= MAX_PASSED);
    }

    /**
     * {@code sheet} over black text on white and white on black: the largest channel difference
     * between the two grounds under it, 0 when nothing under it shows.
     */
    private static int passedThrough(Drawable sheet) {
        int over = composite(sheet, Color.WHITE);
        int under = composite(sheet, Color.BLACK);
        int passed = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            passed = Math.max(passed, Math.abs(((over >> shift) & 0xFF) - ((under >> shift) & 0xFF)));
        return passed;
    }

    private static int composite(Drawable sheet, int ground) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ground);
        sheet.setBounds(0, 0, SIZE, SIZE);
        sheet.draw(canvas);
        // The middle, away from any light-model edge.
        return bitmap.getPixel(SIZE / 2, SIZE / 2);
    }
}
