package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReadingSetTest {

    private static ReadingSet with(ReadingKind... kinds) {
        ReadingSet set = new ReadingSet();
        for (ReadingKind kind : kinds) set.put(kind, ReadingFormat.percent(kind, 10));
        return set;
    }

    @Test
    public void aTapGoesToTheNextReadingThatHasAValue() {
        ReadingSet set = with(ReadingKind.CPU, ReadingKind.RAM, ReadingKind.BATTERY);
        assertEquals(ReadingKind.RAM, set.next(ReadingKind.CPU));
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.RAM));
        assertEquals("the last wraps to the first", ReadingKind.CPU, set.next(ReadingKind.BATTERY));
    }

    @Test
    public void aReadingWithNoValueIsSkipped() {
        ReadingSet set = with(ReadingKind.CPU, ReadingKind.BATTERY);
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.CPU));
    }

    @Test
    public void aLoneReadingStaysWhereItIs() {
        ReadingSet set = with(ReadingKind.BATTERY);
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.BATTERY));
    }

    @Test
    public void aRememberedReadingThatIsNotOfferedShowsTheNextOne() {
        ReadingSet set = with(ReadingKind.RAM, ReadingKind.BATTERY);
        assertEquals(ReadingKind.RAM, set.effective(ReadingKind.GPU));
        // A tap from a reading that is not shown goes on from the one that is.
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.GPU));
    }

    @Test
    public void withNothingOfferedTheChosenReadingStandsEmpty() {
        ReadingValue shown = new ReadingSet().shown(ReadingKind.CPU);
        assertEquals(ReadingKind.CPU, shown.kind);
        assertEquals(ReadingValue.UNKNOWN_NUMBER, shown.number);
        assertEquals("--", shown.full());
    }

    @Test
    public void aChosenReadingStillLoadingIsNotJumpedOver() {
        ReadingSet set = with(ReadingKind.BATTERY);
        set.expect(ReadingKind.CPU, true);
        assertEquals(ReadingKind.CPU, set.effective(ReadingKind.CPU));
        assertEquals("shown as empty until it answers", "--", set.shown(ReadingKind.CPU).full());
        set.put(ReadingKind.CPU, null);
        assertEquals("an answer of none ends the wait", ReadingKind.BATTERY, set.effective(ReadingKind.CPU));
    }

    @Test
    public void putReportsWhetherAnythingChanged() {
        ReadingSet set = new ReadingSet();
        assertTrue(set.put(ReadingKind.CPU, ReadingFormat.percent(ReadingKind.CPU, 5)));
        assertFalse(set.put(ReadingKind.CPU, ReadingFormat.percent(ReadingKind.CPU, 5)));
        assertTrue(set.put(ReadingKind.CPU, ReadingFormat.percent(ReadingKind.CPU, 6)));
        assertTrue(set.put(ReadingKind.CPU, null));
        assertFalse(set.put(ReadingKind.CPU, null));
        assertNull(set.get(ReadingKind.CPU));
    }

    @Test
    public void theStoredWordRoundTripsAndAnythingElseIsTheDefault() {
        for (ReadingKind kind : ReadingKind.values()) {
            assertEquals(kind, ReadingKind.fromKey(kind.key()));
        }
        assertEquals(ReadingKind.DEFAULT, ReadingKind.fromKey("nonsense"));
        assertEquals(ReadingKind.DEFAULT, ReadingKind.fromKey(null));
    }
}
