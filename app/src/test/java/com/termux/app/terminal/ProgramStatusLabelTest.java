package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.terminal.ProgramStatus;
import com.termux.terminal.ProgramStatusStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProgramStatusLabelTest {

    // The library's numbers: idle 0, working 1, done 2, blocked 3, error 4.
    private static final int IDLE = 0, WORKING = 1, DONE = 2, BLOCKED = 3, ERROR = 4;

    private static ProgramStatus record(int state, int progress, String id, String app, String title) {
        ProgramStatusStore store = new ProgramStatusStore();
        store.apply(state, 0, progress, id, app, title, "");
        return store.get(id);
    }

    @Test
    public void wordsFollowTheName() {
        assertEquals("claude working", ProgramStatusLabel.text(record(WORKING, -1, "", "claude", ""), "claude", "w"));
        assertEquals("claude needs input", ProgramStatusLabel.text(record(BLOCKED, -1, "", "claude", ""), "claude", "w"));
        assertEquals("claude done", ProgramStatusLabel.text(record(DONE, -1, "", "claude", ""), "claude", "w"));
        assertEquals("claude error", ProgramStatusLabel.text(record(ERROR, -1, "", "claude", ""), "claude", "w"));
    }

    @Test
    public void percentOnlyOnWorkingAndBlocked() {
        assertEquals("a working 40%", ProgramStatusLabel.text(record(WORKING, 40, "", "a", ""), "a", "w"));
        assertEquals("a needs input 0%", ProgramStatusLabel.text(record(BLOCKED, 0, "", "a", ""), "a", "w"));
        assertEquals("a done", ProgramStatusLabel.text(record(DONE, 40, "", "a", ""), "a", "w"));
        assertEquals("a error", ProgramStatusLabel.text(record(ERROR, 40, "", "a", ""), "a", "w"));
        assertEquals("a working", ProgramStatusLabel.text(record(WORKING, -1, "", "a", ""), "a", "w"));
    }

    @Test
    public void nameFallsBackToTitleThenWindow() {
        ProgramStatus r = record(WORKING, -1, "", "", "build");
        assertEquals("build working", ProgramStatusLabel.text(r, "", "win"));
        assertEquals("build working", ProgramStatusLabel.text(r, null, "win"));
        assertEquals("win working", ProgramStatusLabel.text(record(WORKING, -1, "", "", ""), "", "win"));
        assertEquals("app working", ProgramStatusLabel.text(r, "app", "win"));
    }

    @Test
    public void longNamesAreCutAtTwelveCodePoints() {
        assertEquals("123456789012 done", ProgramStatusLabel.text(
            record(DONE, -1, "", "", ""), "123456789012", "w"));
        assertEquals("123456789012… done", ProgramStatusLabel.text(
            record(DONE, -1, "", "", ""), "1234567890123", "w"));
        String astral = new String(Character.toChars(0x1F600));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 13; i++) many.append(astral);
        String cut = ProgramStatusLabel.cleanName(many.toString());
        assertEquals(13, cut.codePointCount(0, cut.length()));
        assertEquals('…', cut.charAt(cut.length() - 1));
    }

    @Test
    public void controlCharactersAreDropped() {
        assertEquals("ab", ProgramStatusLabel.cleanName("a\u0007\u001bb\u007f\u0085"));
    }

    @Test
    public void idleHasNoStatus() {
        assertNull(ProgramStatusLabel.statusFor(record(IDLE, -1, "", "a", ""), "a", "w"));
        assertEquals(TerminalWindowBar.WindowItem.Status.Kind.BLOCKED,
            ProgramStatusLabel.statusFor(record(BLOCKED, -1, "", "a", ""), "a", "w").kind);
    }

    @Test
    public void oneLineCutsAndFlattens() {
        assertEquals("a b", ProgramStatusLabel.oneLine("a\nb", 200));
        assertEquals("", ProgramStatusLabel.oneLine(null, 200));
        assertEquals("abc…", ProgramStatusLabel.oneLine("abcdef", 3));
    }

    @Test
    public void mostUrgentAcrossPanes() {
        ProgramStatus working = record(WORKING, -1, "", "a", "");
        ProgramStatus done = record(DONE, -1, "", "a", "");
        ProgramStatus error = record(ERROR, -1, "", "a", "");
        ProgramStatus blocked = record(BLOCKED, -1, "", "a", "");
        ProgramStatus idle = record(IDLE, -1, "", "a", "");
        assertEquals(-1, ProgramStatusLabel.pickMostUrgent(Collections.<ProgramStatus>emptyList()));
        assertEquals(-1, ProgramStatusLabel.pickMostUrgent(Arrays.asList(null, idle)));
        assertEquals(1, ProgramStatusLabel.pickMostUrgent(Arrays.asList(done, working)));
        assertEquals(1, ProgramStatusLabel.pickMostUrgent(Arrays.asList(working, error)));
        assertEquals(2, ProgramStatusLabel.pickMostUrgent(Arrays.asList(working, error, blocked)));
        assertEquals(0, ProgramStatusLabel.pickMostUrgent(Arrays.asList(done, idle, null)));
    }

    @Test
    public void tieGoesToTheLaterUpdate() {
        ProgramStatusStore store = new ProgramStatusStore();
        store.apply(WORKING, 0, -1, "x", "", "", "");
        ProgramStatus first = store.get("x");
        store.apply(WORKING, 0, -1, "y", "", "", "");
        ProgramStatus second = store.get("y");
        List<ProgramStatus> panes = new ArrayList<>(Arrays.asList(first, second));
        assertEquals(1, ProgramStatusLabel.pickMostUrgent(panes));
        Collections.reverse(panes);
        assertEquals(0, ProgramStatusLabel.pickMostUrgent(panes));
    }
}
