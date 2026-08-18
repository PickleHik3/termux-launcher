package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.Theme;

@RunWith(RobolectricTestRunner.class)
public class InAppKeyboardColorSchemeTest {

    @Test
    public void assignmentsAndEditedSwatchesRoundTrip() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        scheme.setSwatch(2, 0xFF123456);
        scheme.paint("1:3", InAppKeyboardColorScheme.Role.SECONDARY, 2);
        scheme.paint("1:3", InAppKeyboardColorScheme.Role.SECONDARY_BOTTOM, 1);
        scheme.paint("1:3", InAppKeyboardColorScheme.Role.KEY_BORDER, 0);

        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(
            context, scheme.toJson());
        Map<String, Keyboard2View.KeyColorOverride> overrides = restored.resolvedOverrides();

        assertEquals(0xFF123456, restored.getSwatch(2));
        assertEquals(Integer.valueOf(0xFF123456), overrides.get("1:3").secondaryLabel);
        assertEquals(Integer.valueOf(restored.getSwatch(1)),
            overrides.get("1:3").secondaryBottomLabel);
        assertEquals(Integer.valueOf(restored.getSwatch(0)),
            overrides.get("1:3").borderColor);
        assertNull(overrides.get("1:3").primaryLabel);
    }

    @Test
    public void malformedJsonFallsBackToMaterialDefaults() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(
            context, "{definitely-not-json");

        assertEquals(InAppKeyboardPaletteFactory.defaultEditorSwatches(context).length,
            scheme.swatchCount());
        assertEquals(0, scheme.resolvedOverrides().size());
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, scheme.swatchCount());
        assertTrue(scheme.isFullyDynamic());
    }

    @Test
    public void contextSchemeStartsFromLiveMaterialRolesAndStaysDynamic() {
        Context context = ApplicationProvider.getApplicationContext();
        int[] material = InAppKeyboardPaletteFactory.defaultEditorSwatches(context);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");

        for (int i = 0; i < material.length; i++) {
            assertEquals(material[i], scheme.getSwatch(i));
            assertFalse(scheme.isSwatchPinned(i));
        }
        assertFalse(scheme.refreshDynamicSwatches(context));
    }

    /**
     * Persisted version-2 document as the removed Tinted importer wrote it: {@code colorCount}
     * pinned slots and the imported-palette flag. Devices still hold these, so they must keep
     * loading and rendering.
     */
    private static String importedSchemeJson(int colorCount, int baseColor, String themeId)
        throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", InAppKeyboardColorScheme.SCHEMA_VERSION);
        root.put("base16Palette", true);
        root.put("importedThemeId", themeId);
        JSONArray swatches = new JSONArray();
        for (int i = 0; i < InAppKeyboardColorScheme.BASE24_COLOR_COUNT; i++)
            swatches.put(i < colorCount ? (Object) Integer.valueOf(0xFF000000 | (baseColor + i))
                : JSONObject.NULL);
        root.put("swatches", swatches);
        return root.toString();
    }

    @Test
    public void persistedImportedBase16PaletteStillAppliesSemanticPalette()
        throws JSONException {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            importedSchemeJson(InAppKeyboardColorScheme.BASE16_COLOR_COUNT, 0x101010, ""));

        Theme.Palette original = InAppKeyboardPaletteFactory.create(context, "system");
        Theme.Palette imported = scheme.applyToPalette(original);

        assertEquals(0xFF101010, imported.keyboardBackground);
        assertEquals(0xFF101011, imported.keyBackground);
        assertEquals(0xFF10101D, imported.activatedKeyBackground);
        assertEquals(0xFF101015, imported.labelColor);
        assertEquals(8, imported.indicatorColors.length);
    }

    @Test
    public void persistedImportedBase24PaletteRoundTripsWithoutDroppingExtendedColors()
        throws JSONException {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            importedSchemeJson(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, 0x202020,
                "base24-test"));

        assertTrue(scheme.hasImportedPalette());
        assertEquals(0xFF202037, scheme.getSwatch(23));
        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(context,
            scheme.toJson());
        assertEquals("base24-test", restored.getImportedThemeId());
        assertTrue(restored.shouldApplyImportedPalette("custom"));
    }

    @Test
    public void keyboardBackgroundSwatchRoundTripsAndResolves() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        assertNull(scheme.resolvedKeyboardBackground());
        assertEquals(-1, scheme.getKeyboardBackgroundSwatch());

        scheme.setKeyboardBackgroundSwatch(3);
        scheme.setSwatch(3, 0xFF123456);
        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(context,
            scheme.toJson());
        assertEquals(3, restored.getKeyboardBackgroundSwatch());
        assertEquals(Integer.valueOf(0xFF123456), restored.resolvedKeyboardBackground());

        restored.clearKeyboardBackgroundSwatch();
        assertNull(restored.resolvedKeyboardBackground());
        // Clearing drops the field, so the persisted form is what a pre-field document had.
        assertEquals(-1, InAppKeyboardColorScheme.fromJson(context, restored.toJson())
            .getKeyboardBackgroundSwatch());
    }

    @Test
    public void keyboardBackgroundIgnoresOutOfRangeAndJunkValues() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        scheme.setKeyboardBackgroundSwatch(-2);
        scheme.setKeyboardBackgroundSwatch(scheme.swatchCount());
        assertEquals(-1, scheme.getKeyboardBackgroundSwatch());

        InAppKeyboardColorScheme junk = InAppKeyboardColorScheme.fromJson(context,
            "{\"schemaVersion\":2,\"keyboardBg\":99}");
        assertEquals(-1, junk.getKeyboardBackgroundSwatch());
        assertNull(junk.resolvedKeyboardBackground());
    }

    @Test
    public void migratesLegacySixSwatchesAsDynamicSlots() {
        Context context = ApplicationProvider.getApplicationContext();
        int[] material = InAppKeyboardPaletteFactory.defaultEditorSwatches(context);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            "{\"swatches\":[-16777216,-16777215,-16777214,-16777213,-16777212,-16777211],\"keys\":{}}");

        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, scheme.swatchCount());
        assertTrue(scheme.isFullyDynamic());
        assertEquals(material[2], scheme.getSwatch(2));
    }
}
