package com.termux.app.chrome.scene;

/**
 * The pointer on its way from the main thread to the scene thread: a fixed single-producer /
 * single-consumer ring of plain records, so a touch costs a handful of array stores and one
 * volatile write, with no lock and no allocation.
 *
 * <p>Safe because each index has exactly one writer: the producer (main thread) writes the record
 * fields and only then publishes them with a volatile write of {@link #mHead}; the consumer (scene
 * thread) reads {@code mHead} first, so every field of every record below it is visible, and frees
 * the slot with a volatile write of {@link #mTail}, which the producer reads before it reuses a
 * slot. A full ring drops the new record (the scene thread has stalled for {@link #CAPACITY}
 * records; none of them is worth blocking a touch for).</p>
 */
final class SceneEventRing {

    static final int CAPACITY = 32;
    private static final int MASK = CAPACITY - 1;

    static final int DOWN = 1;
    static final int MOVE = 2;
    static final int UP = 3;

    private final int[] mKind = new int[CAPACITY];
    private final float[] mX = new float[CAPACITY];
    private final float[] mY = new float[CAPACITY];
    /** Next slot to write: written by the producer only. */
    private volatile long mHead;
    /** Next slot to read: written by the consumer only. */
    private volatile long mTail;

    /** Producer: false when the ring is full and the record was dropped. */
    boolean push(int kind, float x, float y) {
        long head = mHead;
        if (head - mTail >= CAPACITY) return false;
        int i = (int) (head & MASK);
        mKind[i] = kind;
        mX[i] = x;
        mY[i] = y;
        mHead = head + 1;
        return true;
    }

    /** Either side: whether nothing waits to be consumed. */
    boolean isEmpty() {
        return mHead == mTail;
    }

    /** Consumer: applies every waiting record to {@code touch}, in order; the count applied. */
    int drainInto(SceneTouch touch) {
        long head = mHead;
        long tail = mTail;
        int n = 0;
        while (tail < head) {
            int i = (int) (tail & MASK);
            switch (mKind[i]) {
                case DOWN: touch.down(mX[i], mY[i]); break;
                case MOVE: touch.move(mX[i], mY[i]); break;
                case UP: touch.up(mX[i], mY[i]); break;
                default: break;
            }
            tail++;
            n++;
        }
        mTail = tail;
        return n;
    }
}
