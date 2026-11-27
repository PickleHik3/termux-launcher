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

    private static ReadingSet missing(ReadingKind... kinds) {
        ReadingSet set = new ReadingSet();
        for (ReadingKind kind : kinds) set.put(kind, null);
        return set;
    }

    @Test
    public void aFreshSetOffersEveryReadingSoATapFromTheDefaultReachesTheNext() {
        ReadingSet set = with(ReadingKind.BATTERY);
        assertEquals("weather is not known to be missing: offered, loading", ReadingKind.WEATHER,
            set.next(ReadingKind.BATTERY));
    }

    @Test
    public void aTapGoesToTheNextReadingNotKnownToBeMissing() {
        ReadingSet set = with(ReadingKind.CPU, ReadingKind.RAM, ReadingKind.BATTERY);
        set.put(ReadingKind.GPU, null);
        assertEquals(ReadingKind.RAM, set.next(ReadingKind.CPU));
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.RAM));
        assertEquals(ReadingKind.WEATHER, set.next(ReadingKind.BATTERY));
        set.put(ReadingKind.WEATHER, null);
        set.put(ReadingKind.MEDIA, null);
        assertEquals("the last wraps to the first", ReadingKind.CPU, set.next(ReadingKind.BATTERY));
    }

    @Test
    public void aReadingKnownToBeMissingIsSkipped() {
        ReadingSet set = missing(ReadingKind.GPU, ReadingKind.WEATHER, ReadingKind.MEDIA, ReadingKind.RAM);
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.CPU));
    }

    @Test
    public void aLoneReadingStaysWhereItIs() {
        ReadingSet set = missing(ReadingKind.CPU, ReadingKind.GPU, ReadingKind.RAM,
            ReadingKind.WEATHER, ReadingKind.MEDIA);
        assertEquals(ReadingKind.BATTERY, set.next(ReadingKind.BATTERY));
    }

    @Test
    public void aRememberedReadingKnownToBeMissingShowsTheNextOne() {
        ReadingSet set = missing(ReadingKind.GPU);
        assertEquals(ReadingKind.RAM, set.effective(ReadingKind.GPU));
        assertEquals(ReadingKind.BATTERY, with(ReadingKind.BATTERY).effective(ReadingKind.BATTERY));
    }

    @Test
    public void aReadingWithNoValueYetShowsAsLoading() {
        ReadingValue shown = new ReadingSet().shown(ReadingKind.CPU);
        assertEquals(ReadingKind.CPU, shown.kind);
        assertEquals("--", shown.full());
    }

    @Test
    public void droppingAValueLeavesTheReadingOfferedButLoadingAndAnAnswerOfNoneMarksItMissing() {
        ReadingSet set = with(ReadingKind.CPU);
        set.drop(ReadingKind.CPU);
        assertEquals(ReadingKind.CPU, set.effective(ReadingKind.CPU));
        assertEquals("--", set.shown(ReadingKind.CPU).full());
        set.put(ReadingKind.CPU, null);
        assertEquals(ReadingKind.GPU, set.effective(ReadingKind.CPU));
        set.forgetMissing();
        assertEquals(ReadingKind.CPU, set.effective(ReadingKind.CPU));
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
