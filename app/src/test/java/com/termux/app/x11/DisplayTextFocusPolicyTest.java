package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayTextFocusPolicy.State;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
    public void aScrollOnThePage_closesWhatAutoOpenOpened() {
        tapOver("xterm");
        assertEquals(Arrays.asList("show"), keyboard.calls);
        // Entering a URL and scrolling the page that loads: the finger lands on the page.
        policy.onCursorName("left_ptr");
        policy.onDisplayDrag();
        assertEquals(Arrays.asList("show", "hide"), keyboard.calls);
        assertEquals(DisplayTextFocusPolicy.State.CLOSED, policy.state());
    }

    @Test
    public void aDragThatBeginsInTheField_isSelecting_andKeepsTheKeyboard() {
        tapOver("xterm");
        policy.onDisplayDrag();
        assertEquals(Arrays.asList("show"), keyboard.calls);
        assertEquals(DisplayTextFocusPolicy.State.AUTO_OPEN, policy.state());
    }

    @Test
    public void aGesture_neverOpensTheKeyboard() {
        policy.onCursorName("xterm");
        policy.onDisplayDrag();
        assertTrue(keyboard.calls.isEmpty());
    }

    @Test
    public void aScroll_leavesAPinnedKeyboardAlone() {
        keyboard.up = true;
        policy.onUserKeyboardIntent(true);
        policy.onCursorName("left_ptr");
        policy.onDisplayDrag();
        assertTrue(keyboard.calls.isEmpty());
        assertEquals(DisplayTextFocusPolicy.State.PINNED, policy.state());
    }

    @Test
    public void aGestureMidWindow_dropsTheWindow() {
        policy.onDisplayTap();
        policy.onDisplayDrag();
        assertNull(scheduler.pending);
        policy.onCursorName("xterm");
        scheduler.expire();
        assertTrue(keyboard.calls.isEmpty());
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

        // Put down by hand, the keyboard is the policy's business again. Focus was heard, so it
        // is focus that raises it, not a tap.
        policy.onUserKeyboardIntent(false);
        assertEquals(State.CLOSED, policy.state());
        policy.onTextFocusSignal(true);
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
        scheduler.expire();
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);

        // And focus out with nothing of ours up closes nothing.
        assertTrue(policy.onTextFocusSignal(false));
        scheduler.expire();
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    // ---- Focus decides: once a focus signal has been heard on the place ----------------------

    @Test
    public void focusOut_hidesOnlyAfterTheGrace() {
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        assertEquals("not yet", List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(DisplayTextFocusPolicy.HIDE_GRACE_MS, scheduler.delayMs);
        assertEquals(150L, DisplayTextFocusPolicy.HIDE_GRACE_MS);
        assertTrue(trace.contains("focus out -> hide in 150 ms"));

        scheduler.expire();
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void fieldToField_withinTheGrace_keepsTheKeyboardUp() {
        policy.onTextFocusSignal(true);
        // Focus leaves the first field and lands on the next one.
        policy.onTextFocusSignal(false);
        policy.onTextFocusSignal(true);
        assertNull("the hide was cancelled", scheduler.pending);
        scheduler.expire();
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());
        assertTrue(trace.contains("hide cancelled: focus in"));
    }

    @Test
    public void aTapOverReadOnlyText_withFocusPresent_showsNothing() {
        // A field had focus and lost it: the terminal the user then taps draws a text cursor too.
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        scheduler.expire();
        keyboard.calls.clear();

        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);
        assertEquals(State.CLOSED, policy.state());
        assertTrue(trace.contains("tap over xterm ignored: no focus"));
    }

    @Test
    public void aFocusOutAlone_isEnoughToKnowFocusIsThere() {
        policy.onTextFocusSignal(false);
        tapOver("xterm");
        assertEquals(List.of(), keyboard.calls);
        assertEquals(State.CLOSED, policy.state());
    }

    @Test
    public void aConfirmingTap_raisesAKeyboardPutDownWhileTheFieldKeptFocus() {
        policy.onTextFocusSignal(true);
        // The keyboard's own hide key: down, focus still in the field, nothing telling the policy.
        keyboard.up = false;
        keyboard.calls.clear();
        tapOver("xterm");
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());

        // And after the user put it down by hand.
        policy.onUserKeyboardIntent(false);
        keyboard.up = false;
        keyboard.calls.clear();
        tapOver("xterm");
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void withFocusPresent_aTapOrADragElsewhere_leavesHidingToFocus() {
        policy.onTextFocusSignal(true);
        tapOver("left_ptr");
        policy.onCursorName("left_ptr");
        policy.onDisplayDrag();
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());
    }

    @Test
    public void aTapThenFocusIn_insideTheTapWindow_showsOnce() {
        policy.onTextFocusSignal(false);
        policy.onDisplayTap();
        policy.onCursorName("xterm");
        policy.onTextFocusSignal(true);
        scheduler.expire();
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());
    }

    @Test
    public void focusInThenATap_showsOnce() {
        policy.onTextFocusSignal(true);
        tapOver("xterm");
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.AUTO_OPEN, policy.state());
    }

    @Test
    public void aTapThenFocusOut_hidesAfterTheGrace() {
        policy.onTextFocusSignal(true);
        // Tapping the terminal: the field's focus-out lands inside the tap's window.
        policy.onDisplayTap();
        policy.onCursorName("xterm");
        policy.onTextFocusSignal(false);
        assertEquals(DisplayTextFocusPolicy.HIDE_GRACE_MS, scheduler.delayMs);
        scheduler.expire();
        assertEquals(List.of("show", "hide"), keyboard.calls);
        assertEquals(State.CLOSED, policy.state());
    }

    @Test
    public void aTapOrADragDuringTheGrace_doesNotCancelTheHide() {
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        policy.onDisplayTap();
        policy.onCursorName("xterm");
        policy.onDisplayDrag();
        scheduler.expire();
        assertEquals(List.of("show", "hide"), keyboard.calls);
        assertEquals(State.CLOSED, policy.state());
    }

    @Test
    public void aPinnedKeyboard_ignoresFocus_andNoGraceRuns() {
        keyboard.up = true;
        policy.onUserKeyboardIntent(true);
        policy.onTextFocusSignal(false);
        assertNull(scheduler.pending);
        policy.onTextFocusSignal(true);
        assertEquals(List.of(), keyboard.calls);
        assertEquals(State.PINNED, policy.state());
    }

    @Test
    public void pinningDuringTheGrace_cancelsTheHide() {
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        policy.onUserKeyboardIntent(true);
        scheduler.expire();
        assertEquals(List.of("show"), keyboard.calls);
        assertEquals(State.PINNED, policy.state());
    }

    @Test
    public void leavingThePlaceOrTheSettingOff_duringTheGrace_hidesNothing() {
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        policy.onPlaceLeft();
        scheduler.expire();
        assertEquals(List.of("show"), keyboard.calls);

        policy.onPlaceEntered(false);
        keyboard.calls.clear();
        policy.onTextFocusSignal(true);
        policy.onTextFocusSignal(false);
        policy.setEnabled(false);
        scheduler.expire();
        assertEquals(List.of("show"), keyboard.calls);
    }

    @Test
    public void withNoFocusSignalSinceEntering_tapRulesAreUnchanged() {
        // Focus heard on an earlier visit does not count on this one.
        policy.onTextFocusSignal(false);
        policy.onPlaceLeft();
        policy.onPlaceEntered(false);

        tapOver("xterm");
        assertEquals(List.of("show"), keyboard.calls);
        tapOver("left_ptr");
        assertEquals(List.of("show", "hide"), keyboard.calls);
        tapOver("xterm");
        policy.onCursorName("left_ptr");
        policy.onDisplayDrag();
        assertEquals(List.of("show", "hide", "show", "hide"), keyboard.calls);
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

    // The policy is given no touch mode: a tap is a click where it lands, so the same tap reads
    // the same in Touchscreen (under the finger), Direct touch (under the finger) and Trackpad
    // (at the pointer, or on the touchpad). These two stand for all three.

    @Test
    public void aTapOverText_showsTheKeyboard_andANonTextTapPutsItDown() {
        assertTrue(policy.isActive());
        tapOver("xterm");
        assertEquals(State.AUTO_OPEN, policy.state());
        assertEquals(List.of("show"), keyboard.calls);

        tapOver("left_ptr");
        assertEquals(State.CLOSED, policy.state());
        assertEquals(List.of("show", "hide"), keyboard.calls);
    }

    @Test
    public void aPinnedKeyboard_keepsItsPriority() {
        policy.onPlaceLeft();
        policy.onPlaceEntered(true);
        keyboard.up = true;
        tapOver("xterm");
        tapOver("left_ptr");
        assertEquals(State.PINNED, policy.state());
        assertEquals(List.of(), keyboard.calls);
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

    @Test
    public void everyDecisionIsTraced() {
        trace.clear();
        tapOver("xterm");
        assertEquals(1, trace.size());
        assertTrue(trace.get(0).contains("xterm"));
        assertTrue(trace.get(0).contains("show"));
    }
}
