package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.Gravity;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TerminalCommandPaletteController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * Where the drawer sits in the back-press chain.
 *
 * <p>The order is the whole feature here: the palette is transient and can be summoned over the
 * drawer, so it keeps the first slot; the drawer is a full-screen plane and both consumers under it
 * — dock tuning and the navigation drawer — are behind it on screen, so a back press must not reach
 * them while it is up. Getting that wrong dismisses something the user cannot see and leaves the
 * thing they were looking at exactly where it was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityBackOrderTest {

    @Test
    public void theDrawerConsumesBackBeforeDockTuning() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        AppDrawerController controller = openDrawer(activity);
        ReflectionHelpers.setField(activity, "mDockTuningMode", true);

        activity.onBackPressed();

        assertFalse(controller.isOpen());
        boolean dockTuningMode = ReflectionHelpers.getField(activity, "mDockTuningMode");
        assertTrue("dock tuning must not consume a back press aimed at the drawer", dockTuningMode);
    }

    @Test
    public void thePaletteStillConsumesBackBeforeTheDrawer() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = openDrawer(activity);
        TerminalCommandPaletteController palette = activity.getCommandPaletteController();
        ReflectionHelpers.setField(palette, "mOpen", true);

        activity.onBackPressed();

        assertFalse(palette.isOpen());
        assertTrue("the drawer must survive the back press that collapses the palette",
            controller.isOpen());
    }

    @Test
    public void withNothingOpenTheDrawerBranchIsSkippedEntirely() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        activity.onBackPressed();

        // The branch is guarded on the field, not on the lazy accessor: a back press on a session
        // that never pulled the drawer down must not build one.
        assertNull(ReflectionHelpers.getField(activity, "mAppDrawerController"));
        assertTrue(activity.getDrawer().isDrawerOpen(Gravity.LEFT));
    }

    // ------------------------------------------------------------------ back inside the drawer

    @Test
    public void backWithSomethingTypedClearsTheQueryAndLeavesTheDrawerUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = openDrawerWithAGrid(activity);
        type(activity, "map");
        assertTrue(controller.getSearchController().hasQuery());

        activity.onBackPressed();

        assertFalse("the query is what the press was spent on",
            controller.getSearchController().hasQuery());
        assertTrue("a search that had something in it must not also close the drawer",
            controller.isOpen());
        // …and the press was consumed here, not passed down the chain.
        assertFalse(activity.getDrawer().isDrawerOpen(Gravity.LEFT));
    }

    @Test
    public void theSecondBackClosesTheDrawer() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = openDrawerWithAGrid(activity);
        type(activity, "map");

        activity.onBackPressed();
        activity.onBackPressed();

        assertFalse(controller.isOpen());
        assertFalse(activity.getDrawer().isDrawerOpen(Gravity.LEFT));
    }

    @Test
    public void anEmptyQueryClosesOnTheFirstBack() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = openDrawerWithAGrid(activity);

        activity.onBackPressed();

        assertFalse(controller.isOpen());
    }

    @Test
    public void thePaletteStillWinsOverADrawerWithAQuery() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = openDrawerWithAGrid(activity);
        type(activity, "map");
        TerminalCommandPaletteController palette = activity.getCommandPaletteController();
        ReflectionHelpers.setField(palette, "mOpen", true);

        activity.onBackPressed();

        assertFalse(palette.isOpen());
        assertTrue(controller.isOpen());
        assertTrue("the drawer's query must survive a press the palette consumed",
            controller.getSearchController().hasQuery());
    }

    /** An open drawer with no views bound — the back consumer reads state, not geometry. */
    private static AppDrawerController openDrawer(TermuxActivity activity) {
        AppDrawerController controller = activity.getAppDrawerController();
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        return controller;
    }

    /**
     * The same, plus the grid the query lives in. Built and injected directly rather than by driving
     * a drag: what is under test is the order of the back consumers, and a test that had to lay a
     * plane out to reach it would be reporting a layout bug as an ordering one.
     */
    private static AppDrawerController openDrawerWithAGrid(TermuxActivity activity) {
        AppDrawerController controller = openDrawer(activity);
        AppDrawerContentView content = new AppDrawerContentView(activity);
        content.bind(null, controller.getSearchController());
        ReflectionHelpers.setField(controller, "mContent", content);
        return controller;
    }

    private static void type(TermuxActivity activity, String text) {
        for (int i = 0; i < text.length(); i++) {
            assertTrue(activity.handleAppDrawerCodePoint(text.charAt(i), false));
        }
    }
}
