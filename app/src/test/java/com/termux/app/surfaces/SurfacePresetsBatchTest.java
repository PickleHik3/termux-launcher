package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.shared.settings.preferences.SharedPreferencesPreview;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SurfacePresets#apply} lands a look as one editor, and leaves the store holding exactly
 * what applying it one setter at a time ({@link SurfacePresets#applyEach}) leaves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfacePresetsBatchTest {

    private final Context context = RuntimeEnvironment.getApplication().getApplicationContext();
    private int stores;

    @Test
    public void everyLookLeavesTheSameStoreBatchedOrOneByOne() {
        for (SurfacePresets.Preset preset : looksUnderTest()) {
            SharedPreferences batched = seededStore();
            SharedPreferences reference = seededStore();
            SurfacePresets.apply(new TermuxAppSharedPreferences(context, batched, null), preset);
            SurfacePresets.applyEach(new TermuxAppSharedPreferences(context, reference, null), preset);
            assertEquals(preset.id, reference.getAll(), batched.getAll());
        }
    }

    @Test
    public void aLookIsOneEditor() {
        AtomicInteger edits = new AtomicInteger();
        SharedPreferences store = countingEdits(seededStore(), edits);
        SurfacePresets.apply(new TermuxAppSharedPreferences(context, store, null),
            SurfacePresets.presets().get(1));
        assertEquals(1, edits.get());
    }

    @Test
    public void theBatchReadsItsOwnWritesAndRemovals() {
        SharedPreferences store = seededStore();
        store.edit().putInt("kept", 1).putString("gone", "x").commit();
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        batch.edit().putInt("kept", 2).remove("gone").putBoolean("new", true).apply();

        assertEquals(2, batch.getInt("kept", 0));
        assertFalse(batch.contains("gone"));
        assertEquals("fallback", batch.getString("gone", "fallback"));
        assertTrue(batch.getBoolean("new", false));
        // Nothing reaches the store until the batch is applied.
        assertEquals(1, store.getInt("kept", 0));
        assertTrue(store.contains("gone"));

        batch.applyToStore();
        assertEquals(2, store.getInt("kept", 0));
        assertFalse(store.contains("gone"));
        assertTrue(store.getBoolean("new", false));
    }

    @Test
    public void aClearInTheBatchEmptiesTheStoreBeforeItsOwnChanges() {
        SharedPreferences store = seededStore();
        store.edit().putInt("old", 1).commit();
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        batch.edit().clear().putInt("fresh", 3).apply();
        assertFalse(batch.contains("old"));
        assertEquals(3, batch.getInt("fresh", 0));
        batch.applyToStore();
        assertEquals(new HashSet<>(java.util.Collections.singletonList("fresh")), store.getAll().keySet());
    }

    @Test
    public void aFrozenCopyKeepsWhatWasHeldWhileTheBatchGoesOn() {
        SharedPreferences store = seededStore();
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        assertTrue(batch.isEmpty());
        batch.edit().putInt("blur", 4).apply();
        SurfacePresets.Batch frozen = batch.frozen();
        batch.edit().putInt("blur", 25).apply();
        assertFalse(batch.isEmpty());
        assertEquals(4, frozen.getInt("blur", 0));
        assertEquals(25, batch.getInt("blur", 0));
    }

    /**
     * The Look slider under a finger: each Look crossed (and the session's Custom values put back
     * on the way) goes into one batch that every reader sees through the preview while nothing is
     * stored; the release stores exactly what stepping through them one by one stores.
     */
    @Test
    public void aDragAcrossTheLooksIsShownUnstoredThenStoredAsTheStepsWouldStoreIt() {
        List<SurfacePresets.Preset> looks = SurfacePresets.presets();
        SharedPreferences dragged = seededStore();
        SharedPreferences stepped = seededStore();
        TermuxAppSharedPreferences live = new TermuxAppSharedPreferences(context, dragged, null);
        TermuxAppSharedPreferences reference = new TermuxAppSharedPreferences(context, stepped, null);
        AppearanceSnapshot session = AppearanceSnapshot.capture(live);
        Map<String, ?> before = new LinkedHashMap<>(dragged.getAll());

        SurfacePresets.Batch batch = new SurfacePresets.Batch(dragged);
        TermuxAppSharedPreferences held = SurfacePresets.writingInto(live, batch);
        try {
            for (SurfacePresets.Preset look : looks) {
                SurfacePresets.apply(held, look);
                SurfacePresets.apply(reference, look);
                SharedPreferencesPreview.show(dragged, batch.frozen());
                // Every reader, through any handle on the store, draws the Look under the finger.
                assertTrue(look.id, SurfacePresets.matches(
                    new TermuxAppSharedPreferences(context, dragged, null), look));
                // And none of it is stored.
                assertEquals(look.id, before, dragged.getAll());
            }
            session.restore(held);
            session.restore(reference);
            SharedPreferencesPreview.show(dragged, batch.frozen());
            assertEquals(session.signature(), AppearanceSnapshot.signatureOf(live));

            batch.applyToStore();
        } finally {
            SharedPreferencesPreview.clear();
        }
        assertEquals(stepped.getAll(), dragged.getAll());
        assertEquals(session.signature(), AppearanceSnapshot.signatureOf(live));
    }

    /** The four shipped looks, plus a saved Custom that detaches cells and names custom-only keys. */
    private List<SurfacePresets.Preset> looksUnderTest() {
        List<SurfacePresets.Preset> looks = new ArrayList<>(SurfacePresets.presets());
        TermuxAppSharedPreferences tuned = new TermuxAppSharedPreferences(context, seededStore(), null);
        tuned.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, 33);
        tuned.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR, 7);
        tuned.setInAppKeyboardKeyOpacity(61);
        tuned.setWallpaperBackdropDim(12);
        Map<String, Object> look = new LinkedHashMap<>(SurfacePresets.captureLook(tuned));
        // A look stored before corners were Layout's still names them; apply must skip them alike.
        look.put(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, 9);
        looks.add(new SurfacePresets.Preset(SurfacePresets.CUSTOM_ID,
            R.string.termux_surface_preset_custom, look));
        return looks;
    }

    /** A fresh store with some detached cells, a non-default Base and Style, the same each time. */
    private SharedPreferences seededStore() {
        SharedPreferences store = context.getSharedPreferences(
            "surface-presets-batch-" + (stores++), Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, store, null);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 25);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 77);
        prefs.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, 40);
        prefs.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        SoftWallpaper.set(prefs, true);
        return store;
    }

    private static SharedPreferences countingEdits(SharedPreferences store, AtomicInteger edits) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                if ("edit".equals(method.getName())) edits.incrementAndGet();
                return method.invoke(store, args);
            });
    }
}
