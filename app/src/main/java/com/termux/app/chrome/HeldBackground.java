package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A surface's background kept for as long as what it was built from has not changed, so a chrome
 * apply that finds the same inputs puts back the very drawable it set last time instead of a new
 * one (a new background swaps in a frame later and can draw one frame lighter, and builds the glass
 * layers again for nothing). Pure: it holds one value and the key it was built under, and never
 * looks inside either.
 *
 * <p>The key is the list of everything the build read: the spec, the backdrop instance, the baked
 * veil and even-out, the colours. Whoever builds names them in {@link #key}; whoever sets the
 * surface to anything else calls {@link #release}, so no bitmap-holding drawable outlives its use.
 * One instance per surface.</p>
 */
public final class HeldBackground<T> {

    @Nullable private List<Object> mKey;
    @Nullable private T mHeld;

    /** The inputs a build read, compared by {@code equals} (identity for a drawable). */
    @NonNull
    public static List<Object> key(@NonNull Object... inputs) {
        return Arrays.asList(inputs);
    }

    /** The held value when it was built under {@code key}; otherwise null, and the caller builds. */
    @Nullable
    public T reuse(@NonNull List<Object> key) {
        return mHeld != null && key.equals(mKey) ? mHeld : null;
    }

    /** Keeps {@code value}, built under {@code key}, for {@link #reuse}. */
    public void hold(@NonNull List<Object> key, @NonNull T value) {
        mKey = key;
        mHeld = value;
    }

    /** Whether {@code value} is the one held. */
    public boolean holds(@Nullable Object value) {
        return value != null && value == mHeld;
    }

    /** Lets go of the held value; the next build is made afresh. */
    public void release() {
        mKey = null;
        mHeld = null;
    }
}
