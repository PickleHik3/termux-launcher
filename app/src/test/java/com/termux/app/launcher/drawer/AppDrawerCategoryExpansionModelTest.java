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
        assertEquals(0, model.setProgress(0.24f));
        assertEquals(AppDrawerCategoryExpansionModel.RELEASE_OVERVIEW,
            model.setProgress(0.25f));
        assertEquals(0, model.setProgress(0.30f));
        assertEquals(AppDrawerCategoryExpansionModel.BIND_DETAIL, model.setProgress(0.35f));
        assertEquals(0, model.setProgress(0.9f));
        assertEquals(AppDrawerCategoryExpansionModel.RELEASE_DETAIL, model.setProgress(0.34f));
        assertEquals(AppDrawerCategoryExpansionModel.BIND_OVERVIEW, model.setProgress(0.24f));
        assertEquals(0, model.setProgress(0.1f));
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
