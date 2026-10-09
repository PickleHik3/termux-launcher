package com.termux.app.launcher.folder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The editing title slides only as far as it must to keep the caret inside the viewport. */
public class FolderRenameTitleOverflowTest {
    @Test public void shortNameDoesNotSlide() {
        assertEquals(0f, FolderRenameTitleView.scrollFor(40f, 3f, 120f), 0f);
    }

    @Test public void caretPastTheEdgeSlidesTheTextJustEnough() {
        assertEquals(23f, FolderRenameTitleView.scrollFor(140f, 3f, 120f), 0f);
    }

    @Test public void zeroWidthViewportStillKeepsTheCaretAtItsEdge() {
        assertEquals(53f, FolderRenameTitleView.scrollFor(50f, 3f, 0f), 0f);
    }
}
