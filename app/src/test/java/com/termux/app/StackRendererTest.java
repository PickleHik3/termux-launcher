package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.window.Counters;
import com.termux.app.window.FrameGuards;
import com.termux.app.window.FramePipeline;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The stack's one writer over fake views: a frame equal to the last writes nothing, a write by
 * anyone else counts the foreign-writer guard, a landing frame that changes a value counts the
 * landing-wrote guard, on a pipeline the shares are written by its step alone, and the plank's
 * slab composed with the crossing's translation is one value written once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererTest {

    private static final int WIDTH = 1080;

    /** Counts every transform write. */
    private static final class Counted extends FrameLayout {
        int writes;
        Counted(Context context) {
            super(context);
        }
        @Override public void setTranslationY(float y) { writes++; super.setTranslationY(y); }
        @Override public void setTranslationX(float x) { writes++; super.setTranslationX(x); }
        @Override public void setClipBounds(Rect clip) { writes++; super.setClipBounds(clip); }
        @Override public void setRotationX(float r) { writes++; super.setRotationX(r); }
        @Override public void setRotationY(float r) { writes++; super.setRotationY(r); }
    }

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mKeyboardShown;
    private boolean mFrontHome;
    private int mKeyboardDownPx;
    private StackRenderer mRenderer;
    private Counted mContainer;
    private Counted mHost;
    private Counted mRows;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mContainer = new Counted(context);
        mHost = new Counted(context);
        mRows = new Counted(context);
        mContainer.addView(mHost);
        mContainer.addView(mRows);
        mContainer.layout(0, 0, WIDTH, 600);
        mHost.layout(0, 0, WIDTH, 600);
        mRows.layout(0, 0, WIDTH, 230);
        mViews.put(R.id.accessory_stack_container, mContainer);
        mViews.put(R.id.accessory_surface_host, mHost);
        mViews.put(R.id.accessory_row_stack, mRows);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return mKeyboardShown; }
            @Override public int keyboardDownLaidOutPx() { return mKeyboardDownPx; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
        Counters.clear();
        Counters.enable(true, null);
    }

    @After public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    @Test public void aNegativeImeLiftDrawsTheContainerLower() {
        FramePipeline frames = attached();
        mRenderer.setTravel(10f, 0f, -300f);
        frames.flush("test");
        assertEquals(310f, mContainer.getTranslationY(), 0f);
    }

    /**
     * Home (#309): the system keyboard does not raise Home's stack, so a lifting ride (300) is
     * stood back down and the container stays at its travel. The Terminal's case is the test
     * above (homeCover off, or the Terminal in front): the ride keeps lifting it.
     */
    @Test public void aLiftingImeRideDoesNotRaiseHomesCoveredStack() {
        FramePipeline frames = attached();
        mFrontHome = true;
        mRenderer.setHomeCover(true, 0);
        mRenderer.setTravel(10f, 0f, 300f);
        frames.flush("test");
        assertEquals(10f, mContainer.getTranslationY(), 0f);
        mFrontHome = false;
        mRenderer.setHomeCover(true, 0);
        mRenderer.setTravel(10f, 0f, 301f);
        frames.flush("test");
        assertEquals("the Terminal's stack still rides the keyboard", -291f,
            mContainer.getTranslationY(), 0f);
    }

    /** The renderer on a pipeline with no window: its step runs when the table is flushed. */
    private FramePipeline attached() {
        FramePipeline frames = new FramePipeline();
        mRenderer.attach(frames);
        return frames;
    }

    private int writes() {
        return mContainer.writes + mHost.writes + mRows.writes;
    }

    @Test public void aFrameEqualToTheLastWritesNothing() {
        mRenderer.setTravel(120f, 0f, 0f);
        mRenderer.setSink(40f, 40f, 40f, 40f);
        assertEquals(120f, mContainer.getTranslationY(), 0f);
        assertNotNull(mContainer.getClipBounds());
        assertEquals(40f, mRows.getTranslationY(), 0f);
        int before = writes();
        mRenderer.apply();
        mRenderer.applyAtRest();
        mRenderer.setTravel(120f, 0f, 0f);
        mRenderer.setSink(40f, 40f, 40f, 40f);
        assertEquals(before, writes());
        // Back at rest: everything put back, once.
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        assertEquals(0f, mContainer.getTranslationY(), 0f);
        assertNull(mContainer.getClipBounds());
        assertNull(mRows.getClipBounds());
        before = writes();
        mRenderer.apply();
        assertEquals(before, writes());
    }

    @Test public void aForeignWriteIsHeard() {
        FramePipeline frames = attached();
        mRenderer.setTravel(120f, 0f, 0f);
        frames.flush("test");
        mRenderer.setSink(40f, 40f, 40f, 40f);
        frames.flush("test");
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        mContainer.setTranslationY(7f);
        mRenderer.apply();
        assertEquals(1, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertTrue(Counters.lines().toString(),
            Counters.lines().toString().contains("accessory_stack_container.translationY"));
    }

    @Test public void onAPipelineTheStepAloneWrites() {
        FramePipeline frames = attached();
        mRenderer.setTravel(120f, 0f, 0f);
        mRenderer.setSink(40f, 40f, 40f, 40f);
        assertEquals(0f, mContainer.getTranslationY(), 0f);
        assertEquals(0f, mRows.getTranslationY(), 0f);
        frames.flush("test");
        assertEquals(120f, mContainer.getTranslationY(), 0f);
        assertEquals(40f, mRows.getTranslationY(), 0f);
        assertEquals(40f, mHost.getTranslationY(), 0f);
    }

    @Test public void aLandingThatStandsTheLastFrameWroteNothing() {
        FramePipeline frames = attached();
        // The crossing's last frame already stands the rest; the landing puts back nothing.
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void aCrossingDrivenToFractionOneLeavesTheLandingNothingToWrite() {
        FramePipeline frames = attached();
        // Home to a window with its keyboard up: the stack laid out for the keyboard, drawn
        // lower by what the seam has not yet brought up (the keyboard follows the seam).
        com.termux.app.place.PlaceChromeTravel.States states =
            new com.termux.app.place.PlaceChromeTravel.States() {
                @androidx.annotation.NonNull @Override
                public com.termux.app.place.PlaceChromeTravel.Rest restOf(
                    @androidx.annotation.NonNull com.termux.app.page.PageKind place) {
                    return new com.termux.app.place.PlaceChromeTravel.Rest(
                        place == com.termux.app.page.PageKind.TERMINAL, false);
                }
            };
        java.util.List<com.termux.app.page.PageKind> row = java.util.Arrays.asList(
            com.termux.app.page.PageKind.HOME, com.termux.app.page.PageKind.TERMINAL);
        com.termux.app.place.StackScene.Motion motion = new com.termux.app.place.StackScene.Motion();
        float[] fractions = {0.4f, 0.8f, 1f};
        for (float fraction : fractions) {
            // Fraction 1 is the wall standing on the arriving page, an offset of a whole width.
            com.termux.app.place.PlaceChromeTravel.Frame frame =
                com.termux.app.place.PlaceChromeTravel.at(row,
                    com.termux.app.page.PageKind.HOME, -fraction * WIDTH, WIDTH, states);
            com.termux.app.place.StackScene.travel(false, frame.keyboardReveal,
                frame.dockReveal, 300, 200, 120, motion);
            mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
            mRenderer.setSink(0f, 0f, 0f, 0f);
            frames.flush("test");
        }
        assertEquals(0f, mContainer.getTranslationY(), 0f);
        int before = writes();
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertEquals(before, writes());
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void aCrossingToAKeyboardDownPageLandsOnTheRestItAlreadyStood() {
        FramePipeline frames = attached();
        // Terminal with its keyboard up to Home with it down, the keyboard laid out the whole way:
        // the seam's last frame draws the stack the keyboard's height lower (reveal 0) and the
        // landing, with the keyboard now down, stands the same number as its rest.
        com.termux.app.place.StackScene.Motion motion = new com.termux.app.place.StackScene.Motion();
        com.termux.app.place.StackScene.travel(false, 0f, 1f, 300, 200, 120, motion);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertEquals(300f, mContainer.getTranslationY(), 0f);
        int before = writes();
        mKeyboardDownPx = 300;
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertEquals("the landing writes nothing for the keyboard", before, writes());
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertEquals(300f, mContainer.getTranslationY(), 0f);
    }

    @Test public void aCrossingCountsTheLaidOutKeyboardOnceAndItsLandingWritesNothing() {
        FramePipeline frames = attached();
        // Home with the keyboard down but laid out to a window with it up: the crossing's travel
        // already carries the keyboard's laid-out height (reveal 0 at its start), so the rest's
        // share is not added on top while the crossing runs.
        mKeyboardDownPx = 300;
        mRenderer.setEnds(true, false, 0f);
        com.termux.app.place.StackScene.Motion motion = new com.termux.app.place.StackScene.Motion();
        com.termux.app.place.StackScene.travel(false, 0f, 1f, 300, 200, 120, motion);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertEquals("counted once", 300f, mContainer.getTranslationY(), 0f);
        com.termux.app.place.StackScene.travel(false, 0.5f, 1f, 300, 200, 120, motion);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        frames.flush("test");
        assertEquals("half way up", 150f, mContainer.getTranslationY(), 0f);
        // Landed with the keyboard up: nothing laid out down, the rest stands at 0.
        com.termux.app.place.StackScene.travel(false, 1f, 1f, 300, 200, 120, motion);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        frames.flush("test");
        int before = writes();
        mKeyboardDownPx = 0;
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        frames.flush("test");
        assertEquals(before, writes());
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void aKeyboardLaidOutButDownStandsTheStackItsHeightLowerAtRest() {
        mKeyboardDownPx = 300;
        mRenderer.applyAtRest();
        assertEquals(300f, mContainer.getTranslationY(), 0f);
        assertNotNull("what slid past the container's bottom is cut", mContainer.getClipBounds());
        // Up again: put back, once.
        mKeyboardDownPx = 0;
        mRenderer.applyAtRest();
        assertEquals(0f, mContainer.getTranslationY(), 0f);
        assertNull(mContainer.getClipBounds());
    }

    @Test public void aKeyboardLaidOutButDownTakesNoTouchAndUpTakesAgain() {
        com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout keyboard =
            new com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout(
                RuntimeEnvironment.getApplication());
        mContainer.addView(keyboard);
        keyboard.layout(0, 300, WIDTH, 600);
        mViews.put(R.id.inapp_keyboard_container, keyboard);
        mKeyboardDownPx = 300;
        mRenderer.applyAtRest();
        assertTrue(keyboard.isTouchRefused());
        mKeyboardDownPx = 0;
        mRenderer.applyAtRest();
        assertFalse(keyboard.isTouchRefused());
    }

    @Test public void aLandingThatMovesTheStackIsCounted() {
        FramePipeline frames = attached();
        mRenderer.setTravel(120f, 0f, 0f);
        mRenderer.setSink(40f, 40f, 40f, 40f);
        frames.flush("test");
        // The last crossing frame left the stack sunk: the landing snaps it back.
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        frames.flush("test");
        assertTrue(Counters.countOf(FrameGuards.LANDING_WROTE) > 0);
        // Only that frame: the next change is not a landing's.
        long landed = Counters.countOf(FrameGuards.LANDING_WROTE);
        mRenderer.setTravel(60f, 0f, 0f);
        frames.flush("test");
        assertEquals(landed, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void thePlankComposedWithTheCrossingWritesOneValue() {
        mRenderer.setTravel(120f, 0f, 0f);
        DockPlankController.Slab slab = new DockPlankController.Slab();
        slab.translationY = 3f;
        slab.rotationX = 1f;
        slab.pivotX = 500f;
        slab.pivotY = 300f;
        int before = mContainer.writes;
        mRenderer.onSlab(slab);
        // Keyboard down: the plank tilts the container, on top of the crossing's travel.
        assertEquals(123f, mContainer.getTranslationY(), 0f);
        assertEquals(1f, mContainer.getRotationX(), 0f);
        assertEquals(2, mContainer.writes - before); // translationY once, rotationX once
        assertEquals(0f, mHost.getTranslationY(), 0f);

        // Keyboard up: the glass host carries it on top of its sink, the container lets go.
        mKeyboardShown = true;
        mRenderer.setSink(40f, 40f, 40f, 40f);
        assertEquals(43f, mHost.getTranslationY(), 0f);
        assertEquals(1f, mHost.getRotationX(), 0f);
        assertEquals(120f, mContainer.getTranslationY(), 0f);
        assertEquals(0f, mContainer.getRotationX(), 0f);

        // A resting plank changes nothing the crossing wrote.
        mRenderer.onSlab(new DockPlankController.Slab());
        assertEquals(40f, mHost.getTranslationY(), 0f);
        assertEquals(0f, mHost.getRotationX(), 0f);
        int resting = writes();
        mRenderer.onSlab(new DockPlankController.Slab());
        assertEquals(resting, writes());
    }

    /**
     * The flat body surface is laid out once, at the smallest room any page has (here Home's, 100
     * above the window's bottom), and the one writer cuts it to the front side's room: a rest on
     * either side writes that cut, a crossing blends the two, and its landing writes nothing.
     */
    private Counted body() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout parent = new FrameLayout(context);
        parent.layout(0, 0, WIDTH, 2000);
        Counted body = new Counted(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.bottomMargin = 100;
        parent.addView(body, params);
        body.layout(0, 0, WIDTH, 1900);
        mViews.put(R.id.terminal_background, body);
        return body;
    }

    @Test public void theBodySurfaceIsCutToTheFrontSidesRoom() {
        Counted body = body();
        mRenderer.setRooms(100, 400);

        mFrontHome = true;
        mRenderer.applyAtRest();
        assertNull("Home's room is the layout's: nothing cut", body.getClipBounds());

        mFrontHome = false;
        mRenderer.applyAtRest();
        assertEquals("the Terminal's room is 300 higher", 1600, body.getClipBounds().bottom);
    }

    @Test public void aCrossingBlendsTheRoomsAndItsLandingWritesNothing() {
        Counted body = body();
        mRenderer.setRooms(100, 400);
        mFrontHome = false;
        mRenderer.applyAtRest();

        mRenderer.setEnds(false, true, 0.5f);
        assertEquals("halfway, 150 up", 1750, body.getClipBounds().bottom);
        mRenderer.setEnds(false, true, 1f);
        assertNull("arrived on Home's rest", body.getClipBounds());

        int before = body.writes;
        mFrontHome = true;
        mRenderer.onLanding();
        assertEquals("the landing finds the arriving rest in place", before, body.writes);
        assertNull(body.getClipBounds());
    }

    @Test public void aChangedLayoutOfTheBodyIsCutAgain() {
        Counted body = body();
        mRenderer.setRooms(100, 400);
        mFrontHome = false;
        mRenderer.applyAtRest();
        assertEquals(1600, body.getClipBounds().bottom);

        // The window grows: the body's parent and the body are laid out taller, and its cut is
        // read off the new height without a pass asking.
        ((FrameLayout) body.getParent()).layout(0, 0, WIDTH, 2200);
        body.layout(0, 0, WIDTH, 2100);
        assertEquals(1800, body.getClipBounds().bottom);
    }
}
