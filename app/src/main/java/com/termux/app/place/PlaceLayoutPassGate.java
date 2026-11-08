package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether the place's layout pass has already run on the inputs it would read again, within one
 * settle. The landing lays the place out once, when the commit arranges it; a second pass the
 * landing asks for after that (a slide that laid its bands out) finds every input the same and
 * has nothing to change, so it is skipped.
 *
 * <p>Pure: the activity {@link #begin}s each settle, {@link #ran}s at the end of every pass and
 * asks {@link #redundant} before another. Outside a settle nothing is ever redundant.
 */
public final class PlaceLayoutPassGate {

    private boolean mInSettle;
    private boolean mRanThisSettle;
    @Nullable private PlaceLayout mLayout;
    private int mRevision;
    @Nullable private Object mVariant;
    private boolean mHome;
    private long mSizes;

    /** A settle starts: no pass has run in it yet. */
    public void begin() {
        mInSettle = true;
        mRanThisSettle = false;
        mLayout = null;
    }

    /** The settle is over. */
    public void end() {
        mInSettle = false;
        mRanThisSettle = false;
        mLayout = null;
    }

    /** A pass ran on these inputs. {@code sizes} folds in every other number the pass reads. */
    public void ran(@NonNull PlaceLayout layout, int revision, @Nullable Object variant, boolean home,
                    long sizes) {
        if (!mInSettle) return;
        mRanThisSettle = true;
        mLayout = layout;
        mRevision = revision;
        mVariant = variant;
        mHome = home;
        mSizes = sizes;
    }

    /** Whether a pass on these inputs would find what the last one of this settle left. */
    public boolean redundant(@NonNull PlaceLayout layout, int revision, @Nullable Object variant, boolean home,
                             long sizes) {
        return mInSettle && mRanThisSettle && mLayout != null && mLayout.equals(layout)
            && mRevision == revision && java.util.Objects.equals(mVariant, variant) && mHome == home && mSizes == sizes;
    }
}
