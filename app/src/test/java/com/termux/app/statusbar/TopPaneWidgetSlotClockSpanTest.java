package com.termux.app.statusbar;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The clock's cell when it has the slot to itself. The widget centres inside its own bounds, so a
 * centred clock is only centred on the bar if the cell is — and the cell used to run from the
 * place icon to the gutter, putting "centre" 26dp toward the trailing edge. These pin the cell
 * to the bar's own centre line whatever the leading icons measure.
 */
public class TopPaneWidgetSlotClockSpanTest {

    private static final int WIDTH = 1080;
    private static final int LEADING = 192; // 64dp at 3x
    private static final int GUTTER = 36;   // 12dp at 3x

    @Test
    public void leftRunsFromThePlaceIconToTheGutter() {
        assertArrayEquals(new int[]{LEADING, WIDTH - GUTTER},
            TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER,
                TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT));
    }

    @Test
    public void rightRunsFromThePlaceIconToTheGutter() {
        assertArrayEquals(new int[]{LEADING, WIDTH - GUTTER},
            TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER,
                TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT));
    }

    @Test
    public void centreIsSymmetricAboutTheBar() {
        int[] span = TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER);
        assertEquals("the cell's centre must be the bar's centre",
            WIDTH / 2f, (span[0] + span[1]) / 2f, .5f);
        assertEquals("the cell keeps clear of the place icon", LEADING, span[0]);
    }

    @Test
    public void centreFollowsWiderLeadingIcons() {
        int wider = LEADING + 120;
        int[] span = TopPaneWidgetSlot.clockOnlySpan(WIDTH, wider, GUTTER,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER);
        assertArrayEquals(new int[]{wider, WIDTH - wider}, span);
    }

    @Test
    public void unknownAndNullFallBackToLeft() {
        int[] left = TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT);
        assertArrayEquals(left, TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER, null));
        assertArrayEquals(left, TopPaneWidgetSlot.clockOnlySpan(WIDTH, LEADING, GUTTER, "middle"));
    }

    @Test
    public void aBarNarrowerThanItsInsetsYieldsAnEmptyCellNotANegativeOne() {
        int[] span = TopPaneWidgetSlot.clockOnlySpan(300, LEADING, GUTTER,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER);
        assertEquals(span[0], span[1]);
    }
}
