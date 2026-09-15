package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The memo behind the symbol-face coverage check: one probe per face and code point, bounded. */
public class SymbolGlyphCacheTest {

    /** Records every probe so a test can prove a second question cost nothing. */
    private static final class RecordingCoverage implements SymbolGlyphCache.Coverage {
        final List<String> probes = new ArrayList<>();
        int coveredFace = 0;

        @Override
        public boolean hasGlyph(int faceIndex, int codePoint) {
            probes.add(faceIndex + ":" + Integer.toHexString(codePoint));
            return faceIndex == coveredFace;
        }
    }

    @Test
    public void asksTheFaceOnceAndAnswersFromTheMemoAfterThat() {
        SymbolGlyphCache cache = new SymbolGlyphCache();
        RecordingCoverage coverage = new RecordingCoverage();

        assertTrue(cache.covers(0, 0xE1A0, coverage));
        assertTrue(cache.covers(0, 0xE1A0, coverage));
        assertFalse(cache.covers(1, 0xE1A0, coverage));
        assertFalse(cache.covers(1, 0xE1A0, coverage));

        assertEquals("[0:e1a0, 1:e1a0]", coverage.probes.toString());
        assertEquals(2, cache.size());
    }

    @Test
    public void aFullTableIsDroppedWholeRatherThanGrown() {
        SymbolGlyphCache cache = new SymbolGlyphCache(4);
        RecordingCoverage coverage = new RecordingCoverage();

        for (int codePoint = 0xE000; codePoint < 0xE010; codePoint++)
            cache.covers(0, codePoint, coverage);

        assertEquals(4, cache.capacity());
        assertTrue("the table must never outgrow its capacity", cache.size() <= 4);
        assertEquals(16, coverage.probes.size());
    }

    @Test
    public void aFaceBeyondTheKeyedRangeIsProbedEveryTimeRatherThanMisfiled() {
        SymbolGlyphCache cache = new SymbolGlyphCache();
        RecordingCoverage coverage = new RecordingCoverage();
        coverage.coveredFace = SymbolGlyphCache.MAX_FACES;

        assertTrue(cache.covers(SymbolGlyphCache.MAX_FACES, 0xE1A0, coverage));
        assertTrue(cache.covers(SymbolGlyphCache.MAX_FACES, 0xE1A0, coverage));

        assertEquals(2, coverage.probes.size());
        assertEquals(0, cache.size());
    }

    @Test
    public void twoCodePointsOfOneFaceAndOneCodePointOfTwoFacesStayApart() {
        SymbolGlyphCache cache = new SymbolGlyphCache();
        RecordingCoverage coverage = new RecordingCoverage();
        coverage.coveredFace = 1;

        assertFalse(cache.covers(0, 0x10FFFD, coverage));
        assertTrue(cache.covers(1, 0x10FFFD, coverage));
        assertFalse(cache.covers(0, 0x10FFFC, coverage));
        assertEquals(3, cache.size());
    }
}
