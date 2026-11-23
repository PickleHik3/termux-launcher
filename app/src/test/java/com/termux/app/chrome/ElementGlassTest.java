package com.termux.app.chrome;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** {@link ElementGlass}: one element's glass from the look grid, Global-only parity first. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ElementGlassTest {

    private static final GlassRefraction.Look FANCIER = new GlassRefraction.Look(9, 3, 40, 30, 20, 10);
    private static final int PAGE = 7;
    private static final int OTHER_PAGE = 8;

    private static LookGrid globalOnly() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(LookGrid.Property.FROST, null, FrostStops.MIST);
        grid.write(LookGrid.Property.OPACITY, null, 52);
        grid.write(LookGrid.Property.TINT, null, 15);
        grid.write(LookGrid.Property.TINT_HUE, null, LookGrid.TINT_HUE_SECONDARY);
        grid.write(LookGrid.Property.DEPTH, null, 11);
        grid.write(LookGrid.Property.RIM, null, LookGrid.RIM_GRADIENT);
        return grid;
    }

    private static void assertSameGlass(ElementGlass expected, ElementGlass actual) {
        assertEquals(expected.blurDp, actual.blurDp);
        assertEquals(expected.opacityPercent, actual.opacityPercent);
        assertEquals(expected.grainPercent, actual.grainPercent);
        assertEquals(expected.tintPercent, actual.tintPercent);
        assertEquals(expected.cornersDp, actual.cornersDp);
        assertEquals(expected.ownHue, actual.ownHue);
        assertEquals(expected.ownRim, actual.ownRim);
    }

    @Test
    public void globalOnly_everyElementFollowsGlobalAndHandsTheGlobalLookBack() {
        LookGrid grid = globalOnly();
        ElementGlass dock = ElementGlass.resolve(grid, LookGrid.Element.DOCK, PAGE);
        for (LookGrid.Element element : LookGrid.Element.values()) {
            ElementGlass glass = ElementGlass.resolve(grid, element, PAGE);
            assertSameGlass(dock, glass);
            assertNull(glass.ownHue);
            assertNull(glass.ownRim);
            assertSame(FANCIER, glass.refraction(FANCIER));
            assertNull(glass.refraction(null));
            GlassLook wall = GlassLook.of(null, "gradient", "secondary");
            assertSame(wall, glass.look(wall));
            assertFalse(glass.rimOff());
        }
        assertEquals(FrostStops.blurDpFor(FrostStops.MIST), dock.blurDp);
        assertEquals(FrostStops.grainPercentFor(FrostStops.MIST), dock.grainPercent);
        assertEquals(52, dock.opacityPercent);
        assertEquals(15, dock.tintPercent);
    }

    @Test
    public void globalOnly_specEqualsTheDockSpecBuiltFromTheNamedGetters() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("element-glass-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(context, store, null);
        preferences.migrateLookGrid(java.util.Collections.emptyList());
        preferences.setSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 61);
        preferences.setSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.TINT, 22);
        int page = TermuxAppSharedPreferences.terminalPageId();
        GlassStack.Spec today = GlassStack.Spec.of(preferences.getExtraKeysBlurRadius(page),
            preferences.getAppBarOpacity(page) / 100f, preferences.getDockGlassGrain(page), 12f,
            FANCIER).withTintStrength(preferences.getDockTintStrength(page));
        for (LookGrid.Element element : LookGrid.Element.values()) {
            assertEquals(element.key, today,
                ElementGlass.of(preferences, element, page).spec(12f, FANCIER));
        }
    }

    @Test
    public void storeRefraction_followsGlobalUntilAnElementHoldsItsOwn() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("element-glass-refraction",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(context, store, null);
        preferences.migrateLookGrid(java.util.Collections.emptyList());
        try {
        for (LookGrid.Element element : LookGrid.Element.values()) {
            assertSame(FANCIER, ElementGlass.refraction(preferences, element, PAGE, FANCIER));
            assertNull(ElementGlass.refraction(preferences, element, PAGE, null));
        }
        // Depth and Shine are app-wide: a surface's own Depth cell is not read.
        preferences.lookGrid().write(LookGrid.Property.DEPTH, LookGrid.Element.KEYBOARD, 2);
        assertSame(FANCIER, ElementGlass.refraction(preferences, LookGrid.Element.KEYBOARD, PAGE,
            FANCIER));
        assertSame(FANCIER, ElementGlass.refraction(preferences, LookGrid.Element.KEYBOARD,
            OTHER_PAGE, FANCIER));
        assertSame(FANCIER, ElementGlass.refraction(preferences, LookGrid.Element.DOCK, PAGE,
            FANCIER));
        } finally {
        }
    }

    @Test
    public void elementCell_changesOnlyThatElement() {
        LookGrid grid = globalOnly();
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.SHEETS, 80);
        grid.write(LookGrid.Property.TINT_HUE, LookGrid.Element.SHEETS,
            LookGrid.TINT_HUE_TERTIARY);
        grid.write(LookGrid.Property.RIM, LookGrid.Element.SHEETS, LookGrid.RIM_OFF);
        ElementGlass sheets = ElementGlass.resolve(grid, LookGrid.Element.SHEETS, -1);
        assertEquals(80, sheets.opacityPercent);
        assertSame(FANCIER, sheets.refraction(FANCIER));
        GlassLook wall = GlassLook.of(null, "gradient", "secondary");
        assertEquals(GlassLook.Hue.TERTIARY, sheets.look(wall).hue);
        assertFalse(sheets.look(wall).gradientRim);
        assertTrue(sheets.rimOff());
        for (LookGrid.Element element : LookGrid.Element.values()) {
            if (element == LookGrid.Element.SHEETS) continue;
            ElementGlass other = ElementGlass.resolve(grid, element, -1);
            assertEquals(52, other.opacityPercent);
            assertSame(FANCIER, other.refraction(FANCIER));
            assertSame(wall, other.look(wall));
        }
    }

    /** An older build's page cell is no page's look: every page reads the same glass (ADR 0031). */
    @Test
    public void anOldPageCell_isNoPagesLook() {
        LookGrid grid = globalOnly();
        grid.store().putInt(LookGrid.key(LookGrid.Property.TINT, null, PAGE), 60);
        grid.store().putInt(LookGrid.key(LookGrid.Property.TINT, null, LookGrid.Kind.TERMINAL), 60);
        for (LookGrid.Element element : LookGrid.Element.values()) {
            ElementGlass onPage = ElementGlass.resolve(grid, element, PAGE);
            assertEquals(15, onPage.tintPercent);
            assertSameGlass(onPage, ElementGlass.resolve(grid, element, OTHER_PAGE));
            assertSameGlass(onPage, ElementGlass.resolve(grid, element, -1));
        }
    }

    @Test
    public void glassWidgetsOff_isASolidCard() {
        LookGrid grid = globalOnly();
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.WIDGETS, 100);
        grid.write(LookGrid.Property.FROST, LookGrid.Element.WIDGETS, FrostStops.SOLID);
        ElementGlass widgets = ElementGlass.resolve(grid, LookGrid.Element.WIDGETS, PAGE);
        assertEquals(100, widgets.opacityPercent);
        assertEquals(0, widgets.blurDp);
        assertNotEquals(widgets.opacityPercent,
            ElementGlass.resolve(grid, LookGrid.Element.DOCK, PAGE).opacityPercent);
    }

}
