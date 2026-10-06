package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;

public class NotesWidgetFormatsTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    /** Tuesday 6 October 2026, 13:40 UTC. */
    private static final long NOW = 1791294000000L;
    private static final long MINUTE = 60_000L;

    private static String compact(long modified) {
        return NotesWidgetFormats.compact(NotesWidgetFormats.age(modified, NOW), "just now", "%dm",
            "%dh", Locale.US, UTC);
    }

    @Test public void nowIsTuesdayAfternoon() {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("EEE d MMM HH:mm", Locale.US);
        format.setTimeZone(UTC);
        assertEquals("Tue 6 Oct 13:40", format.format(new java.util.Date(NOW)));
    }

    @Test public void underAMinuteIsJustNow() {
        assertEquals("just now", compact(NOW));
        assertEquals("just now", compact(NOW - 59_000L));
        assertEquals("just now", compact(NOW + 5 * MINUTE));
    }

    @Test public void minutesThenHours() {
        assertEquals("2m", compact(NOW - 2 * MINUTE - 30_000L));
        assertEquals("59m", compact(NOW - 59 * MINUTE));
        assertEquals("1h", compact(NOW - 60 * MINUTE));
        assertEquals("3h", compact(NOW - 3 * 60 * MINUTE - 10 * MINUTE));
        assertEquals("23h", compact(NOW - 23 * 60 * MINUTE));
    }

    @Test public void daysNameTheWeekdayThenTheDate() {
        assertEquals("Mon", compact(NOW - 24 * 60 * MINUTE));
        assertEquals("Thu", compact(NOW - 5 * 24 * 60 * MINUTE));
        assertEquals("29 Sep", compact(NOW - 7 * 24 * 60 * MINUTE));
    }

    @Test public void spokenDayIsInFull() {
        NotesWidgetFormats.Age monday = NotesWidgetFormats.age(NOW - 24 * 60 * MINUTE, NOW);
        assertEquals("Monday", NotesWidgetFormats.spokenDay(monday, Locale.US, UTC));
        NotesWidgetFormats.Age old = NotesWidgetFormats.age(NOW - 30L * 24 * 60 * MINUTE, NOW);
        assertEquals("6 September", NotesWidgetFormats.spokenDay(old, Locale.US, UTC));
    }

    @Test public void headKeepsLinesAndDropsTrailingBlanks() {
        assertEquals(Arrays.asList("# today", "", "a"), NotesWidgetFormats.head("# today\r\n\na\n\n\n", 10));
        assertEquals(Arrays.asList("1", "2"), NotesWidgetFormats.head("1\n2\n3\n", 2));
        assertEquals(Collections.emptyList(), NotesWidgetFormats.head("", 5));
        assertEquals(Collections.emptyList(), NotesWidgetFormats.head(" \n\n", 5));
    }

    @Test public void previewLinesSkipHeadingsAndBlanks() {
        String note = "# today\nnix-shell -p ffmpeg\n\n  try kitty +kitten icat  \n## more\ndock glass\n";
        assertEquals(Arrays.asList("nix-shell -p ffmpeg", "try kitty +kitten icat"),
            NotesWidgetFormats.previewLines(note, 2));
        assertEquals(3, NotesWidgetFormats.previewLines(note, 10).size());
    }

    @Test public void recognisesHeadingsAndCheckedItems() {
        assertTrue(NotesWidgetFormats.isHeading("# today"));
        assertTrue(NotesWidgetFormats.isHeading("  ## sub"));
        assertFalse(NotesWidgetFormats.isHeading("not # a heading"));
        assertTrue(NotesWidgetFormats.isDoneItem("- [x] fix pane rim"));
        assertTrue(NotesWidgetFormats.isDoneItem("  * [X] done"));
        assertFalse(NotesWidgetFormats.isDoneItem("- [ ] widget sizes"));
        assertFalse(NotesWidgetFormats.isDoneItem("[x] no bullet"));
    }

    @Test public void blankMeansOnlyWhitespace() {
        assertTrue(NotesWidgetFormats.isBlank(null));
        assertTrue(NotesWidgetFormats.isBlank(" \n\t"));
        assertFalse(NotesWidgetFormats.isBlank(" x "));
    }
}
