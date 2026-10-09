package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The return hold's promises: it opens once on leaving, it is let go only once resumed, focused
 * and steady for {@link ReturnResizeHold#STABLE_FRAMES} frames, and the backstops let it go even
 * when the layout never steadies or focus never comes.
 */
public class ReturnResizeHoldTest {

    private static final long KEY_A = 11L;
    private static final long KEY_B = 22L;

    private int mBegins;
    private final List<String> mFinishes = new ArrayList<>();
    private ReturnResizeHold mHold;

    @Before
    public void setUp() {
        mBegins = 0;
        mFinishes.clear();
        mHold = new ReturnResizeHold(new ReturnResizeHold.Host() {
            @Override public void beginHold() { mBegins++; }
            @Override public void finishHold(@NonNull String reason) { mFinishes.add(reason); }
        });
    }

    @Test
    public void beginOpensOnceUntilReleased() {
        assertTrue(mHold.begin());
        assertFalse(mHold.begin());
        assertEquals(1, mBegins);
        assertTrue(mHold.isActive());
    }

    @Test
    public void noFramesWantedBeforeResume() {
        mHold.begin();
        assertFalse(mHold.wantsFrames());
        assertFalse(mHold.onFrame(KEY_A, false, 10));
        assertTrue(mFinishes.isEmpty());
    }

    @Test
    public void steadyFramesWithoutFocusDoNotRelease() {
        mHold.begin();
        mHold.onResumed(0);
        for (long t = 16; t < 400; t += 16) assertTrue(mHold.onFrame(KEY_A, false, t));
        assertTrue(mFinishes.isEmpty());
        assertTrue(mHold.isActive());
    }

    @Test
    public void releasesAfterTwoSteadyFramesOnceFocused() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 20);
        assertTrue(mHold.onFrame(KEY_A, false, 32));   // first sight of the key
        assertTrue(mHold.onFrame(KEY_A, false, 48));   // steady 1
        assertTrue(mFinishes.isEmpty());
        assertFalse(mHold.onFrame(KEY_A, false, 64));  // steady 2: let go
        assertEquals(List.of(ReturnResizeHold.REASON_SETTLED), mFinishes);
        assertFalse(mHold.isActive());
        assertFalse(mHold.wantsFrames());
    }

    @Test
    public void aChangingKeyOrPendingLayoutRestartsTheCount() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 0);
        mHold.onFrame(KEY_A, false, 16);
        mHold.onFrame(KEY_A, false, 32);
        mHold.onFrame(KEY_B, false, 48);               // insets moved: start over
        mHold.onFrame(KEY_B, false, 64);
        mHold.onFrame(KEY_B, true, 80);                // a layout is booked: start over
        mHold.onFrame(KEY_B, false, 96);
        mHold.onFrame(KEY_B, false, 112);
        assertTrue(mFinishes.isEmpty());
        mHold.onFrame(KEY_B, false, 128);
        assertEquals(List.of(ReturnResizeHold.REASON_SETTLED), mFinishes);
    }

    @Test
    public void focusResetsTheCount() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 0);
        mHold.onFrame(KEY_A, false, 16);
        mHold.onFrame(KEY_A, false, 32);
        mHold.onFocusChanged(false, 40);
        mHold.onFocusChanged(true, 41);
        mHold.onFrame(KEY_A, false, 48);
        mHold.onFrame(KEY_A, false, 64);
        assertTrue(mFinishes.isEmpty());
        mHold.onFrame(KEY_A, false, 80);
        assertEquals(1, mFinishes.size());
    }

    @Test
    public void focusBackstopReleasesALayoutThatNeverSteadies() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 100);
        long t = 100;
        boolean more = true;
        while (more && t < 5000) {
            t += 16;
            more = mHold.onFrame(t, false, t);          // a new key every frame
        }
        assertEquals(List.of(ReturnResizeHold.REASON_BACKSTOP), mFinishes);
        assertTrue(t >= 100 + ReturnResizeHold.FOCUS_BACKSTOP_MS);
        assertTrue(t < 100 + ReturnResizeHold.FOCUS_BACKSTOP_MS + 16);
    }

    @Test
    public void unfocusedBackstopReleasesWhenFocusNeverComes() {
        mHold.begin();
        mHold.onResumed(0);
        assertTrue(mHold.onFrame(KEY_A, false, ReturnResizeHold.UNFOCUSED_BACKSTOP_MS - 1));
        assertFalse(mHold.onFrame(KEY_A, false, ReturnResizeHold.UNFOCUSED_BACKSTOP_MS));
        assertEquals(List.of(ReturnResizeHold.REASON_BACKSTOP_UNFOCUSED), mFinishes);
    }

    @Test
    public void aPauseStopsTheFramesAndTheNextResumeStartsOver() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 0);
        mHold.onFrame(KEY_A, false, 16);
        mHold.onFrame(KEY_A, false, 32);
        mHold.onPaused();
        assertFalse(mHold.wantsFrames());
        assertFalse(mHold.onFrame(KEY_A, false, 48));
        mHold.onResumed(5000);
        // Focus was lost with the pause, so the unfocused backstop counts from this resume.
        assertTrue(mHold.onFrame(KEY_A, false, 5000 + 100));
        assertTrue(mFinishes.isEmpty());
        mHold.onFocusChanged(true, 5200);
        mHold.onFrame(KEY_A, false, 5216);
        mHold.onFrame(KEY_A, false, 5232);
        mHold.onFrame(KEY_A, false, 5248);
        assertEquals(List.of(ReturnResizeHold.REASON_SETTLED), mFinishes);
    }

    @Test
    public void cancelDropsWithoutResuming() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.cancel();
        assertFalse(mHold.isActive());
        assertFalse(mHold.onFrame(KEY_A, false, 10_000));
        assertTrue(mFinishes.isEmpty());
        // A later leave opens a fresh hold.
        assertTrue(mHold.begin());
        assertEquals(2, mBegins);
    }

    @Test
    public void releasingTwiceFinishesOnce() {
        mHold.begin();
        mHold.onResumed(0);
        mHold.onFocusChanged(true, 0);
        mHold.onFrame(KEY_A, false, 16);
        mHold.onFrame(KEY_A, false, 32);
        mHold.onFrame(KEY_A, false, 48);
        mHold.onFrame(KEY_A, false, 64);
        mHold.onFrame(KEY_A, false, 10_000);
        assertEquals(1, mFinishes.size());
    }
}
