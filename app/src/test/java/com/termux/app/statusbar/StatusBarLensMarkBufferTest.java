package com.termux.app.statusbar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.StatusBarLensMetrics.Bar;
import com.termux.app.statusbar.StatusBarLensMetrics.MarkBuffer;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The lens's reusable buffer and the bar it keeps across draws. The bar wears no place marks
 * (#90), so the buffer is handed back empty frame after frame.
 */
public class StatusBarLensMarkBufferTest {

    private static final List<PaneWallPage> RING = Arrays.asList(PaneWallPage.WIDGETS,
        PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);
    private static final float DENSITY = 2f;
    private static final int BAR_W = 800;

    @Test
    public void aDragThroughOneBufferLeavesItEmptyAtEveryStep() {
        MarkBuffer buffer = new MarkBuffer();
        Bar bar = new Bar(BAR_W, 136, DENSITY, false, false, 0.6f, 24, 48, 50f, 60f, 12f);
        for (float offset = -1.6f * BAR_W; offset <= 1.6f * BAR_W; offset += BAR_W / 7f) {
            assertTrue(StatusBarLensMetrics.marks(bar, RING, PaneWallPage.TERMINAL, offset, BAR_W,
                buffer).isEmpty());
        }
    }

    @Test
    public void aBarIsTheSameOnlyWhenTheConstructorWouldBuildIt() {
        Bar bar = new Bar(BAR_W, 136, DENSITY, false, true, 1.4f, -3, 8, 50f, 60f, 12f);
        assertTrue(bar.sameAs(BAR_W, 136, DENSITY, false, true, 1.4f, -3, 8, 50f, 60f, 12f));
        // Clamped inputs compare by what the bar stores.
        assertTrue(bar.sameAs(BAR_W, 136, DENSITY, false, true, 1f, 0, 8, 50f, 60f, 12f));
        assertFalse(bar.sameAs(BAR_W, 136, DENSITY, false, true, 0.9f, 0, 8, 50f, 60f, 12f));
        assertFalse(bar.sameAs(BAR_W + 1, 136, DENSITY, false, true, 1f, 0, 8, 50f, 60f, 12f));
        assertFalse(bar.sameAs(BAR_W, 136, DENSITY, true, true, 1f, 0, 8, 50f, 60f, 12f));
        assertFalse(bar.sameAs(BAR_W, 136, DENSITY, false, true, 1f, 0, 8, 51f, 60f, 12f));
        assertFalse(bar.sameAs(BAR_W, 136, DENSITY, false, true, 1f, 0, 8, 50f, 60f, 13f));
    }
}
