package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Paint;
import android.graphics.Typeface;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;

/** Typeface resolution, including the native font.ttf path that must survive every addition. */
@RunWith(RobolectricTestRunner.class)
public class TerminalFontLoaderTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void noFontsConfAndNoDropInsStillUsesTheNativeFontTtfChain() {
        TerminalFontConfig.Result config = TerminalFontConfig.load(
            new File(temporary.getRoot(), TerminalFontConfig.DROP_IN_DIR_NAME),
            new File(temporary.getRoot(), TerminalFontConfig.FILE_NAME));
        assertFalse(config.filePresent);
        assertTrue(config.errors.toString(), config.errors.isEmpty());

        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);

        assertTrue(faces.errors.toString(), faces.errors.isEmpty());
        assertNull(faces.bold);
        assertNull(faces.italic);
        assertNull(faces.boldItalic);
        assertEquals(0, faces.symbolMaps.length);
        assertTrue(faces.fallbackFonts.isEmpty());
        // ~/.termux/font.ttf is absent off device, so the chain must end at Android monospace.
        if (!TermuxConstants.TERMUX_FONT_FILE.isFile())
            assertSame("no config must fall back to font.ttf then Android monospace",
                Typeface.MONOSPACE, faces.regular);
    }

    @Test
    public void resolvesTheFallbackChainInOrderAndDropsBrokenEntries() {
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "fallback_font family=monospace\n"
                + "fallback_font path=/nonexistent/fallback.ttf\n"
                + "fallback_font family=serif\n", true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());

        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);

        assertEquals(2, faces.fallbackFonts.size());
        assertEquals(1, faces.errors.size());
        assertEquals("fallback_font path=/nonexistent/fallback.ttf:"
            + " font path is not a readable non-empty file", faces.errors.get(0));
    }

    /**
     * A face that honours every axis, standing in for a variable font: Robolectric's bundled
     * families are all static, so nothing on the test classpath can accept {@code wght} for real.
     */
    @Implements(Paint.class)
    public static final class ShadowVariableFacePaint {
        @Implementation
        protected boolean setFontVariationSettings(String settings) {
            return settings != null && !settings.isEmpty();
        }
    }

    @Test
    @Config(shadows = ShadowVariableFacePaint.class)
    public void namedSymbolMapsReachTheRendererCarryingTheirOwnFeaturesAndVariations() {
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "symbol_map name=nerd U+E000-U+E0FF family=monospace\n"
                + "symbol_map U+2500-U+257F family=monospace\n"
                + "font_features nerd +ss01\n"
                + "font_variations nerd wght=600\n"
                + "font_features symbols +liga\n"
                + "font_variations symbols wght=500\n", true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());

        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);

        assertTrue(faces.errors.toString(), faces.errors.isEmpty());
        assertEquals(2, faces.symbolMaps.length);
        assertEquals("'ss01' 1", faces.symbolMaps[0].features);
        assertEquals("'wght' 600", faces.symbolMaps[0].variations);
        // The unnamed map declares nothing of its own, so it draws with the shared symbols target.
        assertEquals("'liga' 1", faces.symbolMaps[1].features);
        assertEquals("'wght' 500", faces.symbolMaps[1].variations);
    }

    @Test
    @Config(shadows = ShadowVariableFacePaint.class)
    public void anUnnamedSymbolMapCarriesTheSharedSymbolsSettings() {
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "symbol_map U+E000-U+E0FF family=monospace\n"
                + "font_features symbols +liga\n"
                + "font_variations symbols wght=500\n", true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());

        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);

        assertEquals(1, faces.symbolMaps.length);
        assertEquals("'liga' 1", faces.symbolMaps[0].features);
        assertEquals("'wght' 500", faces.symbolMaps[0].variations);
    }

    @Test
    public void axesASymbolFontCannotHonourAreReportedOnceAndDroppedPerMap() {
        // The stand-in face carries no axes at all, so both the named map's own setting and the
        // shared one an unnamed map inherits have to be dropped instead of reaching Android raw.
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "symbol_map name=nerd U+E000-U+E0FF family=monospace\n"
                + "symbol_map U+2500-U+257F family=monospace\n"
                + "font_variations nerd wght=600\n"
                + "font_variations symbols wght=500\n", true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());

        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);

        assertEquals(faces.errors.toString(), 2, faces.errors.size());
        assertTrue(faces.errors.toString(),
            faces.errors.contains("font_variations nerd: Android rejected the requested axes"));
        // The inherited setting is reported under the target the user actually wrote, once, even
        // though both the map and the shared symbols slot are checked against the same face.
        assertTrue(faces.errors.toString(),
            faces.errors.contains("font_variations symbols: Android rejected the requested axes"));
        assertNull(faces.symbolMaps[0].variations);
        assertNull(faces.symbolMaps[1].variations);
    }
}
