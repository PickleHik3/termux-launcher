package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which session a Linux window joins when it opens and when it is restored (#392). */
public class LinuxWindowHomingTest {

    @Test
    public void aNewWindowJoinsTheCurrentSession() {
        assertEquals(2, LinuxWindowHoming.sessionForNew(2, 3));
    }

    @Test
    public void withoutACurrentSessionItJoinsTheFirst() {
        assertEquals(0, LinuxWindowHoming.sessionForNew(-1, 3));
        assertEquals(0, LinuxWindowHoming.sessionForNew(7, 3));
    }

    @Test
    public void withoutAnySessionItWaits() {
        assertEquals(-1, LinuxWindowHoming.sessionForNew(-1, 0));
        assertEquals(-1, LinuxWindowHoming.sessionForNew(0, 0));
    }

    @Test
    public void aMinimisedWindowRestoresIntoItsOwnSession() {
        String a = "a";
        String b = "b";
        List<String> sessions = Arrays.asList(a, b);
        assertSame(a, LinuxWindowHoming.sessionForRestore(a, sessions, b));
        assertTrue(LinuxWindowHoming.atOldPlace(a, a));
    }

    @Test
    public void ifItsSessionIsGoneItRestoresIntoTheCurrentOne() {
        String gone = "gone";
        String current = "current";
        List<String> sessions = Collections.singletonList(current);
        String target = LinuxWindowHoming.sessionForRestore(gone, sessions, current);
        assertSame(current, target);
        assertFalse(LinuxWindowHoming.atOldPlace(gone, target));
    }

    @Test
    public void withNoSessionAtAllThereIsNowhereToRestore() {
        assertNull(LinuxWindowHoming.sessionForRestore("x", Collections.<String>emptyList(), null));
    }
}
