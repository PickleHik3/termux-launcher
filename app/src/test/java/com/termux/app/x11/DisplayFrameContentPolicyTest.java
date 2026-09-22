package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.DisplayFrameContentPolicy.Content;
import com.termux.app.x11.DisplayFrameContentPolicy.Decision;
import com.termux.app.x11.DisplayFrameContentPolicy.Intent;

import org.junit.Before;
import org.junit.Test;

/** What the keyboard frame holds while mouse mode owns it, event by event. */
public class DisplayFrameContentPolicyTest {

    private DisplayFrameContentPolicy policy;

    @Before
    public void setUp() {
        policy = new DisplayFrameContentPolicy();
    }

    private void assertDecision(Decision decision, Content content, Intent intent) {
        assertDecision(decision, content, intent, false);
    }

    private void assertDecision(Decision decision, Content content, Intent intent,
                                boolean releaseFrame) {
        assertTrue("the event should have been taken", decision.taken);
        assertEquals(content, decision.content);
        assertEquals(intent, decision.intent);
        assertEquals("release frame", releaseFrame, decision.releaseFrame);
        assertEquals(content, policy.content());
    }

    @Test
    public void mouseModeOff_ownsNothing() {
        assertEquals(Content.NONE, policy.content());
        assertFalse(policy.isMouseMode());
        assertFalse(policy.isKeyboardContent());
        // Every keyboard route is the caller's own business until mouse mode takes the frame.
        assertFalse(policy.onKeyboardKey().taken);
        assertFalse(policy.onKeyboardIntent(true).taken);
        assertFalse(policy.onTextFocus(true).taken);
        assertFalse(policy.onTextFocus(false).taken);
        assertFalse(policy.onMouseModeOff().taken);
        assertFalse(policy.onFrameLost().taken);
        assertEquals(Content.NONE, policy.content());
    }

    @Test
    public void mouseModeOn_takesTheFrameForThePad_andPinsNothing() {
        assertDecision(policy.onMouseModeOn(), Content.PAD, Intent.UNPIN);
        assertTrue(policy.isMouseMode());
        assertFalse(policy.isKeyboardContent());
        // Asked for again it changes nothing, and unpins nothing either.
        assertDecision(policy.onMouseModeOn(), Content.PAD, Intent.NONE);
    }

