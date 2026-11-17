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
        });
        Counters.clear();
        Counters.enable(true, null);
    }

    @After public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
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
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
        assertEquals(120f, mContainer.getTranslationY(), 0f);
        assertNotNull(mContainer.getClipBounds());
        assertEquals(40f, mRows.getTranslationY(), 0f);
        int before = writes();
        mRenderer.apply();
        mRenderer.applyAtRest();
        mRenderer.setTravel(120f, 0f, 0f);
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
        assertEquals(before, writes());
        // Back at rest: everything put back, once.
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
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
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
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
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
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
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
        frames.flush("test");
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
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
                    @androidx.annotation.NonNull com.termux.app.wall.PaneWallPage place) {
                    return new com.termux.app.place.PlaceChromeTravel.Rest(
                        place == com.termux.app.wall.PaneWallPage.TERMINAL, false);
                }
            };
        java.util.List<com.termux.app.wall.PaneWallPage> row = java.util.Arrays.asList(
            com.termux.app.wall.PaneWallPage.WIDGETS, com.termux.app.wall.PaneWallPage.TERMINAL);
        com.termux.app.place.StackScene.Motion motion = new com.termux.app.place.StackScene.Motion();
        float[] fractions = {0.4f, 0.8f, 1f};
        for (float fraction : fractions) {
            // Fraction 1 is the wall standing on the arriving page, an offset of a whole width.
            com.termux.app.place.PlaceChromeTravel.Frame frame =
                com.termux.app.place.PlaceChromeTravel.at(row,
                    com.termux.app.wall.PaneWallPage.WIDGETS, -fraction * WIDTH, WIDTH, states);
            com.termux.app.place.StackScene.travel(false, false, frame.keyboardReveal,
                frame.dockReveal, 300, 200, 120, motion);
            mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
            mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
            frames.flush("test");
        }
        assertEquals(0f, mContainer.getTranslationY(), 0f);
        int before = writes();
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
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
        com.termux.app.place.StackScene.travel(false, false, 0f, 1f, 300, 200, 120, motion);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
        frames.flush("test");
        assertEquals(300f, mContainer.getTranslationY(), 0f);
        int before = writes();
        mKeyboardDownPx = 300;
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
        frames.flush("test");
        assertEquals("the landing writes nothing for the keyboard", before, writes());
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertEquals(300f, mContainer.getTranslationY(), 0f);
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
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
        frames.flush("test");
        // The last crossing frame left the stack sunk: the landing snaps it back.
        mRenderer.onLanding();
        mRenderer.setTravel(0f, 0f, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f, 0f, false);
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
        mRenderer.setSink(40f, 40f, 40f, 40f, 0f, false);
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
}
