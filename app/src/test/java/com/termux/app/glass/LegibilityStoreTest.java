package com.termux.app.glass;

import com.termux.app.window.Counters;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The legibility store (ADR 0029 section 9): one correction per page and band, measured at the
 * page's rest and measured again only when the wallpaper, the palette, light or dark or the band's
 * rest rectangle changes; a crossing reads it and measures nothing.
 */
public class LegibilityStoreTest {

    private static final long HOME = LegibilityStore.pageKey(0, -1);
    private static final long HOME_2 = LegibilityStore.pageKey(0, 2);
    private static final long WORKSPACE = LegibilityStore.pageKey(1, -1);
    private static final int LOOK = 7;

    private LegibilityStore store;

    @Before
    public void setUp() {
        store = new LegibilityStore();
    }

    @After
    public void tearDown() {
        Counters.enable(false, null);
    }

    private boolean record(long page, LegibilityBand band, int face) {
        return store.recordFace(page, band, 0, 1800, 1080, 2000, LOOK, face);
    }

    private LegibilityCorrection get(long page, LegibilityBand band) {
        return store.get(page, band, 0, 1800, 1080, 2000, LOOK);
    }

    // -- keys

    @Test
    public void aCorrectionIsKeptPerPage() {
        record(HOME, LegibilityBand.DOCK, 0xFFEEEEEE);
        record(WORKSPACE, LegibilityBand.DOCK, 0xFF202020);
        assertEquals(0xFFEEEEEE, get(HOME, LegibilityBand.DOCK).face(0));
        assertEquals(0xFF202020, get(WORKSPACE, LegibilityBand.DOCK).face(0));
    }

    @Test
    public void twoPagesOfOneKindAreTwoKeys() {
        // Parallax: the wallpaper under the dock differs per page, so each Home page keeps its own.
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        record(HOME_2, LegibilityBand.DOCK, 0xFFDDDDDD);
        assertEquals(0xFF111111, get(HOME, LegibilityBand.DOCK).face(0));
        assertEquals(0xFFDDDDDD, get(HOME_2, LegibilityBand.DOCK).face(0));
    }