    @Test
    public void theMouseKey_turnsItOn_thenOff() {
        assertDecision(policy.onMouseKey(), Content.PAD, Intent.UNPIN);
        // Off again with a keyboard that was already up: the frame is left as it is, so a
        // keyboard still on screen is theirs.
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.PIN);
        assertFalse(policy.isMouseMode());
    }

    /**
     * The keyboard was off when the mouse key came: the pad raised a frame to stand in, so the
     * mouse key closes that frame with the pad — a toggle opens and closes the same thing.
     */
    @Test
    public void theMouseKey_closesTheFrameThePadRaised() {
        assertDecision(policy.onMouseKey(), Content.PAD, Intent.UNPIN);
        policy.onFrameRaisedForPad();
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.NONE, true);
        assertFalse(policy.isMouseMode());

        // Next time round the frame is judged afresh.
        assertDecision(policy.onMouseKey(), Content.PAD, Intent.UNPIN);
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.PIN);
    }

    @Test
    public void aKeyboardTheUserAskedFor_staysWhenMouseModeEnds_evenOnThePadsFrame() {
        policy.onMouseKey();
        policy.onFrameRaisedForPad();
        // The keyboard key brings the keyboard forward, then puts it back behind the pad: the
        // user has asked for a keyboard here, so the frame is theirs now.
        assertDecision(policy.onKeyboardKey(), Content.KEYBOARD, Intent.PIN);
        assertDecision(policy.onKeyboardKey(), Content.PAD, Intent.UNPIN);
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.PIN);
    }

    @Test
    public void textFocusOnThePadsFrame_doesNotMakeTheFrameTheUsers() {
        policy.onMouseKey();
        policy.onFrameRaisedForPad();
        // A text field took the keyboard and let it go again: nobody asked for a keyboard.
        assertDecision(policy.onTextFocus(true), Content.KEYBOARD, Intent.NONE);
        assertDecision(policy.onTextFocus(false), Content.PAD, Intent.NONE);
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.NONE, true);
    }

    @Test
    public void mouseModeEndingFromAParkedKeyboard_keepsIt() {
        policy.onMouseKey();
        policy.onFrameRaisedForPad();
        policy.onTextFocus(true);
        // The user is typing into it: the exit arrow or the swipe ends mouse mode, not the typing.
        assertDecision(policy.onMouseModeOff(), Content.NONE, Intent.PIN);
    }

    @Test
    public void losingTheFrame_forgetsThatThePadRaisedIt() {
        policy.onMouseKey();
        policy.onFrameRaisedForPad();
        policy.onFrameLost();
        // Back on the place a keyboard is up already (the user's): the mouse key leaves it.
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.PIN);
    }

    @Test
    public void textFocus_bringsTheKeyboardToTheFront_andLetsItGoAgain() {
        policy.onMouseModeOn();
        // The text-focus policy has moved its own state, so neither swap reports back to it.
        assertDecision(policy.onTextFocus(true), Content.KEYBOARD, Intent.NONE);
        assertTrue(policy.isKeyboardContent());
        assertTrue(policy.isMouseMode());

        // Focus in twice is one keyboard.
        assertDecision(policy.onTextFocus(true), Content.KEYBOARD, Intent.NONE);

        assertDecision(policy.onTextFocus(false), Content.PAD, Intent.NONE);
        assertFalse(policy.isKeyboardContent());
        // And focus out with the pad already up changes nothing.
        assertDecision(policy.onTextFocus(false), Content.PAD, Intent.NONE);
    }

    @Test
    public void theKeyboardKey_swapsTheFrame_andThePinFollowsTheUser() {
        policy.onMouseModeOn();
        assertDecision(policy.onKeyboardKey(), Content.KEYBOARD, Intent.PIN);
        // Pressed again it hands the frame back to the pad, and the keyboard back to the policy.
        assertDecision(policy.onKeyboardKey(), Content.PAD, Intent.UNPIN);
        assertTrue(policy.isMouseMode());
    }

    @Test
    public void theKeyboardKey_afterTextFocusParkedThePad_isStillTheUsersDoing() {
        policy.onMouseModeOn();
        policy.onTextFocus(true);
        // The keyboard is already the frame's content; the key puts it away and unpins.
        assertDecision(policy.onKeyboardKey(), Content.PAD, Intent.UNPIN);
    }

    @Test
    public void askingForTheKeyboardByName_pinsIt_andAskingItDownHandsItBack() {
        policy.onMouseModeOn();
        assertDecision(policy.onKeyboardIntent(true), Content.KEYBOARD, Intent.PIN);
        // Asked for again while it is already there, it stays pinned.
        assertDecision(policy.onKeyboardIntent(true), Content.KEYBOARD, Intent.PIN);
        assertDecision(policy.onKeyboardIntent(false), Content.PAD, Intent.UNPIN);
        // Asked down again: the frame is already the pad's, so only the pin is reported.
        assertDecision(policy.onKeyboardIntent(false), Content.PAD, Intent.UNPIN);
    }

    @Test
    public void theMouseKey_withTheKeyboardParkedInFront_bringsThePadBack_andStaysInMouseMode() {
        policy.onMouseModeOn();
        policy.onTextFocus(true);
        assertDecision(policy.onMouseKey(), Content.PAD, Intent.UNPIN);
        assertTrue("still mouse mode", policy.isMouseMode());
        // And the next press is the one that leaves.
        assertDecision(policy.onMouseKey(), Content.NONE, Intent.PIN);
    }

    @Test
    public void theExitArrow_leavesMouseMode_fromEitherContent() {
        policy.onMouseModeOn();
        assertDecision(policy.onMouseModeOff(), Content.NONE, Intent.PIN);

        policy.onMouseModeOn();
        policy.onKeyboardKey();
        assertDecision(policy.onMouseModeOff(), Content.NONE, Intent.PIN);
        assertFalse(policy.isMouseMode());
    }

    @Test
    public void losingTheFrame_comesBackToThePad_andNeverEndsMouseMode() {
        policy.onMouseModeOn();
        policy.onKeyboardKey();
        // The wall left the place, or the display stopped: the keyboard is the wall's from here,
        // and mouse mode comes back to the pad when the frame is ours again.
        assertDecision(policy.onFrameLost(), Content.PAD, Intent.NONE);
        assertTrue(policy.isMouseMode());
        // Repeated syncs say the same thing.
        assertDecision(policy.onFrameLost(), Content.PAD, Intent.NONE);
    }

    @Test
    public void mouseModeOnWhileTheKeyboardIsParked_changesNothing() {
        policy.onMouseModeOn();
        policy.onKeyboardKey();
        assertDecision(policy.onMouseModeOn(), Content.KEYBOARD, Intent.NONE);
    }

    @Test
    public void everyContentSaysWhetherMouseModeIsOn() {
        assertFalse(new DisplayFrameContentPolicy().isMouseMode());
        assertFalse(policy.onMouseModeOn().content == Content.NONE);
        assertTrue(policy.onMouseModeOn().mouseMode());
        assertTrue(policy.onKeyboardKey().mouseMode());
        assertFalse(policy.onMouseModeOff().mouseMode());
    }
}
