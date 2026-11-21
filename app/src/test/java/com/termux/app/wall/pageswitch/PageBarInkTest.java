package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.OnGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PageBarInkTest {

    /** The solid card stays the key's pop-up's fallback where its raised glass cannot be worn. */
    @Test public void theKeysPopUpLaysADarkCardUnderPaleInkAndAPaleOneUnderDarkInk() {
        assertEquals(0xFF1F1F1F, PageBarGlass.cardFor(0xFFF2F0E4));
        assertEquals(0xFFF2F2F2, PageBarGlass.cardFor(0xFF1F2A2D));
    }

    /** On raised glass the glyphs are solved on the glass's ground, light or dark, by PageMarkInk. */
    @Test public void onRaisedGlassTheGlyphsReadOnItsGroundInEveryTheme() {
        int paleInk = 0xFFF2F0E4;
        int darkInk = 0xFF1F2A2D;
        int accent = 0xFFFFB59C;
        // Dark glass (dark, black, Material You dark) and pale glass (light) at 74 % over dark,
        // mid and pale wallpapers, and with no wallpaper sampled.
        int[] tints = {0xBD161822, 0xBD000000, 0xBDFFFFFF, 0xBDE8E0D8};
        int[] wallpapers = {0xFF101010, 0xFF808080, 0xFFF0F0F0, 0x00000000};
        for (int fixed : new int[] {paleInk, darkInk}) {
            for (int tint : tints) {
                for (int wallpaper : wallpapers) {
                    int ground = PageBarGlass.raisedGround(tint, wallpaper, fixed);
                    assertEquals(0xFF, ground >>> 24);
                    PageBarGlass.Ink ink = new PageBarGlass.Ink();
                    PageBarGlass.raisedInk(ground, fixed, accent, ink);
                    String at = Integer.toHexString(fixed) + " on " + Integer.toHexString(tint)
                        + "/" + Integer.toHexString(wallpaper);
                    assertEquals(at, ground, ink.ground);
                    assertTrue(at, OnGlass.ratio(ink.ink, ground) >= OnGlass.TARGET_LARGE_TEXT);
                    assertTrue(at, OnGlass.ratio(ink.other, ground)
                        >= OnGlass.TARGET_LARGE_TEXT - 0.01);
                    assertTrue(at, OnGlass.ratio(ink.accent, ground)
                        >= OnGlass.TARGET_LARGE_TEXT - 0.01);
                }
            }
        }
    }

    @Test public void withNoWallpaperSampledTheGroundIsTheTintOverTheInksCard() {
        int tint = 0xBD161822;
        assertEquals(OnGlass.opaque(OnGlass.composite(tint, PageBarGlass.cardFor(0xFFF2F0E4))),
            PageBarGlass.raisedGround(tint, 0, 0xFFF2F0E4));
    }

    /** Home's well: the measured ground under the well's shade, the marks solved on it, the side kept. */
    @Test public void theHomeWellSinksTheGroundAndSolvesTheMarksOnIt() {
        int accent = 0xFFFFB59C;
        int[][] cases = {{0xFF2A2C34, 0xFFF2F0E4}, {0xFFE8E4DC, 0xFF1F2A2D}};
        for (int[] c : cases) {
            PageBarGlass.Ink ink = new PageBarGlass.Ink();
            ink.ground = c[0];
            ink.ink = c[1];
            PageBarGlass.sink(ink, accent);
            boolean dark = c[1] == 0xFFF2F0E4;
            String at = Integer.toHexString(c[0]);
            assertEquals(at, dark, ink.wellDark);
            assertEquals(at, com.termux.app.chrome.InsetGlass.groundIn(c[0], dark), ink.ground);
            assertEquals(at, c[1], ink.ink);
            assertTrue(at, OnGlass.ratio(ink.ink, ink.ground) >= OnGlass.TARGET_LARGE_TEXT);
            assertEquals(at, com.termux.app.chrome.PageMarkInk.other(ink.ground, ink.ink, ink.accent),
                ink.other);
        }
        // Nothing measured: nothing to sink.
        PageBarGlass.Ink none = new PageBarGlass.Ink();
        PageBarGlass.sink(none, accent);
        assertEquals(0, none.ground);
    }
}
