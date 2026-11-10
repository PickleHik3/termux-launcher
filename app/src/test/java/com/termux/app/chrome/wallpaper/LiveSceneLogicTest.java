package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.WallpaperMotion;

import org.junit.Test;

public class LiveSceneLogicTest {

    @Test
    public void picker_opensOnTheKindInUseOnlyWhenSupported() {
        assertEquals(WallpaperMotion.Kind.SHADER, LiveSceneLogic.startKind(WallpaperMotion.shader("abc"), true));
        assertEquals(WallpaperMotion.Kind.VIDEO, LiveSceneLogic.startKind(WallpaperMotion.video("loop.mp4"), true));
        assertEquals(WallpaperMotion.Kind.PHOTO, LiveSceneLogic.startKind(WallpaperMotion.photo(), true));
        // Below Android 10 a stored motion cannot run: the picker stays on Photo.
        assertEquals(WallpaperMotion.Kind.PHOTO, LiveSceneLogic.startKind(WallpaperMotion.shader("abc"), false));
        assertEquals(WallpaperMotion.Kind.PHOTO, LiveSceneLogic.startKind(WallpaperMotion.video("loop.mp4"), false));
    }

    @Test
    public void inUse_isTheStoredShaderOnly() {
        assertTrue(LiveSceneLogic.inUse(WallpaperMotion.shader("abc"), "abc"));
        assertFalse(LiveSceneLogic.inUse(WallpaperMotion.shader("abc"), "xyz"));
        assertFalse(LiveSceneLogic.inUse(WallpaperMotion.photo(), "abc"));
        // A video named like a shader id is not that shader.
        assertFalse(LiveSceneLogic.inUse(WallpaperMotion.video("abc"), "abc"));
        assertFalse(LiveSceneLogic.inUse(WallpaperMotion.shader("abc"), null));
    }

    @Test
    public void backToPhoto_onlyWhileAVideoOrShaderIsInUse() {
        assertTrue(LiveSceneLogic.showsBackToPhoto(WallpaperMotion.shader("abc")));
        assertTrue(LiveSceneLogic.showsBackToPhoto(WallpaperMotion.video("loop.mp4")));
        assertFalse(LiveSceneLogic.showsBackToPhoto(WallpaperMotion.photo()));
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
    public void photoEndsScene_onlyForAHomePhoto() {
        assertTrue(LiveSceneLogic.photoEndsScene(WallpaperSlots.Slot.HOME));
        assertFalse(LiveSceneLogic.photoEndsScene(WallpaperSlots.Slot.LOCK));
    }

    @Test
    public void apply_returnsToTheListUnlessItFailed() {
        assertFalse(LiveSceneLogic.staysOnScenePage(LiveWallpaperApplier.Result.SET));
        assertFalse(LiveSceneLogic.staysOnScenePage(LiveWallpaperApplier.Result.IN_APP_ONLY));
        assertTrue(LiveSceneLogic.staysOnScenePage(LiveWallpaperApplier.Result.STILL_FAILED));
        assertTrue(LiveSceneLogic.staysOnScenePage(LiveWallpaperApplier.Result.FAILED));
    }
}
