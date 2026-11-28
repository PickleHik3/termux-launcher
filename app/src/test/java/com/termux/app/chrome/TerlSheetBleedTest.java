package com.termux.app.chrome;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.DockGlassRendering;
import com.termux.app.launcher.drawer.AppDrawerController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a Terl sheet lets through of the windows under it, measured in pixels (round 5).
 *
 * <p>Each sheet is drawn as its real path builds it: the window menu, Search, the A–Z callout,
 * Overview, the Workspace list and the hint through {@link SheetGlass#terlSpec} (what the
 * activity's {@code terlGlass(SHEETS)} builds, its opacity through
 * {@link SheetGlass#terlSheetOpacityPercent}), the drawer at
 * {@link AppDrawerController#overlayOpacity} (what its {@code prepareOverlay} paints the plane
 * with), the hint's plain ground at the floor; each over a black and a white ground. The
 * difference under the sheet is what a terminal's text keeps of its contrast. At the old 96 % floor
 * it was 10 of 255 (4 %), which read on the flat dark sheet as ghost text on the emulator
 * (round5/a-menu-zoom, a-search, a-drawer measure the same 5 to 10 levels).</p>
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

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("terl-sheet-bleed", Context.MODE_PRIVATE);
        store.edit().clear().putBoolean(TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_DESKTOP, true).commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        preferences.migrateLookGrid(java.util.Collections.emptyList());
    }

    @Test
    public void aSheetAsTerlGlassBuildsItPassesNothingOfTheWindowsUnderIt() {
        Context context = RuntimeEnvironment.getApplication();
        GlassSurfaceFactory glass = new GlassSurfaceFactory(new FakeChromeSurfaces(context));
        ElementGlass global = ElementGlass.of(preferences, null, -1);
        for (int look : LOOK_OPACITIES) {
            int opacity = SheetGlass.terlSheetOpacityPercent(look);
            for (int blurDp : new int[] {0, 12}) {
                // terlGlass samples no backdrop for an opaque sheet (review P3-1).
                assertFalse("an opaque sheet samples no blur", SheetGlass.samplesBackdrop(blurDp, opacity));
                Drawable sheet = GlassStack.build(glass,
                    SheetGlass.terlSpec(global, 0f, null, blurDp, opacity), 1f, null);
                int passed = passedThrough(sheet);
                assertTrue("Look " + look + "%, blur " + blurDp + ": the sheet passes " + passed
                    + " of 255", passed <= MAX_PASSED);
            }
        }
    }

    @Test
    public void theDrawerAsPrepareOverlayPaintsItPassesNothingOfTheWindowsUnderIt() {
        Context context = RuntimeEnvironment.getApplication();
        for (int look : LOOK_OPACITIES) {
            preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, look);
            float opacity = AppDrawerController.overlayOpacity(preferences);
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

    @Test
    public void aGlassThatLetsSomeThroughStillSamplesItsBlur() {
        assertTrue(SheetGlass.samplesBackdrop(12, 60));
        assertFalse(SheetGlass.samplesBackdrop(0, 60));
    }

    /**
     * {@code sheet} over black and over white: the largest channel difference between the two
     * grounds under it, 0 when nothing under it shows.
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
