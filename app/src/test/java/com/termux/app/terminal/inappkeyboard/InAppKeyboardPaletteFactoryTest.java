package com.termux.app.terminal.inappkeyboard;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class InAppKeyboardPaletteFactoryTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    @Test
    public void allStoredVariantsBuildPalettesWithReadableLabels() {
        for (String variant : new String[] {"system", "light", "dark", "black",
                "steel_teal", "mint_fuji", "neon_nightfall", "sakura_wood", "ink_plum"}) {
            Theme.Palette palette = InAppKeyboardPaletteFactory.create(context, variant);

            assertNotNull(variant, palette);
            assertTrue(variant + " primary label contrast",
                ColorUtils.calculateContrast(palette.labelColor, palette.keyBackground) >= 4.5d);
            assertTrue(variant + " sub-label contrast",
                ColorUtils.calculateContrast(palette.subLabelColor, palette.keyBackground) >= 4.5d);
            assertTrue(variant + " action label contrast",
                ColorUtils.calculateContrast(palette.actionLabelColor,
                    palette.actionKeyBackground) >= 4.5d);
            assertTrue(variant + " action sub-label contrast",
                ColorUtils.calculateContrast(palette.actionSubLabelColor,
                    palette.actionKeyBackground) >= 4.5d);
            assertTrue(variant + " activated label contrast",
                ColorUtils.calculateContrast(palette.activatedLabelColor,
                    palette.activatedKeyBackground) >= 4.5d);
            assertTrue(variant + " pressed label contrast",
                ColorUtils.calculateContrast(palette.pressedLabelColor,
                    palette.activatedKeyBackground) >= 4.5d);
            assertTrue(variant + " locked label contrast",
                ColorUtils.calculateContrast(palette.lockedModifierColor,
                    palette.activatedKeyBackground) >= 4.5d);
            assertEquals(variant + " one-dp border",
                context.getResources().getDisplayMetrics().density,
                palette.borderWidth, 0.001f);
        }
    }

    @Test
    public void blackVariantPinsNeutralSurfacesToBlack() {
        Theme.Palette palette = InAppKeyboardPaletteFactory.create(context, "black");

        assertEquals(Color.BLACK, palette.keyboardBackground);
        assertEquals(Color.BLACK, palette.keyBackground);
        assertEquals(Color.BLACK, palette.actionKeyBackground);
        assertEquals(Color.BLACK, palette.spaceBarBackground);
    }

    @Test
    public void fixedThemesUseDesignTokensForSurfaces() {
        Theme.Palette steel = InAppKeyboardPaletteFactory.create(context, "steel_teal");
        assertEquals(0xFFE9E7E2, steel.keyboardBackground);
        assertEquals(0xFFF4F2EC, steel.keyBackground);
        assertEquals(0xFF727A80, steel.actionKeyBackground);
        assertEquals(0xFF727A80, steel.spaceBarBackground);
        assertEquals(0xFF1C7A71, steel.activatedKeyBackground);
        assertNull(steel.indicatorColors);

        Theme.Palette neon = InAppKeyboardPaletteFactory.create(context, "neon_nightfall");
        assertEquals(0xFF0D0D10, neon.keyboardBackground);
        assertEquals(0xFF17171B, neon.keyBackground);
        assertNull(neon.indicatorColors);

        for (String variant : new String[] {"mint_fuji", "sakura_wood", "ink_plum"}) {
            Theme.Palette palette = InAppKeyboardPaletteFactory.create(context, variant);
            assertNull(variant + " has no indicator", palette.indicatorColors);
        }
    }

    @Test
    public void glassVariantsAreTransparentWithReadableComposedLabels() {
        int base = InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(context);
        for (String variant : new String[] {"system", "light", "dark", "black",
                "steel_teal", "mint_fuji", "neon_nightfall", "sakura_wood", "ink_plum"}) {
            Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, variant);

            assertEquals(variant, Color.TRANSPARENT, palette.keyboardBackground);
            assertTrue(variant + " keys are translucent chips",
                Color.alpha(palette.keyBackground) < 255);
            assertTrue(variant + " action keys are translucent chips",
                Color.alpha(palette.actionKeyBackground) < 255);
            assertTrue(variant + " keycap shading gradient enabled",
                palette.keyGradientTopOverlay != 0 && palette.keyGradientBottomOverlay != 0);

            int keyOnBase = ColorUtils.compositeColors(palette.keyBackground, base);
            int actionOnBase = ColorUtils.compositeColors(palette.actionKeyBackground, base);
            int activatedOnBase =
                ColorUtils.compositeColors(palette.activatedKeyBackground, base);
            assertTrue(variant + " label contrast over composed key",
                ColorUtils.calculateContrast(palette.labelColor, keyOnBase) >= 4.5d);
            assertTrue(variant + " sub-label contrast over composed key",
                ColorUtils.calculateContrast(palette.subLabelColor, keyOnBase) >= 4.5d);
            assertTrue(variant + " action label contrast over composed action key",
                ColorUtils.calculateContrast(palette.actionLabelColor, actionOnBase) >= 4.5d);
            assertTrue(variant + " activated label contrast over composed activated key",
                ColorUtils.calculateContrast(palette.activatedLabelColor, activatedOnBase)
                    >= 4.5d);
        }
    }

    @Test
    public void glassChipsKeepThemeIdentity() {
        // Glass keeps each theme's own surface hue: neon stays dark, steel stays light.
        Theme.Palette neon = InAppKeyboardPaletteFactory.createGlass(context, "neon_nightfall");
        assertTrue("neon glass chips stay dark",
            ColorUtils.calculateLuminance(ColorUtils.setAlphaComponent(
                neon.keyBackground, 255)) < 0.2d);
        assertNull("decorative indicator is removed from glass themes", neon.indicatorColors);

        Theme.Palette steel = InAppKeyboardPaletteFactory.createGlass(context, "steel_teal");
        assertTrue("steel glass chips stay light",
            ColorUtils.calculateLuminance(ColorUtils.setAlphaComponent(
                steel.keyBackground, 255)) > 0.6d);

        // The legacy "dock" stored value maps onto glass system.
        Theme.Palette legacy = InAppKeyboardPaletteFactory.create(context, "dock");
        assertEquals(Color.TRANSPARENT, legacy.keyboardBackground);
    }

    @Test
    @Config(sdk = 28, application = Application.class, qualifiers = "night")
    public void glassSystemThemeUsesDarkChipsInNightMode() {
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "system");
        int base = InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(context);
        int keyOnBase = ColorUtils.compositeColors(palette.keyBackground, base);

        assertTrue("night glass chips must read dark, not milky white",
            ColorUtils.calculateLuminance(keyOnBase) < 0.25d);
        assertTrue("night labels are light",
            ColorUtils.calculateLuminance(palette.labelColor) > 0.5d);
    }

    @Test
    public void materialVariantsKeepActionLabelsAlignedWithPrimaryLabels() {
        Theme.Palette palette = InAppKeyboardPaletteFactory.create(context, "dark");
        assertEquals(palette.labelColor, palette.actionLabelColor);
        assertEquals(palette.subLabelColor, palette.actionSubLabelColor);
        assertNull(palette.indicatorColors);
    }

    @Test
    public void sourceSignatureChangesWhenAnyInputColorChanges() {
        int original = InAppKeyboardPaletteFactory.sourceRoleSignature(
            0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004);
        int changed = InAppKeyboardPaletteFactory.sourceRoleSignature(
            0xFF000001, 0xFF000002, 0xFF000103, 0xFF000004);

        assertNotEquals(original, changed);
        assertEquals(InAppKeyboardPaletteFactory.signature(context),
            InAppKeyboardPaletteFactory.signature(context));
    }
}
