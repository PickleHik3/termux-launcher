package com.termux.app.terminal.inappkeyboard.touch;

import juloo.keyboard2.TapGeometry;

/**
 * The keyboard's touch engine: decides when learned borders correct a tap, and turns the stream
 * of released taps into what the {@link TapModel} learns. Pure; no Android types, and every time
 * and size arrives as a plain number, so all of it runs on the JVM.
 *
 * <p>It learns only from text that survived. The last {@link #HOLD} plain taps on characters and
 * space are held, not filed. A Backspace erases the newest held tap, and an erased tap is never
 * filed as landed. Held taps that were not erased are filed when they drop out of the hold, or
 * when the text is final: a key that is never corrected (Enter, arrows, modifiers; not
 * Backspace), a swipe, long press or Ctrl/Alt chord, Esc or Tab, input from anywhere else or the
 * keyboard going away ({@link #interrupt()}), or a gap of more than {@link #TAKE_BACK_WINDOW_MS}
 * between keystrokes. A Backspace that is not a plain tap (held, swiped or chorded) erases an
 * unknown amount, so it drops everything held, unfiled.
 *
 * <p>What it learns from:
 * <ul>
 *   <li>A landed tap, at weight 1, filed against the key the plain grid gave it (never the key a
 *   correction sent), unless it lands more than {@link #OUTLIER_DISTANCE} from that key's
 *   centre.</li>
 *   <li>A take-back: k plain Backspaces in a row (1 to {@link #HOLD}) that erase held taps, then
 *   k plain taps whose keys equal the erased ones, in typed order, except at exactly one place,
 *   where the erased key X and the retyped key Y are grid neighbours, both characters or space.
 *   X's landing point is filed as meant for Y, at weight {@link #TAKE_BACK_WEIGHT}. Anything
 *   else (no difference or two, keys that are not neighbours, a Backspace with nothing held, a
 *   Backspace during the retype, a final event before the retype is complete) is editing, and
 *   the erased taps teach nothing.</li>
 * </ul>
 * Esc and Tab learn from landed taps only: a Backspace does not undo either, so they are never
 * held; each makes the text before it final and is filed at once.
 *
 * <p>Every key also accepts only {@link GridMemory#MAX_OBSERVATIONS_PER_KEY_PER_MINUTE}
 * observations a minute. Correction runs only while the {@link LearningGate} is active: after
 * warm-up, until the drift alarm pauses it.
 *
 * <p>Resident: two arrays of {@link #HOLD} references and at most {@link #HOLD} held entries (a
 * tap is either held or erased, never both), each a 24-byte entry and a 56-byte {@link Tap}:
 * under 1 KB.
 */
public final class TouchEngine {

    /** The longest gap between two keystrokes of one take-back; a longer one makes text final. */
    public static final long TAKE_BACK_WINDOW_MS = 2_000L;
    /** How many landed taps one take-back counts as. */
    public static final float TAKE_BACK_WEIGHT = 3f;
    /** A landed tap further than this from its key's centre, in key widths, is not learned. */
    public static final float OUTLIER_DISTANCE = 0.5f;
    /** Taps held before filing, and so the most Backspaces one take-back can span. */
    public static final int HOLD = 8;

    private static final class Held {
        final GridMemory grid;
        final TapGeometry geometry;
        final Tap tap;

        Held(GridMemory grid, TapGeometry geometry, Tap tap) {
            this.grid = grid;
            this.geometry = geometry;
            this.tap = tap;
        }
    }

    private final LearningGate mGate;
    private long mTapsLearned;
    private boolean mSwitchedOn;
    /** Numbers the current warm-up, so every grid's {@link PassBarCounts} can tell its own. */
    private int mEpoch;
    /** Held taps, oldest first. */
    private final Held[] mHeld = new Held[HOLD];
    private int mHeldCount;
    /** Erased taps in the order they were erased: the last one typed comes first. */
    private final Held[] mErased = new Held[HOLD];
    private int mErasedCount;
    /** Plain taps retyped since the Backspaces that filled {@link #mErased}. */
    private int mRetyped;
    /** Typed-order position of the one retyped key that differs, or -1. */
    private int mDifferAt = -1;
    /** The retyped key at {@link #mDifferAt}: the key that was meant. */
    private int mDifferMeant;
    private boolean mHasLastDown;
    private long mLastDownAt;
    private boolean mHasLastUp;
    private long mLastUpAt;

    public TouchEngine() {
        this(new LearningGate(), 0L, false, 0);
    }

