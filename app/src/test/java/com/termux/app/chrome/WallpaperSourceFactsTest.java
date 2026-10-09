package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperSourceFactsTest {

    @Test
    public void managedOnScreenNeedsTheStoredIdTheSystemIdAndTheFile() {
        assertTrue(WallpaperSourceFacts.of(42, 42, true, 10L, 20L, 1620, 2412).managedOnScreen());
        assertFalse("another wallpaper is on screen",
            WallpaperSourceFacts.of(41, 42, true, 10L, 20L, 1620, 2412).managedOnScreen());
        assertFalse("the picker never stored an id",
            WallpaperSourceFacts.of(0, 42, true, 10L, 20L, 1620, 2412).managedOnScreen());
        assertFalse("the exact copy is gone",
            WallpaperSourceFacts.of(42, 42, false, 0L, 0L, 0, 0).managedOnScreen());
    }

    @Test
    public void unreadableSystemIdNeverMatchesTheUnsetSentinel() {
        assertFalse(WallpaperSourceFacts.of(-1, -1, true, 1L, 1L, 10, 10).managedOnScreen());
        assertFalse(WallpaperSourceFacts.NONE.managedOnScreen());
    }

    @Test
    public void aNewStoredIdMakesTheFactsStale() {
        WallpaperSourceFacts facts = WallpaperSourceFacts.of(42, 42, true, 10L, 20L, 1620, 2412);
        assertFalse(facts.isStaleFor(42));
        assertTrue("the picker stored a new id after writing the file", facts.isStaleFor(43));
        assertTrue("the stored id was cleared", facts.isStaleFor(0));
    }

    @Test
    public void sizeIsKeptOnlyWithAFileAndBothSides() {
        assertTrue(WallpaperSourceFacts.of(1, 1, true, 0L, 0L, 1620, 2412).hasManagedSize());
        WallpaperSourceFacts gone = WallpaperSourceFacts.of(1, 1, false, 0L, 0L, 1620, 2412);
        assertFalse(gone.hasManagedSize());
        assertEquals(0, gone.managedWidth);
        assertEquals(0, gone.managedHeight);
        assertFalse(WallpaperSourceFacts.of(1, 1, true, 0L, 0L, 0, 2412).hasManagedSize());
    }

    @Test
    public void equalFactsCompareEqualAndAnyFieldBreaksIt() {
        WallpaperSourceFacts a = WallpaperSourceFacts.of(5, 5, true, 100L, 200L, 30, 40);
        assertEquals(a, WallpaperSourceFacts.of(5, 5, true, 100L, 200L, 30, 40));
        assertEquals(a.hashCode(), WallpaperSourceFacts.of(5, 5, true, 100L, 200L, 30, 40).hashCode());
        assertNotEquals(a, WallpaperSourceFacts.of(4, 5, true, 100L, 200L, 30, 40));
        assertNotEquals(a, WallpaperSourceFacts.of(5, 6, true, 100L, 200L, 30, 40));
        assertNotEquals(a, WallpaperSourceFacts.of(5, 5, true, 101L, 200L, 30, 40));
        assertNotEquals(a, WallpaperSourceFacts.of(5, 5, true, 100L, 201L, 30, 40));
        assertNotEquals(a, WallpaperSourceFacts.of(5, 5, true, 100L, 200L, 31, 40));
    }
}
