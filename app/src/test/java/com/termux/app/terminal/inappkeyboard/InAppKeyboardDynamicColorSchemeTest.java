package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import juloo.keyboard2.Keyboard2View;

/**
 * Pure JVM coverage of the dynamic/pinned swatch model. Every case goes through the
 * {@code int[]} Material-defaults overloads, so no Android framework is involved.
 */
public class InAppKeyboardDynamicColorSchemeTest {

    /** Stands in for the Material palette of one wallpaper. */
    private static int[] palette(int base) {
        int[] colors = new int[InAppKeyboardColorScheme.BASE24_COLOR_COUNT];
        for (int i = 0; i < colors.length; i++)
            colors[i] = 0xFF000000 | (base + i);
        return colors;
    }

    private static final int[] WALLPAPER_A = palette(0x110000);
    private static final int[] WALLPAPER_B = palette(0x660000);

    @Test
    public void freshSchemeIsFullyDynamic() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");

        assertTrue(scheme.isFullyDynamic());
        assertFalse(scheme.hasPinnedSwatches());
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, scheme.swatchCount());
        for (int i = 0; i < scheme.swatchCount(); i++) {
            assertFalse("slot " + i, scheme.isSwatchPinned(i));
            assertEquals(WALLPAPER_A[i], scheme.getSwatch(i));
        }
    }

    @Test
    public void hexPinMarksOnlyTheEditedSlot() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.pinSwatch(5, 0xFF123456);

        assertTrue(scheme.isSwatchPinned(5));
        assertFalse(scheme.isSwatchPinned(4));
        assertFalse(scheme.isSwatchPinned(6));
        assertTrue(scheme.hasPinnedSwatches());
        assertFalse(scheme.isFullyDynamic());
        assertEquals(0xFF123456, scheme.getSwatch(5));
        assertEquals(WALLPAPER_A[4], scheme.getSwatch(4));
    }

    @Test
    public void legacySetSwatchStillPinsTheSlot() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.setSwatch(3, 0xFFABCDEF);

        assertTrue(scheme.isSwatchPinned(3));
        assertEquals(0xFFABCDEF, scheme.getSwatch(3));
        scheme.refreshDynamicSwatches(WALLPAPER_B);
        assertEquals(0xFFABCDEF, scheme.getSwatch(3));
    }

    @Test
    public void unpinReturnsSlotToDynamicAndFollowsTheNextRefresh() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.pinSwatch(2, 0xFF00FF00);
        scheme.unpinSwatch(2);

        assertFalse(scheme.isSwatchPinned(2));
        // Unpinning immediately restores the last known Material color.
        assertEquals(WALLPAPER_A[2], scheme.getSwatch(2));
        assertTrue(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(WALLPAPER_B[2], scheme.getSwatch(2));
        assertTrue(scheme.isFullyDynamic());
    }

    @Test
    public void dynamicSlotsTrackWallpaperChangesWhilePinnedSlotHolds() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.pinSwatch(1, 0xFFDEAD01);

        assertTrue(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(0xFFDEAD01, scheme.getSwatch(1));
        assertEquals(WALLPAPER_B[0], scheme.getSwatch(0));
        assertEquals(WALLPAPER_B[7], scheme.getSwatch(7));

        assertTrue(scheme.refreshDynamicSwatches(WALLPAPER_A));
        assertEquals(0xFFDEAD01, scheme.getSwatch(1));
        assertEquals(WALLPAPER_A[0], scheme.getSwatch(0));
        assertEquals(WALLPAPER_A[7], scheme.getSwatch(7));
        // A repeat refresh with the same palette reports no change.
        assertFalse(scheme.refreshDynamicSwatches(WALLPAPER_A));
    }

    @Test
    public void refreshSurvivesShortAndLongMaterialPalettes() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.refreshDynamicSwatches(new int[] {0xFF010101, 0xFF020202});

        assertEquals(0xFF010101, scheme.getSwatch(0));
        assertEquals(0xFF020202, scheme.getSwatch(1));
        // Slots the short palette never covered keep their previous dynamic color.
        assertEquals(WALLPAPER_A[2], scheme.getSwatch(2));

        int[] longPalette = new int[InAppKeyboardColorScheme.BASE24_COLOR_COUNT + 6];
        for (int i = 0; i < longPalette.length; i++) longPalette[i] = 0xFF300000 | i;
        scheme.refreshDynamicSwatches(longPalette);
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, scheme.swatchCount());
        assertEquals(0xFF300000 | 23, scheme.getSwatch(23));
    }

    @Test
    public void base24ImportPinsEveryFilledSlot() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        StringBuilder yaml = new StringBuilder("system: base24\npalette:\n");
        for (int i = 0; i < 24; i++)
            yaml.append(String.format("  base%02X: \"%06x\"%n", i, 0x202020 + i));

        assertTrue(scheme.importBasePalette(yaml.toString(),
            InAppKeyboardColorScheme.BASE24_COLOR_COUNT));
        assertTrue(scheme.hasImportedPalette());
        assertFalse(scheme.isFullyDynamic());
        for (int i = 0; i < scheme.swatchCount(); i++)
            assertTrue("slot " + i, scheme.isSwatchPinned(i));
        // Pinned slots ignore a wallpaper change.
        assertFalse(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(0xFF202020, scheme.getSwatch(0));
    }

    @Test
    public void base16ImportLeavesExtendedSlotsDynamic() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        StringBuilder yaml = new StringBuilder("scheme: Test\n");
        for (int i = 0; i < 16; i++)
            yaml.append(String.format("base%02X: \"%06x\"%n", i, 0x101010 + i));

        assertTrue(scheme.importBase16(yaml.toString()));
        for (int i = 0; i < InAppKeyboardColorScheme.BASE16_COLOR_COUNT; i++)
            assertTrue("slot " + i, scheme.isSwatchPinned(i));
        for (int i = InAppKeyboardColorScheme.BASE16_COLOR_COUNT; i < scheme.swatchCount(); i++)
            assertFalse("slot " + i, scheme.isSwatchPinned(i));
        assertTrue(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(0xFF101010, scheme.getSwatch(0));
        assertEquals(WALLPAPER_B[16], scheme.getSwatch(16));
    }

    @Test
    public void unpinAllSwatchesDropsAnImportedPalette() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 16; i++)
            yaml.append(String.format("base%02X: \"%06x\"%n", i, 0x101010 + i));
        assertTrue(scheme.importBase16(yaml.toString()));
        scheme.setImportedThemeId("base16-test");

        scheme.unpinAllSwatches();

        assertTrue(scheme.isFullyDynamic());
        assertFalse(scheme.hasImportedPalette());
        assertEquals("", scheme.getImportedThemeId());
        assertEquals(WALLPAPER_A[0], scheme.getSwatch(0));
    }

    @Test
    public void perKeyAssignmentEditsPinNothing() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.paint("1:3", InAppKeyboardColorScheme.Role.KEY_BACKGROUND, 5);
        scheme.paint("1:3", InAppKeyboardColorScheme.Role.PRIMARY, 3);
        scheme.paint("2:0", InAppKeyboardColorScheme.Role.KEY_BORDER, 14);

        assertTrue(scheme.isFullyDynamic());
        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(
            WALLPAPER_B, scheme.toJson());
        assertTrue(restored.isFullyDynamic());

        Map<String, Keyboard2View.KeyColorOverride> overrides = restored.resolvedOverrides();
        assertEquals(2, overrides.size());
        // The assignment survives and now resolves against the new wallpaper.
        assertEquals(Integer.valueOf(WALLPAPER_B[5]), overrides.get("1:3").keyBackground);
        assertEquals(Integer.valueOf(WALLPAPER_B[3]), overrides.get("1:3").primaryLabel);
        assertEquals(Integer.valueOf(WALLPAPER_B[14]), overrides.get("2:0").borderColor);
    }

    @Test
    public void jsonRoundTripPreservesPinnedAndDynamicSlotsExactly() throws JSONException {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.pinSwatch(0, 0xFF111111);
        scheme.pinSwatch(9, 0xFF999999);
        scheme.paint("0:1", InAppKeyboardColorScheme.Role.SECONDARY, 9);

        String json = scheme.toJson();
        JSONObject root = new JSONObject(json);
        assertEquals(InAppKeyboardColorScheme.SCHEMA_VERSION,
            root.getInt("schemaVersion"));
        JSONArray swatches = root.getJSONArray("swatches");
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, swatches.length());
        assertTrue(swatches.isNull(1));
        assertEquals(0xFF111111, swatches.getInt(0));
        assertEquals(0xFF999999, swatches.getInt(9));

        // Reloading against a different wallpaper keeps pins and moves dynamic slots.
        InAppKeyboardColorScheme restored = InAppKeyboardColorScheme.fromJson(WALLPAPER_B, json);
        for (int i = 0; i < restored.swatchCount(); i++) {
            boolean pinned = i == 0 || i == 9;
            assertEquals("slot " + i, pinned, restored.isSwatchPinned(i));
            assertEquals("slot " + i, pinned ? scheme.getSwatch(i) : WALLPAPER_B[i],
                restored.getSwatch(i));
        }
        assertEquals(1, restored.resolvedOverrides().size());
    }

    @Test
    public void legacyImportedSchemeMigratesToPinnedSlots() throws JSONException {
        String legacy = legacyJson(true);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, legacy);

        assertTrue(scheme.hasImportedPalette());
        for (int i = 0; i < scheme.swatchCount(); i++) {
            assertTrue("slot " + i, scheme.isSwatchPinned(i));
            assertEquals("slot " + i, 0xFF400000 | i, scheme.getSwatch(i));
        }
        assertFalse(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(1, scheme.resolvedOverrides().size());
    }

    @Test
    public void legacyPlainSchemeMigratesToFullyDynamicKeepingAssignments()
        throws JSONException {
        String legacy = legacyJson(false);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, legacy);

        assertTrue(scheme.isFullyDynamic());
        for (int i = 0; i < scheme.swatchCount(); i++)
            assertEquals("slot " + i, WALLPAPER_A[i], scheme.getSwatch(i));
        Map<String, Keyboard2View.KeyColorOverride> overrides = scheme.resolvedOverrides();
        assertEquals(1, overrides.size());
        assertEquals(Integer.valueOf(WALLPAPER_A[4]), overrides.get("1:2").keyBackground);
        assertTrue(scheme.refreshDynamicSwatches(WALLPAPER_B));
        assertEquals(WALLPAPER_B[4], scheme.getSwatch(4));
    }

    /** Version 1 shape: no schemaVersion, 24 absolute colors, per-key assignments. */
    private static String legacyJson(boolean imported) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("base16Palette", imported);
        root.put("importedThemeId", imported ? "base24-legacy" : "");
        JSONArray swatches = new JSONArray();
        for (int i = 0; i < InAppKeyboardColorScheme.BASE24_COLOR_COUNT; i++)
            swatches.put(0xFF400000 | i);
        root.put("swatches", swatches);
        JSONObject keys = new JSONObject();
        JSONObject key = new JSONObject();
        key.put("bg", 4);
        keys.put("1:2", key);
        root.put("keys", keys);
        return root.toString();
    }

    @Test
    public void junkPersistedValuesDegradeToDynamicSlots() {
        // Wrong types, out-of-range indices, and a short array all stay usable.
        String json = "{\"schemaVersion\":2,\"swatches\":[\"#ff0000\",null,-16777216,{},true],"
            + "\"keys\":{\"1:1\":{\"bg\":99,\"primary\":2,\"secondary\":5},"
            + "\"2:2\":\"nonsense\"}}";
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, json);

        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, scheme.swatchCount());
        assertFalse(scheme.isSwatchPinned(0));
        assertFalse(scheme.isSwatchPinned(1));
        assertTrue(scheme.isSwatchPinned(2));
        assertEquals(0xFF000000, scheme.getSwatch(2));
        assertFalse(scheme.isSwatchPinned(3));
        assertFalse(scheme.isSwatchPinned(23));
        assertEquals(WALLPAPER_A[23], scheme.getSwatch(23));
        Map<String, Keyboard2View.KeyColorOverride> overrides = scheme.resolvedOverrides();
        assertEquals(1, overrides.size());
        // The out-of-range background index is dropped, valid label indices survive and resolve
        // against the slot they point at, pinned or dynamic.
        assertNull(overrides.get("1:1").keyBackground);
        assertEquals(Integer.valueOf(0xFF000000), overrides.get("1:1").primaryLabel);
        assertEquals(Integer.valueOf(WALLPAPER_A[5]), overrides.get("1:1").secondaryLabel);
    }

    @Test
    public void junkTopLevelValuesAndFutureVersionsStayUsable() {
        InAppKeyboardColorScheme wrongType = InAppKeyboardColorScheme.fromJson(
            WALLPAPER_A, "{\"schemaVersion\":2,\"swatches\":\"not-an-array\",\"keys\":7}");
        assertTrue(wrongType.isFullyDynamic());
        assertEquals(0, wrongType.resolvedOverrides().size());

        InAppKeyboardColorScheme tooLong = InAppKeyboardColorScheme.fromJson(WALLPAPER_A,
            "{\"schemaVersion\":2,\"swatches\":[-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,"
                + "-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1]}");
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, tooLong.swatchCount());
        assertEquals(0xFFFFFFFF, tooLong.getSwatch(23));

        // A version from the future is read with today's rules instead of throwing.
        InAppKeyboardColorScheme future = InAppKeyboardColorScheme.fromJson(WALLPAPER_A,
            "{\"schemaVersion\":99,\"swatches\":[null,-16777216],\"unknownField\":{\"a\":1}}");
        assertFalse(future.isSwatchPinned(0));
        assertTrue(future.isSwatchPinned(1));
        assertEquals(WALLPAPER_A[0], future.getSwatch(0));

        InAppKeyboardColorScheme empty = InAppKeyboardColorScheme.fromJson(new int[0], "");
        assertEquals(InAppKeyboardColorScheme.BASE24_COLOR_COUNT, empty.swatchCount());
        assertTrue(empty.isFullyDynamic());
    }

    @Test
    public void outOfRangeSlotOperationsAreIgnored() {
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(WALLPAPER_A, "");
        scheme.pinSwatch(-1, 0xFF000000);
        scheme.pinSwatch(scheme.swatchCount(), 0xFF000000);
        scheme.unpinSwatch(-1);
        scheme.unpinSwatch(scheme.swatchCount());

        assertTrue(scheme.isFullyDynamic());
        assertFalse(scheme.isSwatchPinned(-1));
        assertFalse(scheme.isSwatchPinned(scheme.swatchCount()));
    }
}
