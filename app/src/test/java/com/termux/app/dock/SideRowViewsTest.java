package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.R;
import com.termux.app.page.Side;
import com.termux.app.place.Element;

import org.junit.Test;

/** Readers reach rows through their side; each side's extra-keys bar is its own view. */
public class SideRowViewsTest {

    @Test public void theStacksRowsAreTheirSidesViews() {
        assertEquals(R.id.apps_bar_viewpager, SideRowViews.id(Element.APPS, SideRowViews.Part.ROW));
        assertEquals(R.id.apps_bar_row_host,
            SideRowViews.id(Element.APPS, SideRowViews.Part.BOTTOM_HOST));
        assertEquals(R.id.apps_bar_az_row, SideRowViews.id(Element.AZ, SideRowViews.Part.ROW));
        assertEquals(R.id.place_az_bar_host,
            SideRowViews.id(Element.AZ, SideRowViews.Part.EDGE_HOST));
        assertEquals(R.id.terminal_toolbar_view_pager,
            SideRowViews.id(Element.EXTRA_KEYS, SideRowViews.Part.ROW));
        assertEquals(R.id.terminal_toolbar_host,
            SideRowViews.id(Element.EXTRA_KEYS, SideRowViews.Part.BOTTOM_HOST));
        assertEquals(0, SideRowViews.id(Element.STATUS, SideRowViews.Part.ROW));
    }

    @Test public void eachSideHasItsOwnExtraKeysBar() {
        int home = SideRowViews.id(Side.HOME, Side.Row.EXTRA_KEYS, SideRowViews.Part.ROW);
        int work = SideRowViews.id(Side.TERMINAL, Side.Row.EXTRA_KEYS, SideRowViews.Part.ROW);
        assertNotEquals(0, home);
        assertNotEquals(0, work);
        assertNotEquals(home, work);
    }

    @Test public void aSideHasNoViewForARowItDoesNotOwn() {
        for (SideRowViews.Part part : SideRowViews.Part.values()) {
            assertEquals(0, SideRowViews.id(Side.TERMINAL, Side.Row.APPS, part));
            assertEquals(0, SideRowViews.id(Side.HOME, Side.Row.WINDOW_CHIPS, part));
        }
    }
}
