package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The clock's per-frame width memo and glyph advance table measure once and answer the same. */
public class TerminalClockTextWidthsTest {

    /** A measurer whose answer depends on the text and on a "paint" the test moves. */
    private static final class CountingMeasurer implements TerminalClockWidget.TextWidths.Measurer {
        final List<String> measured = new ArrayList<>();
        float scale = 1f;

        @Override
        public float measure(String text) {
            measured.add(text);
            return text.length() * 7.5f * scale + text.charAt(0);
        }
    }

    @Test
    public void aWidthIsMeasuredOnceForTheSameTextFaceSizeAndSpacing() {
        TerminalClockWidget.TextWidths widths = new TerminalClockWidget.TextWidths();
        CountingMeasurer measurer = new CountingMeasurer();
        Object face = new Object();
        float first = widths.width("MON 06 OCT", face, 24f, .2f, measurer);
        for (int frame = 0; frame < 30; frame++) {
            assertEquals(first, widths.width(new String("MON 06 OCT"), face, 24f, .2f, measurer),
                0f);
        }
        assertEquals(1, measurer.measured.size());
    }

    @Test
    public void anyPartOfTheKeyChangingMeasuresAgain() {
        TerminalClockWidget.TextWidths widths = new TerminalClockWidget.TextWidths();
        CountingMeasurer measurer = new CountingMeasurer();
        Object face = new Object();
        widths.width("12:30", face, 24f, 0f, measurer);
        widths.width("12:31", face, 24f, 0f, measurer);
        widths.width("12:30", new Object(), 24f, 0f, measurer);
        widths.width("12:30", face, 25f, 0f, measurer);
        widths.width("12:30", face, 24f, .1f, measurer);
        assertEquals(5, measurer.measured.size());
    }

    @Test
    public void anEvictedEntryIsMeasuredAfreshWithTheSameAnswer() {
        TerminalClockWidget.TextWidths widths = new TerminalClockWidget.TextWidths();
        CountingMeasurer measurer = new CountingMeasurer();
        Object face = new Object();
        float first = widths.width("A", face, 10f, 0f, measurer);
        for (int i = 0; i < TerminalClockWidget.TextWidths.CAPACITY; i++) {
            widths.width("filler" + i, face, 10f, 0f, measurer);
        }
        int before = measurer.measured.size();
        assertEquals(first, widths.width("A", face, 10f, 0f, measurer), 0f);
        assertEquals(before + 1, measurer.measured.size());
    }

    @Test
    public void advancesAreMeasuredPerGlyphOnceAndResetWhenTheFaceMoves() {
        TerminalClockWidget.CharAdvances advances = new TerminalClockWidget.CharAdvances();
        CountingMeasurer measurer = new CountingMeasurer();
        Object face = new Object();
        for (int frame = 0; frame < 20; frame++) {
            for (char c : "12:34".toCharArray()) {
                assertEquals(measurer.measure(String.valueOf(c)),
                    advances.advance(c, face, 30f, -.02f, measurer), 0f);
            }
        }
        // Four distinct glyphs plus the colon, measured once by the table (the assertion's own
        // calls are the other hundred).
        assertEquals(100 + 5, measurer.measured.size());

        measurer.measured.clear();
        measurer.scale = 2f;
        float doubled = advances.advance('1', face, 31f, -.02f, measurer);
        assertEquals(1, measurer.measured.size());
        assertEquals(1 * 7.5f * 2f + '1', doubled, 0f);
    }

    @Test
    public void glyphStringsAreSharedForAsciiAndEqualToValueOf() {
        for (char c = 0; c < 128; c++) {
            assertEquals(String.valueOf(c), TerminalClockWidget.CharAdvances.charString(c));
            assertSame(TerminalClockWidget.CharAdvances.charString(c),
                TerminalClockWidget.CharAdvances.charString(c));
        }
        assertEquals("é", TerminalClockWidget.CharAdvances.charString('é'));
    }
}
