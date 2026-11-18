package com.termux.app.window;

import androidx.annotation.NonNull;

/**
 * The three guards every layer writer runs while the motion log is on (ADR 0028 section 5). They
 * log and count and never crash; off, a check costs one boolean read.
 *
 * <ul>
 *   <li><b>foreign writer</b>: a property the writer owns was changed by someone else since the
 *   writer's last write ({@link Writer#check});</li>
 *   <li><b>landing wrote</b>: the landing frame changed something, so the scene at progress 1 was
 *   not the arriving rest ({@link Writer#wrote} while {@link #setLanding});</li>
 *   <li><b>crossing laid out</b>: a layout request came from a writer that only sets pose and
 *   material, or from the crossing ({@link #layoutRequested}).</li>
 * </ul>
 *
 * <p>A writer registers each property it owns once ({@link Writer#own}) with a reader of the
 * view's current value, and tells the guard after each write ({@link Writer#wrote}). Nothing here
 * allocates on a frame that fires nothing.</p>
 */
public final class FrameGuards {

    public static final String FOREIGN_WRITER = "foreign-writer";
    public static final String LANDING_WROTE = "landing-wrote";
    public static final String CROSSING_LAID_OUT = "crossing-laid-out";

    /** Two values closer than this are the same write. */
    static final float EPSILON = 0.001f;

    /** Reads the current value of a property the writer owns. */
    public interface Reading {
        float read();
    }

    private boolean mLanding;
    private boolean mCrossing;

    /** This frame is a crossing's landing frame: the writers changing anything are counted. */
    public void setLanding(boolean landing) {
        mLanding = landing;
    }

    /** A crossing is under way, which the crossing-laid-out line says. */
    public void setCrossing(boolean crossing) {
        mCrossing = crossing;
    }

    @NonNull
    public Writer writer(@NonNull String name) {
        return new Writer(name);
    }

    /** A layout was requested from {@code who} where none may be; {@code detail} says how. */
    public void layoutRequested(@NonNull String who, @NonNull String detail) {
        if (!Counters.enabled) return;
        Counters.violation(CROSSING_LAID_OUT, who + " " + detail + (mCrossing ? " crossing" : ""));
    }

    /** One layer's writer, with the properties it owns. */
    public final class Writer {
        @NonNull private final String mName;
        private String[] mProperties = new String[4];
        private Reading[] mReadings = new Reading[4];
        private float[] mWritten = new float[4];
        private int mCount;
        /** True while the written values may be out of date (the log was off). */
        private boolean mStale = true;

        private Writer(@NonNull String name) {
            mName = name;
        }

        /** Registers an owned property; the slot to hand {@link #wrote}. */
        public int own(@NonNull String property, @NonNull Reading reading) {
            if (mCount == mReadings.length) {
                int n = mCount * 2;
                mProperties = java.util.Arrays.copyOf(mProperties, n);
                mReadings = java.util.Arrays.copyOf(mReadings, n);
                mWritten = java.util.Arrays.copyOf(mWritten, n);
            }
            mProperties[mCount] = property;
            mReadings[mCount] = reading;
            return mCount++;
        }

        /** Before this writer writes: a value that is not the one it wrote last is someone else's. */
        public void check() {
            if (!Counters.enabled) {
                mStale = true;
                return;
            }
            if (mStale) {
                for (int i = 0; i < mCount; i++) mWritten[i] = mReadings[i].read();
                mStale = false;
                return;
            }
            for (int i = 0; i < mCount; i++) {
                float now = mReadings[i].read();
                if (Math.abs(now - mWritten[i]) > EPSILON) {
                    Counters.violation(FOREIGN_WRITER, mName + " " + mProperties[i]
                        + " wrote=" + mWritten[i] + " now=" + now);
                    mWritten[i] = now;
                }
            }
        }

        /** After the write of {@code slot}: remembers it, and counts a change on a landing frame. */
        public boolean wrote(int slot) {
            if (!Counters.enabled) {
                mStale = true;
                return false;
            }
            boolean changed = false;
            float now = mReadings[slot].read();
            if (!mStale && mLanding && Math.abs(now - mWritten[slot]) > EPSILON) {
                Counters.violation(LANDING_WROTE, mName + " " + mProperties[slot]
                    + " was=" + mWritten[slot] + " now=" + now);
                changed = true;
            }
            mWritten[slot] = now;
            return changed;
        }
    }
}
