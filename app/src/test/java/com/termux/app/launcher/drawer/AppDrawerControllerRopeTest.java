package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawer's one animation loop, now that something other than the transition needs it.
 *
 * <p>The first case is the slice's most dangerous line. Before B-3 the loop never ran while a finger
 * was down, so {@code doFrame}'s {@code !mOpen && p < CLOSED_EPSILON} teardown could not be reached
 * mid-gesture. The rope needs frames <em>during</em> the opening drag — where {@code mOpen} is false
 * and {@code p} starts at zero — so without the {@code !mDragging} guard the drawer is torn down on
 * the first frame of every open, under the user's finger, and presents as "the drawer sometimes
 * refuses to open".
 *
 * <p>The rest pin the seams that make one loop enough: the drag kicks it, a scrub on a settled drawer
 * restarts it through the content's frame-request listener, and a request from a drawer that is
 * already down is refused rather than running the teardown a second time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerControllerRopeTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final long FRAME_NANOS = 16_666_667L;

    private TermuxActivity activity;
    private AppDrawerController controller;

    @Before
    public void setUp() {
        // Frame callbacks must only happen when a test says so: an unpaused legacy scheduler runs a
        // posted callback immediately, which would settle the loop inside the call that started it.
        Robolectric.getForegroundThreadScheduler().pause();
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        controller = activity.getAppDrawerController();
    }

    // ------------------------------------------------------------------ the guard

    @Test
    public void aFrameDuringTheOpeningDragDoesNotTearTheDrawerDown() {
        engage(false, 0f);
        ReflectionHelpers.setField(controller, "mDragging", true);

        // Exactly the state the first frames of every open are in: engaged, not open, p at zero.
        for (int i = 1; i <= 4; i++) {
            controller.doFrame(i * FRAME_NANOS);
        }

        assertTrue("the teardown ran under the finger", controller.isEngaged());
    }

    @Test
    public void theTeardownStillRunsOnceTheFingerIsGone() {
        engage(false, 0f);
        ReflectionHelpers.setField(controller, "mDragging", false);

        controller.doFrame(FRAME_NANOS);

        // The control for the case above: the guard must not have turned the teardown off, only
        // deferred it until there is no gesture to tear down under.
        assertFalse(controller.isEngaged());
        assertFalse(controller.isOpen());
    }

    // ------------------------------------------------------------------ the kicks

    @Test
    public void beginningAndUpdatingADragBothKickTheLoop() {
        // A closing drag, which skips the geometry capture a laid-out dock would be needed for.
        engage(true, 1f);
        setFrameScheduled(false);

        controller.beginDrag(HEIGHT * 0.5f);
        assertTrue("the rope needs frames for the length of the drag", isFrameScheduled());

        setFrameScheduled(false);
        controller.updateDrag(HEIGHT * 0.5f + 40f);
        // The progress spring reports settled the moment it is told where the finger is, so without
        // this the loop would end on the first stationary frame and the rope would freeze mid-swing.
        assertTrue(isFrameScheduled());
    }

    @Test
    public void aScrubOnASettledDrawerRestartsTheLoopThroughTheContent() {
        AppDrawerContentView content = attachContent();
        engage(true, 1f);
        setFrameScheduled(false);

        // What the seam is for: p is pinned at 1 and both springs are settled, so nothing else in the
        // drawer would ever ask for another frame.
        content.onScrubLetterChanged('C');
        assertTrue(isFrameScheduled());

        setFrameScheduled(false);
        content.onScrubEnded();
        assertTrue("the release fade needs frames of its own", isFrameScheduled());
    }

    @Test
    public void theLoopTicksTheDrawerEffectsAndReKicksItselfWhileTheyMove() {
        AppDrawerContentView content = attachContent();
        engage(true, 0.5f);
        setFrameScheduled(false);

        controller.doFrame(FRAME_NANOS);

        // Neither spring is moving — p is at its target — so the only thing that can have re-armed
        // the callback is the rope, which is exactly the term the re-kick had to grow.
        assertTrue("the fx term is missing from the re-kick", isFrameScheduled());
        assertTrue(controller.isEngaged());

        // And it stops: a chain that never reported settled would hold the loop open forever on an
        // idle open drawer.
        int frames = 0;
        for (long nanos = 2 * FRAME_NANOS; frames < 600; nanos += FRAME_NANOS, frames++) {
            setFrameScheduled(false);
            controller.doFrame(nanos);
            if (!isFrameScheduled()) break;
        }
        assertTrue("never settled after " + frames + " frames", frames < 600);
    }

    @Test
    public void aFrameRequestFromATornDownDrawerIsRefused() {
        setFrameScheduled(false);
        // Not engaged: a frame here would enter doFrame with p at zero and run the teardown against
        // geometry that has already been handed back.
        controller.requestFrames();
        assertFalse(isFrameScheduled());

        engage(true, 1f);
        setFrameScheduled(false);
        controller.requestFrames();
        assertTrue(isFrameScheduled());
    }

    // ------------------------------------------------------------------ plumbing

    /** A real, laid-out content with a full alphabet, installed as the controller's own. */
    private AppDrawerContentView attachContent() {
        Context context = activity;
        AppDrawerContentView content = new AppDrawerContentView(context);
        AppDrawerSearchController search = controller.getSearchController();
        content.setFrameRequestListener(controller::requestFrames);
        content.setInteractive(true);
        content.setMetrics(AppDrawerGridMetrics.resolve(WIDTH - content.getColumnWidthPx(),
            context.getResources().getDisplayMetrics().density, 30f));
        content.bind(null, search);
        search.setCatalogue(alphabet());
        content.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
        ReflectionHelpers.setField(controller, "mContent", content);
        return content;
    }

    private void engage(boolean open, float progress) {
        ReflectionHelpers.setField(controller, "mEngaged", true);
        ReflectionHelpers.setField(controller, "mOpen", open);
        Spring spring = ReflectionHelpers.getField(controller, "mProgress");
        spring.value = progress;
        spring.target = progress;
        spring.vel = 0f;
    }

    private boolean isFrameScheduled() {
        return ReflectionHelpers.getField(controller, "mFrameScheduled");
    }

    private void setFrameScheduled(boolean scheduled) {
        ReflectionHelpers.setField(controller, "mFrameScheduled", scheduled);
    }

    private static List<LauncherAppEntry> alphabet() {
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            for (int i = 0; i < 4; i++) {
                String label = letter + "pp " + i;
                entries.add(new LauncherAppEntry(
                    new AppRef("com.example." + label.replace(' ', '.'), ".Main"), label, null));
            }
        }
        return entries;
    }
}
