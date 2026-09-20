package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** When the display announces Android's clipboard to X, and how often it is told so. */
public class DisplayClipboardPolicyTest {

    private final List<String> calls = new ArrayList<>();
    private final DisplayClipboardPolicy policy = new DisplayClipboardPolicy(
        new DisplayClipboardPolicy.Sync() {
            @Override public void activate() { calls.add("on"); }
            @Override public void deactivate() { calls.add("off"); }
        });

    @Test public void allThreeAreNeeded() {
        assertTrue(DisplayClipboardPolicy.shouldSync(true, true, true));
        assertFalse("no page", DisplayClipboardPolicy.shouldSync(false, true, true));
        assertFalse("no server", DisplayClipboardPolicy.shouldSync(true, false, true));
        assertFalse("sharing off", DisplayClipboardPolicy.shouldSync(true, true, false));
    }

    @Test public void itStartsOff() {
        assertFalse(policy.isActive());
        assertEquals(Collections.emptyList(), calls);
    }

    @Test public void onlyTheTransitionsAreAnnounced() {
        assertTrue(policy.apply(true, true, true));
        assertTrue(policy.isActive());
        // A preference broadcast and a reconnect both re-apply the same answer.
        assertFalse(policy.apply(true, true, true));
        assertFalse(policy.apply(true, true, true));
        assertEquals(Collections.singletonList("on"), calls);
    }

    @Test public void theSharingSwitchTurnsItOffAndOnAgain() {
        policy.apply(true, true, true);
        assertTrue(policy.apply(true, true, false));
        assertFalse(policy.isActive());
        assertTrue(policy.apply(true, true, true));
        assertEquals(Arrays.asList("on", "off", "on"), calls);
    }

    @Test public void aDisplayThatCameUpBeforeThePageArmsOnTheConnection() {
        // The server is running but the page has not attached yet.
        assertFalse(policy.apply(false, false, true));
        assertFalse(policy.isActive());
        assertTrue(policy.apply(true, true, true));
        assertEquals(Collections.singletonList("on"), calls);
    }

    @Test public void leavingThePageDisarmsOnce() {
        policy.apply(true, true, true);
        assertTrue(policy.deactivate());
        assertFalse("nothing to disarm twice", policy.deactivate());
        assertEquals(Arrays.asList("on", "off"), calls);
    }

    @Test public void aServerThatDiedDisarms() {
        policy.apply(true, true, true);
        assertTrue(policy.apply(true, false, true));
        assertEquals(Arrays.asList("on", "off"), calls);
    }
}
