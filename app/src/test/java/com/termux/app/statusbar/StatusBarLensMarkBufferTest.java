package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.StatusBarLensMetrics.Bar;
import com.termux.app.statusbar.StatusBarLensMetrics.Mark;
import com.termux.app.statusbar.StatusBarLensMetrics.MarkBuffer;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The lens's reusable mark buffer lays out exactly what a fresh layout does, frame after frame. */
public class StatusBarLensMarkBufferTest {

    private static final List<PaneWallPage> RING = Arrays.asList(PaneWallPage.WIDGETS,
        PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);
    private static final float DENSITY = 2f;
    private static final int BAR_W = 800;

    @Test
    public void aDragThroughOneBufferMatchesFreshMarksAtEveryStep() {
        MarkBuffer buffer = new MarkBuffer();
        for (boolean vertical : new boolean[] {false, true}) {
            Bar bar = new Bar(vertical ? 68 : BAR_W, vertical ? 600 : 136, DENSITY, vertical,
                false, 0.6f, 24, 48, 50f, 60f, 12f);
            int wall = vertical ? 600 : BAR_W;
            for (float offset = -1.6f * wall; offset <= 1.6f * wall; offset += wall / 7f) {
                List<Mark> fresh = StatusBarLensMetrics.marks(bar, RING, PaneWallPage.TERMINAL,
                    offset, wall, offset > 0f);
                List<Mark> reused = StatusBarLensMetrics.marks(bar, RING, PaneWallPage.TERMINAL,
                    offset, wall, offset > 0f, buffer);
                assertEquals(fresh.size(), reused.size());
                for (int i = 0; i < fresh.size(); i++) assertSameMark(fresh.get(i), reused.get(i));
            }
        }
    }

    @Test
    public void theBufferHandsBackTheSameMarkObjectsEachFrame() {
        MarkBuffer buffer = new MarkBuffer();
        Bar bar = new Bar(BAR_W, 136, DENSITY, false, false, 1f, 0, 0, -1f, -1f, -1f);
        List<Mark> first = StatusBarLensMetrics.marks(bar, RING, PaneWallPage.TERMINAL, 0f, BAR_W,
            true, buffer);
        Mark firstMark = first.get(0);
        List<Mark> second = StatusBarLensMetrics.marks(bar, RING, PaneWallPage.TERMINAL, 40f,
            BAR_W, true, buffer);
        assertSame(firstMark, second.get(0));
        // A wall of one place empties the buffer rather than leaving last frame's marks in it.
        assertTrue(StatusBarLensMetrics.marks(bar, Arrays.asList(PaneWallPage.TERMINAL),
            PaneWallPage.TERMINAL, 0f, BAR_W, true, buffer).isEmpty());
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

    private static void assertSameMark(Mark expected, Mark actual) {
        assertEquals(expected.page, actual.page);
        assertEquals(expected.t, actual.t, 0f);
        assertEquals(expected.presence, actual.presence, 0f);
        assertEquals(expected.home, actual.home);
        assertEquals(expected.centerX, actual.centerX, 0f);
        assertEquals(expected.centerY, actual.centerY, 0f);
        assertEquals(expected.sizePx, actual.sizePx, 0f);
        assertEquals(expected.radiusPx, actual.radiusPx, 0f);
        assertEquals(expected.glyphSizePx, actual.glyphSizePx, 0f);
        assertEquals(expected.glyphInk, actual.glyphInk, 0f);
        assertEquals(expected.ink, actual.ink, 0f);
        assertEquals(expected.effectiveInk, actual.effectiveInk, 0f);
        assertEquals(expected.fadeOuterAlpha, actual.fadeOuterAlpha, 0f);
        assertEquals(expected.glow, actual.glow, 0f);
        assertEquals(expected.fades, actual.fades);
        assertEquals(expected.fadesFromNearEnd, actual.fadesFromNearEnd);
        assertBox(expected.tile, actual.tile);
        assertBox(expected.target, actual.target);
    }

    private static void assertBox(StatusBarLensMetrics.Box expected, StatusBarLensMetrics.Box actual) {
        assertEquals(expected.left, actual.left, 0f);
        assertEquals(expected.top, actual.top, 0f);
        assertEquals(expected.right, actual.right, 0f);
        assertEquals(expected.bottom, actual.bottom, 0f);
    }
}
