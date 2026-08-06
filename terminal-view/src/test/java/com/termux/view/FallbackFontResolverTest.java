package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FallbackFontResolverTest {

    /** A scripted stand-in for {@code Paint.hasGlyph}, counting how often it is asked. */
    private static final class ScriptedCoverage implements FallbackFontResolver.Coverage {
        private final int[] primaryCovers;
        private final int[][] chainCovers;
        int probes;

        ScriptedCoverage(int[] primaryCovers, int[][] chainCovers) {
            this.primaryCovers = primaryCovers;
            this.chainCovers = chainCovers;
        }

        @Override
        public boolean hasGlyph(int faceStyle, int faceIndex, int codePoint) {
            probes++;
            int[] covered = faceIndex == FallbackFontResolver.NO_OVERRIDE
                ? primaryCovers : chainCovers[faceIndex];
            for (int candidate : covered) if (candidate == codePoint) return true;
            return false;
        }
    }

    @Test
    public void aCodePointThePrimaryFaceHasIsNeverHandedToTheChain() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{'a'},
            new int[][]{{'a'}, {'a'}});
        FallbackFontResolver resolver = new FallbackFontResolver(2);
        assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(0, 'a', coverage));
        assertEquals("only the primary face is probed", 1, coverage.probes);
    }

    @Test
    public void theFirstFallbackWithTheGlyphWinsInConfiguredOrder() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{},
            new int[][]{{0x4E00}, {0x1F600, 0x4E00}, {0x1F600}});
        FallbackFontResolver resolver = new FallbackFontResolver(3);
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
        assertEquals("the second entry is the first one covering this code point",
            1, resolver.resolve(0, 0x1F600, coverage));
    }

    @Test
    public void aCodePointNobodyCoversFallsThroughToThePlatform() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{{'a'}, {'b'}});
        FallbackFontResolver resolver = new FallbackFontResolver(2);
        assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(0, 'z', coverage));
        assertEquals("the primary and both chain entries were asked once", 3, coverage.probes);
    }

    @Test
    public void anEmptyChainResolvesWithoutProbingAtAll() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{});
        FallbackFontResolver resolver = new FallbackFontResolver(0);
        assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(0, 0x4E00, coverage));
        assertEquals(0, coverage.probes);
        assertEquals(0, resolver.size());
    }

    @Test
    public void everyAnswerIsMemoizedIncludingTheNegativeOne() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{{0x4E00}});
        FallbackFontResolver resolver = new FallbackFontResolver(1);
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
        assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(0, 0x4E01, coverage));
        int probesAfterFirstPass = coverage.probes;
        for (int repeat = 0; repeat < 500; repeat++) {
            assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
            assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(0, 0x4E01, coverage));
        }
        assertEquals("no code point is probed twice", probesAfterFirstPass, coverage.probes);
        assertEquals(2, resolver.size());
    }

    @Test
    public void eachSgrFaceIsResolvedAndMemoizedSeparately() {
        FallbackFontResolver.Coverage coverage = new FallbackFontResolver.Coverage() {
            @Override
            public boolean hasGlyph(int faceStyle, int faceIndex, int codePoint) {
                // The bold face has the glyph itself; the regular one has to borrow it.
                if (faceIndex == FallbackFontResolver.NO_OVERRIDE) return faceStyle == 1;
                return faceIndex == 0;
            }
        };
        FallbackFontResolver resolver = new FallbackFontResolver(1);
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
        assertEquals(FallbackFontResolver.NO_OVERRIDE, resolver.resolve(1, 0x4E00, coverage));
        assertEquals(0, resolver.resolve(2, 0x4E00, coverage));
        assertEquals("one entry per face", 3, resolver.size());
    }

    @Test
    public void theMemoIsBoundedAndClearsWholesaleWhenItFills() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{{}});
        FallbackFontResolver resolver = new FallbackFontResolver(1, 16);
        int limit = 16 * FallbackFontResolver.LOAD_LIMIT_NUMERATOR
            / FallbackFontResolver.LOAD_LIMIT_DENOMINATOR;
        for (int codePoint = 0x3000; codePoint < 0x3000 + limit; codePoint++) {
            resolver.resolve(0, codePoint, coverage);
        }
        assertEquals("filled right up to the load limit", limit, resolver.size());
        resolver.resolve(0, 0x3000 + limit, coverage);
        assertEquals("the table was dropped and only the new entry kept", 1, resolver.size());
        for (int codePoint = 0x3000; codePoint <= 0x3000 + limit; codePoint++) {
            assertEquals(FallbackFontResolver.NO_OVERRIDE,
                resolver.resolve(0, codePoint, coverage));
        }
        assertTrue("the table never grows", resolver.size() <= resolver.capacity());
        assertEquals(16, resolver.capacity());
    }

    @Test
    public void aFullTableKeepsAnsweringCorrectlyAcrossManyClears() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{{0x4E00}});
        FallbackFontResolver resolver = new FallbackFontResolver(1, 16);
        for (int codePoint = 0x4E00; codePoint < 0x4E00 + 400; codePoint++) {
            int expected = codePoint == 0x4E00 ? 0 : FallbackFontResolver.NO_OVERRIDE;
            assertEquals(Integer.toHexString(codePoint), expected,
                resolver.resolve(0, codePoint, coverage));
            assertTrue(resolver.size() <= resolver.capacity());
        }
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
    }

    @Test
    public void clearForgetsEverythingWithoutChangingTheChain() {
        ScriptedCoverage coverage = new ScriptedCoverage(new int[]{}, new int[][]{{0x4E00}});
        FallbackFontResolver resolver = new FallbackFontResolver(1);
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
        resolver.clear();
        assertEquals(0, resolver.size());
        assertEquals(1, resolver.chainLength());
        assertEquals(0, resolver.resolve(0, 0x4E00, coverage));
    }
}
