package com.termux.app.terminal;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * A recorded run of object identities and ints, and a probe that walks the same state again and
 * says whether every step still matches — by identity, not equals, and without allocating.
 *
 * <p>For a notification that usually changes nothing a list depends on: the caller records the
 * shape the list was built from, and on the next notification probes the state the same way; an
 * unchanged shape means the rebuild has nothing to find. Steps are fed through {@link #add} and
 * {@link #addInt} between {@link #beginRecord}/{@link #endRecord} or
 * {@link #beginProbe}/{@link #endProbe}.</p>
 */
public final class IdentitySequence {

    private final ArrayList<Object> mObjects = new ArrayList<>();
    private int[] mInts = new int[16];
    private int mIntCount;
    private boolean mRecorded;

    private boolean mRecording;
    private boolean mProbing;
    private int mObjectCursor;
    private int mIntCursor;
    private boolean mMatches;

    /** True once a record has been taken; until then every probe fails. */
    public boolean isRecorded() {
        return mRecorded;
    }

    /** Forgets the record, so the next probe fails. */
    public void invalidate() {
        mRecorded = false;
        mObjects.clear();
        mIntCount = 0;
    }

    public void beginRecord() {
        mObjects.clear();
        mIntCount = 0;
        mRecording = true;
        mProbing = false;
    }

    public void endRecord() {
        mRecording = false;
        mRecorded = true;
    }

    public void beginProbe() {
        mProbing = true;
        mRecording = false;
        mObjectCursor = 0;
        mIntCursor = 0;
        mMatches = mRecorded;
    }

    /** True when every step of the probe matched the record and the probe was as long. */
    public boolean endProbe() {
        mProbing = false;
        return mMatches && mObjectCursor == mObjects.size() && mIntCursor == mIntCount;
    }

    /** One identity step; null is a step of its own. */
    public void add(@Nullable Object object) {
        if (mRecording) {
            mObjects.add(object);
        } else if (mProbing && mMatches) {
            if (mObjectCursor >= mObjects.size() || mObjects.get(mObjectCursor) != object) {
                mMatches = false;
            }
            mObjectCursor++;
        }
    }

    /** One int step. */
    public void addInt(int value) {
        if (mRecording) {
            if (mIntCount == mInts.length) {
                int[] grown = new int[mInts.length * 2];
                System.arraycopy(mInts, 0, grown, 0, mIntCount);
                mInts = grown;
            }
            mInts[mIntCount++] = value;
        } else if (mProbing && mMatches) {
            if (mIntCursor >= mIntCount || mInts[mIntCursor] != value) {
                mMatches = false;
            }
            mIntCursor++;
        }
    }
}
