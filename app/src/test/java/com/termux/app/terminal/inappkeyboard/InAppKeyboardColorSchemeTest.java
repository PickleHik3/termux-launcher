package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;

import juloo.keyboard2.Keyboard2View;

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
}
