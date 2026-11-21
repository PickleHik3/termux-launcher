package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

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

import java.util.concurrent.TimeUnit;

/**
 * The Home pill's flights end where they should, however they end: a flight to the dock hands the
 * docked bar back whole and leaves the pill hidden, a flight to the pill leaves it at its spot.
 * No timing is asserted.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HomePillFlightPlayerTest {

    private static final float REST_X = 100f;
    private static final float REST_Y = 200f;

    private ActivityController<Activity> controller;
    private FrameLayout root;
    private HomePillLayer layer;
    private HomePillView pill;
    private HomePillFlightPlayer flights;
    private View bar;
    private int ended;

    @Before
    public void setUp() throws Exception {
        controller = Robolectric.buildActivity(Activity.class);
        controller.get().setTheme(com.google.android.material.R.style.Theme_Material3_DayNight);
        controller.setup();
        root = new FrameLayout(controller.get());
        layer = new HomePillLayer(controller.get());
        root.addView(layer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        bar = bar();
        root.addView(bar);
        controller.get().setContentView(root);
        pill = layer.pill();
        pill.setKeys(new ExtraKeysInfo("[['A','B','C']]", "default",
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 1f);
        flights = layer.flights();
        flights.setListener(() -> ended++);
        idle();
        layer.placePill(REST_X, REST_Y);
        layer.setPillShown(true);
        idle();
        assertTrue("the pill has a size", pill.getWidth() > 0);
    }

    private View bar() {
        View view = new View(controller.get());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(600, 100);
        params.gravity = android.view.Gravity.BOTTOM;
        view.setLayoutParams(params);
        view.setVisibility(View.GONE);
        return view;
    }

    private void idle() {
        ShadowLooper.idleMainLooper();
    }

    private void assertBarWhole(View view) {
        assertEquals(1f, view.getAlpha(), 0f);
        assertEquals(0f, view.getTranslationX(), 0f);
        assertEquals(0f, view.getTranslationY(), 0f);
        assertEquals(1f, view.getScaleX(), 0f);
        assertEquals(1f, view.getScaleY(), 0f);
    }

    private void assertPillAtRest() {
        assertEquals(REST_X, pill.getTranslationX(), 0f);
        assertEquals(REST_Y, pill.getTranslationY(), 0f);
        assertEquals(1f, pill.getScaleX(), 0f);
        assertEquals(1f, pill.getScaleY(), 0f);
        assertEquals(1f, pill.pageView().getAlpha(), 0f);
    }

    /** Starts a flight to the dock and gets it into the air. */
    private void flyToDockInTheAir(View dockedBar) {
        flights.flyToDock(dockedBar);
        assertEquals("held at alpha 0 until its layout", 0f, dockedBar.getAlpha(), 0f);
        // The bar comes up and is laid out; the frame about to be drawn tells the player.
        // Dispatched by hand, because the test looper runs frames to the end of a flight.
        dockedBar.setVisibility(View.VISIBLE);
        dockedBar.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        dockedBar.layout(0, 1000, 600, 1100);
        dockedBar.getViewTreeObserver().dispatchOnPreDraw();
        assertTrue("flying", flights.isFlying());
        assertTrue(layer.ownsPose());
    }

    @Test
    public void finishingAFlightToTheDockHandsTheBarBackWholeAndHidesThePill() {
        flyToDockInTheAir(bar);
        flights.finish();
        assertBarWhole(bar);
        assertFalse(flights.isFlying());
        assertEquals(View.INVISIBLE, pill.getVisibility());
        assertPillAtRest();
        assertTrue(ended > 0);
    }

    @Test
    public void aFlightToTheDockWaitingForTheBarsLayoutEndsWithTheBarWhole() {
        flights.flyToDock(bar);
        assertEquals(0f, bar.getAlpha(), 0f);
        flights.finish();
        assertBarWhole(bar);
        assertEquals(View.INVISIBLE, pill.getVisibility());
        assertPillAtRest();
    }

    @Test
    public void aBarThatNeverComesUpIsGivenBackWhenTheBackstopFires() {
        flights.flyToDock(bar);
        ShadowLooper.idleMainLooper(HomePillFlightPlayer.LAYOUT_BACKSTOP_MS + 50,
            TimeUnit.MILLISECONDS);
        assertBarWhole(bar);
        assertFalse(flights.isFlying());
        assertEquals(View.INVISIBLE, pill.getVisibility());
        assertTrue(ended > 0);
    }

    @Test
    public void aSecondSettleEndsTheFirstFlightAtItsFinalState() {
        flyToDockInTheAir(bar);
        View second = bar();
        root.addView(second);
        // The next settle's flight begins by ending the one in the air: the pill is gone, so
        // there is nothing for the second to carry and it leaves its bar alone.
        flights.flyToDock(second);
        assertBarWhole(bar);
        assertBarWhole(second);
        assertFalse(flights.isFlying());
        assertEquals(View.INVISIBLE, pill.getVisibility());
        assertPillAtRest();
    }

    @Test
    public void detachingTheLayerEndsAFlightToTheDock() {
        flyToDockInTheAir(bar);
        root.removeView(layer);
        assertBarWhole(bar);
        assertFalse(flights.isFlying());
    }

    @Test
    public void hidingThePillEndsAFlightToThePillAtItsSpot() {
        layer.setPillShown(false);
        bar.setVisibility(View.VISIBLE);
        idle();
        assertTrue(flights.captureDockedBar(bar));
        flights.armToPill();
        layer.setPillShown(true);
        assertTrue("flying", flights.isFlying());
        assertEquals("the keys come in from nothing", 0f, pill.pageView().getAlpha(), 0f);
        layer.setPillShown(false);
        assertFalse(flights.isFlying());
        assertPillAtRest();
    }

    @Test
    public void finishingAFlightToThePillLeavesItAtItsSpotWithItsKeys() {
        layer.setPillShown(false);
        bar.setVisibility(View.VISIBLE);
        idle();
        assertTrue(flights.captureDockedBar(bar));
        flights.armToPill();
        layer.setPillShown(true);
        assertTrue(flights.isFlying());
        flights.finish();
        assertFalse(flights.isFlying());
        assertEquals(View.VISIBLE, pill.getVisibility());
        assertPillAtRest();
    }

    @Test
    public void aPillThatShowsWithNothingArmedDoesNotFly() {
        layer.setPillShown(false);
        layer.setPillShown(true);
        assertFalse(flights.isFlying());
        assertPillAtRest();
    }

    @Test
    public void aFlyingPillTakesNoTouch() {
        flyToDockInTheAir(bar);
        assertFalse(layer.claimsDown(REST_X + 10f, REST_Y + 10f));
        flights.finish();
    }
}
