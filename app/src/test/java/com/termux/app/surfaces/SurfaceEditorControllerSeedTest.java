package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The Look slider's move to Custom ({@link SurfaceEditorController.SessionCustom}): Custom starts
 * from the Look on screen, this visit's Custom values come back after a Look, and the saved
 * Custom is never what the move applies.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceEditorControllerSeedTest {

    private static final int TWEAKED_BLUR = 63;
    private static final int TWEAKED_DOCK_OPACITY = 77;

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;
    private SurfacePresets.Preset clear;
    private SurfacePresets.Preset mist;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-editor-seed-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        clear = AppearanceLooks.presetForStop(SurfacePresets.presets(), 0);
        mist = AppearanceLooks.presetForStop(SurfacePresets.presets(), 1);
        assertNotNull(clear);
        assertNotNull(mist);
    }

    @Test
    public void movingToCustomFromAnOpenedLookWritesNothing() {
        SurfacePresets.apply(preferences, clear);
        Map<String, ?> before = new HashMap<>(store.getAll());
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.enter(preferences);

        assertEquals(before, store.getAll());
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void customTweaksComeBackAfterAVisitToALook() {
        SurfacePresets.apply(preferences, clear);
        tweak();
        String tweaked = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.leave(preferences);
        SurfacePresets.apply(preferences, clear);
        assertNotEquals(tweaked, AppearanceSnapshot.signatureOf(preferences));
        session.enter(preferences);

        assertEquals(tweaked, AppearanceSnapshot.signatureOf(preferences));
        assertEquals(TWEAKED_BLUR, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
    }

    @Test
    public void customTweaksComeBackAlsoWhenTheyAreTheSavedCustom() {
        SurfacePresets.apply(preferences, clear);
        tweak();
        SurfacePresets.saveCustom(preferences);
        String tweaked = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.leave(preferences);
        SurfacePresets.apply(preferences, clear);
        session.enter(preferences);

        assertEquals(tweaked, AppearanceSnapshot.signatureOf(preferences));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
    }

    @Test
    public void theSavedCustomIsNotWhatTheMoveApplies() {
        SurfacePresets.apply(preferences, mist);
        tweak();
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.apply(preferences, clear);
        String onScreen = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.enter(preferences);

        assertEquals(onScreen, AppearanceSnapshot.signatureOf(preferences));
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void undoForgetsTheVisitsCustomValues() {
        SurfacePresets.apply(preferences, clear);
        tweak();
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();
        session.leave(preferences);
        SurfacePresets.apply(preferences, mist);

        session.clear();
        session.enter(preferences);

        assertTrue(SurfacePresets.matches(preferences, mist));
    }

    @Test
    public void anUntouchedCustomSeedsFromTheNextLook() {
        SurfacePresets.apply(preferences, clear);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();
        session.enter(preferences);

        session.leave(preferences);
        SurfacePresets.apply(preferences, mist);
        session.enter(preferences);

        assertTrue("left as seeded, Custom is not this visit's work", SurfacePresets.matches(preferences, mist));
    }

    @Test
    public void aTouchedCustomComesBackOverTheNextLook() {
        SurfacePresets.apply(preferences, clear);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();
        session.enter(preferences);
        tweak();

        session.leave(preferences);
        SurfacePresets.apply(preferences, mist);
        session.enter(preferences);

        assertEquals(TWEAKED_BLUR, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
    }

    private void tweak() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, TWEAKED_BLUR);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TWEAKED_DOCK_OPACITY);
    }
}
