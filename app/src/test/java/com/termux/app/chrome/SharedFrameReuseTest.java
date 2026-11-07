package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SharedFrameReuseTest {

    private final Object mHeld = new Object();
    private final Object mHost = new Object();
    private final Object mParallax = new Object();

    @Test
    public void sameHostAndParallaxKeepsTheDrawable() {
        assertTrue(SharedFrameReuse.reuses(mHeld, mHost, mHost, mParallax, mParallax));
        assertTrue(SharedFrameReuse.reuses(mHeld, mHost, mHost, null, null));
    }

    @Test
    public void nothingHeldBuildsAfresh() {
        assertFalse(SharedFrameReuse.reuses(null, mHost, mHost, mParallax, mParallax));
    }

    @Test
    public void anotherViewBuildsAfresh() {
        // A recreated keyboard host is a new view: its anchor reads a different view's layout.
        assertFalse(SharedFrameReuse.reuses(mHeld, mHost, new Object(), mParallax, mParallax));
        assertFalse(SharedFrameReuse.reuses(mHeld, null, null, mParallax, mParallax));
    }

    @Test
    public void anotherParallaxBuildsAfresh() {
        assertFalse(SharedFrameReuse.reuses(mHeld, mHost, mHost, mParallax, new Object()));
        assertFalse(SharedFrameReuse.reuses(mHeld, mHost, mHost, null, mParallax));
    }
}
