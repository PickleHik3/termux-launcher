package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FontCompactPolicyTest {

    @Test
    public void tapFromAPinnedSizeRemembersItAndTakesCompact() {
        FontCompactPolicy.Next next = FontCompactPolicy.toggle(36, FontCompactPolicy.NOT_COMPACT, 20);
        assertEquals(20, next.pinnedSize);
        assertEquals(36, next.returnSize);
        assertTrue(FontCompactPolicy.isCompact(next.returnSize));
    }

    @Test
    public void tapFromCompactRestoresTheExactRememberedSize() {
        FontCompactPolicy.Next there = FontCompactPolicy.toggle(38, FontCompactPolicy.NOT_COMPACT, 20);
        FontCompactPolicy.Next back = FontCompactPolicy.toggle(there.pinnedSize, there.returnSize, 20);
        assertEquals(38, back.pinnedSize);
        assertEquals(FontCompactPolicy.NOT_COMPACT, back.returnSize);
        assertFalse(FontCompactPolicy.isCompact(back.returnSize));
    }

    @Test
    public void aPaneFollowingTheDefaultGoesBackToFollowingIt() {
        FontCompactPolicy.Next there = FontCompactPolicy.toggle(0, FontCompactPolicy.NOT_COMPACT, 20);
        assertEquals(20, there.pinnedSize);
        assertEquals("0 stands for following the default", 0, there.returnSize);
        assertTrue(FontCompactPolicy.isCompact(there.returnSize));
        FontCompactPolicy.Next back = FontCompactPolicy.toggle(there.pinnedSize, there.returnSize, 20);
        assertEquals(0, back.pinnedSize);
        assertEquals(FontCompactPolicy.NOT_COMPACT, back.returnSize);
    }

    @Test
    public void zoomingOrSteppingWhileCompactLeavesCompact() {
        assertEquals(FontCompactPolicy.NOT_COMPACT, FontCompactPolicy.afterManualChange(36));
        assertEquals(FontCompactPolicy.NOT_COMPACT, FontCompactPolicy.afterManualChange(0));
        // The next tap goes to Compact again, remembering the size the zoom left.
        FontCompactPolicy.Next next = FontCompactPolicy.toggle(24, FontCompactPolicy.NOT_COMPACT, 20);
        assertEquals(20, next.pinnedSize);
        assertEquals(24, next.returnSize);
    }

    @Test
    public void compactSizeIsTheStoredOneOrSeventyPercentOfTheDefaultWithinTheLimits() {
        assertEquals(28, FontCompactPolicy.compactSize(28, 40, 8, 256));
        assertEquals(28, FontCompactPolicy.compactSize(0, 40, 8, 256));
        assertEquals("clamped up to the minimum", 8, FontCompactPolicy.compactSize(0, 10, 8, 256));
        assertEquals("clamped down to the maximum", 256, FontCompactPolicy.compactSize(900, 40, 8, 256));
    }

    @Test
    public void restoreTreatsMissingOrNegativeAsNotCompact() {
        assertEquals(FontCompactPolicy.NOT_COMPACT, FontCompactPolicy.restore(-1));
        assertEquals(0, FontCompactPolicy.restore(0));
        assertEquals(36, FontCompactPolicy.restore(36));
    }
}
