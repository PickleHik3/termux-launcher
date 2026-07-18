package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

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
    }

    @Test
    public void importsCompleteBase16YamlAndAppliesSemanticPalette() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        StringBuilder yaml = new StringBuilder("scheme: Test\n");
        for (int i = 0; i < 16; i++)
            yaml.append(String.format("base%02X: \"%06x\"\n", i, 0x101010 + i));

        assertTrue(scheme.importBase16(yaml.toString()));
        Theme.Palette original = InAppKeyboardPaletteFactory.create(context, "system");
        Theme.Palette imported = scheme.applyToPalette(original);

        assertEquals(0xFF101010, imported.keyboardBackground);
        assertEquals(0xFF101011, imported.keyBackground);
        assertEquals(0xFF10101D, imported.activatedKeyBackground);
        assertEquals(0xFF101015, imported.labelColor);
        assertEquals(8, imported.indicatorColors.length);
    }

    @Test
    public void migratesLegacySixSwatchesWithoutChangingTheirIndexes() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            "{\"swatches\":[-16777216,-16777215,-16777214,-16777213,-16777212,-16777211],\"keys\":{}}");

        assertEquals(16, scheme.swatchCount());
        assertEquals(0xFF000002, scheme.getSwatch(2));
    }
}
