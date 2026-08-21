package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardColorSchemeFragmentTest {

    /** The 24 Material roles the swatch slots follow, in slot order. */
    private static final String[] EXPECTED_ROLE_IDS = {
        "surfaceContainerHigh", "primary", "secondary", "onSurface", "onSurfaceVariant",
        "secondaryContainer", "surface", "surfaceContainerHighest", "error", "tertiary",
        "primaryContainer", "onPrimary", "onSecondary", "onTertiary", "outlineVariant",
        "errorContainer", "surface (same as base06)", "surfaceContainer",
        "error + onSurface 20%", "tertiary + onSurface 20%", "primary + onSurface 20%",
        "secondary + onSurface 20%", "tertiary + primary 50%", "primary + secondary 50%"
    };

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    /** Stands in for the Material palette resolved from one wallpaper. */
    private static int[] palette() {
        int[] colors = new int[InAppKeyboardColorScheme.BASE24_COLOR_COUNT];
        for (int i = 0; i < colors.length; i++)
            colors[i] = 0xFF000000 | (0x010203 + i);
        return colors;
    }

    private static InAppKeyboardColorScheme scheme() {
        return InAppKeyboardColorScheme.fromJson(palette(), "");
    }

    /**
     * Persisted version-2 document as the removed Tinted importer wrote it: 16 pinned slots and
     * the imported-palette flag. Devices still hold these, so the editor must keep describing
     * them.
     */
    private static InAppKeyboardColorScheme importedScheme() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", InAppKeyboardColorScheme.SCHEMA_VERSION);
        root.put("base16Palette", true);
        root.put("importedThemeId", "");
        JSONArray swatches = new JSONArray();
        for (int i = 0; i < InAppKeyboardColorScheme.BASE24_COLOR_COUNT; i++)
            swatches.put(i < InAppKeyboardColorScheme.BASE16_COLOR_COUNT
                ? (Object) Integer.valueOf(0xFF0A0B00 + i) : JSONObject.NULL);
        root.put("swatches", swatches);
        return InAppKeyboardColorScheme.fromJson(palette(), root.toString());
    }

    @Test
    public void parsesOpaqueAndArgbHexColors() {
        assertEquals(Integer.valueOf(0xFF12ABEF),
            KeyboardColorSchemeFragment.parseHexColor("#12abef"));
        assertEquals(Integer.valueOf(0x8012ABEF),
            KeyboardColorSchemeFragment.parseHexColor("8012ABEF"));
    }

    @Test
    public void rejectsInvalidHexColors() {
        assertNull(KeyboardColorSchemeFragment.parseHexColor("#12345"));
        assertNull(KeyboardColorSchemeFragment.parseHexColor("#hello!"));
    }

    @Test
    public void namesEverySlotWithItsBaseIndex() {
        assertEquals("base00", KeyboardColorSchemeFragment.slotName(0));
        assertEquals("base0A", KeyboardColorSchemeFragment.slotName(10));
        assertEquals("base17", KeyboardColorSchemeFragment.slotName(23));
    }

    @Test
    public void mapsEverySlotToItsMaterialRole() {
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, EXPECTED_ROLE_IDS.length);
        for (int i = 0; i < EXPECTED_ROLE_IDS.length; i++)
            assertEquals("slot " + i, EXPECTED_ROLE_IDS[i],
                KeyboardColorSchemeFragment.slotRoleId(i));
        assertEquals("", KeyboardColorSchemeFragment.slotRoleId(
            InAppKeyboardColorScheme.BASE24_COLOR_COUNT));
        assertEquals("", KeyboardColorSchemeFragment.slotRoleId(-1));
    }

    @Test
    public void labelsEverySlotWithAShortRoleName() {
        for (int i = 0; i < InAppKeyboardColorScheme.BASE24_COLOR_COUNT; i++) {
            String label = KeyboardColorSchemeFragment.slotRoleLabel(context, i);
            assertFalse("slot " + i + " has no label", label.isEmpty());
            assertTrue("slot " + i + " label is too long for a chip: " + label,
                label.length() <= 18);
        }
        assertEquals("Surface high", KeyboardColorSchemeFragment.slotRoleLabel(context, 0));
        assertEquals("Primary", KeyboardColorSchemeFragment.slotRoleLabel(context, 1));
        // 06 and 10 are both colorSurface in the palette factory; both say so.
        assertEquals(KeyboardColorSchemeFragment.slotRoleLabel(context, 0x06),
            KeyboardColorSchemeFragment.slotRoleLabel(context, 0x10));
        assertEquals("", KeyboardColorSchemeFragment.slotRoleLabel(context,
            InAppKeyboardColorScheme.BASE24_COLOR_COUNT));
    }

    @Test
    public void describesSlotWithRoleAndPinnedState() {
        InAppKeyboardColorScheme scheme = scheme();
        assertEquals("base01, primary, follows theme",
            KeyboardColorSchemeFragment.slotDescription(context, scheme, 1));
        scheme.setSwatch(1, 0xFF445566);
        assertEquals("base01, primary, pinned",
            KeyboardColorSchemeFragment.slotDescription(context, scheme, 1));
    }

    @Test
    public void statusLineSaysColorsFollowTheThemeWhileFullyDynamic() {
        assertEquals("Every color follows your system theme, so the keyboard moves with your"
                + " wallpaper.",
            KeyboardColorSchemeFragment.statusText(context, scheme()));
    }

    @Test
    public void statusLineCountsPinnedSlots() {
        InAppKeyboardColorScheme scheme = scheme();
        scheme.setSwatch(0, 0xFF112233);
        scheme.setSwatch(5, 0xFF223344);
        assertEquals(2, KeyboardColorSchemeFragment.pinnedSwatchCount(scheme));
        assertEquals("2 of 24 colors are pinned. The rest follow your system theme and wallpaper.",
            KeyboardColorSchemeFragment.statusText(context, scheme));
    }

    @Test
    public void statusLineNamesAnImportedPalette() throws JSONException {
        InAppKeyboardColorScheme scheme = importedScheme();
        assertTrue(scheme.hasImportedPalette());
        assertEquals("An imported palette is in use, so no color follows your wallpaper.",
            KeyboardColorSchemeFragment.statusText(context, scheme));
        scheme.setImportedThemeId("base16-apathy");
        assertEquals("Imported palette base16-apathy is in use, so no color follows your"
                + " wallpaper.",
            KeyboardColorSchemeFragment.statusText(context, scheme));
    }

    @Test
    public void resetActionUnpinsEverySlotAndDropsTheImport() throws JSONException {
        InAppKeyboardColorScheme scheme = importedScheme();
        scheme.setImportedThemeId("base16-apathy");
        scheme.setSwatch(20, 0xFF778899);
        scheme.paint("q", InAppKeyboardColorScheme.Role.KEY_BACKGROUND, 3);

        KeyboardColorSchemeFragment.resetSchemeToTheme(scheme);

        assertTrue(scheme.isFullyDynamic());
        assertFalse(scheme.hasPinnedSwatches());
        assertFalse(scheme.hasImportedPalette());
        assertEquals(0, KeyboardColorSchemeFragment.pinnedSwatchCount(scheme));
        assertEquals(palette()[20], scheme.getSwatch(20));
        // Painted keys survive the reset: unpinAllSwatches() only touches the swatches.
        assertFalse(scheme.resolvedOverrides().isEmpty());
        assertEquals("Every color follows your system theme, so the keyboard moves with your"
                + " wallpaper.",
            KeyboardColorSchemeFragment.statusText(context, scheme));
    }
}
