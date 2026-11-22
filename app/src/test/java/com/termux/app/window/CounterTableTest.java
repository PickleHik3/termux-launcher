package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The counter table that outlives the motion log's ring (ADR 0028 section 5). */
public class CounterTableTest {

    @Test
    public void aNameKeepsItsCountFirstLastAndLastDetail() {
        CounterTable table = new CounterTable();
        table.count(100, "hold:x:backstop", "ms=160");
        table.count(250, "hold:x:backstop", "ms=300");

        List<String> lines = table.lines();

        assertEquals(1, lines.size());
        assertEquals("hold:x:backstop count=2 first=100 last=250 detail=ms=300", lines.get(0));
        assertEquals(2, table.countOf("hold:x:backstop"));
        assertEquals(0, table.countOf("other"));
    }

    @Test
    public void aNullDetailKeepsTheLastOneAndALongOneIsCut() {
        CounterTable table = new CounterTable();
        table.count(1, "a", "d");
        table.count(2, "a", null);
        table.count(3, "b", new String(new char[200]).replace('\0', 'x'));

        assertTrue(table.lines().get(0), table.lines().get(0).endsWith("detail=d"));
        assertEquals(CounterTable.DETAIL_MAX,
            table.lines().get(1).substring(table.lines().get(1).indexOf("detail=") + 7).length());
    }

    @Test
    public void linesComeInNameOrderAndClearEmptiesTheTable() {
        CounterTable table = new CounterTable();
        table.count(1, "b", null);
        table.count(1, "a", null);

        assertTrue(table.lines().get(0).startsWith("a "));
        assertTrue(!table.isEmpty());
        table.clear();
        assertTrue(table.isEmpty());
        assertTrue(table.lines().isEmpty());
    }

    @Test
    public void theTableSurvivesAnyNumberOfRingLines() {
        CounterTable table = new CounterTable();
        table.count(1, "INVARIANT-like", "once");
        for (int i = 0; i < 5000; i++) table.count(i, "noise", null);

        assertEquals(1, table.countOf("INVARIANT-like"));
        assertEquals(5000, table.countOf("noise"));
    }
}
