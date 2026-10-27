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
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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
            WallHalf.WORKSPACE);

        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.HOME,
            23);

        // Home's detach is the wall's: the Workspace's dock wears the same number.
        assertEquals("the Workspace's dock follows the wall", 23,
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                WallHalf.WORKSPACE));
        assertEquals(23, preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.HOME));
        assertNotEquals("Home's own value is a change", atOpen,
            AppearanceSnapshot.signatureOf(preferences));
    }

    @Test
    public void undoPutsBothHalvesBackAndTheirLinks() {
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT, WallHalf.HOME, 40);
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        String atOpen = entry.signature();
        int homeTint = preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            WallHalf.HOME);
        int workspaceTint = preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            WallHalf.WORKSPACE);
        boolean workspaceFollows = preferences.isSurfaceInheriting(SurfaceSlot.STATUS,
            SurfaceProperty.TINT, WallHalf.WORKSPACE);

        // Edits on both halves, a reset, a copy and a Look on one half.
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR,
            WallHalf.WORKSPACE, 30);
        preferences.resetSurfaceToLook(SurfaceSlot.STATUS, WallHalf.HOME);
        preferences.copySurfaceToOtherHalf(SurfaceSlot.KEYBOARD, WallHalf.WORKSPACE);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets().get(3), WallHalf.HOME);
        assertNotEquals(atOpen, AppearanceSnapshot.signatureOf(preferences));

        entry.restore(preferences);

        assertEquals(atOpen, AppearanceSnapshot.signatureOf(preferences));
        assertEquals(homeTint, preferences.getSurfaceValue(SurfaceSlot.STATUS,
            SurfaceProperty.TINT, WallHalf.HOME));
        assertEquals(workspaceTint, preferences.getSurfaceValue(SurfaceSlot.STATUS,
            SurfaceProperty.TINT, WallHalf.WORKSPACE));
        assertFalse("Home's own value is its own again", preferences.isSurfaceInheriting(
            SurfaceSlot.STATUS, SurfaceProperty.TINT, WallHalf.HOME));
        assertEquals(workspaceFollows, preferences.isSurfaceInheriting(SurfaceSlot.STATUS,
            SurfaceProperty.TINT, WallHalf.WORKSPACE));
    }

    @Test
    public void aHalfThatFollowedTheSharedValueFollowsItAgain() {
        // Both halves equal at open: no half keeps a key of its own.
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        assertFalse(hasHalfKeys());

        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 77);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN, WallHalf.WORKSPACE, 12);
        assertTrue(hasHalfKeys());

        entry.restore(preferences);

        assertFalse("every half key written in the session is gone", hasHalfKeys());
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
    }

    @Test
    public void theHalvesKeysAreEveryPerHalfCell() {
        java.util.List<String> keys = AppearanceSnapshot.halfAbleKeys();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.isPerHalf(slot, property))
                    continue;
                assertTrue(slot + " " + property, keys.contains(
                    TermuxAppSharedPreferences.surfaceOverrideKey(slot, property)));
            }
        }
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (TermuxAppSharedPreferences.isPerHalfProperty(property))
                assertTrue(property.name(), keys.contains(property.baseKey));
        }
    }

    private boolean hasHalfKeys() {
        for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
            String key = entry.getKey();
            // The halves are pages: the Workspace's own values are the Terminal page's overrides.
            String page = "@" + com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID;
            if (key.endsWith(page)) {
                for (String halfAble : AppearanceSnapshot.halfAbleKeys()) {
                    if (key.equals(halfAble + page))
                        return true;
                }
            }
        }
        return false;
    }
}