    public TouchEngine(LearningGate gate, long tapsLearned, boolean switchedOn, int epoch) {
        mGate = gate == null ? new LearningGate() : gate;
        mTapsLearned = Math.max(0L, tapsLearned);
        mSwitchedOn = switchedOn;
        mEpoch = epoch;
    }

    /** The current warm-up's number; it changes whenever a warm-up starts. */
    public int epoch() {
        return mEpoch;
    }

    public LearningGate gate() {
        return mGate;
    }

    /** Observations filed into any model since the last reset. */
    public long tapsLearned() {
        return mTapsLearned;
    }

    /** Whether the feature was on when last seen; turning it on starts a warm-up. */
    public boolean switchedOn() {
        return mSwitchedOn;
    }

    /**
     * Follows the feature's switch. Turning it on starts a fresh warm-up. Returns whether the
     * state changed and needs saving.
     */
    public boolean onSwitch(boolean on) {
        if (on == mSwitchedOn)
            return false;
        mSwitchedOn = on;
        if (on)
            restartWarmUp();
        else
            interrupt();
        return true;
    }

    /** Leaves a pause: a fresh warm-up, keeping what was learned. */
    public void resume() {
        restartWarmUp();
    }

    private void restartWarmUp() {
        mGate.restartWarmUp();
        mEpoch++;
    }

    /**
     * The key a plain tap released on {@code rawIndex} should commit: {@code rawIndex} unless
     * correction is active and the model moves it.
     */
    public int resolve(GridMemory grid, TapGeometry geometry, int rawIndex, float x, float y) {
        if (grid == null || geometry == null || !mGate.isCorrecting()
            || grid.keyCount() != geometry.keyCount)
            return rawIndex;
        return grid.model().resolve(geometry, rawIndex, x, y);
    }

    /** A press on {@code geometry} was released. */
    public void onRelease(GridMemory grid, TapGeometry geometry, Tap tap) {
        if (tap == null || grid == null || geometry == null
            || grid.keyCount() != geometry.keyCount
            || !inRange(geometry, tap.rawIndex) || !inRange(geometry, tap.committedIndex)) {
            interrupt();
            return;
        }
        long gap = mHasLastDown ? tap.downAtMs - mLastDownAt : -1L;
        mHasLastDown = true;
        mLastDownAt = tap.downAtMs;
        // A pause, or a different grid, makes what is held final before this keystroke counts.
        if ((mHasLastUp && tap.downAtMs - mLastUpAt > TAKE_BACK_WINDOW_MS) || holdsOtherGrid(grid))
            interrupt();
        mHasLastUp = true;
        mLastUpAt = tap.upAtMs;

        boolean clean = tap.plain && !tap.modified;
        if (geometry.role[tap.committedIndex] == TapGeometry.ROLE_BACKSPACE) {
            if (clean)
                onBackspace();
            else
                dropAll();
            return;
        }
        if (!clean || !geometry.correctable[tap.rawIndex]
            || !geometry.correctable[tap.committedIndex]) {
            interrupt();
            return;
        }

        boolean holds = takesBack(geometry, tap.committedIndex);
        Held missed = null;
        int meant = -1;
        if (!holds) {
            // Esc or Tab: what came before is final.
            interrupt();
        } else if (mErasedCount > 0) {
            missed = retype(geometry, tap.committedIndex);
            meant = mDifferMeant;
        }

        PassBarCounts counts = grid.counts();
        counts.sync(mEpoch);
        int phase = mGate.state();
        if (missed != null) {
            endSequence();
            fileTakeBack(missed, meant, tap.upAtMs);
            counts.countTakeBack(meant, missed.tap.committedIndex, phase);
            mGate.onTakeBack();
        }
        counts.countTap(tap.committedIndex, phase);
        mGate.onTap();
        grid.log().addSample(tap.rawIndex,
            tap.x - geometry.centerX(tap.rawIndex), heightsOff(geometry, tap.rawIndex, tap.y),
            tap.upAtMs - tap.downAtMs, tap.contactSize, gap);
        Held held = new Held(grid, geometry, tap);
        if (holds)
            hold(held);
        else
            fileLanded(held);
    }

    /**
     * Input that did not come from a plain tap on these keys: a paste, a hardware key, the
     * keyboard going away. The text is final: files every held tap that was not erased, and ends
     * any take-back in progress.
     */
    public void interrupt() {
        endSequence();
        for (int i = 0; i < mHeldCount; i++) {
            fileLanded(mHeld[i]);
            mHeld[i] = null;
        }
        mHeldCount = 0;
    }

