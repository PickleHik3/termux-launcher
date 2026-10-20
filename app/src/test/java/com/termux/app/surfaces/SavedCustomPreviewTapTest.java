package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;
import com.termux.app.surfaces.AppearanceSurfaceController.PageId;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;

/**
 * The saved Custom through the editor's own entry points, as the device repro drives them: a
 * visit that opens on Clear, taps the keyboard in the preview (Custom seeded from Clear), changes
 * nothing and presses Done; then the next visit, opened where the surface remembers the person
 * left, moves to Custom. The saved Custom must be untouched by the first visit and be what the
 * second one shows at Custom.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SavedCustomPreviewTapTest {

    private static final int USER_KEYBOARD_OPACITY = 90;
    private static final int USER_KEYBOARD_TINT = 100;

    private Activity activity;
    private TermuxAppSharedPreferences preferences;
    private SurfaceEditorController controller;
    private SurfacePresets.Preset clear;
    /** The live look as the user saved it as Custom. */
    private String savedLook;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        SharedPreferences store = activity.getSharedPreferences("saved-custom-preview-tap",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, store, null);
        clear = AppearanceLooks.presetForStop(SurfacePresets.presets(preferences), 0);
        assertNotNull(clear);

        FrameLayout root = new FrameLayout(themed);
        root.setId(R.id.terminal_root_container);
        FrameLayout overlay = new FrameLayout(themed);
        overlay.setId(R.id.surface_tuning_gesture_overlay);
        int[] groups = {R.id.surface_tuning_wallpaper_gesture_group,
            R.id.surface_tuning_canvas_gesture_group, R.id.surface_tuning_status_gesture_group,
            R.id.surface_tuning_dock_gesture_group, R.id.surface_tuning_keyboard_gesture_group};
        for (int id : groups) {
            View group = new View(themed);
            group.setId(id);
            overlay.addView(group, new FrameLayout.LayoutParams(100, 100));
        }
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(root, new ViewGroup.LayoutParams(1080, 2000));

        controller = new SurfaceEditorController(host(themed));

        // The user's Custom, saved: keyboard Glass 0, Opacity 90, Tint 100, as the repro sets it.
        SurfacePresets.applyLook(preferences, clear);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            USER_KEYBOARD_OPACITY);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT,
            USER_KEYBOARD_TINT);
        SurfacePresets.saveCustom(preferences);
        savedLook = AppearanceSnapshot.signatureOf(preferences);
        // Then a visit chose Clear and pressed Done.
        SurfacePresets.applyLook(preferences, clear);
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void aPreviewTapAtALookThenDoneLeavesTheSavedCustomAndTheNextVisitShowsIt() {
        String saved = preferences.getSurfaceCustomPreset();

        // Visit: opens on Clear, the keyboard tapped in the preview, nothing changed, Done.
        open(null);
        assertEquals(0, stop());
        activity.findViewById(R.id.surface_tuning_keyboard_gesture_group).performClick();
        assertTrue("the tap moves the slider to Custom", AppearanceLooks.isCustomStop(stop()));
        AppearanceReturnState left = leaveByDone();

        assertEquals("Done at an untouched seed keeps the saved Custom", saved,
            preferences.getSurfaceCustomPreset());
        assertTrue(SurfacePresets.matches(preferences, clear));

        // Next visit, where the surface remembers it was left; the slider moves to Custom.
        open(left);
        lookStop(AppearanceLooks.CUSTOM_STOP);

        assertTrue(AppearanceLooks.isCustomStop(stop()));
        assertFalse("at Custom the next visit shows the saved Custom, not Clear",
            SurfacePresets.matches(preferences, clear));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(USER_KEYBOARD_OPACITY,
            preferences.getSurfaceOverrideValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(USER_KEYBOARD_TINT,
            preferences.getSurfaceOverrideValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT));
        assertEquals(savedLook, AppearanceSnapshot.signatureOf(preferences));
        assertEquals(saved, preferences.getSurfaceCustomPreset());
    }

    @Test
    public void aTouchedCustomIsStillRememberedAndSaved() {
        open(null);
        activity.findViewById(R.id.surface_tuning_keyboard_gesture_group).performClick();
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, 33);
        AppearanceReturnState left = leaveByDone();

        assertFalse(SurfacePresets.matches(preferences, clear));
        SurfacePresets.Preset custom = SurfacePresets.custom(preferences);
        assertNotNull(custom);

        open(left);
        assertTrue("a Custom the user made reopens at Custom", AppearanceLooks.isCustomStop(stop()));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(33, preferences.getSurfaceOverrideValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
    }

    // ------------------------------------------------------------------------------- the drive

    private void open(AppearanceReturnState restore) {
        controller.restoreNext(restore);
        controller.present(EditorMode.LOOK, null, null, 0f, 0f, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** What the surface does for Done: remembers where the editor stands, then Done, then off. */
    private AppearanceReturnState leaveByDone() {
        AppearanceReturnState.Builder where = new AppearanceReturnState.Builder(PageId.LOOK);
        controller.describe(where);
        AppearanceReturnState state = AppearanceReturnState.parse(
            where.build(System.currentTimeMillis()).serialize());
        controller.done();
        controller.dismiss(-1f, 0f, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return state;
    }

    /** A tap on the Look slider's stop, as the panel reports it. */
    private void lookStop(int stop) {
        Object panel = ReflectionHelpers.getField(controller, "mPanel");
        AppearanceEditorPanel.Listener listener = ReflectionHelpers.getField(panel, "mListener");
        listener.onLookStop(stop, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private int stop() {
        return ReflectionHelpers.getField(controller, "mStop");
    }

    private SurfaceEditorController.Host host(Context themed) {
        PlaceLayout place = place();
        return (SurfaceEditorController.Host) Proxy.newProxyInstance(
            SurfaceEditorController.Host.class.getClassLoader(),
            new Class<?>[] {SurfaceEditorController.Host.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "context": return themed;
                    case "findView": return activity.findViewById((Integer) args[0]);
                    case "preferences": return preferences;
                    case "placeLayout": return place;
                    case "isInAppKeyboardShown": return true;
                    case "editorPaintsWallpaper": return false;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "host";
                    default: break;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == float.class) return 0f;
                if (type == long.class) return 0L;
                return null;
            });
    }

    private static PlaceLayout place() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(PlaceLayout.Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, new Slot(false, PlaceLayout.Edge.BOTTOM, 0));
        slots.put(Element.AZ, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 1));
        slots.put(Element.EXTRA_KEYS, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 2));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }
}
