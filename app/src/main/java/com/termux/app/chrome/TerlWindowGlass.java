package com.termux.app.chrome;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.window.WindowLayer;

/**
 * A Terl window's glass (live window residency): an entry of the glass service at the tiles' or
 * the Floats' priority, worn through {@link TerlGlassWear}, drawn by the route the desktop set at
 * rest. On the parity route, and whenever it is not granted Live, the stored wallpaper frame at
 * the Windows Blur, aimed at the window; on the effect route the recorder's picture of what is
 * beneath, in the same material.
 *
 * <p>The window wears its glass drawable even while the stored frame of its radius is still
 * baking (the cache answers null meanwhile): the drawable is dressed again when the frame lands
 * ({@link TerlGlassWear#refreshStored}, run by the chrome's frame-ready pass). Pong 11-28: a shell
 * window dressed then used to keep no glass at all.</p>
 */
public final class TerlWindowGlass {

    /** The stored frames a window's glass samples. */
    public interface Frames {
        /** Whether the wallpaper can be sampled at all (the launcher draws it). */
        boolean sampleable();

        /** The stored frame at {@code blurDp} now; null while it bakes. */
        @Nullable Object frame(int blurDp);
    }

    /** How a window's glass is drawn, in one material for both routes. */
    public interface Dress {
        /** Over the stored frame {@code frame} (null while it bakes: the material alone). */
        @NonNull Drawable stored(float radiusPx, @NonNull View anchor, int blurDp, int tintPercent,
                                 @Nullable Object frame);

        /** Over the recorder's picture {@code live}. */
        @NonNull Drawable live(float radiusPx, int blurDp, int tintPercent, @NonNull LivePicture live);
    }

    @NonNull private final TerlGlassWear mWear;
    @NonNull private final Frames mFrames;
    @NonNull private final Dress mDress;
    private final float mDensity;

    public TerlWindowGlass(@NonNull TerlGlassWear wear, @NonNull Frames frames, @NonNull Dress dress,
                           float density) {
        mWear = wear;
        mFrames = frames;
        mDress = dress;
        mDensity = density;
    }

    /**
     * Window {@code windowId}'s glass at {@code blurDp} and {@code tintPercent} of tint, cut to
     * {@code radiusPx}, for {@code anchor} ({@code floating} for a Float's). Null, letting go of
     * any entry, with no blur or no wallpaper to sample.
     */
    @Nullable
    public Drawable glass(float radiusPx, @NonNull View anchor, int blurDp, int tintPercent, long windowId,
                          boolean floating) {
        if (blurDp <= 0 || !mFrames.sampleable()) {
            mWear.letGo(anchor);
            return null;
        }
        int priority = floating ? GlassEntry.PRIORITY_FLOAT : GlassEntry.PRIORITY_TILE;
        return mWear.wear(anchor, TerlGlassWear.windowElement(windowId), WindowLayer.PAGES, priority,
            blurDp * mDensity, true, new TerlGlassWear.Builder() {
                @Nullable @Override public Drawable build(@Nullable LivePicture live) {
                    return live != null ? mDress.live(radiusPx, blurDp, tintPercent, live)
                        : mDress.stored(radiusPx, anchor, blurDp, tintPercent, mFrames.frame(blurDp));
                }

                @Nullable @Override public Object frameNow() {
                    return mFrames.frame(blurDp);
                }
            });
    }
}
