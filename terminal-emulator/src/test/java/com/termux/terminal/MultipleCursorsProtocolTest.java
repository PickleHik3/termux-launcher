package com.termux.terminal;

import java.util.HashMap;
import java.util.Map;

public class MultipleCursorsProtocolTest extends TerminalTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(6, 4);
    }

    public void testSupportAndEmptyQueries() {
        assertEnteringStringGivesResponse("\033[> q", "\033[>1;2;3;29;30;40;100;101 q");
        assertEnteringStringGivesResponse("\033[>100 q", "\033[>100 q");
        assertEnteringStringGivesResponse("\033[>101 q", "\033[>101;30:0;40:0 q");
    }

    public void testPointMainCursorAndRepeatedCoordinateGroups() {
        enterString("\033[2;3H\033[>29;0 q");
        enterString("\033[>1;2:1:1:2:2;2:4:6 q");

        Map<String, Integer> cursors = cursors();
        assertEquals(4, cursors.size());
        assertEquals(Integer.valueOf(29), cursors.get("1:2"));
        assertEquals(Integer.valueOf(1), cursors.get("0:0"));
        assertEquals(Integer.valueOf(1), cursors.get("1:1"));
        assertEquals(Integer.valueOf(1), cursors.get("3:5"));
        assertEnteringStringGivesResponse("\033[>100 q",
            "\033[>100;29:2:2:3;1:2:1:1;1:2:2:2;1:2:4:6 q");
    }

    public void testRectanglesReplaceAndClearOnlyTheirCells() {
        enterString("\033[>1;4 q");
        assertEquals(24, mTerminal.getExtraCursors().length);
        enterString("\033[>2;4:2:2:3:4 q");
        Map<String, Integer> cursors = cursors();
        assertEquals(24, cursors.size());
        assertEquals(Integer.valueOf(2), cursors.get("1:1"));
        assertEquals(Integer.valueOf(2), cursors.get("2:3"));
        assertEquals(Integer.valueOf(1), cursors.get("0:0"));

        enterString("\033[>0;4:2:2:3:4 q");
        cursors = cursors();
        assertEquals(18, cursors.size());
        assertFalse(cursors.containsKey("1:1"));
        assertEquals(Integer.valueOf(1), cursors.get("3:5"));
        enterString("\033[>0;4 q");
        assertEquals(0, mTerminal.getExtraCursors().length);
    }

    public void testMalformedAndOutOfRangeCoordinatesAreIgnored() {
        enterString("\033[>1;2:0:1:1:0:5:2:1 q");
        enterString("\033[>3;4:5:1:8:8 q");
        enterString("\033[>77;2:1:1 q");
        assertEquals(0, mTerminal.getExtraCursors().length);

        enterString("\033[>2;2:1:1:2 q");
        assertEquals(1, mTerminal.getExtraCursors().length);
        assertEquals(2, mTerminal.getExtraCursors()[0].shape);
    }

    public void testColorsAndColorQuery() {
        enterString("\033[>30;2:10:20:30 q");
        enterString("\033[>40;5:123 q");
        assertEquals(2, mTerminal.getExtraCursorTextColor().type);
        assertEquals(0xff0a141e, mTerminal.getExtraCursorTextColor().value);
        assertEquals(5, mTerminal.getExtraCursorColor().type);
        assertEquals(123, mTerminal.getExtraCursorColor().value);
        assertEnteringStringGivesResponse("\033[>101 q",
            "\033[>101;30:2:10:20:30;40:5:123 q");

        enterString("\033[>40;1 q\033[>30;0 q");
        assertEnteringStringGivesResponse("\033[>101 q", "\033[>101;30:0;40:1 q");
    }

    public void testScreenClearResetAndAlternateScreenRemoveCursors() {
        String add = "\033[>1;2:1:1 q";
        enterString(add + "\033[2J");
        assertEquals(0, mTerminal.getExtraCursors().length);
        enterString(add + "\033[3J");
        assertEquals(0, mTerminal.getExtraCursors().length);
        enterString(add + "\033[22J");
        assertEquals(0, mTerminal.getExtraCursors().length);
        enterString(add + "\033[?1049h");
        assertEquals(0, mTerminal.getExtraCursors().length);
        enterString(add + "\033[?1049l");
        assertEquals(0, mTerminal.getExtraCursors().length);
        enterString(add + "\033c");
        assertEquals(0, mTerminal.getExtraCursors().length);
    }

    public void testScrollingDoesNotMoveCursors() {
        enterString("\033[>3;2:2:3 q");
        enterString("1\r\n2\r\n3\r\n4\r\n5");
        TerminalEmulator.ExtraCursor cursor = mTerminal.getExtraCursors()[0];
        assertEquals(1, cursor.row);
        assertEquals(2, cursor.col);
        assertEquals(3, cursor.shape);
    }

    private Map<String, Integer> cursors() {
        Map<String, Integer> result = new HashMap<>();
        for (TerminalEmulator.ExtraCursor cursor : mTerminal.getExtraCursors())
            result.put(cursor.row + ":" + cursor.col, cursor.shape);
        return result;
    }
}
