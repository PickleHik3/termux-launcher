package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.Theme;

/** Attaching, detaching, the tap-or-swipe grace period, and honouring the setting. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class KeyPopupControllerTest {

    private FrameLayout host;
    private Keyboard2View keyboardView;
    private KeyPopupController controller;

    @Before
    public void setUp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        host = new FrameLayout(context);
        juloo.keyboard2.Config.Builder builder =
            new juloo.keyboard2.Config.Builder(context.getResources(), new NoOpHandler());
        builder.rowHeightPx = 100f;
        builder.horizontalMarginPx = 0f;
        builder.bottomMarginPx = 0f;
        builder.marginTopPx = 0f;
        builder.hapticEnabled = false;
        keyboardView = new Keyboard2View(context, builder.build(), palette());
        keyboardView.setKeyboard(KeyboardData.load_string_exn(
            "<keyboard bottom_row='false'><row><key c='a' ne='1'/></row></keyboard>"));
        keyboardView.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
        keyboardView.layout(0, 0, 400, keyboardView.getMeasuredHeight());
        host.addView(keyboardView);
        host.layout(0, 0, 400, 400);
        controller = new KeyPopupController(host, keyboardView);
    }

    @Test
    public void privateUseGlyphsTakeTheLabelFontAndPlainTextStaysMonospace() {
        assertTrue("Nerd Font tool glyph (supplementary PUA)",
            KeyPopupOverlayView.usesLabelFont("\uDB80\uDF33"));
        assertTrue("BMP private-use glyph", KeyPopupOverlayView.usesLabelFont("\uE011"));
        assertFalse(KeyPopupOverlayView.usesLabelFont("-"));
        assertFalse(KeyPopupOverlayView.usesLabelFont("Esc"));
        assertFalse(KeyPopupOverlayView.usesLabelFont(null));
    }

    @Test
    public void theOverlayIsNotInTheViewTreeUntilTheSettingTurnsItOn() {
        assertFalse(controller.isEnabled());
        assertNull("nothing is added while the setting is off", controller.overlay().getParent());

        press();
        assertFalse("and nothing is reported either", controller.overlay().hasActivePopups());
    }

    @Test
    public void turningItOnAttachesTheOverlayAboveEverythingElse() {
        controller.setEnabled(true);

        assertSame(host, controller.overlay().getParent());
        assertSame("the popup is drawn over the keyboard, not inside it",
            controller.overlay(), host.getChildAt(host.getChildCount() - 1));
    }

    @Test
    public void aPressPutsAGlyphUpOnceTheGraceperiodPassesAndTheReleaseTakesItDown() {
        controller.setEnabled(true);

        press();
        assertFalse("nothing is drawn on the way down",
            controller.overlay().hasActivePopups());
        assertTrue("the finger is down, the glyph is only waiting",
            controller.overlay().hasPendingPopups());

        settle();
        assertTrue(controller.overlay().hasActivePopups());
        assertFalse(controller.overlay().hasPendingPopups());

        release();
        assertFalse(controller.overlay().hasActivePopups());
    }

    @Test
    public void aFingerThatLiftsInsideTheGracePeriodNeverShowsAnything() {
        controller.setEnabled(true);

        press();
        release();

        assertFalse(controller.overlay().hasActivePopups());
        assertFalse(controller.overlay().hasPendingPopups());

        // And the timer does not fire behind the finger's back afterwards.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
        assertFalse(controller.overlay().hasActivePopups());
    }

    @Test
    public void aSwipeInsideTheGracePeriodShowsItsTargetStraightAway() {
        controller.setEnabled(true);

        press();
        controller.onKeyPopupTarget(0, "1", false, 2);

        assertTrue(controller.overlay().hasActivePopups());
        assertFalse("the centre value never got its turn",
            controller.overlay().hasPendingPopups());

        // The timer that would have shown the centre value is gone, not merely overtaken.
        settle();
        assertTrue(controller.overlay().hasActivePopups());
    }

    @Test
    public void aSwipeAfterTheGlyphIsUpReplacesItInPlace() {
        controller.setEnabled(true);
        press();
        settle();

        controller.onKeyPopupTarget(0, "1", false, 2);

        assertTrue("still exactly one glyph for the finger",
            controller.overlay().hasActivePopups());
        release();
        assertFalse(controller.overlay().hasActivePopups());
    }

    @Test
    public void turningItOffClearsWhatIsUpAndLeavesTheHostAsItWasFound() {
        controller.setEnabled(true);
        press();
        settle();

        controller.setEnabled(false);

        assertFalse(controller.overlay().hasActivePopups());
        assertNull(controller.overlay().getParent());
        assertFalse(controller.isEnabled());
    }

    @Test
    public void tearingDownWhileAFingerIsStillInsideTheGracePeriodDropsTheTimer() {
        controller.setEnabled(true);
        press();

        controller.destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertFalse(controller.overlay().hasActivePopups());
        assertFalse(controller.overlay().hasPendingPopups());
    }

    @Test
    public void destroyingItDetachesTheOverlayWhateverStateItWasIn() {
        controller.setEnabled(true);
        press();

        controller.destroy();

        assertNull(controller.overlay().getParent());
        assertFalse(controller.overlay().hasActivePopups());
    }

    @Test
    public void theOverlayNeverTakesATouch() {
        controller.setEnabled(true);
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0);
        assertFalse(controller.overlay().onTouchEvent(event));
        event.recycle();
    }

    @Test
    public void theWindowsContentViewIsWhereAPopupFloats() {
        assertNotNull(KeyPopupController.findPopupHost(keyboardView));
    }

    private void press() {
        touch(MotionEvent.ACTION_DOWN);
    }

    /** Wait out the tap-or-swipe grace period, so the glyph is actually on screen. */
    private void settle() {
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(KeyPopupOverlayView.SHOW_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void release() {
        touch(MotionEvent.ACTION_UP);
    }

    private void touch(int action) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, 50f, 50f, 0);
        keyboardView.onTouch(keyboardView, event);
        event.recycle();
    }

    private static Theme.Palette palette() {
        return new Theme.Palette(
            Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
            Color.GRAY, Color.WHITE, Color.LTGRAY, Color.WHITE, Color.WHITE,
            Color.WHITE, Color.GRAY, false, 0f, 0f, 1f);
    }

    private static final class NoOpHandler implements juloo.keyboard2.Config.IKeyEventHandler {
        @Override public void key_down(KeyValue value, boolean isSwipe) {}
        @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
        @Override public void mods_changed(Pointers.Modifiers modifiers) {}
        @Override public void suggestion_entered(String text) {}
    }
}
