package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SameMessageMarkTest {

    private final List<Runnable> mQueued = new ArrayList<>();
    private final SameMessageMark mMark = new SameMessageMark(mQueued::add);

    private void endMessage() {
        List<Runnable> run = new ArrayList<>(mQueued);
        mQueued.clear();
        for (Runnable r : run) r.run();
    }

    @Test
    public void unmarkedUntilMarked() {
        assertFalse(mMark.isMarked());
    }

    @Test
    public void aMarkHoldsForTheRestOfItsMessage() {
        mMark.mark();
        assertTrue(mMark.isMarked());
    }

    @Test
    public void aMarkIsGoneOnceItsMessageEnds() {
        mMark.mark();
        endMessage();
        assertFalse(mMark.isMarked());
    }

    @Test
    public void markingTwiceInOneMessagePostsOnce() {
        mMark.mark();
        mMark.mark();
        assertEquals(1, mQueued.size());
        endMessage();
        assertFalse(mMark.isMarked());
        mMark.mark();
        assertTrue(mMark.isMarked());
    }
}
