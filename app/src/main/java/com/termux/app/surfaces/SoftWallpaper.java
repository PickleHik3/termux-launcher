package com.termux.app.surfaces;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.Nullable;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Soft wallpaper (appearance-layout-editor SPEC §3.4): the launcher's own wallpaper drawn with a
 * fixed blur and a mild dim. The dim half is plain {@code wallpaper_backdrop_dim} (see
 * {@link AppearanceLooks#storedDim}); this is the switch itself and the blur half, a
 * {@link RenderEffect} on the wallpaper backdrop view, so nothing in the chrome renderer has to
 * know. Below Android 12 there is no RenderEffect and Soft is the dim alone.
 *
 * <p>The switch is its own key rather than a reading of the dim, because a dim of 25 or more is
 * also something a user can set by hand.</p>
 */
public final class SoftWallpaper {

    private SoftWallpaper() {}

    /** Stored beside the other look keys; absent reads as off. */
    public static final String KEY_WALLPAPER_SOFT = "wallpaper_backdrop_soft";

    public static boolean isOn(@Nullable TermuxAppSharedPreferences prefs) {
        if (prefs == null || prefs.getSharedPreferences() == null)
            return false;
        // Through the utilities, as every getter reads: a Look the editor previews is seen here too.
        return SharedPreferenceUtils.getBoolean(prefs.getSharedPreferences(), KEY_WALLPAPER_SOFT,
            false);
    }

    public static void set(@Nullable TermuxAppSharedPreferences prefs, boolean on) {
        if (prefs == null || prefs.getSharedPreferences() == null)
            return;
        prefs.getSharedPreferences().edit().putBoolean(KEY_WALLPAPER_SOFT, on).apply();
    }

    /** Puts the blur on the backdrop, or takes it off; a no-op below API 31. */
    public static void apply(@Nullable View backdrop, @Nullable TermuxAppSharedPreferences prefs) {
        apply(backdrop, isOn(prefs));
    }

    public static void apply(@Nullable View backdrop, boolean on) {
        if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        if (!on) {
            backdrop.setRenderEffect(null);
            return;
        }
        float radius = AppearanceLooks.SOFT_BLUR_DP
            * backdrop.getResources().getDisplayMetrics().density;
        // The same effect instance for the same radius: the view's render node keeps an effect it
        // already holds and redraws nothing, where a new one each pass re-blurred the whole
        // screen's backdrop on every inset dispatch.
        backdrop.setRenderEffect(blurEffect(radius));
    }

    /** The last blur handed out and the radius it was made for; an effect is immutable. */
    @Nullable private static RenderEffect sBlurEffect;
    private static float sBlurRadiusPx = Float.NaN;

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private static RenderEffect blurEffect(float radiusPx) {
        RenderEffect effect = sBlurEffect;
        if (effect == null || Float.compare(radiusPx, sBlurRadiusPx) != 0) {
            effect = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP);
            sBlurEffect = effect;
            sBlurRadiusPx = radiusPx;
        }
        return effect;
    }
}
