package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A session's row of terminal and Linux windows (#392): places, adding, closing, moving. */
public class SessionRowTest {

    private static final long FIREFOX = 11L;
    private static final long GTK = 12L;
    private static final long XTERM = 13L;

    private SessionRow<String> row;
    private List<String> terms;

    @Before
    public void setUp() {
        row = new SessionRow<>();
        terms = new ArrayList<>(Arrays.asList("t1", "t2", "t3"));
    }

    /** The row as text: terminal names, Linux ids as L<id>. */
    private String order() {
        StringBuilder b = new StringBuilder();
        int n = row.count(terms);
        for (int i = 0; i < n; i++) {
            if (b.length() > 0) b.append(' ');
            long linux = row.linuxAt(terms, i);
            int t = row.terminalAt(terms, i);
            b.append(linux != SessionRow.NONE ? "L" + linux : terms.get(t));
        }
        return b.toString();
    }

    @Test
    public void withoutLinuxWindowsTheRowIsTheTerminalList() {
        assertEquals("t1 t2 t3", order());
        assertEquals(3, row.count(terms));
        assertEquals(1, row.frontIndex(terms, 1));
        assertEquals(2, row.indexOfTerminal(terms, 2));
        assertFalse(row.hasLinux());
    }

    @Test
    public void aLinuxWindowOpenedFromTheSecondLandsRightAfterIt() {
        row.add(terms, 1, GTK);
        assertEquals("t1 t2 L12 t3", order());
        assertEquals(2, row.indexOfLinux(terms, GTK));
        assertEquals(3, row.indexOfTerminal(terms, 2));
        // It does not come in front by itself; the caller pages to it.
        assertEquals(1, row.frontIndex(terms, 1));
        assertTrue(row.selectLinux(GTK));
    }

    @Test
    public void aNewLinuxWindowGoesBeforeOnesAlreadyAfterTheSameTerminal() {
        row.add(terms, 0, FIREFOX);
        row.add(terms, 0, GTK);
        assertEquals("t1 L12 L11 t2 t3", order());
    }

    @Test
    public void aLinuxWindowOpenedFromALinuxWindowLandsAfterIt() {
        row.add(terms, 0, FIREFOX);
        row.selectLinux(FIREFOX);
        row.add(terms, 0, GTK);
        assertEquals("t1 L11 L12 t2 t3", order());
    }

    @Test
    public void aLinuxWindowInAnEmptyRowStandsAtTheStart() {
        terms.clear();
        row.add(terms, 0, FIREFOX);
        assertEquals("L11", order());
        assertEquals(0, row.frontIndex(terms, 0));
    }

    @Test
    public void selectReportsWhichKindCameInFront() {
        row.add(terms, 1, GTK);
        assertEquals(-1, row.select(terms, 2));
        assertEquals(GTK, row.front());
        assertEquals(2, row.select(terms, 3));
        assertEquals(SessionRow.NONE, row.front());
        assertEquals(-2, row.select(terms, 9));
    }

    @Test
    public void closingTheLinuxWindowInFrontLandsOnItsRightNeighbour() {
        row.add(terms, 1, GTK);
        row.selectLinux(GTK);
        int next = row.remove(terms, 1, GTK);
        assertEquals("t1 t2 t3", order());
        assertEquals(2, next);
        assertEquals(2, row.terminalAt(terms, next));
        assertEquals(SessionRow.NONE, row.front());
    }

    @Test
    public void closingTheLastLinuxWindowInFrontLandsOnItsLeftNeighbour() {
        row.add(terms, 2, GTK);
        row.add(terms, 2, FIREFOX);
        // t1 t2 t3 L11 L12
        row.selectLinux(GTK);
        int next = row.remove(terms, 2, GTK);
        assertEquals(3, next);
        assertEquals(FIREFOX, row.front());
    }

    @Test
    public void closingALinuxWindowBehindKeepsTheSameWindowInFront() {
        row.add(terms, 0, GTK);
        // t1 L12 t2 t3, t3 in front.
        int next = row.remove(terms, 2, GTK);
        assertEquals(2, next);
        assertEquals(2, row.terminalAt(terms, next));
        assertEquals(Integer.MIN_VALUE, row.remove(terms, 2, GTK));
    }

    @Test
    public void aTerminalWindowClosingHandsItsLinuxWindowsToTheOneBefore() {
        row.add(terms, 1, GTK);
        row.add(terms, 1, FIREFOX);
        assertEquals("t1 t2 L11 L12 t3", order());
        terms.remove("t2");
        assertEquals("t1 L11 L12 t3", order());
        terms.remove("t1");
        assertEquals("L11 L12 t3", order());
    }

    @Test
    public void aRestoreNeverShiftsTerminalWindows() {
        // A session saved and restored holds only its terminal windows, in order.
        row.add(terms, 0, GTK);
        List<String> saved = new ArrayList<>(terms);
        SessionRow<String> restored = new SessionRow<>();
        assertEquals(3, restored.count(saved));
        assertEquals(1, restored.indexOfTerminal(saved, 1));
        assertEquals(Arrays.asList("t1", "t2", "t3"), saved);
    }

