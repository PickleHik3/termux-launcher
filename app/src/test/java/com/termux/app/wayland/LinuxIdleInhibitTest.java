package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinuxIdleInhibitTest {

    private final List<Boolean> calls = new ArrayList<>();
    private final Set<Long> front = new HashSet<>();
    private final LinuxIdleInhibit idle = new LinuxIdleInhibit(calls::add);

    @Test
    public void keepsTheScreenOnOnlyWhileAnInhibitingWindowIsInFront() {
        idle.inhibit(1, true);
        idle.update(front::contains);
        assertFalse("inhibiting but not in front", idle.isOn());
        front.add(1L);
        idle.update(front::contains);
        assertTrue(idle.isOn());
        front.clear();
        idle.update(front::contains);
        assertFalse("left the front", idle.isOn());
        assertEquals(List.of(true, false), calls);
    }

    @Test
    public void aFrontWindowWithoutAnInhibitorDoesNotKeepIt() {
        front.add(1L);
        idle.update(front::contains);
        assertFalse(idle.isOn());
        assertTrue(calls.isEmpty());
    }

    @Test
    public void destroyingTheInhibitorLetsGo() {
        front.add(1L);
        idle.inhibit(1, true);
        idle.update(front::contains);
        idle.inhibit(1, false);
        idle.update(front::contains);
        assertFalse(idle.isOn());
    }

    @Test
    public void aClosedWindowLetsGo() {
        front.add(1L);
        idle.inhibit(1, true);
        idle.update(front::contains);
        idle.forget(1);
        idle.update(front::contains);
        assertFalse(idle.isOn());
        assertFalse(idle.any());
    }

    @Test
    public void aDeadHostLetsGoAtOnce() {
        front.add(1L);
        idle.inhibit(1, true);
        idle.update(front::contains);
        idle.clear();
        assertFalse(idle.isOn());
        assertEquals(List.of(true, false), calls);
    }

    @Test
    public void oneWindowStillInFrontKeepsItWhenAnotherLetsGo() {
        front.add(1L);
        front.add(2L);
        idle.inhibit(1, true);
        idle.inhibit(2, true);
        idle.update(front::contains);
        idle.inhibit(1, false);
        idle.update(front::contains);
        assertTrue(idle.isOn());
        assertEquals("the screen is touched once", List.of(true), calls);
    }
}
