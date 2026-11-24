package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class RootUnderOverviewTest {

    @Test
    public void theRootShowsWhenTheExitStartsAndIsAtRestWhenItEnds() {
        View root = new View(RuntimeEnvironment.getApplication());
        RootUnderOverview under = new RootUnderOverview();
        under.hide(root);
        assertEquals(0f, root.getAlpha(), 0f);
        under.reveal(root);
        assertEquals(1f, root.getAlpha(), 0f);
        // The backstop or a second press ends the overview without a frame: restore still lands.
        under.restore(root);
        assertEquals(1f, root.getAlpha(), 0f);
    }

    @Test
    public void aRootThatStartedTranslucentComesBackTranslucent() {
        View root = new View(RuntimeEnvironment.getApplication());
        root.setAlpha(0.6f);
        RootUnderOverview under = new RootUnderOverview();
        under.hide(root);
        under.reveal(root);
        assertEquals(0.6f, root.getAlpha(), 0f);
        under.restore(root);
        assertEquals(0.6f, root.getAlpha(), 0f);
    }
}
