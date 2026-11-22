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
    private final boolean mBorder;
    @Nullable private final Bitmap mFrame;
    @NonNull private final Rect mFrameRect;
    @Nullable private final ColorFilter mFrostFilter;
    private final int mTint;
    private final boolean mRim;
    private final boolean mOpeningLine;
    private final int mGrainStrength;
    private final float mCornerRadiusPx;
    private final int mCornerRadiusDp;
    private final int mGapDp;
    @Nullable private final Bitmap mWallBehind;
    private final int mWallBehindColor;
    @Nullable private final com.termux.app.chrome.GlassRefraction.Look mRefraction;
    @Nullable private final Bitmap mClearFrame;
    /** The Look's glass the widgets wear ({@link PaneSurfaceStyle#lookGlass}), or null. */
    @Nullable private final Object mLookGlass;

    private PaneStyleKey(@NonNull PaneSurfaceStyle style) {
        mGlass = style.isPaneGlassActive();
        mBorder = style.paneBorderEnabled();
        mFrame = style.paneGlassBlurFrame();
        mFrameRect = new Rect(style.paneGlassBlurFrameRect());
        mFrostFilter = style.paneGlassFrostFilter();
        mTint = style.paneGlassTintColor();
        mRim = style.paneGlassRimWanted();
        mOpeningLine = style.paneOpeningLine();
        mGrainStrength = style.paneGlassGrainStrength();
        mCornerRadiusPx = style.paneGlassCornerRadiusPx();
        mCornerRadiusDp = style.paneCornerRadiusDp();
        mGapDp = style.paneGapDp();
        mWallBehind = style.wallBehindFrame();
        mWallBehindColor = style.wallBehindColor();
        mRefraction = style.paneGlassRefraction();
        mClearFrame = style.paneGlassClearFrame();
        com.termux.app.chrome.LookGlassStyle look = style.lookGlass();
        mLookGlass = look == null ? null : look.key();
    }

    @NonNull
    public static PaneStyleKey of(@NonNull PaneSurfaceStyle style) {
        return new PaneStyleKey(style);
    }

    private static final String[] FIELDS = {
        "glass", "border", "frame", "frameRect", "frostFilter", "tint", "rim", "openingLine",
        "grainStrength", "cornerRadiusPx", "cornerRadiusDp", "gapDp", "wallBehind",
        "wallBehindColor", "refraction", "clearFrame", "lookGlass"
    };
    private static final int WALL_GROUND = 13;

    /** The one definition of each field's comparison; {@code index} is a position in FIELDS. */
    private boolean fieldEquals(int index, @NonNull PaneStyleKey that) {
        switch (index) {
            case 0: return mGlass == that.mGlass;
            case 1: return mBorder == that.mBorder;
            case 2: return mFrame == that.mFrame;
            case 3: return mFrameRect.equals(that.mFrameRect);
            case 4: return mFrostFilter == that.mFrostFilter;
            case 5: return mTint == that.mTint;
            case 6: return mRim == that.mRim;
            case 7: return mOpeningLine == that.mOpeningLine;
            case 8: return mGrainStrength == that.mGrainStrength;
            case 9: return Float.compare(mCornerRadiusPx, that.mCornerRadiusPx) == 0;
            case 10: return mCornerRadiusDp == that.mCornerRadiusDp;
            case 11: return mGapDp == that.mGapDp;
            case 12: return mWallBehind == that.mWallBehind;
            case 13: return mWallBehindColor == that.mWallBehindColor;
            case 14: return Objects.equals(mRefraction, that.mRefraction);
            case 15: return mClearFrame == that.mClearFrame;
            default: return Objects.equals(mLookGlass, that.mLookGlass);
        }
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof PaneStyleKey)) return false;
        PaneStyleKey that = (PaneStyleKey) other;
        for (int i = 0; i < FIELDS.length; i++) if (!fieldEquals(i, that)) return false;
        return true;
    }

    /**
     * True when only the wall's ground colour differs. Nothing a terminal pane is dressed with
     * reads it (only the Display page's corner mask does), so a pass that sees just this can skip
     * re-dressing the panes.
     */
    public boolean sameButWallGround(@Nullable PaneStyleKey other) {
        if (other == null) return false;
        if (fieldEquals(WALL_GROUND, other)) return false;
        for (int i = 0; i < FIELDS.length; i++) {
            if (i != WALL_GROUND && !fieldEquals(i, other)) return false;
        }
        return true;
    }

    /** The names of the fields that differ from {@code other}, comma separated; "all" for null. */
    @NonNull
    public String movedFields(@Nullable PaneStyleKey other) {
        if (other == null) return "all";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < FIELDS.length; i++) {
            if (fieldEquals(i, other)) continue;
            if (out.length() > 0) out.append(',');
            out.append(FIELDS[i]);
        }
        return out.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGlass, mBorder, System.identityHashCode(mFrame), mFrameRect,
            System.identityHashCode(mFrostFilter), mTint, mRim, mOpeningLine, mGrainStrength, mCornerRadiusPx,
            mCornerRadiusDp, mGapDp, System.identityHashCode(mWallBehind), mWallBehindColor,
            mRefraction, System.identityHashCode(mClearFrame), mLookGlass);
    }
}
