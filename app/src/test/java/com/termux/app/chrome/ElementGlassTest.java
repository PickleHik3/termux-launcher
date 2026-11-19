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

/** {@link ElementGlass}: one element's glass from the look grid, Base-only parity first. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ElementGlassTest {

    private static final GlassRefraction.Look FANCIER = new GlassRefraction.Look(9, 3, 40, 30, 20, 10);
    private static final int PAGE = 7;
    private static final int OTHER_PAGE = 8;

    private static LookGrid baseOnly() {
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
        assertEquals(expected.ownDepthDp, actual.ownDepthDp);
        assertEquals(expected.ownEdgePercent, actual.ownEdgePercent);
    }

    @Test
    public void baseOnly_everyElementFollowsBaseAndHandsTheGlobalLookBack() {
        LookGrid grid = baseOnly();
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
    public void baseOnly_specEqualsTheDockSpecBuiltFromTheNamedGetters() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("element-glass-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(context, store, null);
        preferences.migrateLookGrid(java.util.Collections.emptyList());
        preferences.setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 61);
        preferences.setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.TINT, 22);
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
    public void storeRefraction_followsBaseUntilAnElementHoldsItsOwn() {
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
        preferences.lookGrid().write(LookGrid.Property.DEPTH, LookGrid.Element.KEYBOARD, 2);
        // One look on every page (ADR 0031): the keyboard's own Depth holds on each.
        assertEquals(2, ElementGlass.refraction(preferences, LookGrid.Element.KEYBOARD, PAGE,
            FANCIER).bendDp);
        assertEquals(2, ElementGlass.refraction(preferences, LookGrid.Element.KEYBOARD,
            OTHER_PAGE, FANCIER).bendDp);
        assertSame(FANCIER, ElementGlass.refraction(preferences, LookGrid.Element.DOCK, PAGE,
            FANCIER));
        } finally {
        }
    }

    @Test
    public void elementCell_changesOnlyThatElement() {
        LookGrid grid = baseOnly();
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.SHEETS, 80);
        grid.write(LookGrid.Property.DEPTH, LookGrid.Element.SHEETS, 3);
        grid.write(LookGrid.Property.EDGE, LookGrid.Element.SHEETS, 70);
        grid.write(LookGrid.Property.TINT_HUE, LookGrid.Element.SHEETS,
            LookGrid.TINT_HUE_TERTIARY);
        grid.write(LookGrid.Property.RIM, LookGrid.Element.SHEETS, LookGrid.RIM_OFF);
        ElementGlass sheets = ElementGlass.resolve(grid, LookGrid.Element.SHEETS, -1);
        assertEquals(80, sheets.opacityPercent);
        GlassRefraction.Look look = sheets.refraction(FANCIER);
        assertEquals(3, look.bendDp);
        assertEquals(70, look.dispersionPercent);
        assertEquals(FANCIER.edgeWidthDp, look.edgeWidthDp);
        assertEquals(FANCIER.specularPercent, look.specularPercent);
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
        LookGrid grid = baseOnly();
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
        LookGrid grid = baseOnly();
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.WIDGETS, 100);
        grid.write(LookGrid.Property.FROST, LookGrid.Element.WIDGETS, FrostStops.SOLID);
        ElementGlass widgets = ElementGlass.resolve(grid, LookGrid.Element.WIDGETS, PAGE);
        assertEquals(100, widgets.opacityPercent);
        assertEquals(0, widgets.blurDp);
        assertNotEquals(widgets.opacityPercent,
            ElementGlass.resolve(grid, LookGrid.Element.DOCK, PAGE).opacityPercent);
    }

}
