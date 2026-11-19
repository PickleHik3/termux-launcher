package com.termux.app.surfaces;

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

}
