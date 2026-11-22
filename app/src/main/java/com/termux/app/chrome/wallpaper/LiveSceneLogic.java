package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.WallpaperMotion;

/**
 * The pure choices behind the Video and Shader halves of the wallpaper picker, kept apart from
 * views so they can be tested: which kind the picker opens on, which shader is in use, whether
 * Back to photo shows, whether Use as wallpaper touches the phone's wallpaper, and the screen size
 * a motion still is made at.
 */
public final class LiveSceneLogic {

    private LiveSceneLogic() {}

    /** The kind the picker opens on (Photo page, Video page or Shader list): the one in use when motion can run, else Photo. */
    @NonNull
    public static WallpaperMotion.Kind startKind(@NonNull WallpaperMotion stored, boolean supported) {
        return supported ? stored.kind : WallpaperMotion.Kind.PHOTO;
    }

    /**
     * Whether a photo applied from the Photo page ends the motion: one set for Home does (the user
     * chose a photo themselves); a lock-only photo does not (motion plays on the home screen).
     */
    public static boolean photoEndsScene(@NonNull WallpaperSlots.Slot appliedSlot) {
        return appliedSlot == WallpaperSlots.Slot.HOME;
    }

    /**
     * Whether Use as wallpaper leaves its page open: only when it did not go through (the still
     * could not be drawn, or the phone refused it), so the message has a page to sit on.
     * Otherwise the page returns to the list, where "In use" confirms it.
     */
    public static boolean staysOnScenePage(@NonNull LiveWallpaperApplier.Result result) {
        return result == LiveWallpaperApplier.Result.STILL_FAILED || result == LiveWallpaperApplier.Result.FAILED;
    }

    /** Whether the shader {@code id} is the one in use. */
    public static boolean inUse(@NonNull WallpaperMotion stored, @Nullable String id) {
        return stored.kind == WallpaperMotion.Kind.SHADER && id != null && id.equals(stored.id);
    }

    /** Back to photo shows only while a video or a shader is in use. */
    public static boolean showsBackToPhoto(@NonNull WallpaperMotion stored) {
        return stored.moves();
    }

    /** "Also set as phone wallpaper" shows in terminal-app mode only. */
    public static boolean showsAlsoPhone(boolean launcherIsHomeApp) {
        return !launcherIsHomeApp;
    }

    /**
     * Whether Use as wallpaper puts a still on the phone's Home and Lock: always while the
     * launcher is the home app; in terminal-app mode only with Also set as phone wallpaper on.
     */
    public static boolean setsSystemWallpaper(boolean launcherIsHomeApp, boolean alsoPhone) {
        return launcherIsHomeApp || alsoPhone;
    }

    /** The size, px, of a motion still: the screen's portrait size (as every wallpaper set here). Width first. */
    @NonNull
    public static int[] stillSize(int screenW, int screenH) {
        int w = Math.max(1, Math.min(screenW, screenH));
        int h = Math.max(1, Math.max(screenW, screenH));
        return new int[] {w, h};
    }

    /** A phone-proportioned frame's height for {@code widthPx}: the screen's portrait aspect. */
    public static int frameHeight(int widthPx, int screenW, int screenH) {
        int[] s = stillSize(screenW, screenH);
        return Math.max(1, Math.round(widthPx * (s[1] / (float) s[0])));
    }

    /**
     * The tallest a preview gets: a third of the screen's height, at most 280 dp, so the knobs
     * stay in view under it on a phone-sized screen.
     */
    public static int previewMaxHeightPx(int screenHeightPx, float density) {
        return Math.max(1, Math.min(Math.round(280f * density), Math.round(screenHeightPx * 0.34f)));
    }
}