    /** Taps held and not yet filed. */
    int heldCount() {
        return mHeldCount;
    }

    private void onBackspace() {
        // A Backspace during the retype is editing; it starts a new run of its own.
        if (mRetyped > 0)
            endSequence();
        if (mHeldCount == 0) {
            // It erases text that is not held, so no retype can be matched against it.
            endSequence();
            return;
        }
        Held newest = mHeld[--mHeldCount];
        mHeld[mHeldCount] = null;
        // Each Backspace of a run moves one held tap here, so this never overflows.
        if (mErasedCount < HOLD)
            mErased[mErasedCount++] = newest;
    }

    /**
     * Compares a retyped key with the erased one at the same typed position. Returns the erased
     * tap to file as a take-back once the whole run is retyped with exactly one neighbouring
     * difference, and null otherwise; ends the sequence as soon as it can no longer match.
     */
    private Held retype(TapGeometry geometry, int key) {
        Held erased = mErased[mErasedCount - 1 - mRetyped];
        int hit = erased.tap.committedIndex;
        if (hit != key) {
            if (mDifferAt >= 0 || !takesBack(erased.geometry, hit)
                || !KeyGroups.adjacent(geometry, hit, key)) {
                endSequence();
                return null;
            }
            mDifferAt = mRetyped;
            mDifferMeant = key;
        }
        mRetyped++;
        if (mRetyped < mErasedCount)
            return null;
        if (mDifferAt < 0) {
            // Retyped exactly what was erased: editing, nothing to learn.
            endSequence();
            return null;
        }
        return mErased[mErasedCount - 1 - mDifferAt];
    }

    /** Drops the erased taps unfiled and forgets the retype. */
    private void endSequence() {
        for (int i = 0; i < mErasedCount; i++)
            mErased[i] = null;
        mErasedCount = 0;
        mRetyped = 0;
        mDifferAt = -1;
    }

    /** Drops everything held, unfiled: an unknown amount of it may have been erased. */
    private void dropAll() {
        endSequence();
        for (int i = 0; i < mHeldCount; i++)
            mHeld[i] = null;
        mHeldCount = 0;
    }

    private void hold(Held held) {
        if (mHeldCount == HOLD) {
            // The oldest drops out of reach of any Backspace this can follow: it landed.
            fileLanded(mHeld[0]);
            System.arraycopy(mHeld, 1, mHeld, 0, HOLD - 1);
            mHeldCount--;
        }
        mHeld[mHeldCount++] = held;
    }

    private boolean holdsOtherGrid(GridMemory grid) {
        return (mHeldCount > 0 && mHeld[0].grid != grid)
            || (mErasedCount > 0 && mErased[0].grid != grid);
    }

    private void fileLanded(Held held) {
        TapGeometry geometry = held.geometry;
        int raw = held.tap.rawIndex;
        if (!geometry.correctable[raw])
            return;
        float dx = KeyGroups.isWide(geometry, raw) ? 0f : held.tap.x - geometry.centerX(raw);
        float dy = held.tap.y - geometry.centerY(raw);
        if (dx * dx + dy * dy > OUTLIER_DISTANCE * OUTLIER_DISTANCE)
            return;
        if (!held.grid.admit(raw, held.tap.upAtMs))
            return;
        if (held.grid.model().observe(geometry, raw, held.tap.x, held.tap.y, 1f))
            mTapsLearned++;
    }

    private void fileTakeBack(Held missed, int meant, long nowMs) {
        TapGeometry geometry = missed.geometry;
        missed.grid.log().addTakeBack(meant, missed.tap.committedIndex,
            missed.tap.x - geometry.centerX(meant), heightsOff(geometry, meant, missed.tap.y));
        if (!missed.grid.admit(meant, nowMs))
            return;
        if (missed.grid.model().observe(geometry, meant, missed.tap.x, missed.tap.y,
            TAKE_BACK_WEIGHT))
            mTapsLearned++;
    }

    /** Characters and space can be taken back; Esc and Tab cannot. */
    private static boolean takesBack(TapGeometry geometry, int i) {
        int role = geometry.role[i];
        return role == TapGeometry.ROLE_CHAR || role == TapGeometry.ROLE_SPACE;
    }

    /** How far {@code y} sits from key {@code i}'s centre, in that key's heights. */
    private static float heightsOff(TapGeometry geometry, int i, float y) {
        float height = geometry.height(i);
        return height > 0f ? (y - geometry.centerY(i)) / height : 0f;
    }

    private static boolean inRange(TapGeometry geometry, int i) {
        return i >= 0 && i < geometry.keyCount;
    }
}
