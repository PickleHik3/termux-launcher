package com.termux.app.tiling;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * A finger resting on a place's row (the Workspace list's, Overview's) predicts the landing: after
 * {@link #REST_MS} on one row, that place is told, so the desktop makes it ready ahead of the go
 * (live window residency section 4.4). A finger that moves on before then tells nothing; the
 * sheet closing tells null when a place was told.
 */
public final class LandingRest {

    /** How long a finger rests on a row before its place is made ready. */
    public static final long REST_MS = 120L;

    @NonNull private final Consumer<Place> mTell;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mLand = this::land;
    @Nullable private Place mUnder;
    private boolean mTold;

    /** {@code tell} hears the predicted place, or null when there is none any more. */
    public LandingRest(@NonNull Consumer<Place> tell) {
        mTell = tell;
    }

    /** The finger is over {@code place}'s row now; null when over none. */
    public void over(@Nullable Place place) {
        mHandler.removeCallbacks(mLand);
        mUnder = place;
        if (place != null) mHandler.postDelayed(mLand, REST_MS);
    }

    /** The sheet closed: nothing is predicted any more. */
    public void closed() {
        mHandler.removeCallbacks(mLand);
        mUnder = null;
        if (!mTold) return;
        mTold = false;
        mTell.accept(null);
    }

    private void land() {
        if (mUnder == null) return;
        mTold = true;
        mTell.accept(mUnder);
    }
}
