package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * The editor across a configuration relaunch (new wallpaper colours, the theme, the font scale):
 * Android rebuilds the activity, and with it the editor, while the person is editing. The live
 * values are in the preferences already; what the old editor alone knew is the state at open,
 * which Undo and the unsaved question measure from. The next activity's editor continues that
 * session ({@link SurfaceEditorController#carrySession}, {@link SurfaceEditorController#adoptSessionNext})
 * instead of taking the edited look as its new starting point.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SurfaceEditorRelaunchTest {

    private static final int EDITED_KEYBOARD_OPACITY = 33;

    private TermuxAppSharedPreferences preferences;
    private SurfacePresets.Preset clear;

    @Before
    public void setUp() {
        Activity probe = Robolectric.buildActivity(Activity.class).setup().get();
        SharedPreferences store = probe.getSharedPreferences("surface-editor-relaunch",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(probe, store, null);
        clear = AppearanceLooks.presetForStop(SurfacePresets.presets(preferences), 0);
        assertNotNull(clear);
        SurfacePresets.applyLook(preferences, clear);
    }

    @Test
    public void theRelaunchedEditorKeepsTheUnsavedEditAndItsUndo() {
        String atOpen = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController before = editorOnNewActivity();
        present(before);
        int stopAtOpen = stop(before);
        // An edit, written through to the preferences as every editor write is.
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            EDITED_KEYBOARD_OPACITY);
        String edited = AppearanceSnapshot.signatureOf(preferences);
        assertNotEquals(atOpen, edited);
        assertTrue(isDirty(before));

        AppearanceSurfaceController.EditorSession carried = before.carrySession();
        assertNotNull("an editor that is up has a session to carry", carried);

        // The activity Android builds next, with its own editor.
        SurfaceEditorController after = editorOnNewActivity();
        after.adoptSessionNext(carried);
        present(after);

        assertEquals("the edit is still on screen", edited,
            AppearanceSnapshot.signatureOf(preferences));
        assertTrue("and still unsaved: the question and Undo are as they were", isDirty(after));
        assertEquals(stopAtOpen, stop(after));

        after.undo();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals("Undo goes back to the look the first editor opened on", atOpen,
            AppearanceSnapshot.signatureOf(preferences));
        assertTrue(SurfacePresets.matches(preferences, clear));
        assertFalse(isDirty(after));
    }

    @Test
    public void withoutTheSessionTheEditWouldBecomeTheNewStartingPoint() {
        SurfaceEditorController before = editorOnNewActivity();
        present(before);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            EDITED_KEYBOARD_OPACITY);
        String edited = AppearanceSnapshot.signatureOf(preferences);

        // What a relaunch did before: a new editor, measuring from whatever is live.
        SurfaceEditorController after = editorOnNewActivity();
        present(after);
        after.undo();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertFalse("nothing reads as unsaved", isDirty(after));
        assertEquals("and Undo cannot reach the look at the first open", edited,
            AppearanceSnapshot.signatureOf(preferences));
    }

    @Test
    public void aSessionIsCarriedOnlyOnceAndNotByAClosedEditor() {
        SurfaceEditorController before = editorOnNewActivity();
        assertNull("nothing presented, nothing to carry", before.carrySession());
        present(before);
        AppearanceSurfaceController.EditorSession carried = before.carrySession();

        SurfaceEditorController after = editorOnNewActivity();
        after.adoptSessionNext(carried);
        // Still waiting to be presented: a second relaunch now hands the same session on.
        assertEquals(carried, after.carrySession());
        present(after);
        after.dismiss(-1f, 0f, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertNull("a dismissed editor carries nothing", after.carrySession());
    }

    // ------------------------------------------------------------------------------- the drive

    private static void present(SurfaceEditorController editor) {
        editor.present(EditorMode.LOOK, null, null, 0f, 0f, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private static boolean isDirty(SurfaceEditorController editor) {
        return ReflectionHelpers.callInstanceMethod(editor, "isDirty");
    }

    private static int stop(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mStop");
    }

    /** An activity with the views the editor looks for, and an editor over it. */
    private SurfaceEditorController editorOnNewActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
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
        return new SurfaceEditorController(host(activity, themed));
    }

    private SurfaceEditorController.Host host(Activity activity, Context themed) {
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
