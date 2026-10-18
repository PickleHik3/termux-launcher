package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
 * A Frost or Tint drag in the Look editor: each tick is one light glass pass at most, and the
 * work a tick cannot afford (the full glass pass with its backdrop re-cut, keyboard face sampling
 * and palette, and the window bar's full refresh) runs not at all until the release, then once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class GlassDragPreviewTest {

    private static final int TICKS = 12;

    private Activity activity;
    private TermuxAppSharedPreferences preferences;
    private SurfaceEditorController controller;

    /** The host's light flag as the editor last set it. */
    private boolean light;
    private int lightGlassPasses;
    private int fullGlassPasses;
    private int lightBarRefreshes;
    private int fullBarRefreshes;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        SharedPreferences store = activity.getSharedPreferences("glass-drag-preview",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, store, null);

        // The activity's root, so the editor coalesces its passes to the animation frame.
        FrameLayout activityRoot = new FrameLayout(themed);
        activityRoot.setId(R.id.activity_termux_root_view);
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
        activityRoot.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(activityRoot, new ViewGroup.LayoutParams(1080, 2000));

        controller = new SurfaceEditorController(host(themed));
        controller.present(EditorMode.LOOK, null, null, 0f, 0f, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        resetCounts();
    }

    @Test
    public void aTintDragTicksLightAndTheReleasePaysTheFullPassOnce() {
        drag(Control.TINT, 10);

        assertTrue("the preview follows the drag", lightGlassPasses >= 1);
        assertTrue("one light pass per tick at most, was " + lightGlassPasses,
            lightGlassPasses <= TICKS);
        assertEquals("no full glass pass mid-drag", 0, fullGlassPasses);
        assertEquals("no full window bar refresh mid-drag", 0, fullBarRefreshes);
        assertTrue(light);

        release();

        assertFalse("the release leaves the light pass", light);
        assertEquals("the release runs the full glass pass once", 1, fullGlassPasses);
        assertEquals("and the window bar's full refresh once", 1, fullBarRefreshes);
    }

    @Test
    public void aFrostDragTicksLightAndTheReleasePaysTheFullPassOnce() {
        drag(Control.GLASS, 5);

        assertTrue(lightGlassPasses >= 1);
        // Frost writes Blur and Grain each tick; the two still share the tick's one pass.
        assertTrue("one light pass per tick at most, was " + lightGlassPasses,
            lightGlassPasses <= TICKS);
        assertEquals(0, fullGlassPasses);
        assertEquals(0, fullBarRefreshes);

        release();

        assertFalse(light);
        assertEquals(1, fullGlassPasses);
        assertEquals(1, fullBarRefreshes);
    }

    @Test
    public void aTapOnTheSliderIsAFullPass() {
        listener().onSlider(Control.TINT, 60, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertFalse(light);
        assertEquals(0, lightGlassPasses);
        assertEquals(1, fullGlassPasses);
    }

    // ------------------------------------------------------------------------------- the drive

    private void drag(Control control, int from) {
        for (int i = 0; i < TICKS; i++) {
            listener().onSlider(control, from + i * 3, true);
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        }
    }

    private void release() {
        listener().onSliderReleased();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private AppearanceEditorPanel.Listener listener() {
        Object panel = ReflectionHelpers.getField(controller, "mPanel");
        return ReflectionHelpers.getField(panel, "mListener");
    }

    private void resetCounts() {
        lightGlassPasses = 0;
        fullGlassPasses = 0;
        lightBarRefreshes = 0;
        fullBarRefreshes = 0;
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
                    case "setGlassDragPreview":
                        light = (Boolean) args[0];
                        return null;
                    case "applyGlassPreview":
                        if (light) lightGlassPasses++;
                        else fullGlassPasses++;
                        return null;
                    case "refreshTerminalWindowBar":
                        if (light) lightBarRefreshes++;
                        else fullBarRefreshes++;
                        return null;
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
