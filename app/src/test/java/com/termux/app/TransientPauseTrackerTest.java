package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TransientPauseTrackerTest {

    private final List<Runnable> mQueued = new ArrayList<>();
    private final TransientPauseTracker mTracker = new TransientPauseTracker(mQueued::add);

    private void endMessage() {
        List<Runnable> run = new ArrayList<>(mQueued);
        mQueued.clear();
        for (Runnable r : run) r.run();
    }

    @Test
    public void firstResumeAfterCreateIsNotTransient() {
        assertFalse(mTracker.resumesTransientPause());
    }

    @Test
    public void pauseAndResumeInOneMessageIsTransient() {
        mTracker.onPause();
        assertTrue(mTracker.resumesTransientPause());
    }

    @Test
    public void aPauseThatOutlivesItsMessageIsNot() {
        mTracker.onPause();
        endMessage();
        assertFalse(mTracker.resumesTransientPause());
    }

    @Test
    public void aSecondPauseInTheSameMessagePostsOnce() {
        mTracker.onPause();
        mTracker.onPause();
        assertEquals(1, mQueued.size());
        endMessage();
        assertFalse(mTracker.resumesTransientPause());
        mTracker.onPause();
        assertTrue(mTracker.resumesTransientPause());
    }
}
