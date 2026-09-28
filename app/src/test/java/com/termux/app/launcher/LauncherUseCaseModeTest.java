package com.termux.app.launcher;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The three nested presets, the mapping of the older two-way switch's value, and the "Custom"
 * the settings row shows once a switch below has moved the surfaces off the stored preset. Every
 * test says whether the build offers a display, so none depends on the flavour it runs under.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherUseCaseModeTest {

    private static final boolean DISPLAY_OFFERED = true;
    private static final boolean NO_DISPLAY = false;

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "launcher-use-case-mode-test", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    private PlaceLayoutStore places() {
        return new PlaceLayoutStore(preferences);
    }

    private void assertAppsRowEverywhere(RowPlacement expected) {
        PlaceLayoutStore places = places();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                assertEquals(place + " " + orientation, expected,
                    rowOf(places.resolve(orientation), com.termux.app.place.Element.APPS));
            }
        }
    }

    /** Bottom in portrait, the left rail in landscape — the shipped default on every place. */
    private void assertAppsRowAtShippedDefault() {
        PlaceLayoutStore places = places();
        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " portrait", RowPlacement.BOTTOM,
                rowOf(places.resolve(PlaceOrientation.PORTRAIT), com.termux.app.place.Element.APPS));
            assertEquals(place + " landscape", RowPlacement.LEFT,
                rowOf(places.resolve(PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
        }
    }

    // ------------------------------------------------------------------------ the three modes

    @Test
    public void freshInstallIsHomeMode() {
        assertFalse(LauncherUseCaseMode.isTerminalOnly(preferences));
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void theDisplayModeIsOfferedOnlyWithAServerInTheBuild() {
        assertEquals(Arrays.asList(LauncherUseCaseMode.MODE_TERMINAL, LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.MODE_DISPLAY), LauncherUseCaseMode.offeredModes(DISPLAY_OFFERED));
        assertEquals(Arrays.asList(LauncherUseCaseMode.MODE_TERMINAL, LauncherUseCaseMode.MODE_HOME),
            LauncherUseCaseMode.offeredModes(NO_DISPLAY));
        // A stored display mode reads as home in such a build, and is applied as home.
        preferences.setAppLauncherUseCaseMode(LauncherUseCaseMode.MODE_DISPLAY);
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, NO_DISPLAY));
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_DISPLAY, NO_DISPLAY);
        assertFalse(preferences.isX11DisplayEnabled());
    }

    @Test
    public void terminalOnlyDisablesEveryHomeSurfaceTheDisplayAndShowsInRecents() {
        preferences.setShowInRecentsWhenNotDefaultEnabled(false);
        preferences.setX11DisplayEnabled(true);

        assertTrue(LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL,
            DISPLAY_OFFERED));

        assertAppsRowEverywhere(RowPlacement.HIDDEN);
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertFalse(preferences.isAppLauncherDrawerEnabled());
        assertFalse(preferences.isAppLauncherWidgetPaneEnabled());
        assertFalse(preferences.isX11DisplayEnabled());
        assertTrue(preferences.isShowInRecentsWhenNotDefaultEnabled());
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
        assertTrue(LauncherUseCaseMode.isTerminalOnly(preferences));
    }

    /** The Layout editor's own A-Z key wins over the global switch, so the mode writes both. */
    @Test
    public void terminalOnlyWritesTheAzRowIntoEveryOrientationsLayout() {
        for (PlaceOrientation orientation : PlaceOrientation.values())
            places().setAzRowShown(orientation, true);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);

        for (PlaceOrientation orientation : PlaceOrientation.values())
            assertFalse(orientation.name(), places().azRowShown(orientation));

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME, DISPLAY_OFFERED);

        for (PlaceOrientation orientation : PlaceOrientation.values())
            assertTrue(orientation.name(), places().azRowShown(orientation));
    }

    @Test
    public void terminalOnlyLeavesTheExtraKeysRowAlone() {
        preferences.setAppLauncherExtraKeysRowEnabled(true);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);

        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());
    }

    @Test
    public void displayModeTurnsTheDisplayOnAndHomeModeTurnsItOff() {
        assertTrue(LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_DISPLAY,
            DISPLAY_OFFERED));
        assertTrue(preferences.isX11DisplayEnabled());
        // The home surfaces were on already; the display is the only switch that moved.
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
        assertAppsRowAtShippedDefault();

        assertTrue(LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME,
            DISPLAY_OFFERED));
        assertFalse(preferences.isX11DisplayEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
    }

    @Test
    public void switchingBackPutsTheAppsRowBackAtItsShippedDefaultOnEveryPlace() {
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherDrawerEnabled(true);
        preferences.setAppLauncherWidgetPaneEnabled(false);
        preferences.setShowInRecentsWhenNotDefaultEnabled(false);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        assertAppsRowEverywhere(RowPlacement.HIDDEN);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME, DISPLAY_OFFERED);

        assertAppsRowAtShippedDefault();
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertFalse(preferences.isAppLauncherWidgetPaneEnabled());
        assertFalse(preferences.isShowInRecentsWhenNotDefaultEnabled());
    }

    @Test
    public void terminalToDisplayRestoresTheHomeSurfacesAndTurnsTheDisplayOn() {
        preferences.setAppLauncherDrawerEnabled(false);
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_DISPLAY, DISPLAY_OFFERED);

        assertAppsRowAtShippedDefault();
        assertFalse("the snapshot's drawer choice", preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
        assertTrue(preferences.isX11DisplayEnabled());
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void reapplyingTerminalOnlyKeepsTheOriginalSnapshot() {
        preferences.setAppLauncherAzRowEnabled(false);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        assertFalse(LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL,
            DISPLAY_OFFERED));
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME, DISPLAY_OFFERED);

        assertAppsRowAtShippedDefault();
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void switchingBackWithNoSnapshotEnablesEverySurface() {
        preferences.setAppLauncherUseCaseMode(LauncherUseCaseMode.MODE_TERMINAL);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherDrawerEnabled(false);
        preferences.setAppLauncherWidgetPaneEnabled(false);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME, DISPLAY_OFFERED);

        assertAppsRowAtShippedDefault();
        assertTrue(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void reenablingOneSurfaceKeepsTheChosenMode() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);

        preferences.setAppLauncherDrawerEnabled(true);

        assertTrue(LauncherUseCaseMode.isTerminalOnly(preferences));
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
    }

    /** The bug this mode was rewritten for: a mid-mode surface flip must not eat the snapshot. */
    @Test
    public void surfaceFlipInTerminalModeDoesNotCostTheOtherSurfacesOnTheWayBack() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        preferences.setAppLauncherAzRowEnabled(true);
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME, DISPLAY_OFFERED);

        assertAppsRowAtShippedDefault();
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void anUnknownModeIsIgnored() {
        assertFalse(LauncherUseCaseMode.applyMode(preferences, "kiosk", DISPLAY_OFFERED));
        assertFalse(LauncherUseCaseMode.applyMode(preferences, null, DISPLAY_OFFERED));
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
    }

    // -------------------------------------------------------------------------- the migration

    @Test
    public void theOldLauncherValueMeansDisplayWhenTheDisplayWasOnUnderIt() {
        preferences.setAppLauncherUseCaseMode("launcher");
        preferences.setX11DisplayEnabled(true);
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
        // ...and home in a build that has no display to be on.
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, NO_DISPLAY));
    }

    @Test
    public void theOldLauncherValueMeansHomeOtherwise() {
        preferences.setAppLauncherUseCaseMode("launcher");
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void anUnknownStoredValueMeansHome() {
        preferences.setAppLauncherUseCaseMode("kiosk");
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.currentMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void migrationWritesTheMappedValueOnceAndLeavesARecognisedOneAlone() {
        preferences.setAppLauncherUseCaseMode("launcher");
        preferences.setX11DisplayEnabled(true);
        LauncherUseCaseMode.migrateIfNeeded(preferences, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY, preferences.getAppLauncherUseCaseMode());

        // Turning the display off afterwards does not re-map: the value is a real mode now.
        preferences.setX11DisplayEnabled(false);
        LauncherUseCaseMode.migrateIfNeeded(preferences, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY, preferences.getAppLauncherUseCaseMode());

        preferences.setAppLauncherUseCaseMode(LauncherUseCaseMode.MODE_TERMINAL);
        LauncherUseCaseMode.migrateIfNeeded(preferences, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL, preferences.getAppLauncherUseCaseMode());
    }

    @Test
    public void applyingTheModeAnOldValueAlreadyMeansOnlyRecordsIt() {
        preferences.setAppLauncherUseCaseMode("launcher");
        preferences.setAppLauncherDrawerEnabled(false);

        assertFalse(LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_HOME,
            DISPLAY_OFFERED));

        assertEquals(LauncherUseCaseMode.MODE_HOME, preferences.getAppLauncherUseCaseMode());
        assertFalse("no surface was touched", preferences.isAppLauncherDrawerEnabled());
    }

    // ------------------------------------------------------------------ what the row says

    @Test
    public void theRowNamesTheStoredModeWhileTheSwitchesStillSpellIt() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_DISPLAY, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY,
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL,
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void aHomeSurfaceBroughtBackUnderTerminalModeReadsAsCustom() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        preferences.setAppLauncherDrawerEnabled(true);
        assertNull(LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.modeMatchingSwitches(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void oneHomeSurfaceSwitchedOffUnderHomeModeIsStillAHomeScreen() {
        preferences.setAppLauncherWidgetPaneEnabled(false);
        assertEquals(LauncherUseCaseMode.MODE_HOME,
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void everyHomeSurfaceSwitchedOffUnderHomeModeReadsAsCustom() {
        preferences.setAppLauncherDrawerEnabled(false);
        preferences.setAppLauncherWidgetPaneEnabled(false);
        preferences.setAppLauncherAzRowEnabled(false);
        for (PlaceOrientation orientation : PlaceOrientation.values())
            places().setAppsRow(orientation, RowPlacement.HIDDEN);
        assertNull(LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL,
            LauncherUseCaseMode.modeMatchingSwitches(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void theDisplayOnWithNoHomeScreenMatchesNoPreset() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        preferences.setX11DisplayEnabled(true);
        assertNull(LauncherUseCaseMode.modeMatchingSwitches(preferences, DISPLAY_OFFERED));
        assertNull(LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
    }

    // --------------------------------------------------------- the Display switch on its own

    @Test
    public void theDisplaySwitchMovesTheModeBetweenHomeAndDisplay() {
        preferences.setX11DisplayEnabled(true);
        LauncherUseCaseMode.onDisplaySwitched(preferences, true, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY, preferences.getAppLauncherUseCaseMode());
        assertEquals(LauncherUseCaseMode.MODE_DISPLAY,
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));

        preferences.setX11DisplayEnabled(false);
        LauncherUseCaseMode.onDisplaySwitched(preferences, false, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_HOME, preferences.getAppLauncherUseCaseMode());
    }

    @Test
    public void theDisplaySwitchLeavesATerminalOnlyChoiceAlone() {
        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL, DISPLAY_OFFERED);
        preferences.setX11DisplayEnabled(true);
        LauncherUseCaseMode.onDisplaySwitched(preferences, true, DISPLAY_OFFERED);
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL, preferences.getAppLauncherUseCaseMode());
        assertNull("terminal plus display is no preset",
            LauncherUseCaseMode.summaryMode(preferences, DISPLAY_OFFERED));
    }

    @Test
    public void azRowFollowsTheAppsRowButKeepsItsStoredChoice() {
        preferences.setAppLauncherAzRowEnabled(true);
        preferences.setAppLauncherAppsRowEnabled(false);

        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherAzRowChosen());

        preferences.setAppLauncherAppsRowEnabled(true);

        assertTrue(preferences.isAppLauncherAzRowEnabled());
    }

    @Test
    public void malformedSnapshotFallsBackToDefaults() {
        assertEquals(null, LauncherUseCaseMode.parseSnapshot(""));
        assertEquals(null, LauncherUseCaseMode.parseSnapshot("1,0"));
        assertEquals(null, LauncherUseCaseMode.parseSnapshot("1,0,1,yes"));
    }

    /**
     * One element's slot as the terse row placement the old model spelled. Only the tests speak
     * it now: the model itself keeps the slot, so a bar on the top edge is a top edge rather than
     * being folded into the bottom, and this helper says so by refusing to name one.
     */
    private static RowPlacement rowOf(com.termux.app.place.PlaceLayout layout,
                                                  com.termux.app.place.Element element) {
        com.termux.app.place.Slot slot = layout.slot(element);
        if (slot.hidden) return RowPlacement.HIDDEN;
        switch (slot.edge) {
            case LEFT: return RowPlacement.LEFT;
            case RIGHT: return RowPlacement.RIGHT;
            case BOTTOM: return RowPlacement.BOTTOM;
            default: throw new AssertionError("no row placement for " + slot);
        }
    }
}
