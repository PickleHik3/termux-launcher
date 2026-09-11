package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayTextFocusPolicy.State;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The Display place's "keyboard follows text fields" state machine, window and all. */
public class DisplayTextFocusPolicyTest {

    /** Records what the policy asked for, in order: {@code show} and {@code hide}. */
    private static final class RecordingKeyboard implements DisplayTextFocusPolicy.Keyboard {
        final List<String> calls = new ArrayList<>();
        boolean up;
        @Override public void showKeyboardForTextFocus() { calls.add("show"); up = true; }
        @Override public void hideKeyboardForTextFocus() { calls.add("hide"); up = false; }
        @Override public boolean isKeyboardUp() { return up; }
    }

    /** The tap window, stepped by hand. */
    private static final class FakeScheduler implements DisplayTextFocusPolicy.Scheduler {
        @Nullable Runnable pending;
        long delayMs;
        int scheduled;
        int cancelled;

        @Override public void schedule(long delayMs, @NonNull Runnable action) {
            this.delayMs = delayMs;
            pending = action;
            scheduled++;
        }

        @Override public void cancel() {
            if (pending != null) cancelled++;
            pending = null;
        }

        /** Let the window expire. */
        void expire() {
            Runnable run = pending;
            pending = null;
            if (run != null) run.run();
        }
    }

    private RecordingKeyboard keyboard;
    private FakeScheduler scheduler;
    private List<String> trace;
    private DisplayTextFocusPolicy policy;

    @Before
    public void setUp() {
        keyboard = new RecordingKeyboard();
        scheduler = new FakeScheduler();
        trace = new ArrayList<>();
        policy = new DisplayTextFocusPolicy(keyboard, scheduler, trace::add);
        policy.setEnabled(true);
        policy.setTouchMode(DisplayTextFocusPolicy.TOUCH_MODE_TOUCHSCREEN);
        policy.onPlaceEntered(false);
    }

    private void tapOver(@Nullable String cursorName) {
        policy.onDisplayTap();
        if (cursorName != null) policy.onCursorName(cursorName);
        scheduler.expire();
    }

    @Test
    public void textCursorNames_areTheFourTheServerReports() {
        assertTrue(DisplayTextFocusPolicy.isTextCursor("xterm"));
        assertTrue(DisplayTextFocusPolicy.isTextCursor("text"));
        assertTrue(DisplayTextFocusPolicy.isTextCursor("ibeam"));
        assertTrue(DisplayTextFocusPolicy.isTextCursor("vertical-text"));
        assertTrue(DisplayTextFocusPolicy.isTextCursor("XTerm"));
        assertFalse(DisplayTextFocusPolicy.isTextCursor("left_ptr"));
        assertFalse(DisplayTextFocusPolicy.isTextCursor("hand2"));
        assertFalse(DisplayTextFocusPolicy.isTextCursor(""));
        assertFalse(DisplayTextFocusPolicy.isTextCursor(null));
    }

    @Test
    public void tapOnAText_opens_andTapElsewhere_closes() {
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);

