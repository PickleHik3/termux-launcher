package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LayoutCanvasSlotContentTest {

    @Test
    public void suppliedItemsDriveTheCountCappedByWhatFits() {
        // 200 long at 20 per slot: floor(184 / 20) = 9 fit.
        assertEquals(5, LayoutCanvasArtwork.slotsForContent(5, 99, 200f, 20f));
        assertEquals(9, LayoutCanvasArtwork.slotsForContent(12, 99, 200f, 20f));
    }

    @Test
    public void noSuppliedItemsFallsBackToThePackRule() {
        assertEquals(LayoutCanvasArtwork.slotsFor(-1, 200f, 20f),
            LayoutCanvasArtwork.slotsForContent(0, -1, 200f, 20f));
        assertEquals(3, LayoutCanvasArtwork.slotsForContent(0, 3, 200f, 20f));
    }

    @Test
    public void keyLabelsAreCutToTheSlot() {
        assertEquals("ESC", LayoutCanvasArtwork.fitLabel("ESC", 3));
        assertEquals("PG", LayoutCanvasArtwork.fitLabel("PGUP", 2));
        assertEquals("", LayoutCanvasArtwork.fitLabel("X", 0));
    }
}
