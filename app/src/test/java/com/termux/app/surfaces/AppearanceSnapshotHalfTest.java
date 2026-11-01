package com.termux.app.surfaces;

import com.termux.app.wall.PaneWallPage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

/**
 * The editor's Undo and dirty state across the two halves of the wall (ADR 0013): a card writes
 * the half the frame shows, so what the editor puts back and what it calls unsaved has to see
 * Home's own values and the Workspace's, and which half followed the shared value at open.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceSnapshotHalfTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("appearance-snapshot-half-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        SurfacePresets.apply(preferences, SurfacePresets.presets().get(1));
    }

    @Test
    public void aHomeOnlyWriteIsUnsaved() {
        String atOpen = AppearanceSnapshot.signatureOf(preferences);
        int workspace = preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId());

        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL,
            23);

        // Home's detach is the wall's: the Workspace's dock wears the same number.
        assertEquals("the Workspace's dock follows the wall", 23,
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(23, preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL));
        assertNotEquals("Home's own value is a change", atOpen,
            AppearanceSnapshot.signatureOf(preferences));
    }

    @Test
    public void aHalfThatFollowedTheSharedValueFollowsItAgain() {
        // Both halves equal at open: no half keeps a key of its own.
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        assertFalse(hasHalfKeys());

        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.terminalPageId(), 77);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId(), 12);
        assertTrue(hasHalfKeys());

        entry.restore(preferences);

        assertFalse("every half key written in the session is gone", hasHalfKeys());
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void aHomeOrDisplayKindOverrideWrittenInTheSessionGoesOnRestore() {
        int home = 7;
        int display = 9;
        com.termux.shared.termux.settings.preferences.LookGrid.install(page ->
            page == home ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.HOME
                : page == display ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.DISPLAY
                : page == com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID
                    ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.TERMINAL : null,
            null);
        try {
            preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, display, 31);
            AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
            // A page's own dock opacity is the dock's cell on that page's kind (ADR 0020 §2).
            String homeKey = com.termux.shared.termux.settings.preferences.LookGrid.key(
                com.termux.shared.termux.settings.preferences.LookGrid.Property.OPACITY,
                com.termux.shared.termux.settings.preferences.LookGrid.Element.DOCK,
                com.termux.shared.termux.settings.preferences.LookGrid.Kind.HOME);
            String displayKey = com.termux.shared.termux.settings.preferences.LookGrid.key(
                com.termux.shared.termux.settings.preferences.LookGrid.Property.OPACITY,
                com.termux.shared.termux.settings.preferences.LookGrid.Element.DOCK,
                com.termux.shared.termux.settings.preferences.LookGrid.Kind.DISPLAY);
            assertEquals("the Display page writes the Display kind's cell", 31,
                store.getInt(displayKey, -1));
            boolean homeHeld = store.contains(homeKey);
            int homeAtOpen = store.getInt(homeKey, -1);

            preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, home, 15);
            preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, display, 60);
            assertNotEquals("a Home page's own value is a change", entry.signature(),
                AppearanceSnapshot.signatureOf(preferences));

            entry.restore(preferences);

            assertEquals("Home's override written in the session is gone", homeHeld,
                store.contains(homeKey));
            assertEquals(homeAtOpen, store.getInt(homeKey, -1));
            assertEquals("the Display kind's override is put back", 31, store.getInt(displayKey, -1));
            assertEquals(entry.signature(), AppearanceSnapshot.signatureOf(preferences));
        } finally {
            com.termux.app.look.TerminalPageIdFixture.installKinds();
        }
    }

    @Test
    public void theTerminalsMaterialIsNotWrittenOntoTheWall() {
        int terminal = TermuxAppSharedPreferences.terminalPageId();
        String wallMaterial = preferences.getSurfaceMaterial(TermuxAppSharedPreferences.PAGE_WALL);
        String own = wallMaterial.equals("frost") ? "clear" : "frost";
        preferences.setSurfaceMaterial(terminal, own);
        preferences.setSurfaceMaterialIntensity(terminal, 13);
        int wallIntensity = preferences.getSurfaceMaterialIntensity(
            TermuxAppSharedPreferences.PAGE_WALL);
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);

        entry.restore(preferences);

        assertEquals(wallMaterial, preferences.getSurfaceMaterial(
            TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(wallIntensity, preferences.getSurfaceMaterialIntensity(
            TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(own, preferences.getSurfaceMaterial(terminal));
        assertEquals(13, preferences.getSurfaceMaterialIntensity(terminal));
    }

    @Test
    public void theHalvesKeysAreEveryPerHalfCell() {
        java.util.List<String> keys = AppearanceSnapshot.halfAbleKeys();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.isPerPage(slot, property))
                    continue;
                assertTrue(slot + " " + property, keys.contains(
                    TermuxAppSharedPreferences.surfaceOverrideKey(slot, property)));
            }
        }
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (TermuxAppSharedPreferences.isPerPageProperty(property))
                assertTrue(property.name(), keys.contains(property.baseKey));
        }
    }

    private boolean hasHalfKeys() {
        for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
            String key = entry.getKey();
            // The Workspace's own grid values are the Terminal kind's cells (ADR 0020 §2).
            if (key.endsWith("@" + com.termux.shared.termux.settings.preferences.LookGrid.KIND_PREFIX
                    + com.termux.shared.termux.settings.preferences.LookGrid.Kind.TERMINAL.key)
                    && com.termux.shared.termux.settings.preferences.LookGrid.parse(key) != null)
                return true;
            // The halves are pages: the Workspace's own values are the Terminal page's overrides.
            String page = "@" + com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID;
            if (key.endsWith(page)) {
                // A grid cell on the Terminal page is one of its own values.
                if (com.termux.shared.termux.settings.preferences.LookGrid.parse(key) != null)
                    return true;
                for (String halfAble : AppearanceSnapshot.halfAbleKeys()) {
                    if (key.equals(halfAble + page))
                        return true;
                }
            }
        }
        return false;
    }

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        com.termux.shared.termux.settings.preferences.LookGrid.install(null, null);
    }
}
