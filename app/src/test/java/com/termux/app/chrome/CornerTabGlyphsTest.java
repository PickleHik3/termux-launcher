package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.font.NerdFontSpans;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The corner tabs' marks. A glyph nobody can see is a tofu box on the home screen, so each one is
 * pinned to its code point and checked to be in the blocks the bundled symbols face covers — a
 * typo in a Private Use Area escape is otherwise invisible until it ships.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CornerTabGlyphsTest {

    @Test
    public void everyGlyphIsOneNerdFontCodePoint() {
        assertGlyph(CornerTabGlyphs.SETTINGS, 0xF013);
        assertGlyph(CornerTabGlyphs.EDIT, 0xF040);
        assertGlyph(CornerTabGlyphs.POWER, 0xF011);
        // nf-md-palette: the same mark the Appearance editor wears, so the button matches what it opens.
        assertGlyph(CornerTabGlyphs.APPEARANCE, 0xF03D8);
        // nf-md-view_dashboard, in the plane-15 block the Material Design icons sit in.
        assertGlyph(CornerTabGlyphs.LAYOUT, 0xF056E);
    }

    /** Layout and Appearance are supplementary code points, so two chars each — and never cut in half. */
    @Test
    public void theLayoutGlyphIsASurrogatePair() {
        assertEquals(2, CornerTabGlyphs.LAYOUT.length());
        assertEquals(1, CornerTabGlyphs.LAYOUT.codePointCount(0, CornerTabGlyphs.LAYOUT.length()));
        assertEquals(2, CornerTabGlyphs.APPEARANCE.length());
        assertEquals(1, CornerTabGlyphs.APPEARANCE.codePointCount(0, CornerTabGlyphs.APPEARANCE.length()));
    }

    @Test
    public void helpIsTheQuestionMarkItself() {
        assertEquals("?", CornerTabGlyphs.help(RuntimeEnvironment.getApplication()));
    }

    private static void assertGlyph(String glyph, int codePoint) {
        assertEquals("one code point per glyph", 1, glyph.codePointCount(0, glyph.length()));
        assertEquals(codePoint, glyph.codePointAt(0));
        assertTrue("U+" + Integer.toHexString(codePoint) + " is not a Nerd Font symbol",
            NerdFontSpans.isNerdSymbol(codePoint));
    }
}
