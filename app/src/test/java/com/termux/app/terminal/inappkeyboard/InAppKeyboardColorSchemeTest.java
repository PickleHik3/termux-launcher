package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void importsCompleteBase24PaletteWithoutDroppingExtendedColors() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        StringBuilder yaml = new StringBuilder("system: base24\npalette:\n");
        for (int i = 0; i < 24; i++)
            yaml.append(String.format("  base%02X: \"%06x\"\n", i, 0x202020 + i));

        assertTrue(scheme.importBasePalette(yaml.toString(),
            InAppKeyboardColorScheme.BASE24_COLOR_COUNT));
        scheme.setImportedThemeId("base24-test");
        assertEquals(0xFF202037, scheme.getSwatch(23));
        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(context,
            scheme.toJson());
        assertEquals("base24-test", restored.getImportedThemeId());
        assertTrue(restored.shouldApplyImportedPalette("custom"));
    }

    @Test
    public void importsTinted8NamedPalette() {
        Context context = ApplicationProvider.getApplicationContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context, "");
        String yaml = "palette:\n" +
            "  black: '#101010'\n  white: '#f0f0f0'\n  red: '#ee1111'\n" +
            "  yellow: '#eeee11'\n  green: '#11ee11'\n  cyan: '#11eeee'\n" +
            "  blue: '#1111ee'\n  magenta: '#ee11ee'\n  orange: '#ee8811'\n";

        assertTrue(scheme.importTinted8(yaml));
        assertEquals(0xFF101010, scheme.getSwatch(0));
        assertEquals(0xFFEE8811, scheme.getSwatch(9));
        assertEquals(0xFF1111EE, scheme.getSwatch(13));
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
