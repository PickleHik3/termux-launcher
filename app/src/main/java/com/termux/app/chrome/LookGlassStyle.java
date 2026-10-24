package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Arrays;
import java.util.List;

/**
 * The Look's glass ({@link LookGlassSpec}: Base's blur, opacity, grain, tint and corners) as a
 * {@link PaneSurfaceStyle}, so a Home widget is dressed by {@link com.termux.app.terminal.PaneGlass}
 * exactly as a terminal pane is, from the same shared blur frames, film grain, rim, Fancier Glass
 * look and wallpaper pan. Only the numbers differ: these are Base's, never a detached surface's.
 *
 * <p>One per activity. It asks the blur cache for a frame only while a widget wears it
 * ({@link #setWorn}), so a Home with no glass widget holds no frame for them; the frame it handed
 * out is remembered ({@link #holds}) for the cache's in-use scan, as the panes' is.</p>
 */
public final class LookGlassStyle implements PaneSurfaceStyle, WidgetInkSource {

    /** What only the activity knows. */
    public interface Host {
        @Nullable TermuxAppSharedPreferences preferences();
        /** The view the wallpaper frame is captured against. */
        @Nullable View wallpaperFrame();
        /** Whether the readable picture is one to blur at all (not one nobody chose). */
        boolean wallpaperBlurs();
        /** The chrome's glass base colour, in the scheme; its alpha is ignored. */
        int glassBaseColor();
        /** Fancier Glass as it stands, or null for the plain frost. */
        @Nullable GlassRefraction.Look refraction();
        @Nullable WallpaperParallax parallax();
    }

    @NonNull private final Context mContext;
    @NonNull private final ChromeRenderer mChrome;
    @NonNull private final Host mHost;
    private boolean mWorn;
    @Nullable private Bitmap mFrame;
    @Nullable private Bitmap mClearFrame;

    public LookGlassStyle(@NonNull Context context, @NonNull ChromeRenderer chrome,
                          @NonNull Host host) {
        mContext = context;
        mChrome = chrome;
        mHost = host;
    }

    /** The Look as stored this instant. */
    @NonNull
    public LookGlassSpec spec() {
        // Widgets stand on Home, so they wear Home's Look (ADR 0013).
        return LookGlassSpec.read(mHost.preferences(),
            TermuxAppSharedPreferences.WallHalf.HOME);
    }

    /**
     * Whether any widget wears this glass. Off, no frame is asked for and none is held; on, the
     * next pass asks for one, and its landing re-dresses the wearers through {@link #key}.
     */
    public void setWorn(boolean worn) {
        if (mWorn == worn) return;
        mWorn = worn;
        if (!worn) {
            mFrame = null;
            mClearFrame = null;
        }
    }

    public boolean isWorn() {
        return mWorn;
    }

    /** Whether some widget is drawing {@code frame}; for the blur cache's in-use scan. */
    public boolean holds(@Nullable Bitmap frame) {
        return frame != null && (frame == mFrame || frame == mClearFrame);
    }

    /** The cache let every frame go; the next pass asks again. */
    public void onCacheCleared() {
        mFrame = null;
        mClearFrame = null;
    }

    /**
     * What the wearers are dressed with, frozen, for the chrome pass's change check: the Look's
     * numbers and the frames this style hands out now.
     */
    @NonNull
    public Object key() {
        GlassRefraction.Look refraction = paneGlassRefraction();
        List<Object> key = Arrays.asList(spec(), mWorn, paneGlassTintColor(),
            identity(paneGlassBlurFrame()), identity(paneGlassClearFrame()),
            new Rect(paneGlassBlurFrameRect()), identity(paneGlassFrostFilter()), refraction);
        return key;
    }

    /** Bitmaps and filters compare by identity: a new blur frame is a new bitmap. */
    @Nullable
    private static Object identity(@Nullable Object value) {
        return value == null ? null : new Identity(value);
    }

