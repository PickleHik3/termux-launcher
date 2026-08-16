package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Part;
import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Presentation;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Test;

public class AppDrawerCategoryTouchRegionsTest {
    private static final Frame BODY = new Frame(0, 0, 100, 200);
    private static final Frame OVERVIEW = new Frame(0, 20, 100, 180);
    private static final Frame ACTION = new Frame(10, 30, 30, 50);
    private static final Frame HEADER = new Frame(0, 100, 100, 120);
    private static final Frame LIST = new Frame(0, 130, 100, 200);

    @Test public void overviewUsesHalfOpenEdgesAndActionWins() {
        assertEquals(Part.EXPAND_ACTION, resolve(10, 30, Presentation.OVERVIEW));
        assertEquals(Part.OVERVIEW_LIST, resolve(30, 50, Presentation.OVERVIEW));
        assertEquals(Part.EMPTY_CHROME, resolve(50, 19.999f, Presentation.OVERVIEW));
        assertEquals(Part.OUTSIDE, resolve(100, 20, Presentation.OVERVIEW));
        assertEquals(Part.OUTSIDE, resolve(0, 200, Presentation.OVERVIEW));
    }

    @Test public void expandedHeaderAndListAreOwnedButEmptyTopIsChrome() {
        assertEquals(Part.EMPTY_CHROME, resolve(50, 50, Presentation.EXPANDED));
        assertEquals(Part.COLLAPSE_ACTION, resolve(50, 100, Presentation.EXPANDED));
        assertEquals(Part.EMPTY_CHROME, resolve(50, 120, Presentation.EXPANDED));
        assertEquals(Part.DETAIL_LIST, resolve(50, 130, Presentation.EXPANDED));
        assertTrue(AppDrawerCategoryTouchRegions.isContentOwned(Part.COLLAPSE_ACTION));
        assertTrue(AppDrawerCategoryTouchRegions.isContentOwned(Part.DETAIL_LIST));
        assertFalse(AppDrawerCategoryTouchRegions.isContentOwned(Part.EMPTY_CHROME));
    }

    @Test public void everyMovingPresentationOwnsTheWholeBody() {
        for (Presentation value : new Presentation[] {Presentation.EXPANDING,
            Presentation.COLLAPSING, Presentation.COLLAPSE_DRAGGING}) {
            assertEquals(Part.TRANSITION_BODY, resolve(50, 50, value));
            assertTrue(AppDrawerCategoryTouchRegions.isContentOwned(
                resolve(50, 50, value)));
        }
    }

    private static Part resolve(float x, float y, Presentation presentation) {
        return AppDrawerCategoryTouchRegions.resolve(x, y, BODY, presentation,
            OVERVIEW, ACTION, HEADER, LIST);
    }
}