        tapOver("left_ptr");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void tapWithNoNameAtAll_closesWhatAutoOpenOpened() {
        tapOver("xterm");
        // A cursor with no name is not a text cursor, so the next tap puts the keyboard down.
        tapOver("");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void tapThatChangesNothing_isAnsweredByTheCursorAlreadyShowing() {
        // X only reports a change, so a second tap inside the same field sends no new name.
        tapOver("xterm");
        keyboard.calls.clear();
        tapOver(null);
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of(), keyboard.calls);

        // And the same over a desktop the pointer has been resting on.
        policy.onCursorName("left_ptr");
        tapOver(null);
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("hide"), keyboard.calls);
    }

    @Test
    public void onlyTheLastNameInsideTheWindowCounts() {
        policy.onDisplayTap();
        policy.onCursorName("watch");
        policy.onCursorName("left_ptr");
        policy.onCursorName("xterm");
        assertTrue(policy.isTapWindowOpen());
        assertEquals(DisplayTextFocusPolicy.TAP_WINDOW_MS, scheduler.delayMs);
        scheduler.expire();
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void nameArrivingAfterTheWindow_decidesNothing() {
        tapOver("left_ptr");
        policy.onCursorName("xterm");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void cursorNamesWithNoTap_neverToggleAnything() {
        policy.onCursorName("xterm");
        policy.onCursorName("left_ptr");
        policy.onCursorName("xterm");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of(), keyboard.calls);
        assertFalse(policy.isTapWindowOpen());
    }

    @Test
    public void aSecondTap_restartsTheWindow() {
        policy.onDisplayTap();
        policy.onCursorName("xterm");
        policy.onDisplayTap();
        assertEquals(1, scheduler.cancelled);
        // The second tap's window has heard no name, so the cursor still showing answers it.
        scheduler.expire();
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void autoHide_onlyClosesWhatAutoShowOpened() {
        // Nothing of ours is up: a tap on the desktop is not our business.
        tapOver("left_ptr");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void userToggle_pins_andFocusIsIgnoredUntilItIsHiddenAgain() {
        policy.onUserKeyboardIntent(true);
        assertEquals(State.PINNED, policy.state());

        tapOver("left_ptr");
        assertEquals(State.PINNED, policy.state());
        assertTrue(policy.onTextFocusSignal(false));
        assertEquals(State.PINNED, policy.state());
        assertEquals(List.of(), keyboard.calls);

        // Put down by hand, the keyboard is the policy's business again.
        policy.onUserKeyboardIntent(false);
        assertEquals(State.CLOSED, policy.state());
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void userToggle_fromAutoOpen_pinsWhatThePolicyOpened() {
        tapOver("xterm");
        policy.onUserKeyboardIntent(true);
        assertEquals(State.PINNED, policy.state());
        tapOver("left_ptr");
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void focusSignals_openAndCloseWithNoTap() {
        assertTrue(policy.onTextFocusSignal(true));
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);

        // Focus in twice is one keyboard.
        assertTrue(policy.onTextFocusSignal(true));
        assertEquals(List.of("show"), keyboard.calls);

        assertTrue(policy.onTextFocusSignal(false));
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);

        // And focus out with nothing of ours up closes nothing.
        assertTrue(policy.onTextFocusSignal(false));
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void focusSignal_isNotTakenWhileInert() {
        policy.onPlaceLeft();
        assertFalse(policy.onTextFocusSignal(true));
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void leavingThePlace_leavesPinned_andTouchesNoKeyboard() {
        policy.onUserKeyboardIntent(true);
        policy.onPlaceLeft();
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of(), keyboard.calls);
        assertFalse(policy.isActive());
    }

    @Test
    public void arrivingWithKeyboardOnEnter_isPinned() {
        policy.onPlaceEntered(true);
        assertEquals(State.PINNED, policy.state());
        tapOver("left_ptr");
        assertEquals(State.PINNED, policy.state());
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void arrivingWithoutIt_startsClosed_evenAfterAPin() {
        policy.onUserKeyboardIntent(true);
        policy.onPlaceLeft();
        policy.onPlaceEntered(false);
        assertEquals(State.CLOSED, policy.state());
        assertTrue(policy.isActive());
    }

    @Test
    public void trackpadAndDirectTouch_areInert() {
        policy.setTouchMode(1);
        assertFalse(policy.isActive());
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);
        assertEquals(State.CLOSED, policy.state());

        policy.setTouchMode(3);
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);

        // Back in Touchscreen the same tap is read again.
        policy.setTouchMode(DisplayTextFocusPolicy.TOUCH_MODE_TOUCHSCREEN);
        tapOver("xterm");
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void theSettingOff_isInert_andLeavesAnOpenKeyboardAlone() {
        tapOver("xterm");
        keyboard.calls.clear();
        policy.setEnabled(false);
        assertFalse(policy.isActive());
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of(), keyboard.calls);

        tapOver("left_ptr");
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void aModeChangeMidWindow_dropsTheWindow() {
        policy.onDisplayTap();
        policy.onCursorName("xterm");
        policy.setTouchMode(1);
        assertFalse(policy.isTapWindowOpen());
        scheduler.expire();
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void aKeyboardTheUserPutDownBehindOurBack_isRaisedAgainByTheNextTextTap() {
        tapOver("xterm");
        // The keyboard's own hide key: down, with nothing telling the policy so.
        keyboard.up = false;
        keyboard.calls.clear();
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void aKeyboardTheUserPutDownWhilePinned_unpinsAndTextTapsWorkAgain() {
        // Arriving with "keyboard on enter" pins the policy...
        policy.onPlaceLeft();
        policy.onPlaceEntered(true);
        keyboard.up = true;
        assertEquals(State.PINNED, policy.state());
        tapOver("xterm");
        assertEquals("pinned: the keyboard is the user's", List.of(), keyboard.calls);

        // ...and the dock's keyboard button putting it down is reported as the user's doing,
        // whatever reason the keyboard hid for, so the place goes back to following text fields.
        policy.onUserKeyboardIntent(false);
        assertEquals(State.CLOSED, policy.state());
        keyboard.up = false;
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
    }

    // ---- Mouse mode's touchpad holding the keyboard frame ------------------------------------

    @Test
    public void thePadsTaps_areReadInEveryTouchMode() {
        // Trackpad: a tap on the display decides nothing, as it always has...
        policy.setTouchMode(1);
        assertFalse(policy.isActive());
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);

        // ...but the pad's tap is a click where the pointer stands, so it is read.
        policy.setPadUp(true);
        assertTrue(policy.isActive());
        assertTrue(policy.isPadUp());
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);

        // And a tap on anything else puts the keyboard back behind the pad.
        tapOver("left_ptr");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void thePadGoingDown_inTrackpad_standsThePolicyDown() {
        policy.setTouchMode(1);
        policy.setPadUp(true);
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        keyboard.calls.clear();

        // Mouse mode off: the touch mode under the pad is the answer again, so this is inert.
        policy.setPadUp(false);
        assertFalse(policy.isActive());
        assertEquals(State.CLOSED, policy.state());
        assertEquals("the keyboard is left exactly as it is", List.of(), keyboard.calls);
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void thePadGoingDown_inTouchscreen_changesNothing() {
        policy.setPadUp(true);
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        keyboard.calls.clear();

        policy.setPadUp(false);
        assertTrue(policy.isActive());
        assertEquals("Touchscreen was the gate all along", State.AUTO_OPEN, policy.state());
        tapOver("left_ptr");
        assertEquals(List.of("hide"), keyboard.calls);
    }

    @Test
    public void thePad_doesNotSurviveTheSettingOff_orLeavingThePlace() {
        policy.setTouchMode(3);
        policy.setPadUp(true);
        assertTrue(policy.isActive());

        policy.setEnabled(false);
        assertFalse(policy.isActive());
        policy.setEnabled(true);
        assertTrue(policy.isActive());

        policy.onPlaceLeft();
        assertFalse(policy.isActive());
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);
    }

    @Test
    public void aFocusSignal_isTakenWhileThePadHoldsTheFrame() {
        policy.setTouchMode(1);
        policy.setPadUp(true);
        assertTrue(policy.onTextFocusSignal(true));
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);
        assertTrue(policy.onTextFocusSignal(false));
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void everyDecisionIsTraced() {
        trace.clear();
        tapOver("xterm");
        assertEquals(1, trace.size());
        assertTrue(trace.get(0).contains("xterm"));
        assertTrue(trace.get(0).contains("show"));
    }
}
