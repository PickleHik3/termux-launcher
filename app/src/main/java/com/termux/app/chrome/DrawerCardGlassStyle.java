package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.GlassBackdropTint;
import com.termux.app.launcher.drawer.AppDrawerSectionSwitchGlass;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Arrays;

/**
 * The open app drawer's own card surface as a {@link PaneSurfaceStyle}, so the section switch's
 * slab ({@code PageBarGlass.dress}) shows what the card shows: the wallpaper frost at the card's
 * blur radius (the very cache entry the card's frost holds, so no new radius), its refraction, the
 * veil the plane's picture wears and the plane's tint, lifted a little ({@link
 * AppDrawerSectionSwitchGlass#tint}). Global's Look is not consulted.
 *
 * <p>One per activity. It asks the blur cache for a frame only while the open Default drawer wants
 * it ({@link #setWanted}); the frames it handed out are remembered ({@link #holds}) for the
 * cache's in-use scan.</p>
 */
public final class DrawerCardGlassStyle implements PaneSurfaceStyle, WidgetInkSource {

    /** What only the activity knows. */
    public interface Host {
        @Nullable TermuxAppSharedPreferences preferences();
        /** The view the wallpaper frame is captured against. */
        @Nullable View wallpaperFrame();
        /** Whether the wallpaper shows through the chrome at all (the frost's own test). */
        boolean wallpaperPassthrough();
        /** The card's blur radius: the dock's, which the card's frost draws at. */
        int cardBlurDp();
        /** The chrome's glass base colour for the dock and the card, in the scheme. */
        int glassBaseColor();
        /** The sheets' Fancier Glass look (Global's element, the resting page), or null. */
        @Nullable GlassRefraction.Look refraction();
        @Nullable WallpaperParallax parallax();
    }

    @NonNull private final Context mContext;
    @NonNull private final ChromeRenderer mChrome;
    @NonNull private final Host mHost;
    private boolean mWanted;
    @Nullable private Bitmap mFrame;
    @Nullable private Bitmap mClearFrame;

    public DrawerCardGlassStyle(@NonNull Context context, @NonNull ChromeRenderer chrome,
                                @NonNull Host host) {
        mContext = context;
        mChrome = chrome;
        mHost = host;
    }

    /** Whether the open Default drawer's switch wears this glass. Off, no frame is held. */
    public void setWanted(boolean wanted) {
        if (mWanted == wanted) return;
        mWanted = wanted;
        if (!wanted) onCacheCleared();
    }

    /** Whether the switch is drawing {@code frame}; for the blur cache's in-use scan. */
    public boolean holds(@Nullable Bitmap frame) {
        return frame != null && (frame == mFrame || frame == mClearFrame);
    }

    /** The cache let every frame go; the next pass asks again. */
    public void onCacheCleared() {
        mFrame = null;
        mClearFrame = null;
    }

    /**
     * What the switch is dressed with, frozen, for the content view's change check: the numbers
     * and the frames this style hands out now.
     */
    @NonNull
    public Object key() {
        Bitmap frame = paneGlassBlurFrame();
        return Arrays.asList(mWanted, isPaneGlassActive(), mHost.cardBlurDp(), paneGlassTintColor(),
            identity(frame), identity(paneGlassClearFrame()), new Rect(paneGlassBlurFrameRect()),
            paneGlassGrainStrength(), paneGlassRefraction());
    }

    /** Bitmaps compare by identity: a new blur frame is a new bitmap. */
    @NonNull
    private static Object identity(@Nullable Bitmap value) {
        return value == null ? Boolean.FALSE : new Identity(value);
    }

