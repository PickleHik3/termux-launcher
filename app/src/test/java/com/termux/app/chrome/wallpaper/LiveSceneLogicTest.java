package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;

import org.junit.Test;

public class LiveSceneLogicTest {

    private static LiveWallpaperChoice stored(String scene) {
        return new LiveWallpaperChoice(scene, 1.5f, 60, 0.4f, 2);
    }

    @Test
    public void picker_opensOnLiveOnlyWhileASceneIsInUseAndSupported() {
        assertEquals(LiveSceneLogic.Kind.LIVE, LiveSceneLogic.startKind(stored("aurora"), true));
        assertEquals(LiveSceneLogic.Kind.PHOTO, LiveSceneLogic.startKind(LiveWallpaperChoice.none(), true));
        // Below Android 13 a stored scene cannot run: the picker stays on Photo.
        assertEquals(LiveSceneLogic.Kind.PHOTO, LiveSceneLogic.startKind(stored("aurora"), false));
    }

    @Test
    public void inUse_isTheStoredSceneOnly() {
        assertTrue(LiveSceneLogic.inUse(stored("nebula"), "nebula"));
        assertFalse(LiveSceneLogic.inUse(stored("nebula"), "bloom"));
        assertFalse(LiveSceneLogic.inUse(LiveWallpaperChoice.none(), "nebula"));
    }

    @Test
    public void detailStart_sceneInUseKeepsEveryKnob() {
        LiveWallpaperChoice s = stored("aurora");
        assertEquals(s, LiveSceneLogic.detailStart(s, "aurora"));
    }

    @Test
    public void detailStart_otherSceneKeepsSpeedAndRateButNotDensityOrColour() {
        LiveWallpaperChoice start = LiveSceneLogic.detailStart(stored("aurora"), "particles");
        assertEquals("particles", start.scene);
        assertEquals(1.5f, start.speed, 0f);
        assertEquals(60, start.rateHz);
        assertEquals(0f, start.density, 0f);
        assertEquals(0, start.palette);
    }

    @Test
    public void detailStart_withNothingInUseIsDefaultsOnTheScene() {
        LiveWallpaperChoice start = LiveSceneLogic.detailStart(LiveWallpaperChoice.none(), "bloom");
        assertEquals("bloom", start.scene);
        assertEquals(LiveWallpaperChoice.DEFAULT_SPEED, start.speed, 0f);
        assertEquals(LiveWallpaperChoice.DEFAULT_RATE_HZ, start.rateHz);
    }

    @Test
    public void knobs_densityAndColourOnlyWhereTheSceneHasThem() {
        for (String kind : LiveWallpaperChoice.USER_KINDS) {
            assertEquals(kind, LiveWallpaperChoice.supportsDensity(kind), LiveSceneLogic.showsDensity(kind));
            assertEquals(kind, LiveWallpaperChoice.supportsPalette(kind), LiveSceneLogic.showsColour(kind));
            assertEquals(kind, LiveSceneLogic.showsColour(kind), LiveSceneLogic.palettes(kind).length > 0);
        }
        assertTrue(LiveSceneLogic.showsDensity("aurora"));
        assertTrue(LiveSceneLogic.showsColour("particles"));
        assertFalse(LiveSceneLogic.showsDensity("nebula"));
        assertFalse(LiveSceneLogic.showsColour("contour-waves"));
        assertFalse(LiveSceneLogic.showsColour("bloom"));
        assertArrayEquals(LiveWallpaperChoice.paletteNames("aurora"), LiveSceneLogic.palettes("aurora"));
    }

    @Test
    public void backToPhoto_onlyWhileASceneIsInUse() {
        assertTrue(LiveSceneLogic.showsBackToPhoto(stored("bloom")));
        assertFalse(LiveSceneLogic.showsBackToPhoto(LiveWallpaperChoice.none()));
    }

    @Test
    public void alsoPhone_showsInTerminalAppModeOnly() {
        assertFalse(LiveSceneLogic.showsAlsoPhone(true));
        assertTrue(LiveSceneLogic.showsAlsoPhone(false));
    }

    @Test
    public void systemWallpaper_homeAppAlways_terminalAppOnlyWhenOptedIn() {
        assertTrue(LiveSceneLogic.setsSystemWallpaper(true, false));
        assertTrue(LiveSceneLogic.setsSystemWallpaper(true, true));
        assertFalse(LiveSceneLogic.setsSystemWallpaper(false, false));
        assertTrue(LiveSceneLogic.setsSystemWallpaper(false, true));
    }

    @Test
    public void stillSize_isThePortraitScreen() {
        assertArrayEquals(new int[] {1080, 2400}, LiveSceneLogic.stillSize(1080, 2400));
        assertArrayEquals(new int[] {1080, 2400}, LiveSceneLogic.stillSize(2400, 1080));
        assertArrayEquals(new int[] {1, 1}, LiveSceneLogic.stillSize(0, 0));
    }

    @Test
    public void frameHeight_followsTheScreenAspect() {
        assertEquals(2000, LiveSceneLogic.frameHeight(1000, 1080, 2160));
        assertEquals(2000, LiveSceneLogic.frameHeight(1000, 2160, 1080));
        assertEquals(1, LiveSceneLogic.frameHeight(0, 1080, 2160));
    }

    @Test
    public void previewMaxHeight_isAThirdOfTheScreenAndAtMost280dp() {
        // 2400px at 2.75 density: a third is 816px, more than 280dp (770px): 280dp wins.
        assertEquals(770, LiveSceneLogic.previewMaxHeightPx(2400, 2.75f));
        // A short screen: 34% of 1280px = 435px, less than 280dp = 560px at density 2.
        assertEquals(435, LiveSceneLogic.previewMaxHeightPx(1280, 2f));
        assertEquals(1, LiveSceneLogic.previewMaxHeightPx(0, 2f));
    }

    @Test
    public void everyUserSceneHasANameAndAFact() {
        for (String kind : LiveWallpaperChoice.USER_KINDS) {
            assertTrue(kind, LiveSceneLogic.nameRes(kind) != 0);
            assertTrue(kind, LiveSceneLogic.descRes(kind) != 0);
        }
        assertEquals(R.string.live_scene_contour_waves, LiveSceneLogic.nameRes("contour-waves"));
        assertEquals(R.string.live_scene_bloom_desc, LiveSceneLogic.descRes("bloom"));
    }
}
