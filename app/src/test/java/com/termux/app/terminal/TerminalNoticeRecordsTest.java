package com.termux.app.terminal;

import com.termux.terminal.ProgramStatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TerminalNoticeRecordsTest {

    @Test
    public void oneRecordPerWindow() {
        TerminalNoticeRecords records = new TerminalNoticeRecords();
        Object pane = new Object();
        records.put(TerminalNoticeKind.MESSAGE, pane, "w1", "", null, null);
        TerminalNoticeRecords.Record second =
            records.put(TerminalNoticeKind.ERROR, pane, "w1", "id", "s", ProgramStatus.State.ERROR);
        assertEquals(1, records.size());
        assertSame(second, records.get("w1"));
        assertEquals("id", second.messageId);
        assertEquals("s", second.statusId);
        assertSame(pane, second.pane());
    }

    @Test
    public void generationIncreasesOnEveryPut() {
        TerminalNoticeRecords records = new TerminalNoticeRecords();
        Object pane = new Object();
        int a = records.put(TerminalNoticeKind.MESSAGE, pane, "w1", "", null, null).generation;
        int b = records.put(TerminalNoticeKind.MESSAGE, pane, "w2", "", null, null).generation;
        int c = records.put(TerminalNoticeKind.MESSAGE, pane, "w1", "", null, null).generation;
        assertTrue(a < b && b < c);
    }

    @Test
    public void removeReturnsTheRecord() {
        TerminalNoticeRecords records = new TerminalNoticeRecords();
        TerminalNoticeRecords.Record r =
            records.put(TerminalNoticeKind.MESSAGE, new Object(), "w1", "", null, null);
        assertSame(r, records.remove("w1"));
        assertNull(records.remove("w1"));
        assertEquals(0, records.size());
    }

    @Test
    public void removeForPaneDropsOnlyThatPanesRecords() {
        TerminalNoticeRecords records = new TerminalNoticeRecords();
        Object a = new Object();
        Object b = new Object();
        records.put(TerminalNoticeKind.MESSAGE, a, "w1", "", null, null);
        records.put(TerminalNoticeKind.MESSAGE, b, "w2", "", null, null);
        List<TerminalNoticeRecords.Record> removed = records.removeForPane(a);
        assertEquals(1, removed.size());
        assertEquals("w1", removed.get(0).windowKey);
        assertNotNull(records.get("w2"));
        assertEquals(1, records.size());
    }

    @Test
    public void bellMarksAreOnePerWindowAndClearWithThePane() {
        TerminalNoticeRecords records = new TerminalNoticeRecords();
        Object a = new Object();
        Object b = new Object();
        assertFalse(records.bellSpent("w1"));
        records.markBellSpent("w1", a);
        records.markBellSpent("w1", a);
        records.markBellSpent("w2", b);
        assertTrue(records.bellSpent("w1"));
        records.clearBellFor(a);
        assertFalse(records.bellSpent("w1"));
        assertTrue(records.bellSpent("w2"));
    }

    @Test
    public void sharedIsOneInstance() {
        assertSame(TerminalNoticeRecords.shared(), TerminalNoticeRecords.shared());
    }
}
