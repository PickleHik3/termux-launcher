package com.termux.app.launcher.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NeighbourBindingTest {
    @Test public void samePageAndRevisionIsReused() {
        assertTrue(NeighbourBinding.reusable(true, 2, 7L, 2, 7L));
    }

    @Test public void anotherPageOrANewRevisionRebinds() {
        assertFalse(NeighbourBinding.reusable(true, 2, 7L, 1, 7L));
        assertFalse(NeighbourBinding.reusable(true, 2, 7L, 2, 8L));
    }

    @Test public void noLayerOrNoBoundPageRebinds() {
        assertFalse(NeighbourBinding.reusable(false, 2, 7L, 2, 7L));
        assertFalse(NeighbourBinding.reusable(true, -1, 7L, -1, 7L));
    }
}
