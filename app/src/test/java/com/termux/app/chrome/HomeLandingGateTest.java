package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A home press's landing held behind one frame with no pages: the wallpaper alone draws, the page
 * settles after that frame, and Home's first frame brings everything back. Each wait has a way
 * out, so the gate can never leave the home screen blank.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeLandingGateTest {

    private FrameLayout root;
    private FrameLayout ground;
    private WallpaperBackdropView wallpaper;
    private View strip;
    private FrameLayout pages;
    private FrameLayout chrome;
    private HomeLandingGate gate;
    private final List<String> ran = new ArrayList<>();
    private final Runnable settle = () -> ran.add("settle");

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        root.setId(R.id.window_layers_root);
        ground = new FrameLayout(activity);
        ground.setId(R.id.window_layer_ground);
        wallpaper = new WallpaperBackdropView(activity);
        strip = new View(activity);
        ground.addView(wallpaper);
        ground.addView(strip);
        pages = new FrameLayout(activity);
        pages.setId(R.id.activity_termux_root_relative_layout);
        chrome = new FrameLayout(activity);
        chrome.setId(R.id.window_layer_chrome);
        root.addView(ground);
        root.addView(pages);
        root.addView(chrome);
        activity.setContentView(root);
        idle();
        gate = new HomeLandingGate();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private void assertHidden() {
        assertEquals(0f, pages.getTransitionAlpha(), 0f);
        assertEquals(0f, chrome.getTransitionAlpha(), 0f);
        assertEquals("the page's ground goes with it", 0f, strip.getTransitionAlpha(), 0f);
        assertEquals("the wallpaper stays", 1f, wallpaper.getTransitionAlpha(), 0f);
    }

    private void assertShown() {
        // A frame reveal fades Home in; the views reach their own alpha when it ends.
        idleFor(HomeLandingGate.REVEAL_FADE_MS + 20);
        assertFalse("the fade has ended", gate.isFading());
        assertEquals(1f, pages.getTransitionAlpha(), 0f);
        assertEquals(1f, chrome.getTransitionAlpha(), 0f);
        assertEquals(1f, strip.getTransitionAlpha(), 0f);
        assertEquals(1f, wallpaper.getTransitionAlpha(), 0f);
    }

    @Test public void aFrameRevealFadesHomeInAndAFinishEndsTheFadeAtOnce() {
        assertTrue(gate.begin(root, settle));
        gate.onHiddenFrameDrawn();
        idle();
        assertTrue(gate.onRevealFrame());
        assertTrue("Home fades in", gate.isFading());
        assertTrue("not shown at once", pages.getTransitionAlpha() < 1f);
        gate.finishNow();
        assertFalse(gate.isFading());
        assertEquals(1f, pages.getTransitionAlpha(), 0f);
        assertEquals(1f, chrome.getTransitionAlpha(), 0f);
        assertEquals(1f, strip.getTransitionAlpha(), 0f);
    }

    @Test public void hidesEverythingButTheWallpaperAndSettlesAfterTheHiddenFrame() {
        assertTrue(gate.begin(root, settle));
        assertTrue(gate.isRunning());
        assertHidden();
        assertTrue("nothing settles before the hidden frame", ran.isEmpty());

        gate.onHiddenFrameDrawn();
        assertTrue("not inside the draw: on the loop's next turn", ran.isEmpty());
        idle();
        assertEquals(List.of("settle"), ran);
        assertEquals(HomeLandingGate.BY_FRAME, gate.settledBy());
        assertHidden();

        // Home's first frame, laid out: everything comes back in its pre-draw.
        assertTrue(gate.onRevealFrame());
        assertFalse(gate.isRunning());
        assertEquals(HomeLandingGate.BY_FRAME, gate.lastEnd());
        assertShown();

        idleFor(HomeLandingGate.REVEAL_BACKSTOP_MS + HomeLandingGate.HIDDEN_FRAME_BACKSTOP_MS);
        assertEquals("the settle runs once", 1, ran.size());
        assertShown();
    }

    @Test public void aHiddenFrameThatNeverDrawsStillSettlesAndShows() {
        assertTrue(gate.begin(root, settle));
        idleFor(HomeLandingGate.HIDDEN_FRAME_BACKSTOP_MS
            + HomeLandingGate.REVEAL_BACKSTOP_MS + 10);
        assertEquals(List.of("settle"), ran);
        assertFalse(gate.isRunning());
        assertShown();
    }

    @Test public void framesThatKeepLayingOutShowAfterTheFrameLimit() {
        assertTrue(gate.begin(root, settle));
        gate.onHiddenFrameDrawn();
        idle();
        assertEquals(List.of("settle"), ran);

        for (int i = 1; i < HomeLandingGate.REVEAL_FRAMES; i++) {
            root.requestLayout();
            assertFalse("a frame with layout pending keeps waiting", gate.onRevealFrame());
            assertHidden();
        }
        root.requestLayout();
        assertTrue(gate.onRevealFrame());
        assertEquals(HomeLandingGate.BY_FRAMES, gate.lastEnd());
        assertShown();
    }

    @Test public void noFrameAfterTheSettleShowsAtTheBackstop() {
        assertTrue(gate.begin(root, settle));
        gate.onHiddenFrameDrawn();
        idle();
        assertTrue(gate.isRunning());
        idleFor(HomeLandingGate.REVEAL_BACKSTOP_MS + 1);
        assertFalse(gate.isRunning());
        assertShown();
    }

    @Test public void finishNowSettlesOnceAndShows() {
        assertTrue(gate.begin(root, settle));
        gate.finishNow();
        assertEquals(List.of("settle"), ran);
        assertEquals(HomeLandingGate.BY_FINISH, gate.settledBy());
        assertEquals(HomeLandingGate.BY_FINISH, gate.lastEnd());
        assertShown();

        gate.onHiddenFrameDrawn();
        idleFor(HomeLandingGate.HIDDEN_FRAME_BACKSTOP_MS + HomeLandingGate.REVEAL_BACKSTOP_MS);
        gate.finishNow();
        assertEquals("nothing runs twice", 1, ran.size());
    }

    @Test public void aSettleThatThrowsShowsOnTheWayOut() {
        assertTrue(gate.begin(root, () -> { throw new IllegalStateException("boom"); }));
        try {
            gate.finishNow();
            fail("the settle's exception is not swallowed");
        } catch (IllegalStateException expected) {
            // The views came back all the same.
        }
        assertFalse(gate.isRunning());
        assertEquals(HomeLandingGate.BY_EXCEPTION, gate.lastEnd());
        assertShown();
    }

    @Test public void aViewThatLeftItsParentMeanwhileIsGivenBack() {
        assertTrue(gate.begin(root, () -> {
            ran.add("settle");
            ground.removeView(strip);
        }));
        gate.finishNow();
        assertEquals(1f, strip.getTransitionAlpha(), 0f);
    }

    @Test public void noWindowHoldsNothing() {
        assertFalse(gate.begin(null, settle));
        FrameLayout loose = new FrameLayout(root.getContext());
        assertFalse("a root in no window has no frame to wait on", gate.begin(loose, settle));
        assertFalse(gate.isRunning());
        assertTrue(ran.isEmpty());
        assertNull(gate.lastEnd());
        assertShown();
    }
}