    private static final class Identity {
        private final Object mValue;
        Identity(@NonNull Object value) { mValue = value; }
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof Identity && ((Identity) other).mValue == mValue;
        }
        @Override public int hashCode() { return System.identityHashCode(mValue); }
    }

    private float opacity() {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        // As AppDrawerController.prepareOverlay reads it for the plane's tint.
        return preferences == null ? 0.5f : SheetGlass.of(preferences).tintAlpha();
    }

    /** The frost's own test ({@code WallpaperFrostPainter.applyFrost}'s {@code wanted}). */
    private boolean wantsFrost() {
        return mWanted && mHost.cardBlurDp() > 0 && mHost.wallpaperFrame() != null
            && (mHost.wallpaperPassthrough() || !PanelBackdrop.pictured());
    }

    // ----- PaneSurfaceStyle --------------------------------------------------------------------

    /** Active while a frame at the card's radius is resident; else the switch draws its wash. */
    @Override public boolean isPaneGlassActive() {
        return paneGlassBlurFrame() != null;
    }

    @Override @Nullable public Bitmap paneGlassBlurFrame() {
        View wallpaperFrame = mHost.wallpaperFrame();
        if (!wantsFrost() || wallpaperFrame == null) {
            mFrame = null;
            return null;
        }
        Bitmap frame = mChrome.blurCache().obtain(mHost.cardBlurDp(), wallpaperFrame);
        mFrame = frame != null && !frame.isRecycled() ? frame : null;
        return mFrame;
    }

    @Override @Nullable public Bitmap paneGlassClearFrame() {
        View wallpaperFrame = mHost.wallpaperFrame();
        if (!wantsFrost() || wallpaperFrame == null) {
            mClearFrame = null;
            return null;
        }
        Bitmap clear = WallpaperFrostPainter.sharpFrame(mChrome.blurCache(),
            paneGlassRefraction(), mHost.cardBlurDp(), wallpaperFrame);
        mClearFrame = clear != null && !clear.isRecycled() ? clear : null;
        return mClearFrame;
    }

    @Override @NonNull public Rect paneGlassBlurFrameRect() {
        return mChrome.blurCache().frameRectRef();
    }

    @Override public boolean paneGlassCrossfade() {
        return mChrome.blurCache().isCrossfadedRadius(mHost.cardBlurDp())
            && !com.termux.app.ReducedMotion.isEnabled(mContext);
    }

    /** The filter the card's frost gets ({@code WallpaperFrostPainter.applyFrost}'s setColorFilter). */
    @Override @Nullable public ColorFilter paneGlassFrostFilter() {
        return GlassFilters.frost();
    }

    @Override public int paneGlassTintColor() {
        return AppDrawerSectionSwitchGlass.tint(GlassBackdropTint.colorFor(1f),
            mHost.glassBaseColor(), opacity());
    }

    @Override @Nullable public Drawable paneGlassGrainLayer() {
        int grain = paneGlassGrainStrength();
        return grain > 0 ? mChrome.glass().grainLayer(grain) : null;
    }

    @Override public int paneGlassGrainStrength() {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        return preferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN
            : SheetGlass.of(preferences).grainPercent;
    }

    @Override public boolean paneGlassRimWanted() {
        return true;
    }

    @Override @Nullable public Drawable paneGlassRim(float radiusPx) {
        return mChrome.glass().rimDrawable(radiusPx);
    }

    @Override @Nullable public Drawable paneRimDrawable(float radiusPx) {
        return mChrome.glass().rimDrawable(radiusPx);
    }

    /** The switch is a capsule whose radius {@code dress} is handed; this one is never asked. */
    @Override public float paneGlassCornerRadiusPx() {
        return 0f;
    }

    @Override public int paneGapDp() {
        return 0;
    }

    @Override @Nullable public WallpaperParallax wallpaperParallax() {
        return mHost.parallax();
    }

    // ----- WidgetInkSource: the capsule's ink is solved as Home's page bar pill's is -------------

    @Override @NonNull public ChromeInk.Polarity widgetPolarity() {
        return mChrome.ink().widgetPolarity();
    }

    /** Ready when the card's own frame is resident, the frame the capsule's glass is cut from. */
    @Override public boolean widgetFrameReady() {
        return isPaneGlassActive();
    }

    /** Solved at the sheets' opacity, Global's with its one floor ({@link SheetGlass}). */
    @Override @NonNull public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                           int veilColor, int textInk,
                                                           int graphicInk) {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        int opacityPercent = preferences == null ? Math.round(opacity() * 100f)
            : SheetGlass.of(preferences).opacityPercent;
        return mChrome.ink().widget(rootRect, body, veilColor, textInk, graphicInk,
            opacityPercent);
    }

    @Override @Nullable public android.graphics.ColorMatrix widgetEvenOut(@NonNull Rect rootRect) {
        return mChrome.ink().widgetEvenOutMatrix(rootRect);
    }

    @Override public int widgetWallpaper(@NonNull Rect rootRect) {
        return mChrome.ink().widgetWallpaper(rootRect);
    }

    @Override @Nullable public GlassRefraction.Look paneGlassRefraction() {
        return mHost.refraction();
    }
}