    private static final class Identity {
        private final Object mValue;
        Identity(@NonNull Object value) { mValue = value; }
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof Identity && ((Identity) other).mValue == mValue;
        }
        @Override public int hashCode() { return System.identityHashCode(mValue); }
    }

    // ----- WidgetInkSource ---------------------------------------------------------------------

    @Override @NonNull public ChromeInk.Polarity widgetPolarity() {
        return mChrome.ink().polarity();
    }

    /**
     * Ready when the Look's glass needs no frame (no blur), or when the frame at Base's blur is
     * resident. Never asks for one: the wearers' own pass does that.
     */
    @Override public boolean widgetFrameReady() {
        LookGlassSpec spec = spec();
        if (!spec.wantsBlurFrame()) return true;
        return mHost.wallpaperFrame() != null && mHost.wallpaperBlurs()
            && mChrome.blurCache().hasRadius(spec.blurDp);
    }

    @Override @NonNull public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                           int veilColor, int textInk,
                                                           int graphicInk) {
        return mChrome.ink().widget(rootRect, body, veilColor, textInk, graphicInk);
    }

    @Override public int widgetWallpaper(@NonNull Rect rootRect) {
        return mChrome.ink().widgetWallpaper(rootRect);
    }

    // ----- PaneSurfaceStyle --------------------------------------------------------------------

    @Override public boolean isPaneGlassActive() {
        return spec().slabShown();
    }

    @Override @Nullable public Bitmap paneGlassBlurFrame() {
        LookGlassSpec spec = spec();
        View wallpaperFrame = mHost.wallpaperFrame();
        if (!mWorn || !spec.wantsBlurFrame() || wallpaperFrame == null || !mHost.wallpaperBlurs()) {
            mFrame = null;
            return null;
        }
        Bitmap frame = mChrome.blurCache().obtain(spec.blurDp, wallpaperFrame);
        mFrame = frame != null && !frame.isRecycled() ? frame : null;
        return mFrame;
    }

    @Override @Nullable public Bitmap paneGlassClearFrame() {
        LookGlassSpec spec = spec();
        View wallpaperFrame = mHost.wallpaperFrame();
        if (!mWorn || !spec.slabShown() || wallpaperFrame == null || !mHost.wallpaperBlurs()
                || !WallpaperFrostPainter.wantsClearFrame(paneGlassRefraction(), spec.blurDp)) {
            mClearFrame = null;
            return null;
        }
        Bitmap clear = mChrome.blurCache().obtain(GlassRefraction.CLARITY_RADIUS_DP,
            wallpaperFrame);
        mClearFrame = clear != null && !clear.isRecycled() ? clear : null;
        return mClearFrame;
    }

    @Override @NonNull public Rect paneGlassBlurFrameRect() {
        return mChrome.blurCache().frameRectRef();
    }

    @Override public boolean paneGlassCrossfade() {
        return mChrome.blurCache().isCrossfadedRadius(spec().blurDp)
            && !com.termux.app.ReducedMotion.isEnabled(mContext);
    }

    @Override @Nullable public ColorFilter paneGlassFrostFilter() {
        return GlassFilters.frost(spec().opacityPercent);
    }

    @Override public int paneGlassTintColor() {
        LookGlassSpec spec = spec();
        return mChrome.glass().look(spec.tintPercent)
            .flatTint(spec.surfaceColor(mHost.glassBaseColor()));
    }

    @Override @Nullable public Drawable paneGlassGrainLayer() {
        int grain = paneGlassGrainStrength();
        return grain > 0 ? mChrome.glass().grainLayer(grain) : null;
    }

    @Override public int paneGlassGrainStrength() {
        return spec().grainPercent;
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

    @Override public float paneGlassCornerRadiusPx() {
        return spec().cornerRadiusDp * mContext.getResources().getDisplayMetrics().density;
    }

    @Override public int paneCornerRadiusDp() {
        return spec().cornerRadiusDp;
    }

    /** A widget keeps its own spacing; the Look gives it none. */
    @Override public int paneGapDp() {
        return 0;
    }

    @Override @Nullable public WallpaperParallax wallpaperParallax() {
        return mHost.parallax();
    }

    @Override @Nullable public GlassRefraction.Look paneGlassRefraction() {
        return mHost.refraction();
    }
}
