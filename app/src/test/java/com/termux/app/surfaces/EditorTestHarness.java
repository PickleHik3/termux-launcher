package com.termux.app.surfaces;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.robolectric.Robolectric;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;

/**
 * An activity with the views the Appearance editor looks for (the launcher's container, the
 * gesture overlay and its groups, the activity's root), and an editor over it whose host answers
 * the little the editor needs and defaults the rest. For the editor's controller tests.
 */
final class EditorTestHarness {

    /** What a test may answer for a host method before the defaults; null to fall through. */
    interface Answers {
        @Nullable Object answer(@NonNull String method, @Nullable Object[] args);
    }

    final Activity activity;
    final Context themed;
    final SurfaceEditorController editor;

    EditorTestHarness(@NonNull TermuxAppSharedPreferences preferences, @Nullable Answers answers) {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
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
        View outline = new View(themed);
        outline.setId(R.id.surface_editor_selection_outline);
        overlay.addView(outline, new FrameLayout.LayoutParams(100, 100));
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        activityRoot.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(activityRoot, new ViewGroup.LayoutParams(1080, 2000));
        editor = new SurfaceEditorController(host(preferences, answers));
    }

    private SurfaceEditorController.Host host(@NonNull TermuxAppSharedPreferences preferences,
                                              @Nullable Answers answers) {
        PlaceLayout place = place();
        return (SurfaceEditorController.Host) Proxy.newProxyInstance(
            SurfaceEditorController.Host.class.getClassLoader(),
            new Class<?>[] {SurfaceEditorController.Host.class},
            (proxy, method, args) -> {
                if (answers != null) {
                    Object answer = answers.answer(method.getName(), args);
                    if (answer != null)
                        return answer;
                }
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

    static PlaceLayout place() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(PlaceLayout.Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, new Slot(false, PlaceLayout.Edge.BOTTOM, 0));
        slots.put(Element.AZ, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 1));
        slots.put(Element.EXTRA_KEYS, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 2));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }
}
