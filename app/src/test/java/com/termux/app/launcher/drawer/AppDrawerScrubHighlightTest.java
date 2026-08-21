package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerScrubHighlight.Result;

import org.junit.Test;

/**
 * The scrub highlight.
 *
 * <p>The headline is the strength-0 identity. Three separate write paths apply this table — the
 * per-frame walk over attached children, the rule at the end of {@code onBindViewHolder}, and the
 * reset in {@code onViewRecycled} — and if the released state is anything other than exactly 1 and 1,
 * a cell goes back to the pool dimmed and comes out of it dim for the rest of the process.
 */
public class AppDrawerScrubHighlightTest {

    private static final float EPS = 1e-5f;

    /** No scrub in progress. */
    private static final char NONE = '\0';

    @Test
    public void strengthZeroIsTheIdentityForEveryLetter() {
        for (char letter : "ACZ#".toCharArray()) {
            for (char active : new char[] {NONE, 'A', 'C', '#'}) {
                Result result = AppDrawerScrubHighlight.resolve(letter, active, 0f);
                assertEquals("letter " + letter + " active " + active, 1f, result.alpha, 0f);
                assertEquals("letter " + letter + " active " + active, 1f, result.scale, 0f);
                assertEquals(1f, AppDrawerScrubHighlight.alphaFor(letter, active, 0f), 0f);
                assertEquals(1f, AppDrawerScrubHighlight.scaleFor(letter, active, 0f), 0f);
                // The common case is also the allocation-free one.
                assertSame(AppDrawerScrubHighlight.NEUTRAL, result);
            }
        }
        // A released scrub still holds a letter for the frame the spring reaches zero on.
        assertSame(AppDrawerScrubHighlight.NEUTRAL, AppDrawerScrubHighlight.resolve('M', 'M', 0f));
        assertSame(AppDrawerScrubHighlight.NEUTRAL, AppDrawerScrubHighlight.resolve('M', NONE, 1f));
    }

    @Test
    public void theMatchingLetterLiftsAndKeepsFullOpacity() {
        Result full = AppDrawerScrubHighlight.resolve('C', 'C', 1f);
        assertEquals(1f, full.alpha, 0f);
        assertEquals(AppDrawerScrubHighlight.MATCH_SCALE, full.scale, EPS);

        Result half = AppDrawerScrubHighlight.resolve('C', 'C', 0.5f);
        assertEquals(1f, half.alpha, 0f);
        assertEquals(1f + ((AppDrawerScrubHighlight.MATCH_SCALE - 1f) * 0.5f), half.scale, EPS);
        // The lift is small on purpose: this is setScaleX/Y on a bound cell, not a re-render.
        assertTrue(full.scale > 1f && full.scale < 1.1f);
    }

    @Test
    public void everyOtherLetterDimsAndDoesNotMove() {
        Result full = AppDrawerScrubHighlight.resolve('F', 'C', 1f);
        assertEquals(AppDrawerScrubHighlight.DIM_ALPHA, full.alpha, EPS);
        assertEquals(1f, full.scale, 0f);

        Result half = AppDrawerScrubHighlight.resolve('F', 'C', 0.5f);
        assertEquals(1f + ((AppDrawerScrubHighlight.DIM_ALPHA - 1f) * 0.5f), half.alpha, EPS);
        assertEquals(1f, half.scale, 0f);
        // Dim, not invisible: the grid must still read as a grid behind the highlighted run.
        assertTrue(full.alpha > 0.2f && full.alpha < 0.35f);
    }

    @Test
    public void strengthIsClampedAndTheLetterMatchIsCaseInsensitive() {
        // A spring overshooting past 1, and one dipping below 0 on the way home.
        assertEquals(AppDrawerScrubHighlight.DIM_ALPHA,
            AppDrawerScrubHighlight.resolve('F', 'C', 1.4f).alpha, EPS);
        assertEquals(AppDrawerScrubHighlight.MATCH_SCALE,
            AppDrawerScrubHighlight.resolve('C', 'C', 1.4f).scale, EPS);
        assertSame(AppDrawerScrubHighlight.NEUTRAL, AppDrawerScrubHighlight.resolve('F', 'C', -0.2f));

        // Letters arrive normalised from the provider, but a lower-case one must never make a
        // matching cell dim.
        assertEquals(1f, AppDrawerScrubHighlight.resolve('c', 'C', 1f).alpha, 0f);
        assertEquals(1f, AppDrawerScrubHighlight.resolve('C', 'c', 1f).alpha, 0f);
        assertEquals(AppDrawerScrubHighlight.MATCH_SCALE,
            AppDrawerScrubHighlight.resolve('#', '#', 1f).scale, EPS);
    }

    @Test
    public void theScalarPathTheFrameWalkUsesAgreesWithTheResultObject() {
        for (char letter : "ACF#".toCharArray()) {
            for (char active : new char[] {NONE, 'A', 'C', '#'}) {
                for (float strength : new float[] {0f, 0.25f, 0.5f, 1f}) {
                    Result result = AppDrawerScrubHighlight.resolve(letter, active, strength);
                    String message = letter + "/" + active + "@" + strength;
                    assertEquals(message, result.alpha,
                        AppDrawerScrubHighlight.alphaFor(letter, active, strength), 0f);
                    assertEquals(message, result.scale,
                        AppDrawerScrubHighlight.scaleFor(letter, active, strength), 0f);
                }
            }
        }
    }
}