    @Test
    public void aCorrectionIsKeptPerBand() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        store.record(HOME, LegibilityBand.KEYBOARD, 0, 1000, 1080, 1800, LOOK,
            LegibilityCorrection.ofRows(new int[] {1, 2, 3, 4}, LegibilityCorrection.TONE_PALE));
        assertEquals(0xFF111111, get(HOME, LegibilityBand.DOCK).face(0));
        LegibilityCorrection keys = store.get(HOME, LegibilityBand.KEYBOARD, 0, 1000, 1080, 1800, LOOK);
        assertEquals(4, keys.rows());
        assertEquals(LegibilityCorrection.TONE_PALE, keys.tone());
        assertNull(get(HOME, LegibilityBand.STRIP));
    }

    @Test
    public void aPageNeverMeasuredNeedsItsMeasurement() {
        assertTrue(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertFalse(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
        assertTrue(store.needsMeasure(WORKSPACE, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
    }

    @Test
    public void recordingTheSameFaceAgainKeepsTheSameObject() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        LegibilityCorrection first = get(HOME, LegibilityBand.DOCK);
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertSame(first, get(HOME, LegibilityBand.DOCK));
    }

    // -- re-measure triggers

    @Test
    public void aWallpaperChangeMakesEveryCorrectionStale() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        record(WORKSPACE, LegibilityBand.DOCK, 0xFF222222);
        store.onWallpaperChanged();
        assertNull(get(HOME, LegibilityBand.DOCK));
        assertNull(store.peek(WORKSPACE, LegibilityBand.DOCK));
        assertTrue(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
        assertEquals(1, store.invalidations(LegibilityStore.Reason.WALLPAPER));
    }

    @Test
    public void aPaletteChangeMakesEveryCorrectionStale() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        store.onPaletteChanged();
        assertNull(store.peek(HOME, LegibilityBand.DOCK));
        assertEquals(1, store.invalidations(LegibilityStore.Reason.PALETTE));
    }

    @Test
    public void lightToDarkMakesEveryCorrectionStaleButTheSameSchemeDoesNot() {
        store.onSchemeChanged(false);
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        store.onSchemeChanged(false);
        assertNotNull(store.peek(HOME, LegibilityBand.DOCK));
        store.onSchemeChanged(true);
        assertNull(store.peek(HOME, LegibilityBand.DOCK));
        assertEquals(1, store.invalidations(LegibilityStore.Reason.SCHEME));
    }

    @Test
    public void theFirstSchemeReportIsNotAChange() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        store.onSchemeChanged(true);
        assertNotNull(store.peek(HOME, LegibilityBand.DOCK));
        assertEquals(0, store.invalidations(LegibilityStore.Reason.SCHEME));
    }

    @Test
    public void aMovedRestRectangleMakesThatCorrectionStale() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertNull(store.get(HOME, LegibilityBand.DOCK, 0, 1700, 1080, 2000, LOOK));
        assertTrue(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1700, 1080, 2000, LOOK));
        // The other bands and pages did not move.
        record(WORKSPACE, LegibilityBand.DOCK, 0xFF222222);
        assertNotNull(get(WORKSPACE, LegibilityBand.DOCK));
    }

    @Test
    public void aCrossingReadsTheLastMeasurementWhateverRectTheArrivingPageNowRestsAt() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertNotNull(store.peek(HOME, LegibilityBand.DOCK));
    }

    @Test
    public void aLookChangeMakesThatCorrectionStale() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertTrue(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK + 1));
        assertNull(store.get(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK + 1));
    }

    @Test
    public void nothingIsRemeasuredWhileNothingMoves() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        int before = store.measurements();
        for (int i = 0; i < 100; i++) {
            assertFalse(store.needsMeasure(HOME, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
            record(HOME, LegibilityBand.DOCK, 0xFF111111);
        }
        assertEquals(before, store.measurements());
    }

    // -- no measurement in a crossing

    @Test
    public void aCrossingMeasuresNothingAndAsksForNothing() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        store.beginCrossing();
        assertFalse(store.needsMeasure(WORKSPACE, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
        assertFalse(record(WORKSPACE, LegibilityBand.DOCK, 0xFF222222));
        assertNull(store.peek(WORKSPACE, LegibilityBand.DOCK));
        assertEquals(1, store.measuredInCrossing());
        store.endCrossing();
        assertTrue(store.needsMeasure(WORKSPACE, LegibilityBand.DOCK, 0, 1800, 1080, 2000, LOOK));
    }

    @Test
    public void aCrossingStillReadsWhatRestMeasured() {
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        record(WORKSPACE, LegibilityBand.DOCK, 0xFF222222);
        store.beginCrossing();
        assertNotNull(store.peek(HOME, LegibilityBand.DOCK));
        assertNotNull(store.peek(WORKSPACE, LegibilityBand.DOCK));
        assertEquals(0, store.measuredInCrossing());
    }

    @Test
    public void aSampleReadInACrossingIsCounted() {
        store.noteSample();
        assertEquals(0, store.sampledInCrossing());
        store.beginCrossing();
        store.noteSample();
        store.noteSample();
        assertEquals(2, store.sampledInCrossing());
    }

    @Test
    public void aMeasurementInACrossingLeavesAGuardLineWhenTheMotionLogIsOn() {
        Counters.enable(true, null);
        long before = Counters.countOf(LegibilityStore.GUARD_MEASURED_IN_CROSSING);
        store.beginCrossing();
        record(HOME, LegibilityBand.DOCK, 0xFF111111);
        assertEquals(before + 1, Counters.countOf(LegibilityStore.GUARD_MEASURED_IN_CROSSING));
    }

    // -- bounded

    @Test
    public void thePagesItKeepsAreBounded() {
        for (int id = 0; id < LegibilityStore.MAX_PAGES + 8; id++) {
            record(LegibilityStore.pageKey(0, id), LegibilityBand.DOCK, 0xFF000000 | id);
        }
        // The oldest are gone, the newest are kept.
        assertNull(store.peek(LegibilityStore.pageKey(0, 0), LegibilityBand.DOCK));
        assertNotNull(store.peek(LegibilityStore.pageKey(0, LegibilityStore.MAX_PAGES + 7),
            LegibilityBand.DOCK));
    }
}
