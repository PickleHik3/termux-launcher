package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class AppDrawerCategoryExpansionModelTest {
    private static final String ID = "social";

    @Test public void legalTransitionsAndIllegalEventsAreNoOps() {
        AppDrawerCategoryExpansionModel model = new AppDrawerCategoryExpansionModel();
        assertFalse(model.collapse());
        assertFalse(model.beginCollapseDrag());
        assertTrue(model.expand(ID));
        assertFalse(model.expand("other"));
        model.setProgress(1f);
        model.settle();
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED, model.state());
        assertTrue(model.beginCollapseDrag());
        model.setProgress(0.7f);
        model.finishCollapseDrag(false);
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDING, model.state());
        model.setProgress(1f);
        model.settle();
        assertTrue(model.collapse());
        model.setProgress(0f);
        model.settle();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, model.state());
        assertNull(model.selectedId());
    }

    @Test public void stagingCrossingsFireOnceInBothDirections() {
        AppDrawerCategoryExpansionModel model = new AppDrawerCategoryExpansionModel();
        model.expand(ID);
        float boundary = AppDrawerCategoryExpansionModel.STAGING_BOUNDARY;
        assertEquals(0, model.setProgress(boundary - 0.01f));
        // One boundary, both crossings: the overview is released and the detail bound together, in
        // that order, so the two icon sets still never hold the shared cache at the same time.
        assertEquals(AppDrawerCategoryExpansionModel.RELEASE_OVERVIEW
            | AppDrawerCategoryExpansionModel.BIND_DETAIL, model.setProgress(boundary));
        assertEquals(0, model.setProgress(0.30f));
        assertEquals(0, model.setProgress(0.9f));
        assertEquals(AppDrawerCategoryExpansionModel.RELEASE_DETAIL
            | AppDrawerCategoryExpansionModel.BIND_OVERVIEW,
            model.setProgress(boundary - 0.01f));
        assertEquals(0, model.setProgress(0.1f));
    }

    /**
     * The invariant Back's second rung stands on: in every non-OVERVIEW state — mid-expansion,
     * settled, mid-drag and already collapsing — collapse() accepts, so a back press over any
     * category surface is always spent on the category and can never fall through to a caller
     * that would close the whole drawer.
     */
    @Test public void collapseIsAcceptedInEveryNonOverviewState() {
        AppDrawerCategoryExpansionModel model = new AppDrawerCategoryExpansionModel();

        // EXPANDING, at a mid-animation progress.
        assertTrue(model.expand(ID));
        model.setProgress(0.4f);
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDING, model.state());
        assertTrue(model.collapse());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, model.state());

        // COLLAPSING: a repeated press keeps being consumed rather than closing the drawer.
        assertTrue(model.collapse());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, model.state());
        model.setProgress(0f);
        model.settle();

        // EXPANDED.
        assertTrue(model.expand(ID));
        model.setProgress(1f);
        model.settle();
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED, model.state());
        assertTrue(model.collapse());

        // COLLAPSE_DRAGGING: back during a live drag retargets it into a collapse, and the drag's
        // own late finish is a no-op against the new state.
        model.setProgress(0f);
        model.settle();
        assertTrue(model.expand(ID));
        model.setProgress(1f);
        model.settle();
        assertTrue(model.beginCollapseDrag());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSE_DRAGGING, model.state());
        assertTrue(model.collapse());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, model.state());
        model.finishCollapseDrag(false);
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, model.state());
    }

    @Test public void backRetargetPullCommitRefreshQueryAndTeardownResetLegally() {
        AppDrawerCategoryExpansionModel model = new AppDrawerCategoryExpansionModel();
        model.expand(ID);
        model.setProgress(0.5f);
        assertTrue(model.collapse());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, model.state());
        assertTrue(model.reconcile(Collections.singleton(ID)));
        assertFalse(model.reconcile(Collections.singleton("other")));
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, model.state());

        model.expand(ID);
        model.queryStarted();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, model.state());
        model.expand(ID);
        model.teardown();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, model.state());
    }
}
