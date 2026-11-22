package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * The pill riding the in-app keyboard: where each progress puts it, that it docks and lets go at
 * the ends, and that a change with no progress snaps. Geometry comes from a stub source.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HomePillKeyboardDockTest {

    private static final float REST_X = 100f;
    private static final float REST_Y = 200f;
    private static final float TRAVEL = 400f;
    private static final float[] DOCKED = {50f, 1500f, 950f, 1620f};

    private ActivityController<Activity> controller;
    private HomePillLayer layer;
    private HomePillView pill;
    private HomePillKeyboardDock dock;
    private boolean dockedKnown = true;
    private int pillW;
    private int pillH;

    private final HomePillKeyboardDock.Source source = new HomePillKeyboardDock.Source() {
        @Override
        public boolean readDockedRect(@NonNull float[] out) {
            if (!dockedKnown) return false;
            System.arraycopy(DOCKED, 0, out, 0, 4);
            return true;
        }

        @Override
        public float stackTravelPx(float progress) {
            return TRAVEL * (1f - progress);
        }
    };

    @Before
    public void setUp() throws Exception {
        controller = Robolectric.buildActivity(Activity.class);
        controller.get().setTheme(com.google.android.material.R.style.Theme_Material3_DayNight);
        controller.setup();
        FrameLayout root = new FrameLayout(controller.get());
        layer = new HomePillLayer(controller.get());
        root.addView(layer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        controller.get().setContentView(root);
        pill = layer.pill();
        pill.setKeys(new ExtraKeysInfo("[['A','B','C']]", "default",
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 1f);
        dock = layer.keyboardDock();
        dock.setSource(source);
        idle();
        layer.placePill(REST_X, REST_Y);
        layer.setPillShown(true);
        idle();
        pillW = pill.getWidth();
        pillH = pill.getHeight();
        assertTrue(pillW > 0 && pillH > 0);
    }

    private void idle() {
        ShadowLooper.idleMainLooper();
    }

    /** The rect the pill is drawn on now, from its translation, scale and laid-out size. */
    private float[] drawnRect() {
        float w = pill.getWidth() * pill.getScaleX();
        float h = pill.getHeight() * pill.getScaleY();
        float cx = pill.getLeft() + pill.getTranslationX() + pill.getWidth() * 0.5f;
        float cy = pill.getTop() + pill.getTranslationY() + pill.getHeight() * 0.5f;
        return new float[] {cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f};
    }

    private void assertRect(float[] expected, float[] actual) {
        for (int i = 0; i < 4; i++) assertEquals("edge " + i, expected[i], actual[i], 0.01f);
    }

    private float[] pillRect() {
        return new float[] {REST_X, REST_Y, REST_X + pillW, REST_Y + pillH};
    }

    private float[] expectedPose(float p) {
        float[] out = new float[4];
        float[] target = {DOCKED[0], DOCKED[1] + TRAVEL * (1f - p), DOCKED[2],
            DOCKED[3] + TRAVEL * (1f - p)};
        HomePillFlight.keyboardPose(pillRect(), target, p, out);
        return out;
    }

    @Test
    public void aProgressPutsThePillBetweenItsSpotAndTheDockedRectRidingTheStack() {
        dock.onProgress(0.5f);
        assertTrue(dock.ownsPose());
        assertFalse("not docked before the end", dock.isDocked());
        assertTrue(pill.isFloating());
        assertRect(expectedPose(0.5f), drawnRect());
        dock.onProgress(0.25f);
        assertRect(expectedPose(0.25f), drawnRect());
    }

    @Test
    public void atTheEndItIsLaidOutAtTheDockedRectAndHoldsActOnAKey() {
        dock.onProgress(0.5f);
        dock.onProgress(1f);
        assertTrue(dock.isDocked());
        assertFalse(pill.isFloating());
        idle();
        assertEquals(Math.round(DOCKED[2] - DOCKED[0]), pill.getWidth());
        assertEquals(Math.round(DOCKED[3] - DOCKED[1]), pill.getHeight());
        assertEquals(1f, pill.getScaleX(), 0f);
        assertEquals(1f, pill.getScaleY(), 0f);
        assertEquals(DOCKED[0], pill.getTranslationX(), 0f);
        assertEquals(DOCKED[1], pill.getTranslationY(), 0f);
    }

    @Test
    public void aReversingSwipeLetsGoOfTheDockAtTheFirstProgressBelowOne() {
        dock.onProgress(1f);
        idle();
        assertTrue(dock.isDocked());
        dock.onProgress(0.9f);
        assertFalse(dock.isDocked());
        assertTrue("floating again at once", pill.isFloating());
        // Posed by its size at this moment (still the docked one until the layout lands).
        idle();
        assertEquals(pillW, pill.getWidth());
        assertRect(expectedPose(0.9f), drawnRect());
    }

    @Test
    public void atZeroItIsBackOnItsSpotAtItsOwnSize() {
        dock.onProgress(1f);
        idle();
        dock.onProgress(0.4f);
        dock.onProgress(0f);
        idle();
        assertFalse(dock.ownsPose());
        assertTrue(pill.isFloating());
        assertEquals(pillW, pill.getWidth());
        assertEquals(1f, pill.getScaleX(), 0f);
        assertEquals(1f, pill.getScaleY(), 0f);
        assertEquals(REST_X, pill.getTranslationX(), 0f);
        assertEquals(REST_Y, pill.getTranslationY(), 0f);
    }

    @Test
    public void aKeyboardChangeWithNoProgressSnapsAtTheNextFrame() {
        dock.onDockedChanged(true, false);
        assertFalse(dock.isDocked());
        pill.getViewTreeObserver().dispatchOnPreDraw();
        assertTrue(dock.isDocked());
        assertFalse(pill.isFloating());

        dock.onDockedChanged(false, false);
        pill.getViewTreeObserver().dispatchOnPreDraw();
        idle();
        assertFalse(dock.isDocked());
        assertTrue(pill.isFloating());
        assertEquals(REST_X, pill.getTranslationX(), 0f);
    }

    @Test
    public void progressThatArrivesKeepsTheFrameSnapAway() {
        dock.onDockedChanged(true, false);
        dock.onProgress(0.3f);
        pill.getViewTreeObserver().dispatchOnPreDraw();
        assertFalse("the swipe is still travelling", dock.isDocked());
        assertRect(expectedPose(0.3f), drawnRect());
    }

    @Test
    public void aSwipeKeepsTheFrameSnapOffAndItsLandingDocks() {
        dock.onDockedChanged(true, true);
        pill.getViewTreeObserver().dispatchOnPreDraw();
        assertFalse(dock.isDocked());
        dock.onKeyboardSettled();
        assertTrue(dock.isDocked());
    }

    @Test
    public void withNoDockedRectItStaysOnItsSpot() {
        dockedKnown = false;
        dock.onProgress(0.5f);
        dock.onProgress(1f);
        assertFalse(dock.isDocked());
        assertTrue(pill.isFloating());
        assertEquals(REST_X, pill.getTranslationX(), 0f);
    }

    @Test
    public void hidingThePillPutsItBackFloating() {
        dock.onProgress(1f);
        idle();
        layer.setPillShown(false);
        idle();
        assertFalse(dock.isDocked());
        assertTrue(pill.isFloating());
        assertEquals(pillW, pill.getWidth());
        assertEquals(REST_X, pill.getTranslationX(), 0f);
        assertEquals(REST_Y, pill.getTranslationY(), 0f);
        assertEquals(1f, pill.getScaleX(), 0f);
    }

    @Test
    public void aHiddenPillIsLeftAloneByTheReveal() {
        layer.setPillShown(false);
        dock.onProgress(0.5f);
        assertFalse(dock.ownsPose());
        assertEquals(View.INVISIBLE, pill.getVisibility());
    }
}
