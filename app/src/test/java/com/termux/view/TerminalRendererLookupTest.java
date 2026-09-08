package com.termux.view;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The two lookups the render loop runs per run and per cell. Both are consulted for every cell of
 * every visible row, so what is checked here is that they reach the same answer as the exhaustive
 * form they replaced — the shaping they feed is not observable from a JVM test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalRendererLookupTest {

    private static TerminalRenderer renderer(TerminalRenderer.SymbolMap... maps) {
        return new TerminalRenderer(24, Typeface.MONOSPACE, null, null, null, maps, null, null,
            null, null, null, null, null, null);
    }

    private static TerminalRenderer.SymbolMap map(int first, int last, Typeface face) {
        return new TerminalRenderer.SymbolMap(first, last, face);
    }

    @Test
    public void oneFaceWithTwoAxisSetsIsTwoInstancesAndOneRepeatedLookupIsOne() {
        TerminalRenderer.VariationCache cache = new TerminalRenderer.VariationCache();

        cache.put(Typeface.MONOSPACE, "'wght' 600", Typeface.DEFAULT_BOLD);
        cache.put(Typeface.MONOSPACE, "'wght' 300", Typeface.SANS_SERIF);

        assertEquals("one base face with two axis sets must be two instances", 2, cache.size());
        assertEquals("both belong to the one face", 1, cache.faceCount());
        assertSame(Typeface.DEFAULT_BOLD, cache.get(Typeface.MONOSPACE, "'wght' 600"));
        cache.put(Typeface.MONOSPACE, "'wght' 600", Typeface.DEFAULT_BOLD);
        assertEquals("a repeated lookup must hit the instance already built", 2, cache.size());
    }

    @Test
    public void twoFacesWithOneAxisSetAreTwoInstancesAndDoNotSeeEachOther() {
        TerminalRenderer.VariationCache cache = new TerminalRenderer.VariationCache();

        cache.put(Typeface.MONOSPACE, "'wght' 600", Typeface.DEFAULT_BOLD);
        cache.put(Typeface.SERIF, "'wght' 600", Typeface.SANS_SERIF);

        assertEquals(2, cache.size());
        assertEquals(2, cache.faceCount());
        assertSame(Typeface.DEFAULT_BOLD, cache.get(Typeface.MONOSPACE, "'wght' 600"));
        assertSame(Typeface.SANS_SERIF, cache.get(Typeface.SERIF, "'wght' 600"));
        assertNull(cache.get(Typeface.DEFAULT, "'wght' 600"));
    }

    @Test
    public void aCodePointOutsideEveryConfiguredRangeMatchesNothing() {
        TerminalRenderer renderer = renderer(map(0xE000, 0xF8FF, Typeface.SERIF));

        assertNull(renderer.symbolMapFor('a'));
        assertNull(renderer.symbolMapFor(0xDFFF));
        assertNull(renderer.symbolMapFor(0xF900));
    }

    @Test
    public void aRendererWithNoMapsMatchesNothing() {
        TerminalRenderer renderer = renderer();

        assertNull(renderer.symbolMapFor('a'));
        assertNull(renderer.symbolMapFor(0xE000));
    }

    @Test
    public void aLaterOverlappingRangeStillWinsAfterTheEarlierOneWasMatched() {
        TerminalRenderer.SymbolMap wide = map(0xE000, 0xF8FF, Typeface.SERIF);
        TerminalRenderer.SymbolMap narrow = map(0xE100, 0xE1FF, Typeface.SANS_SERIF);
        TerminalRenderer renderer = renderer(wide, narrow);

        // Matching the overlapped map first must not let it answer for a cell the later map owns.
        assertSame(wide, renderer.symbolMapFor(0xE000));
        assertSame(narrow, renderer.symbolMapFor(0xE100));
        assertSame(wide, renderer.symbolMapFor(0xE000));
        assertSame(narrow, renderer.symbolMapFor(0xE1FF));
        assertSame(wide, renderer.symbolMapFor(0xE200));
    }

    @Test
    public void repeatedCellsInOneNonOverlappedRangeKeepMatchingIt() {
        TerminalRenderer.SymbolMap icons = map(0xE000, 0xE0FF, Typeface.SERIF);
        TerminalRenderer.SymbolMap emoji = map(0x1F300, 0x1F5FF, Typeface.SANS_SERIF);
        TerminalRenderer renderer = renderer(icons, emoji);

        assertSame(icons, renderer.symbolMapFor(0xE000));
        assertSame(icons, renderer.symbolMapFor(0xE0FF));
        assertSame(emoji, renderer.symbolMapFor(0x1F300));
        assertSame(icons, renderer.symbolMapFor(0xE050));
        assertNull(renderer.symbolMapFor('x'));
        assertNull(renderer.symbolMapFor(0xE100));
    }
}
