package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;

/**
 * The pure choices behind the Live half of the wallpaper picker, kept apart from views so they
 * can be tested: whether Live is offered, which half the picker opens on, what a scene's page
 * starts with and which knobs it shows, whether Use as wallpaper touches the phone's wallpaper,
 * and the screen size a scene still is made at.
 */
public final class LiveSceneLogic {

    /** Which half of the picker shows. */
    public enum Kind { PHOTO, LIVE }

    private LiveSceneLogic() {}

    /** The half the picker opens on: Live while a scene is in use (and scenes can run), else Photo. */
    @NonNull
    public static Kind startKind(@NonNull LiveWallpaperChoice stored, boolean supported) {
        return supported && stored.scene != null ? Kind.LIVE : Kind.PHOTO;
    }

    /**
     * Whether a photo applied from the Photo page ends the scene: one set for Home does (the user
     * chose a photo themselves); a lock-only photo does not (scenes play on the home screen).
     */
    public static boolean photoEndsScene(@NonNull WallpaperSlots.Slot appliedSlot) {
        return appliedSlot == WallpaperSlots.Slot.HOME;
    }

    /**
     * Whether Use as wallpaper leaves the scene's page open: only when it did not go through (the
     * still could not be drawn, or the phone refused it), so the message has a page to sit on.
     * Otherwise the page returns to the grid, where "In use" confirms it.
     */
    public static boolean staysOnScenePage(@NonNull LiveWallpaperApplier.Result result) {
        return result == LiveWallpaperApplier.Result.STILL_FAILED || result == LiveWallpaperApplier.Result.FAILED;
    }

    /** Whether {@code kind} is the scene in use. */
    public static boolean inUse(@NonNull LiveWallpaperChoice stored, @NonNull String kind) {
        return kind.equals(stored.scene);
    }

    /**
     * What a scene's page opens with. The scene in use keeps every stored knob; another scene
     * keeps the speed and the frame rate (they mean the same everywhere) and starts its density and
     * colour at the defaults (a colour index names a different palette in every scene).
     */
    @NonNull
    public static LiveWallpaperChoice detailStart(@NonNull LiveWallpaperChoice stored, @NonNull String kind) {
        if (kind.equals(stored.scene)) return stored;
        return stored.withScene(kind).withDensity(0f).withPalette(0);
    }

    /** The colour chips of {@code kind}: the palette names, none when the scene has no palettes. */
    @NonNull
    public static int[] palettes(@NonNull String kind) {
        return LiveWallpaperChoice.supportsPalette(kind) ? LiveWallpaperChoice.paletteNames(kind) : new int[0];
    }

    /** Whether the Density knob shows. */
    public static boolean showsDensity(@NonNull String kind) {
        return LiveWallpaperChoice.supportsDensity(kind);
    }

    /** Whether the Colour chips show: the scene has palettes. */
    public static boolean showsColour(@NonNull String kind) {
        return palettes(kind).length > 0;
    }

    /** Back to photo shows only while a scene is in use. */
    public static boolean showsBackToPhoto(@NonNull LiveWallpaperChoice stored) {
        return stored.scene != null;
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

    /** The size, px, of a scene still: the screen's portrait size (as every wallpaper set here). Width first. */
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
     * The tallest the scene page's preview gets: a third of the screen's height, at most 280 dp, so
     * Speed and Frame rate stay in view under it on a phone-sized screen.
     */
    public static int previewMaxHeightPx(int screenHeightPx, float density) {
        return Math.max(1, Math.min(Math.round(280f * density), Math.round(screenHeightPx * 0.34f)));
    }

    /** The scene's name. */
    @StringRes
    public static int nameRes(@Nullable String kind) {
        if ("aurora".equals(kind)) return R.string.live_scene_aurora;
        if ("contour-waves".equals(kind)) return R.string.live_scene_contour_waves;
        if ("nebula".equals(kind)) return R.string.live_scene_nebula;
        if ("bloom".equals(kind)) return R.string.live_scene_bloom;
        return R.string.live_scene_particles;
    }

    /** One fact about the scene. */
    @StringRes
    public static int descRes(@Nullable String kind) {
        if ("aurora".equals(kind)) return R.string.live_scene_aurora_desc;
        if ("contour-waves".equals(kind)) return R.string.live_scene_contour_waves_desc;
        if ("nebula".equals(kind)) return R.string.live_scene_nebula_desc;
        if ("bloom".equals(kind)) return R.string.live_scene_bloom_desc;
        return R.string.live_scene_particles_desc;
    }
}
