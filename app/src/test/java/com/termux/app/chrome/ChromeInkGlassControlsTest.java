package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The ink check measures the glass as it is drawn: the base colour with the Tint amount's accent
 * mixed in. No Tint value can carry a band past its target unmeasured.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkGlassControlsTest {

    private static final int NIGHT_BASE = 0xFF1C1B1F;
    private static final int LABEL = 0xFFE6E1E5;
    private static final int LIGHT_WALLPAPER = 0xFFE8E4DA;
    private static final Rect KEYBOARD_RECT = new Rect(0, 1800, 1080, 2400);
    private static final int CAP = OnGlass.withAlpha(0xFF2B2930, 96);

    private static final int DAY_BASE = 0xFFFEF7FF;
    private static final int DARK_WALLPAPER = 0xFF202226;
    private static final Rect STATUS_RECT = new Rect(0, 0, 1080, 96);
    /** termux_primary, light and night: the chrome's two inks. */
    private static final int LIGHT_INK = 0xFF345CA8;
    private static final int NIGHT_INK = 0xFFB8C7FF;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private int wallpaper = LIGHT_WALLPAPER;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = NIGHT_BASE;
        ink = fresh();
    }

    /** A new ink check over the surfaces and the wallpaper as they stand. */
    private ChromeInk fresh() {
        ChromeInk fresh = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        fresh.backdrops().setSampler(rect -> wallpaper);
        return fresh;
    }

    private OnGlass.Resolution keys() {
        return ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, LABEL, LABEL, CAP,
            OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void theVeilIsTheTintedBaseTheSurfaceTold() {
        int tinted = GlassLook.SCHEME.withAmountPercent(100)
            .withAccentColors(0xFF4F378B, 0xFFD0BCFF).tintBase(NIGHT_BASE);
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, tinted);
        OnGlass.Resolution accented = keys();
        assertTrue("a light wallpaper still asks for a veil", Color.alpha(accented.veil) > 0);
        assertEquals("of the colour the base is really drawn in", OnGlass.opaque(tinted),
            OnGlass.opaque(accented.veil));

        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f);
        assertEquals("the untold glass is the mode's base", OnGlass.opaque(NIGHT_BASE),
            OnGlass.opaque(keys().veil));
    }


    // ------------------------------------------------------------- Tint 100 on the tinted frame

    /**
     * Tint at its full amount on every Look's opacity, both themes, over light, mid, dark and
     * coloured wallpapers, with the frame tinted as the glass bakes it: the chrome's label still
     * reaches its target, and a fixed label that read at Tint 0 still reads.
     */
    @Test
    public void theContrastFloorHoldsAtTintOneHundred() {
        int[][] themes = {{NIGHT_BASE, 0xFF4F378B, 0xFFD0BCFF}, {DAY_BASE, 0xFFEADDFF, 0xFF6750A4}};
        int[] walls = {LIGHT_WALLPAPER, 0xFF808080, DARK_WALLPAPER, 0xFF3050A0, 0xFFA05030};
        int[] opacities = {10, 35, 46, 60, 92};
        for (int[] theme : themes) {
            surfaces.glassBase = theme[0];
            GlassLook full = GlassLook.SCHEME.withAmountPercent(100)
                .withAccentColors(theme[1], theme[2]);
            int tinted = full.tintBase(theme[0]);
            for (int opacity : opacities) {
                int frameTint = full.frameTint(opacity);
                for (int wall : walls) {
                    String at = Integer.toHexString(theme[0]) + " at " + opacity + " over "
                        + Integer.toHexString(wall);
                    wallpaper = GlassFrameTint.apply(frameTint, wall);
                    ChromeInk on = fresh();
                    on.noteBandGlass(GlassBackdropCache.Band.DOCK, opacity / 100f, true, 0f, 1f,
                        tinted);
                    OnGlass.Resolution label = on.onGlass(GlassBackdropCache.Band.DOCK,
                        STATUS_RECT, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
                    assertTrue(at + ": " + label.ratio,
                        label.ratio >= OnGlass.TARGET_BODY_TEXT && !label.shortfall);

                    wallpaper = wall;
                    ChromeInk bare = fresh();
                    bare.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, opacity / 100f, false,
                        0f, 0.8f, theme[0]);
                    boolean readBefore = !bare.onFixedInk(GlassBackdropCache.Band.KEYBOARD,
                        KEYBOARD_RECT, LABEL, LABEL, CAP, OnGlass.TARGET_BODY_TEXT).shortfall;
                    wallpaper = GlassFrameTint.apply(frameTint, wall);
                    ChromeInk keys = fresh();
                    keys.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, opacity / 100f, false,
                        0f, 0.8f, tinted);
                    OnGlass.Resolution key = keys.onFixedInk(GlassBackdropCache.Band.KEYBOARD,
                        KEYBOARD_RECT, LABEL, LABEL, CAP, OnGlass.TARGET_BODY_TEXT);
                    if (readBefore)
                        assertTrue(at + " key label: " + key.ratio, !key.shortfall);
                }
            }
        }
    }

    // --------------------------------------------------------- no Shade: the chrome ink on Clear

    /**
     * With Shade retired, the face is the wallpaper, the dim and the tinted base alone: the
     * chrome's label on Clear's 10% reads in both themes over dark, mid, light and coloured
     * wallpapers, the veil and the ink answering as they always did.
     */
    @Test
    public void theChromeInkReadsOnClearWithoutShade() {
        int[] walls = {LIGHT_WALLPAPER, 0xFF808080, 0xFF606060, DARK_WALLPAPER, 0xFF3050A0};
        for (int base : new int[] {NIGHT_BASE, DAY_BASE}) {
            surfaces.glassBase = base;
            for (int wall : walls) {
                wallpaper = wall;
                ChromeInk on = fresh();
                on.noteBandGlass(GlassBackdropCache.Band.DOCK, 0.10f, true, 0f, 1f, base);
                OnGlass.Resolution label = on.onGlass(GlassBackdropCache.Band.DOCK, STATUS_RECT,
                    LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
                assertTrue(Integer.toHexString(base) + " over " + Integer.toHexString(wall) + ": "
                    + label.ratio, label.ratio >= OnGlass.TARGET_BODY_TEXT && !label.shortfall);
            }
        }
    }
}