    @Test
    public void movingALinuxWindowAmongTerminalWindows() {
        row.add(terms, 0, GTK);
        assertEquals("t1 L12 t2 t3", order());
        assertTrue(row.move(terms, 1, 3));
        assertEquals("t1 t2 t3 L12", order());
        assertTrue(row.move(terms, 3, 0));
        assertEquals("L12 t1 t2 t3", order());
        assertEquals(Arrays.asList("t1", "t2", "t3"), terms);
    }

    @Test
    public void movingATerminalWindowPastALinuxWindowLeavesItInPlace() {
        row.add(terms, 0, GTK);
        // t1 L12 t2 t3 -> move t1 to the end: L12 t2 t3 t1.
        assertTrue(row.move(terms, 0, 3));
        assertEquals("L12 t2 t3 t1", order());
        assertEquals(Arrays.asList("t2", "t3", "t1"), terms);
        assertFalse(row.move(terms, 0, 0));
        assertFalse(row.move(terms, 0, 9));
    }

    @Test
    public void aMinimisedWindowComesBackToItsPlace() {
        row.add(terms, 1, FIREFOX);
        row.add(terms, 1, GTK);
        row.selectLinux(GTK);
        row.add(terms, 1, XTERM);
        // t1 t2 L12 L13 L11 t3
        assertEquals("t1 t2 L12 L13 L11 t3", order());
        SessionRow.Place<String> place = row.placeOf(terms, XTERM);
        row.remove(terms, 1, XTERM);
        row.restore(terms, 0, XTERM, place);
        assertEquals("t1 t2 L12 L13 L11 t3", order());

        // The Linux window before it gone: first after its terminal window.
        place = row.placeOf(terms, XTERM);
        row.remove(terms, 1, XTERM);
        row.remove(terms, 1, GTK);
        row.restore(terms, 0, XTERM, place);
        assertEquals("t1 t2 L13 L11 t3", order());

        // Its terminal window gone too: where a new one goes.
        place = row.placeOf(terms, XTERM);
        row.remove(terms, 1, XTERM);
        terms.remove("t2");
        row.clearFront();
        row.restore(terms, 1, XTERM, place);
        assertEquals("t1 L11 t3 L13", order());
        assertNull(row.placeOf(terms, 99L));
    }

    @Test
    public void clearingDropsEveryLinuxWindow() {
        row.add(terms, 0, GTK);
        row.selectLinux(GTK);
        row.clearLinux();
        assertEquals("t1 t2 t3", order());
        assertEquals(SessionRow.NONE, row.front());
    }

    @Test
    public void linuxIdsComeInRowOrder() {
        row.add(terms, 2, FIREFOX);
        row.add(terms, 0, GTK);
        List<Long> ids = new ArrayList<>();
        row.linuxIds(terms, ids);
        assertEquals(Arrays.asList(GTK, FIREFOX), ids);
    }
    @Test
    public void newTerminalWindowLandsRightAfterTheTerminalWindowInFront() {
        row.add(terms, 1, GTK);
        int at = row.newTerminalIndex(terms, 1);
        assertEquals(2, at);
        terms.add(at, "new");
        row.terminalAdded(terms, "new");
        assertEquals("t1 t2 new L12 t3", order());
        assertEquals(SessionRow.NONE, row.front());
    }

    @Test
    public void newTerminalWindowLandsRightAfterTheLinuxWindowInFront() {
        row.add(terms, 1, GTK);
        row.add(terms, 1, FIREFOX);
        // t1 t2 L11 L12 t3; GTK came first, so FIREFOX went before it, right after t2.
        assertEquals("t1 t2 L11 L12 t3", order());
        assertTrue(row.selectLinux(FIREFOX));
        int at = row.newTerminalIndex(terms, 1);
        terms.add(at, "new");
        row.terminalAdded(terms, "new");
        assertEquals("t1 t2 L11 new L12 t3", order());
        // Still in front until the caller shows the new window.
        assertEquals(FIREFOX, row.front());
    }

    @Test
    public void newTerminalWindowAfterALinuxWindowAtTheRowStartGoesFirst() {
        terms.clear();
        row.add(terms, 0, GTK);
        assertTrue(row.selectLinux(GTK));
        int at = row.newTerminalIndex(terms, 0);
        assertEquals(0, at);
        terms.add(at, "new");
        row.terminalAdded(terms, "new");
        assertEquals("L12 new", order());
    }

    @Test
    public void addAtEndStandsAfterTheLastTerminalWindowAndLinuxWindows() {
        row.add(terms, 2, FIREFOX);
        row.addAtEnd(terms, GTK);
        assertEquals("t1 t2 t3 L11 L12", order());
        terms.clear();
        SessionRow<String> empty = new SessionRow<>();
        empty.addAtEnd(terms, XTERM);
        assertEquals(1, empty.count(terms));
        assertEquals(XTERM, empty.linuxAt(terms, 0));
    }
}
