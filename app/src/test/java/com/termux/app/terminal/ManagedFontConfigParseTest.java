package com.termux.app.terminal;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.fonts.FontCatalog;
import com.termux.app.fonts.FontInstaller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Round trip: whatever {@link FontInstaller} writes into {@code ~/.termux/fonts.d} has to parse
 * cleanly through the real config parser. A managed file the app itself generates but the loader
 * rejects would break the terminal font for every user at once, silently, at app start.
 */
@RunWith(RobolectricTestRunner.class)
public class ManagedFontConfigParseTest {

    @Test
    public void everyBundledFamilyGeneratesAConfigTheParserAccepts() {
        Context context = ApplicationProvider.getApplicationContext();
        FontCatalog.Result catalog = FontCatalog.load(context);
        FontCatalog.SymbolFont symbols = catalog.symbolFont;
        assertNotNull(symbols);
        assertTrue(catalog.families.size() > 0);

        for (FontCatalog.Family family : catalog.families) {
            for (FontInstaller.Options options : optionMatrix(family)) {
                String text = FontInstaller.buildManagedConfig(family, options, symbols.installName);
                TerminalFontConfig.Result parsed = TerminalFontConfig.parse(text, true);
                assertEquals(family.id + " " + options.ligatures + " icons=" + options.nerdIcons
                    + " weight=" + options.weight + ": " + parsed.errors, 0, parsed.errors.size());

                // The parser has to see exactly the faces the family declares, and nothing else.
                assertEquals(family.id, family.faces.size(), parsed.faces.size());
                for (FontCatalog.FaceSlot slot : family.faces.keySet()) {
                    TerminalFontConfig.FaceSpec face = parsed.face(faceOf(slot));
                    assertNotNull(family.id + "/" + slot.key, face);
                    assertEquals(TerminalFontConfig.SourceType.PATH, face.type);
                    assertTrue(face.value, face.value.endsWith("/" + slot.fileName));
                }
                // Both private-use planes, matching the shipped fonts.conf example: BMP PUA plus
                // SPUA-A for the Material Design set.
                assertEquals(family.id, options.nerdIcons ? 2 : 0, parsed.symbolMaps.size());
                if (options.nerdIcons) {
                    assertRange(parsed.symbolMaps.get(0), 0xE000, 0xF8FF, symbols.installName);
                    assertRange(parsed.symbolMaps.get(1), 0xF0000, 0xFFFFD, symbols.installName);
                }
                assertEquals(family.id, policyOf(options.ligatures), parsed.ligaturePolicy);
                // A static family must never be handed an axis it cannot honour.
                assertEquals(family.id, family.weightAxis == null ? 0 : family.faces.size(),
                    parsed.fontVariations.size());
            }
        }
    }

    /** The toggle combinations that actually change the generated text. */
    private static FontInstaller.Options[] optionMatrix(FontCatalog.Family family) {
        return new FontInstaller.Options[] {
            FontInstaller.Options.recommendedFor(family),
            FontInstaller.Options.recommendedFor(family).withNerdIcons(false),
            FontInstaller.Options.recommendedFor(family)
                .withLigatures(FontInstaller.LIGATURES_ALWAYS).withRecommendedFeatures(false),
            FontInstaller.Options.recommendedFor(family)
                .withLigatures(FontInstaller.LIGATURES_NEVER)
                .withWeight(family.weightAxis == null ? 0 : family.weightAxis.min),
        };
    }

    /** One symbol map has to cover exactly one range and point at the extracted symbols face. */
    private static void assertRange(TerminalFontConfig.SymbolMapSpec map, int first, int last,
                                    String symbolsFileName) {
        assertEquals(1, map.ranges.size());
        assertEquals(first, map.ranges.get(0).first);
        assertEquals(last, map.ranges.get(0).last);
        assertEquals(TerminalFontConfig.SourceType.PATH, map.font.type);
        assertTrue(map.font.value, map.font.value.endsWith("/symbols/" + symbolsFileName));
    }

    private static TerminalFontConfig.Face faceOf(FontCatalog.FaceSlot slot) {
        switch (slot) {
            case BOLD: return TerminalFontConfig.Face.BOLD;
            case ITALIC: return TerminalFontConfig.Face.ITALIC;
            case BOLD_ITALIC: return TerminalFontConfig.Face.BOLD_ITALIC;
            default: return TerminalFontConfig.Face.REGULAR;
        }
    }

    private static TerminalFontConfig.LigaturePolicy policyOf(String policy) {
        if (FontInstaller.LIGATURES_CURSOR.equals(policy)) {
            return TerminalFontConfig.LigaturePolicy.CURSOR;
        }
        if (FontInstaller.LIGATURES_ALWAYS.equals(policy)) {
            return TerminalFontConfig.LigaturePolicy.ALWAYS;
        }
        return TerminalFontConfig.LigaturePolicy.NEVER;
    }
}
