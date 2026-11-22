package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.termux.app.chrome.GlassLook;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory.GlassInputs;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory.GlassMemo;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Theme;

/** The glass palette memo's key: equal inputs hit, any input moving misses. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class GlassPaletteMemoTest {

    private static final int[] FACES = {0xFF203040, 0xFF102030};
    private static final int TONE = InAppKeyboardPaletteFactory.TONE_PALE;
    private static final int GLASS_BASE = 0xFF1C1B1F;

    private static int[] roles() {
        int[] roles = new int[InAppKeyboardPaletteFactory.ROLE_COUNT];
        for (int i = 0; i < roles.length; i++) roles[i] = 0xFF000000 | (i * 0x0A0B0C);
        return roles;
    }

    private static GlassInputs base() {
        return new GlassInputs("system", GlassLook.SCHEME, FACES.clone(), 80, TONE, true, 2.75f,
            GLASS_BASE, roles());
    }

    @Test
    public void sameInputsAreOneKey() {
        assertEquals(base(), base());
        assertEquals(base().hashCode(), base().hashCode());
        // The legacy dock variant and null are the system theme's glass.
        assertEquals(base(), new GlassInputs(null, GlassLook.SCHEME, FACES, 80, TONE, true, 2.75f,
            GLASS_BASE, roles()));
        assertEquals(base(), new GlassInputs("dock", GlassLook.SCHEME, FACES, 80, TONE, true,
            2.75f, GLASS_BASE, roles()));
    }

    @Test
    public void theKeyKeepsItsOwnCopies() {
        int[] faces = FACES.clone();
        int[] roles = roles();
        GlassInputs key = new GlassInputs("system", GlassLook.SCHEME, faces, 80, TONE, true,
            2.75f, GLASS_BASE, roles);
        faces[0] = 0xFFFFFFFF;
        roles[3] = 0xFFFFFFFF;
        assertEquals(base(), key);
    }

    @Test
    public void everyInputMovingMisses() {
        GlassLook obsidian = GlassLook.of(TERMUX_APP.GLASS_TINT_OBSIDIAN, null);
        int[] r = roles();
        GlassInputs[] moved = {
            // Keyboard theme.
            new GlassInputs("dark", GlassLook.SCHEME, FACES, 80, TONE, true, 2.75f, GLASS_BASE, r),
            // Glass Look.
            new GlassInputs("system", obsidian, FACES, 80, TONE, true, 2.75f, GLASS_BASE, r),
            // Measured faces, and none measured yet.
            new GlassInputs("system", GlassLook.SCHEME, new int[] {0xFF203040, 0xFF102031}, 80,
                TONE, true, 2.75f, GLASS_BASE, r),
            new GlassInputs("system", GlassLook.SCHEME, new int[0], 80, TONE, true, 2.75f,
                GLASS_BASE, r),
            // Key opacity, and the theme's own.
            new GlassInputs("system", GlassLook.SCHEME, FACES, 81, TONE, true, 2.75f, GLASS_BASE, r),
            new GlassInputs("system", GlassLook.SCHEME, FACES, -1, TONE, true, 2.75f, GLASS_BASE, r),
            // Legend tone.
            new GlassInputs("system", GlassLook.SCHEME, FACES, 80,
                InAppKeyboardPaletteFactory.TONE_DARK, true, 2.75f, GLASS_BASE, r),
            // Night mode (the configuration's uiMode).
            new GlassInputs("system", GlassLook.SCHEME, FACES, 80, TONE, false, 2.75f, GLASS_BASE,
                r),
            // Density.
            new GlassInputs("system", GlassLook.SCHEME, FACES, 80, TONE, true, 3f, GLASS_BASE, r),
            // Glass base: the scheme chrome, an app-only picture or the system neutral moved.
            new GlassInputs("system", GlassLook.SCHEME, FACES, 80, TONE, true, 2.75f, 0xFF2B2930, r),
        };
        for (GlassInputs key : moved) assertNotEquals(base(), key);
    }

    @Test
    public void eachMaterialRoleMovingMisses() {
        // A theme, Material You or scheme change moves a role while every preference, the
        // colour scheme's JSON included, stays the same.
        for (int i = 0; i < InAppKeyboardPaletteFactory.ROLE_COUNT; i++) {
            int[] r = roles();
            r[i] ^= 0x000001;
            assertNotEquals("role " + i, base(), new GlassInputs("system", GlassLook.SCHEME,
                FACES, 80, TONE, true, 2.75f, GLASS_BASE, r));
        }
    }

    @Test
    public void theMemoHitsOnEqualInputsAndForgetsTheEldest() {
        GlassMemo memo = new GlassMemo(2);
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(base());
        memo.put(base(), palette);
        assertSame(palette, memo.get(base()));

        GlassInputs dark = new GlassInputs("dark", GlassLook.SCHEME, FACES, 80, TONE, true, 2.75f,
            GLASS_BASE, roles());
        GlassInputs light = new GlassInputs("light", GlassLook.SCHEME, FACES, 80, TONE, true,
            2.75f, GLASS_BASE, roles());
        memo.put(dark, InAppKeyboardPaletteFactory.createGlass(dark));
        memo.get(base());
        memo.put(light, InAppKeyboardPaletteFactory.createGlass(light));
        // The system entry was read last, so the dark one went.
        assertSame(palette, memo.get(base()));
        assertNull(memo.get(dark));
    }

    @Test
    public void aBuildReadsOnlyItsInputs() {
        Theme.Palette a = InAppKeyboardPaletteFactory.createGlass(base());
        Theme.Palette b = InAppKeyboardPaletteFactory.createGlass(base());
        assertEquals(a.labelColor, b.labelColor);
        assertEquals(a.keyBackground, b.keyBackground);
        assertEquals(a.functionKeyBackground, b.functionKeyBackground);
        assertEquals(a.functionLabelColor, b.functionLabelColor);
        assertEquals(a.borderRadius, b.borderRadius, 0f);
    }

    @Test
    public void inputsReadFromOneContextTwiceHitAndBuildTheContextsPalette() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        GlassInputs a = InAppKeyboardPaletteFactory.glassInputs(context, "system",
            GlassLook.SCHEME, FACES, 80, TONE);
        GlassInputs b = InAppKeyboardPaletteFactory.glassInputs(context, "system",
            GlassLook.SCHEME, FACES.clone(), 80, TONE);
        assertEquals(a, b);
        Theme.Palette fromContext = InAppKeyboardPaletteFactory.createGlass(context, "system",
            GlassLook.SCHEME, FACES, 80, TONE);
        Theme.Palette fromInputs = InAppKeyboardPaletteFactory.createGlass(a);
        assertEquals(fromContext.labelColor, fromInputs.labelColor);
        assertEquals(fromContext.keyBackground, fromInputs.keyBackground);
        assertEquals(fromContext.functionLabelColor, fromInputs.functionLabelColor);
    }
}
