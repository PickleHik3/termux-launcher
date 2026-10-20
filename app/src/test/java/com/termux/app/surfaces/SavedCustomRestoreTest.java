package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The saved Custom across editor visits: Custom set up with keyboard and terminal overrides and
 * left with Done comes back whole on a later visit's move to Custom, after a Look was picked and
 * saved in between; picking the Look again resets everything to it and leaves the saved Custom
 * alone. Each visit is a fresh {@link SurfaceEditorController.SessionCustom} over a fresh
 * preferences object on the same store, as after a restart.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SavedCustomRestoreTest {

    private static final int GLASS = 0;
    private static final int OPACITY = 90;
    private static final int TINT = 100;
    private static final int KEYS = 0;

    private Context context;
    private SharedPreferences store;
    private SurfacePresets.Preset clear;
    private SurfacePresets.Preset mist;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("saved-custom-restore-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        clear = AppearanceLooks.presetForStop(SurfacePresets.presets(), 0);
        mist = AppearanceLooks.presetForStop(SurfacePresets.presets(), 1);
        assertNotNull(clear);
        assertNotNull(mist);
    }

    @Test
    public void customComesBackWholeAcrossVisitsWhileNoLookIsSaved() {
        // Visit 1: from Mist to Custom, the keyboard and the terminal tuned, Done.
        TermuxAppSharedPreferences first = freshPreferences();
        SurfacePresets.applyLook(first, mist);
        SurfaceEditorController.SessionCustom visit1 = new SurfaceEditorController.SessionCustom();
        visit1.enter(first);
        tuneTheKeyboardAndTheTerminal(first);
        doneAtCustom(visit1, first);
        String custom = AppearanceSnapshot.signatureOf(first);

        // Visit 2: opens at Custom; Clear tried, back to Custom in the same visit, Done at Custom.
        TermuxAppSharedPreferences second = freshPreferences();
        SurfaceEditorController.SessionCustom visit2 = new SurfaceEditorController.SessionCustom();
        visit2.leave(second);
        SurfacePresets.applyLook(second, clear);
        assertOnClear(second);
        visit2.enter(second);
        assertEquals(custom, AppearanceSnapshot.signatureOf(second));
        doneAtCustom(visit2, second);

        // Visit 3: opens at Custom with the whole setup.
        TermuxAppSharedPreferences third = freshPreferences();
        SurfaceEditorController.SessionCustom visit3 = new SurfaceEditorController.SessionCustom();
        visit3.enter(third);
        assertEquals(custom, AppearanceSnapshot.signatureOf(third));
        assertTuned(third);
    }

    @Test
    public void aLookSavedIsCustomsNextStartingPoint() {
        // Visit 1: a Custom setup saved.
        TermuxAppSharedPreferences first = freshPreferences();
        SurfacePresets.applyLook(first, mist);
        SurfaceEditorController.SessionCustom visit1 = new SurfaceEditorController.SessionCustom();
        visit1.enter(first);
        tuneTheKeyboardAndTheTerminal(first);
        doneAtCustom(visit1, first);

        // Visit 2: Clear picked and saved: the saved Custom goes.
        TermuxAppSharedPreferences second = freshPreferences();
        SurfaceEditorController.SessionCustom visit2 = new SurfaceEditorController.SessionCustom();
        visit2.leave(second);
        SurfacePresets.applyLook(second, clear);
        doneAtLook(second);
        assertOnClear(second);
        assertNull(SurfacePresets.custom(second));

        // Visit 3: opens on Clear; Custom starts from Clear's values, not the old setup.
        TermuxAppSharedPreferences third = freshPreferences();
        SurfaceEditorController.SessionCustom visit3 = new SurfaceEditorController.SessionCustom();
        visit3.enter(third);
        assertOnClear(third);
        assertFalse("seeded from Clear, untouched", visit3.isTouched(third));
    }

    @Test
    public void theSavedCustomHoldsTheGlassPositionsAndKeys() {
        TermuxAppSharedPreferences prefs = freshPreferences();
        SurfacePresets.applyLook(prefs, mist);
        tuneTheKeyboardAndTheTerminal(prefs);
        SurfacePresets.saveCustom(prefs);

        java.util.Map<String, Object> blob =
            SurfacePresets.deserialize(prefs.getSurfaceCustomPreset());
        assertNotNull(blob);
        assertEquals(GLASS, blob.get(
            TermuxAppSharedPreferences.surfaceGlassPositionKey(SurfaceSlot.KEYBOARD)));
        assertEquals(GLASS, blob.get(
            TermuxAppSharedPreferences.surfaceGlassPositionKey(SurfaceSlot.CANVAS)));
        assertFalse("a forgotten position is not saved", blob.containsKey(
            TermuxAppSharedPreferences.surfaceGlassPositionKey(SurfaceSlot.DOCK)));
        assertEquals(KEYS, blob.get(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY));
        assertEquals(TINT, blob.get(TermuxAppSharedPreferences.surfaceOverrideKey(
            SurfaceSlot.CANVAS, SurfaceProperty.TINT)));
        assertTrue("positions never decide a match",
            SurfacePresets.matches(prefs, SurfacePresets.custom(prefs)));
    }

    @Test
    public void anOlderBlobWithoutTheNewFieldsStillLoads() {
        TermuxAppSharedPreferences prefs = freshPreferences();
        prefs.setSurfaceCustomPreset("{\"format_version\":6,\"surface_base_blur\":21,"
            + "\"" + TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.KEYBOARD,
                SurfaceProperty.OPACITY) + "\":" + OPACITY + "}");
        SurfacePresets.applyLook(prefs, clear);
        prefs.setInAppKeyboardKeyOpacity(37);
        prefs.setSurfaceGlassPosition(SurfaceSlot.DOCK, 12);

        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();
        session.enter(prefs);

        assertEquals(21, prefs.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertFalse(prefs.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(OPACITY, prefs.getInAppKeyboardBackgroundOpacity());
        assertTrue(prefs.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertEquals("no Keys saved reads as the theme's",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY, prefs.getInAppKeyboardKeyOpacity());
        assertEquals("no position saved reads as forgotten",
            -1, prefs.getSurfaceGlassPosition(SurfaceSlot.DOCK));
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS,
            prefs.getInAppKeyboardBlurRadiusRaw());
    }

    @Test
    public void undoPutsTheSavedCustomBackAsItWasAtOpen() {
        TermuxAppSharedPreferences prefs = freshPreferences();
        SurfacePresets.applyLook(prefs, mist);
        tuneTheKeyboardAndTheTerminal(prefs);
        SurfacePresets.saveCustom(prefs);
        SurfacePresets.applyLook(prefs, clear);
        // An editor visit opens on Clear.
        AppearanceSnapshot entry = AppearanceSnapshot.capture(prefs);
        String entryCustom = prefs.getSurfaceCustomPreset();
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.enter(prefs);
        prefs.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, 33);
        session.leave(prefs);
        SurfacePresets.applyLook(prefs, mist);
        assertFalse(entryCustom.equals(prefs.getSurfaceCustomPreset()));

        // Undo: the look at open and the saved Custom at open, in one step.
        entry.restore(prefs);
        prefs.setSurfaceCustomPreset(entryCustom);
        session.clear();

        assertTrue(SurfacePresets.matches(prefs, clear));
        session.enter(prefs);
        assertTuned(prefs);
        assertTrue(prefs.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
    }

    @Test
    public void aDragAcrossCustomSavesInItsBatch() {
        TermuxAppSharedPreferences prefs = freshPreferences();
        SurfacePresets.applyLook(prefs, mist);
        tuneTheKeyboardAndTheTerminal(prefs);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        TermuxAppSharedPreferences drag = SurfacePresets.writingInto(prefs, batch);

        session.leave(drag);
        SurfacePresets.applyLook(drag, clear);
        assertEquals("nothing stored before the release", "", prefs.getSurfaceCustomPreset());
        batch.applyToStore();

        assertTrue(SurfacePresets.matches(prefs, clear));
        SurfaceEditorController.SessionCustom next = new SurfaceEditorController.SessionCustom();
        next.enter(prefs);
        assertTuned(prefs);
    }

    private TermuxAppSharedPreferences freshPreferences() {
        return new TermuxAppSharedPreferences(context, store, null);
    }

    /** The editor's Done at a Look's stop (SurfaceEditorController.commitLook). */
    private static void doneAtLook(TermuxAppSharedPreferences prefs) {
        prefs.setSurfaceCustomPreset(null);
    }

    /** The editor's Done at the Custom stop. */
    private static void doneAtCustom(SurfaceEditorController.SessionCustom session,
                                     TermuxAppSharedPreferences prefs) {
        if (session.isTouched(prefs))
            SurfacePresets.saveCustom(prefs);
    }

    /** What Look › Custom's keyboard and terminal cards write: Glass 0, Opacity 90, Tint 100. */
    private static void tuneTheKeyboardAndTheTerminal(TermuxAppSharedPreferences prefs) {
        for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.KEYBOARD, SurfaceSlot.CANVAS}) {
            prefs.setSurfaceGlassPosition(slot, GLASS);
            prefs.detachSurfaceValue(slot, SurfaceProperty.BLUR, GlassControls.blurForGlass(GLASS));
            prefs.detachSurfaceValue(slot, SurfaceProperty.GRAIN,
                GlassControls.grainForGlass(GLASS));
            prefs.detachSurfaceValue(slot, SurfaceProperty.OPACITY, OPACITY);
            prefs.detachSurfaceValue(slot, SurfaceProperty.TINT, TINT);
        }
        prefs.setInAppKeyboardKeyOpacity(KEYS);
    }

    private static void assertTuned(TermuxAppSharedPreferences prefs) {
        for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.KEYBOARD, SurfaceSlot.CANVAS}) {
            for (SurfaceProperty property : new SurfaceProperty[] {SurfaceProperty.BLUR,
                SurfaceProperty.GRAIN, SurfaceProperty.OPACITY, SurfaceProperty.TINT})
                assertFalse(slot + " " + property, prefs.isSurfaceInheriting(slot, property));
            assertEquals(slot.toString(), GLASS, prefs.getSurfaceGlassPosition(slot));
        }
        assertEquals(OPACITY, prefs.getInAppKeyboardBackgroundOpacity());
        assertEquals(TINT, prefs.getInAppKeyboardTintStrength());
        assertEquals(OPACITY, prefs.getTerminalBackgroundOpacity());
        assertEquals(TINT, prefs.getSurfaceOverrideValue(SurfaceSlot.CANVAS, SurfaceProperty.TINT));
        assertEquals(KEYS, prefs.getInAppKeyboardKeyOpacity());
    }

    private void assertOnClear(TermuxAppSharedPreferences prefs) {
        assertTrue(SurfacePresets.matches(prefs, clear));
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values())
                assertTrue(slot + " " + property, prefs.isSurfaceInheriting(slot, property));
            assertEquals(slot.toString(), -1, prefs.getSurfaceGlassPosition(slot));
        }
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY,
            prefs.getInAppKeyboardKeyOpacity());
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS,
            prefs.getInAppKeyboardBlurRadiusRaw());
    }
}
