package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.DockGlassRendering;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The chrome module's pure decisions: the glass tuning curves, the unified-keyboard-material rule,
 * the wallpaper-read prompt gate, and the wallpaper-mode preference swap. Nothing here touches a
 * view or an Activity, so every one of them is a plain unit test.
 */
public final class ChromePolicy {

    private ChromePolicy() {}

    /**
     * How far the wallpaper frame is downsampled before it is blurred. Also the cap the realtime
     * blur views run at, so a live-blur surface and a pre-blurred crop of the same radius match.
     */
    public static final int ACCESSORY_BLUR_DOWNSAMPLE_FACTOR = 4;

    /** Literal opacity endpoint: 100% is an opaque material and 0% is fully transparent. */
    public static final int DOCK_GLASS_BASE_MAX_ALPHA = 255;

    public static int dockGlassBaseAlpha(float opacity) {
        return DockGlassRendering.baseAlpha(opacity);
    }

    public static int dockGlassGrainAlpha(int grainPercent) {
        return DockGlassRendering.grainAlpha(grainPercent);
    }

    public static boolean dockBlurEnabled(int blurRadiusDp) {
        return DockGlassRendering.blurEnabled(blurRadiusDp);
    }

    /**
     * The default edge-to-edge dock and a glass-matched keyboard are one rectangular material.
     * Render one backdrop through both instead of placing two independently cropped glass layers
     * next to each other. Floating/capsule styling deliberately remains on its separate path.
     */
    public static boolean shouldUseUnifiedDefaultKeyboardGlassSurface(boolean toolbarShown,
                                                                      boolean keyboardShown,
                                                                      boolean roundedDockStyle,
                                                                      boolean keyboardGlassSurface) {
        return toolbarShown && keyboardShown && !roundedDockStyle && keyboardGlassSurface;
    }

    /**
     * True when the scheme's background color or the opacity slider repaints the surface.
     *
     * <p>"Match all surfaces" outranks both. An edited keyboard scheme sets a background swatch,
     * which used to drop the keyboard onto its own local surface painted in that colour — a
     * surface no dock/status opacity write reaches, so the keyboard sat visibly lighter than
     * every other surface until the keyboard section was reset. While surfaces are normalized the
     * keyboard renders the shared material and the scheme keeps only its key colours.</p>
     */
    public static boolean hasInAppKeyboardBackgroundOverride(boolean surfacesNormalized,
                                                             @Nullable Integer schemeBackgroundColor,
                                                             int backgroundOpacityPercent,
                                                             int sharedMaterialOpacityPercent) {
        if (surfacesNormalized)
            return false;
        // Compare against the opacity the shared dock material actually renders, not a
        // compile-time default: a detached row holding the shared number repaints nothing, and
        // judging it against a constant is what let a default flip silently split the unified
        // dock/keyboard/nav glass sheet on every install detached at the old value.
        return schemeBackgroundColor != null
            || backgroundOpacityPercent != sharedMaterialOpacityPercent;
    }

    /**
     * The prompt is worth showing only when the wallpaper read has already failed, the bands are
     * actually sourcing the wallpaper, the permission is the thing standing in the way, and the
     * user has not been asked before.
     */
    public static boolean shouldPromptForWallpaperRead(boolean readDenied, boolean wallpaperPassthrough,
                                                       boolean permissionGranted, boolean alreadyPrompted) {
        return readDenied && wallpaperPassthrough && !permissionGranted && !alreadyPrompted;
    }

    public static void applyWallpaperModePreferences(@NonNull TermuxAppSharedPreferences preferences,
                                                     boolean enabled) {
        if (enabled) {
            preferences.setUseSystemWallpaperEnabled(true);
            int terminal = preferences.getWallpaperEnabledTerminalBackgroundOpacity();
            int appBar = preferences.getWallpaperEnabledAppBarOpacity();
            int blur = preferences.getWallpaperEnabledExtraKeysBlurRadius();
            // Restore the shared layer first, so every surface that was following it comes back
            // with it, then place the surfaces that remembered something different. Writing these
            // through the plain setters would have made each one overwrite the last, because while
            // they are linked they all name the same Base value.
            preferences.setSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, appBar);
            preferences.setSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.BLUR, blur);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, appBar);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.CANVAS,
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, terminal);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
                TermuxAppSharedPreferences.SurfaceProperty.BLUR, blur);
        } else {
            preferences.setWallpaperEnabledTerminalBackgroundOpacity(preferences.getTerminalBackgroundOpacity());
            preferences.setWallpaperEnabledAppBarOpacity(preferences.getAppBarOpacity());
            preferences.setWallpaperEnabledExtraKeysBlurRadius(preferences.getExtraKeysBlurRadius());
            preferences.setUseSystemWallpaperEnabled(false);
            // Opaque and unblurred is a uniform state, so it belongs on the shared layer: every
            // surface goes opaque, including the ones this policy never named individually.
            preferences.setSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 100);
            preferences.setSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.BLUR, 0);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 100);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.CANVAS,
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 100);
            preferences.setSurfaceValueExact(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
                TermuxAppSharedPreferences.SurfaceProperty.BLUR, 0);
        }
    }
}
