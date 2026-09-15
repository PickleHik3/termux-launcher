package com.termux.view;

import android.app.Application;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowPaint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    /**
     * A symbol face with a hole in its coverage, standing in for every Nerd Font build: their
     * cmaps skip whole stretches of the private-use area the app's own config maps to them.
     */
    @Implements(Paint.class)
    public static final class ShadowFaceWithAHole extends ShadowPaint {
        @RealObject private Paint mPaint;

        @Implementation
        protected boolean hasGlyph(String string) {
            return mPaint.getTypeface() != Typeface.SERIF;
        }
    }

    @Test
    @Config(shadows = ShadowFaceWithAHole.class)
    public void aMappedCodePointTheSymbolFaceCannotDrawGoesBackToTheNormalChain() {
        TerminalRenderer.SymbolMap hole = map(0xE000, 0xF8FF, Typeface.SERIF);
        TerminalRenderer renderer = renderer(hole);

        // The map still owns the range — that is what the user configured.
        assertSame(hole, renderer.symbolMapFor(0xE1A0));
        // It just cannot draw this one, so the cell is handed back rather than stamped with tofu.
        assertNull(renderer.symbolMapWithGlyphFor(0xE1A0));
    }

    @Test
    @Config(shadows = ShadowFaceWithAHole.class)
    public void aSymbolFaceThatHasTheGlyphStillClaimsTheCell() {
        TerminalRenderer.SymbolMap icons = map(0xE000, 0xF8FF, Typeface.MONOSPACE);
        TerminalRenderer renderer = renderer(icons);

        assertNotNull(renderer.symbolMapWithGlyphFor(0xE1A0));
        assertSame(icons, renderer.symbolMapWithGlyphFor(0xE1A0));
    }

    @Test
    @Config(shadows = ShadowFaceWithAHole.class)
    public void theFaceThatCanDrawTheCodePointWinsOverTheOneThatCannot() {
        TerminalRenderer.SymbolMap hole = map(0xE000, 0xF8FF, Typeface.SERIF);
        TerminalRenderer.SymbolMap covering = map(0xE1A0, 0xE1B6, Typeface.MONOSPACE);
        TerminalRenderer renderer = renderer(hole, covering);

        assertSame(covering, renderer.symbolMapWithGlyphFor(0xE1A0));
        // Outside the covering map's range the wider one is still the only candidate, and still
        // cannot draw, so those cells fall through.
        assertNull(renderer.symbolMapWithGlyphFor(0xE200));
    }

    /**
     * The phone's real stack: a kitty.conf icon map read first, then the app's own drop-in mapping
     * the whole private-use area to the bundled symbols face. The drop-in wins the range and has
     * no glyph in it, so the earlier map has to get the cell rather than the generic fallback.
     */
    @Test
    @Config(shadows = ShadowFaceWithAHole.class)
    public void anEarlierMapDrawsWhatTheLaterOneThatOutranksItCannot() {
        TerminalRenderer.SymbolMap icons = map(0xE1A0, 0xE1B6, Typeface.MONOSPACE);
        TerminalRenderer.SymbolMap managed = map(0xE000, 0xF8FF, Typeface.SERIF);
        TerminalRenderer renderer = renderer(icons, managed);

        // The later map still owns the range — that is the precedence the user configured.
        assertSame(managed, renderer.symbolMapFor(0xE1A0));
        assertSame(icons, renderer.symbolMapWithGlyphFor(0xE1A0));
        assertSame(icons, renderer.symbolMapWithGlyphFor(0xE1B6));
        // Outside the earlier map nothing can draw, so the cell goes to the generic fallback.
        assertNull(renderer.symbolMapWithGlyphFor(0xE19F));
        assertNull(renderer.symbolMapWithGlyphFor(0xF8FF));
    }

    @Test
    @Config(shadows = ShadowFaceWithAHole.class)
    public void theLaterMapStillWinsEveryCodePointItCanActuallyDraw() {
        TerminalRenderer.SymbolMap icons = map(0xE1A0, 0xE1B6, Typeface.SANS_SERIF);
        TerminalRenderer.SymbolMap managed = map(0xE000, 0xF8FF, Typeface.MONOSPACE);
        TerminalRenderer renderer = renderer(icons, managed);

        assertSame(managed, renderer.symbolMapWithGlyphFor(0xE1A0));
        assertSame(managed, renderer.symbolMapWithGlyphFor(0xE500));
    }
}
