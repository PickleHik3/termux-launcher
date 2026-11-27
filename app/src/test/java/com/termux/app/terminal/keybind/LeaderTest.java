package com.termux.app.terminal.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The leader setting and how leader-relative bindings are typed under each leader (ADR 0042). */
public class LeaderTest {

    // ---- Custom chords: what is allowed, refused or warned about

    @Test
    public void modifiersAloneAreHeldAndAKeyMakesAPrefix() {
        LeaderChord held = LeaderChord.parse("Ctrl+Shift").chord;
        assertNotNull(held);
        assertTrue(held.isHeld());
        assertEquals("ctrl+shift", held.spec());

        LeaderChord prefix = LeaderChord.parse("ctrl+B").chord;
        assertNotNull(prefix);
        assertTrue(prefix.isPrefix());
        assertEquals("ctrl+b", prefix.spec());
    }

    @Test
    public void modifiersAreSpelledInTheResolversOrder() {
        assertEquals("ctrl+alt+shift", LeaderChord.parse("shift+alt+ctrl").chord.spec());
    }

    @Test
    public void androidsChordsAreRefusedWithAReason() {
        assertRefused("ctrl+meta", LeaderChord.META);
        assertRefused("super+a", LeaderChord.META);
        assertRefused("ctrl+space", LeaderChord.CTRL_SPACE);
        assertRefused("ctrl+shift+space", LeaderChord.CTRL_SPACE);
        assertRefused("alt+tab", LeaderChord.ALT_TAB);
        assertRefused("alt+shift", LeaderChord.ALT_SHIFT);
    }

    @Test
    public void ctrlShiftIsAllowedWithAWarning() {
        LeaderChord.Check check = LeaderChord.parse("ctrl+shift");
        assertTrue(check.ok());
        assertEquals(LeaderChord.CTRL_SHIFT_WARNING, check.warning);
        assertNull(LeaderChord.parse("ctrl+b").warning);
    }

    @Test
    public void aLeaderNeedsTwoKeysOneOfThemCtrlOrAlt() {
        assertRefused("ctrl", LeaderChord.NEEDS_TWO);
        assertRefused("", LeaderChord.NEEDS_TWO);
        assertRefused(null, LeaderChord.NEEDS_TWO);
        assertRefused("shift+a", LeaderChord.NEEDS_CTRL_OR_ALT);
        assertRefused("ctrl+a+b", LeaderChord.ONE_KEY);
        assertFalse(LeaderChord.parse("ctrl+nosuchkey").ok());
    }

    private static void assertRefused(String chord, String reason) {
        LeaderChord.Check check = LeaderChord.parse(chord);
        assertFalse(chord, check.ok());
        assertEquals(chord, reason, check.refusal);
    }

    // ---- The setting

    @Test
    public void theTerlKeyIsTheDefaultAndWhatABrokenCustomFallsBackTo() {
        assertSame(Leader.TERL_KEY, Leader.of(null, null));
        assertSame(Leader.TERL_KEY, Leader.of("terl", "ctrl+b"));
        assertSame(Leader.TERL_KEY, Leader.of("custom", "alt+tab"));
        assertSame(Leader.TERL_KEY, Leader.of("nonsense", null));
        assertSame(Leader.CTRL_ALT, Leader.of("ctrl+alt", null));
        assertEquals(Leader.custom(LeaderChord.parse("ctrl+b").chord),
            Leader.of("custom", "Ctrl+B"));
    }

    // ---- Bindings are leader-relative

    @Test
    public void theTerlKeyAndCtrlAltTypeBindingsAsCtrlAlt() {
        for (Leader leader : new Leader[] {Leader.TERL_KEY, Leader.CTRL_ALT}) {
            assertEquals("ctrl+alt+q", leader.physicalSequence("ctrl+alt+q"));
            assertEquals("ctrl+alt+shift+left", leader.physicalSequence("ctrl+alt+shift+left"));
        }
    }

    @Test
    public void aHeldCustomLeaderReplacesCtrlAltAndKeepsTheBindingsOwnShift() {
        Leader leader = Leader.of("custom", "ctrl+shift");
        assertEquals("ctrl+shift+q", leader.physicalSequence("ctrl+alt+q"));
        assertEquals("ctrl+shift+left", leader.physicalSequence("ctrl+alt+shift+left"));
        Leader altShiftless = Leader.of("custom", "ctrl+alt+shift");
        assertEquals("ctrl+alt+shift+1", altShiftless.physicalSequence("ctrl+alt+1"));
    }

    @Test
    public void aPrefixLeaderComesFirstAndTheBindingFollows() {
        Leader leader = Leader.of("custom", "ctrl+b");
        assertEquals("ctrl+b>q", leader.physicalSequence("ctrl+alt+q"));
        assertEquals("ctrl+b>shift+1", leader.physicalSequence("ctrl+alt+shift+1"));
        assertEquals("ctrl+b>space>p", leader.physicalSequence("ctrl+alt+space>p"));
    }

    @Test
    public void bindingsThatDoNotStartWithTheLeaderAreLeftAlone() {
        Leader leader = Leader.of("custom", "ctrl+b");
        assertEquals("ctrl+shift+c", leader.physicalSequence("ctrl+shift+c"));
        assertEquals("f5", leader.physicalSequence("f5"));
    }

    // ---- Holding the leader, for the hint card

    @Test
    public void ctrlAltHeldIsTheLeaderUnderTheTerlKeyAndCtrlAlt() {
        Leader.Held held = Leader.TERL_KEY.held(true, true, true);
        assertNotNull(held);
        assertEquals("ctrl+alt+", held.prefix);
        assertTrue(held.shift);
        assertNull(Leader.CTRL_ALT.held(true, false, false));
    }

    @Test
    public void aCustomLeaderIsHeldOnlyByItsOwnModifiers() {
        Leader leader = Leader.of("custom", "ctrl+shift");
        Leader.Held held = leader.held(true, false, true);
        assertNotNull(held);
        assertEquals("ctrl+shift+", held.prefix);
        assertFalse(held.shift);
        assertNull(leader.held(true, true, true));
        assertNull(leader.held(true, false, false));
        assertNull(Leader.of("custom", "ctrl+b").held(true, false, false));
    }
}
