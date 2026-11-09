package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveWallpaperChoiceTest {

    @Test
    public void everySceneNameReadsAsNoneNowTheScenesAreGone() {
        for (String kind : new String[] {"aurora", "contour-waves", "nebula", "bloom", "particles",
            "coastal-sunset", "contour", "filament", "", "off"}) {
            assertNull(kind, LiveWallpaperChoice.parseScene(kind));
        }
        assertNull(LiveWallpaperChoice.parseScene(null));
        assertEquals(0, LiveWallpaperChoice.USER_KINDS.length);
    }

    @Test
    public void speedClampsToRange() {
        assertEquals(0.25f, LiveWallpaperChoice.clampSpeed(0f), 0f);
        assertEquals(0.25f, LiveWallpaperChoice.clampSpeed(-3f), 0f);
        assertEquals(2f, LiveWallpaperChoice.clampSpeed(9f), 0f);
        assertEquals(1.5f, LiveWallpaperChoice.clampSpeed(1.5f), 0f);
        assertEquals(1f, LiveWallpaperChoice.clampSpeed(Float.NaN), 0f);
        assertEquals(1f, LiveWallpaperChoice.clampSpeed(Float.POSITIVE_INFINITY), 0f);
    }

    @Test
    public void rateIsFifteenThirtyOrSixty() {
        assertEquals(15, LiveWallpaperChoice.clampRate(15));
        assertEquals(30, LiveWallpaperChoice.clampRate(30));
        assertEquals(60, LiveWallpaperChoice.clampRate(60));
        assertEquals(30, LiveWallpaperChoice.clampRate(0));
        assertEquals(30, LiveWallpaperChoice.clampRate(90));
        assertEquals(30, LiveWallpaperChoice.clampRate(-15));
    }

    @Test
    public void densityAndPaletteClamp() {
        assertEquals(-1f, LiveWallpaperChoice.clampDensity(-5f), 0f);
        assertEquals(1f, LiveWallpaperChoice.clampDensity(5f), 0f);
        assertEquals(0.25f, LiveWallpaperChoice.clampDensity(0.25f), 0f);
        assertEquals(0f, LiveWallpaperChoice.clampDensity(Float.NaN), 0f);
        assertEquals(0, LiveWallpaperChoice.clampPalette(-1));
        assertEquals(2, LiveWallpaperChoice.clampPalette(7));
        assertEquals(1, LiveWallpaperChoice.clampPalette(1));
    }

    @Test
    public void constructorNormalisesEveryField() {
        LiveWallpaperChoice c = new LiveWallpaperChoice("contour", 10f, 44, -9f, 12);
        assertNull(c.scene);
        assertEquals(2f, c.speed, 0f);
        assertEquals(30, c.rateHz);
        assertEquals(-1f, c.density, 0f);
        assertEquals(2, c.palette);
    }

    @Test
    public void noneHasDefaults() {
        LiveWallpaperChoice c = LiveWallpaperChoice.none();
        assertNull(c.scene);
        assertEquals(1f, c.speed, 0f);
        assertEquals(30, c.rateHz);
        assertEquals(0f, c.density, 0f);
        assertEquals(0, c.palette);
        assertEquals(c, LiveWallpaperChoice.none());
    }

    @Test
    public void withersKeepTheOtherFields() {
        LiveWallpaperChoice c = LiveWallpaperChoice.none().withScene("aurora").withSpeed(1.5f)
            .withRateHz(60).withDensity(0.5f).withPalette(2);
        assertNull(c.scene);
        assertEquals(1.5f, c.speed, 0f);
        assertEquals(60, c.rateHz);
        assertEquals(0.5f, c.density, 0f);
        assertEquals(2, c.palette);
        assertEquals(c, new LiveWallpaperChoice(null, 1.5f, 60, 0.5f, 2));
    }

    @Test
    public void supportStartsAtApi29() {
        assertFalse(LiveWallpaperChoice.isSupported(28));
        assertTrue(LiveWallpaperChoice.isSupported(29));
        assertTrue(LiveWallpaperChoice.isSupported(35));
    }

    @Test
    public void noKindHasDensityOrPalette() {
        assertFalse(LiveWallpaperChoice.supportsDensity("aurora"));
        assertFalse(LiveWallpaperChoice.supportsPalette("particles"));
        assertEquals(0, LiveWallpaperChoice.paletteNames("aurora").length);
        assertEquals(0, LiveWallpaperChoice.paletteNames(null).length);
    }

    @Test
    public void choiceKeysAreRecognised() {
        for (String key : LiveWallpaperChoice.KEYS) assertTrue(LiveWallpaperChoice.isChoiceKey(key));
        assertFalse(LiveWallpaperChoice.isChoiceKey("wallpaper_lock_choice"));
        assertFalse(LiveWallpaperChoice.isChoiceKey(null));
    }
}
