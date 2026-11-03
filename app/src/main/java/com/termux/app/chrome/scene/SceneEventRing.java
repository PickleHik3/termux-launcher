package com.termux.app.chrome.scene;

/**
 * The scene's reactions on their way from the main thread to the scene thread: a fixed
 * single-producer / single-consumer ring of plain records, so a touch-down, a key spark or a bell
 * costs a handful of array stores and one volatile write, with no lock and no allocation.
 *
 * <p>Safe because each index has exactly one writer: the producer (main thread) writes the record
 * fields and only then publishes them with a volatile write of {@link #mHead}; the consumer (scene
 * thread) reads {@code mHead} first, so every field of every record below it is visible, and frees
 * the slot with a volatile write of {@link #mTail}, which the producer reads before it reuses a
 * slot. A full ring drops the new record (the scene thread has stalled for {@link #CAPACITY}
 * reactions; none of them is worth blocking a touch for).</p>
 */
final class SceneEventRing {

    static final int CAPACITY = 32;
    private static final int MASK = CAPACITY - 1;

    static final int HILL = 1;
    static final int SPARK = 2;
    static final int PULSE = 3;
    static final int FOCUS = 4;

    private final int[] mKind = new int[CAPACITY];
    private final float[] mA = new float[CAPACITY];
    private final float[] mB = new float[CAPACITY];
    private final float[] mC = new float[CAPACITY];
    private final float[] mD = new float[CAPACITY];
    private final long[] mAtMs = new long[CAPACITY];
    /** Next slot to write: written by the producer only. */
    private volatile long mHead;
    /** Next slot to read: written by the consumer only. */
    private volatile long mTail;

    /** Producer: false when the ring is full and the record was dropped. */
    boolean push(int kind, float a, float b, float c, float d, long atMs) {
        long head = mHead;
        if (head - mTail >= CAPACITY) return false;
        int i = (int) (head & MASK);
        mKind[i] = kind;
        mA[i] = a;
        mB[i] = b;
        mC[i] = c;
        mD[i] = d;
        mAtMs[i] = atMs;
        mHead = head + 1;
        return true;
    }

    /** Either side: whether nothing waits to be consumed. */
    boolean isEmpty() {
        return mHead == mTail;
    }

    /** Consumer: applies every waiting record to {@code events}, in order; the count applied. */
    int drainInto(SceneEvents events) {
        long head = mHead;
        long tail = mTail;
        int n = 0;
        while (tail < head) {
            int i = (int) (tail & MASK);
            switch (mKind[i]) {
                case HILL: events.addHill(mA[i], mB[i], mAtMs[i]); break;
                case SPARK: events.addSpark(mA[i], mB[i], mAtMs[i]); break;
                case PULSE: events.addPulse(mA[i], mB[i], mC[i], mD[i], mAtMs[i]); break;
                case FOCUS: events.addFocus(mAtMs[i]); break;
                default: break;
            }
            tail++;
            n++;
        }
        mTail = tail;
        return n;
    }
}
