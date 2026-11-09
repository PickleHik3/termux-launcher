package com.termux.app.launcher.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings › Home › Gestures: what each gesture on a Home page's free area does. Pure, so the
 * Settings rows, the pane's detectors and the activity's dispatch all read one table.
 *
 * <p>The defaults are Home as it always was: a swipe up opens the palette with the keyboard and
 * every other gesture does nothing. A swipe down that meets an open palette or a raised keyboard
 * always puts it away, whatever it is set to — that way out is never given up — so its action
 * runs only when nothing is up.
 *
 * <p>Swipe up keeps the key it always had ({@code app_launcher_home_palette_gesture}) and that
 * key's two old values, {@code swipe_up} (the palette) and {@code off} (nothing), so an install
 * that turned the palette gesture off keeps it off with no migration.
 */
public final class HomeGestures {

    private HomeGestures() {}

    /** The old palette gesture's values, which swipe up keeps writing for its two old choices. */
    static final String LEGACY_PALETTE = "swipe_up";
    static final String LEGACY_OFF = "off";

    /** Suffix of the key holding the app a gesture opens, beside the gesture's own key. */
    public static final String APP_KEY_SUFFIX = "_app";

    public enum Gesture {
        SWIPE_UP("app_launcher_home_palette_gesture"),
        SWIPE_DOWN("app_launcher_home_gesture_swipe_down"),
        DOUBLE_TAP("app_launcher_home_gesture_double_tap"),
        TWO_FINGER_UP("app_launcher_home_gesture_two_finger_up"),
        TWO_FINGER_DOWN("app_launcher_home_gesture_two_finger_down");

        /** The preference holding this gesture's action. */
        @NonNull public final String key;

        Gesture(@NonNull String key) {
            this.key = key;
        }

        /** The preference holding the app this gesture opens, when its action is {@link Action#APP}. */
        @NonNull public String appKey() {
            return key + APP_KEY_SUFFIX;
        }

        /** The gesture a key names, or null. */
        @Nullable public static Gesture forKey(@Nullable String key) {
            for (Gesture gesture : values()) if (gesture.key.equals(key)) return gesture;
            return null;
        }
    }

    public enum Action {
        NONE("none", false),
        PALETTE("palette", false),
        DRAWER("drawer", false),
        /** Pulls the phone's notification shade down. */
        NOTIFICATIONS("notifications", true),
        /** Pulls the phone's quick settings down. */
        QUICK_SETTINGS("quick_settings", true),
        LOCK_SCREEN("lock_screen", true),
        /** Slides the wall to the Terminal. */
        TERMINAL("terminal", false),
        /** Opens the app picked for this gesture. */
        APP("app", false);

        @NonNull public final String value;
        /**
         * Whether the launcher needs a helper to do it: its accessibility service, or Shizuku.
         * An app cannot pull the system's shade down or lock the phone on its own.
         */
        public final boolean needsHelper;

        Action(@NonNull String value, boolean needsHelper) {
            this.value = value;
            this.needsHelper = needsHelper;
        }
    }

    /** What the phone offers right now, as far as these actions care. */
    public static final class Availability {
        /** The launcher's accessibility service is on, or Shizuku is connected. */
        public final boolean helper;
        /** The app drawer is switched on. */
        public final boolean drawer;

        public Availability(boolean helper, boolean drawer) {
            this.helper = helper;
            this.drawer = drawer;
        }
    }

    /** What {@code gesture} does when nothing has been chosen: Home as it always was. */
    @NonNull
    public static Action defaultAction(@NonNull Gesture gesture) {
        return gesture == Gesture.SWIPE_UP ? Action.PALETTE : Action.NONE;
    }

    /** The action a stored value names for {@code gesture}; unknown or unset is the default. */
    @NonNull
    public static Action parse(@NonNull Gesture gesture, @Nullable String stored) {
        if (stored == null) return defaultAction(gesture);
        if (LEGACY_PALETTE.equals(stored)) return Action.PALETTE;
        if (LEGACY_OFF.equals(stored)) return Action.NONE;
        for (Action action : Action.values()) if (action.value.equals(stored)) return action;
        return defaultAction(gesture);
    }

    /**
     * The value to store for {@code action} on {@code gesture}: swipe up writes its old two values
     * for the palette and for nothing, so a build from before this table reads them as it did.
     */
    @NonNull
    public static String storedValue(@NonNull Gesture gesture, @NonNull Action action) {
        if (gesture == Gesture.SWIPE_UP) {
            if (action == Action.PALETTE) return LEGACY_PALETTE;
            if (action == Action.NONE) return LEGACY_OFF;
        }
        return action.value;
    }

    /** Whether {@code action} can run on this phone now. */
    public static boolean isAvailable(@NonNull Action action, @NonNull Availability availability) {
        if (action.needsHelper) return availability.helper;
        if (action == Action.DRAWER) return availability.drawer;
        return true;
    }

    /**
     * The choices a gesture's row lists, in order: every action that can run, and the one chosen
     * now even when it cannot (so the row still names it, beside the sentence on what to turn on).
     */
    @NonNull
    public static List<Action> choices(@NonNull Action current, @NonNull Availability availability) {
        ArrayList<Action> out = new ArrayList<>();
        for (Action action : Action.values()) {
            if (action == current || isAvailable(action, availability)) out.add(action);
        }
        return Collections.unmodifiableList(out);
    }

    /** Whether some action that needs a helper is out of reach, so the rows explain what to turn on. */
    public static boolean helperMissing(@NonNull Availability availability) {
        return !availability.helper;
    }

    /**
     * Whether the pane should watch for {@code gesture}: double tap and the two-finger swipes are
     * watched only while they do something, so with them unset a tap and a pinch reach the page
     * as they always did.
     */
    public static boolean watched(@NonNull Action action) {
        return action != Action.NONE;
    }

    /**
     * Whether a press is the second of a double tap: it lands within {@code timeoutMs} of the first
     * tap's lift and within {@code slopPx} of where that tap was. {@code previousUpMs} below 0 is no
     * first tap.
     */
    public static boolean isSecondTap(long previousUpMs, float previousX, float previousY,
                                      long downMs, float x, float y, long timeoutMs, float slopPx) {
        if (previousUpMs < 0 || downMs < previousUpMs || downMs - previousUpMs > timeoutMs) return false;
        float dx = x - previousX;
        float dy = y - previousY;
        return dx * dx + dy * dy <= slopPx * slopPx;
    }

    /**
     * Which way a two-finger swipe went once its fingers lift: -1 up, 1 down, 0 neither (short of
     * {@code commitPx}, or more sideways than vertical). {@code dx} and {@code dy} are the
     * fingers' mean travel.
     */
    public static int twoFingerDirection(float dx, float dy, float commitPx) {
        if (Math.abs(dy) < commitPx || Math.abs(dy) <= Math.abs(dx)) return 0;
        return dy < 0 ? -1 : 1;
    }
}
