package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * What a {@link PaneSurfaceStyle} answered, frozen. The style is a live view onto the activity,
 * so handing it over twice looks like two different styles; the chrome passes that run several
 * times a page change compare keys instead, and re-dress the panes and the wall's pages only when
 * one of the answers a pane is dressed with has moved. Bitmaps and the filter are compared by
 * identity — a new blur frame is a new bitmap — and the grain layer by the strength it is built
 * from, since the layer itself is a fresh drawable per call.
 */
public final class PaneStyleKey {

    private final boolean mGlass;
    @Nullable private final Bitmap mFrame;
    @NonNull private final Rect mFrameRect;
    @Nullable private final ColorFilter mFrostFilter;
    private final int mTint;
    private final int mGrainStrength;
    private final float mCornerRadiusPx;
    private final int mCornerRadiusDp;
    private final int mGapDp;
    @Nullable private final Bitmap mWallBehind;
    private final int mWallBehindColor;

    private PaneStyleKey(@NonNull PaneSurfaceStyle style) {
        mGlass = style.isPaneGlassActive();
        mFrame = style.paneGlassBlurFrame();
        mFrameRect = new Rect(style.paneGlassBlurFrameRect());
        mFrostFilter = style.paneGlassFrostFilter();
        mTint = style.paneGlassTintColor();
        mGrainStrength = style.paneGlassGrainStrength();
        mCornerRadiusPx = style.paneGlassCornerRadiusPx();
        mCornerRadiusDp = style.paneCornerRadiusDp();
        mGapDp = style.paneGapDp();
        mWallBehind = style.wallBehindFrame();
        mWallBehindColor = style.wallBehindColor();
    }

    @NonNull
    public static PaneStyleKey of(@NonNull PaneSurfaceStyle style) {
        return new PaneStyleKey(style);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof PaneStyleKey)) return false;
        PaneStyleKey that = (PaneStyleKey) other;
        return mGlass == that.mGlass
            && mFrame == that.mFrame
            && mFrameRect.equals(that.mFrameRect)
            && mFrostFilter == that.mFrostFilter
            && mTint == that.mTint
            && mGrainStrength == that.mGrainStrength
            && Float.compare(mCornerRadiusPx, that.mCornerRadiusPx) == 0
            && mCornerRadiusDp == that.mCornerRadiusDp
            && mGapDp == that.mGapDp
            && mWallBehind == that.mWallBehind
            && mWallBehindColor == that.mWallBehindColor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGlass, System.identityHashCode(mFrame), mFrameRect,
            System.identityHashCode(mFrostFilter), mTint, mGrainStrength, mCornerRadiusPx,
            mCornerRadiusDp, mGapDp, System.identityHashCode(mWallBehind), mWallBehindColor);
    }
}
