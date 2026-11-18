package com.termux.app.x11;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;

/**
 * The Display place's use of the phone's own keyboard: whether it takes the route, raising and
 * lowering it over the display, and the room it covers. Main thread only.
 */
public final class DisplayKeys {

    /** What the keyboard route asks of its window. */
    public interface Host {
        /** The display's frame on the wall; null before it is built. */
        @Nullable X11PaneFrame displayFrame();
        /** The setting that gives the Display place the phone's keyboard. */
        boolean androidKeyboardEnabled();
        boolean isDisplayWindowInFront();
        /** The launcher's own keyboard; null before it is built. */
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        /** Mouse mode's touchpad owns the keyboard frame. */
        boolean isDisplayTouchpadFrame();
        /** Every path that shows the system IME calls this first. */
        void onSystemImeRequested();
        boolean isImeVisible();
        void applyPlaceSystemImeOwner();
        /** The content root, whose bottom padding makes room for the keyboard. */
        @Nullable View rootView();
    }

    @NonNull private final Host mHost;
    @NonNull private final DisplayServer mDisplayServer;

    public DisplayKeys(@NonNull Host host, @NonNull DisplayServer displayServer) {
        mHost = host;
        mDisplayServer = displayServer;
    }

    /** Set from the moment the phone's keyboard is raised over the display until it goes down. */
    private boolean mDisplaySystemKeyboardUp;

    /** The display's own view, while the wall carries a display window at all. */
    @Nullable
    public com.termux.x11.LorieView displayView() {
        com.termux.app.x11.X11PaneFrame frame = mHost.displayFrame();
        return frame == null ? null : frame.display();
    }

    /** Which keyboard a request lands on right now. */
    @NonNull
    private com.termux.app.x11.DisplayKeyboardRoute.Target keyboardRoute() {
        return com.termux.app.x11.DisplayKeyboardRoute.decide(
            mHost.androidKeyboardEnabled(),
            mHost.isDisplayWindowInFront(), mDisplayServer.isRunning(),
            mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isEnabled());
    }

    /**
     * True while the Display place is typed into with the phone's own keyboard. Every route into
     * a keyboard on that place aims at it then, and the launcher's own stays down.
     */
    public boolean displayTakesSystemKeyboard() {
        return keyboardRoute() == com.termux.app.x11.DisplayKeyboardRoute.Target.SYSTEM_IME;
    }

    /**
     * Raise or lower the phone's keyboard over the display. True when that is the route a
     * keyboard request takes here and it was taken, so the caller leaves every other keyboard be.
     */
    public boolean applyDisplaySystemKeyboard(boolean show) {
        if (!displayTakesSystemKeyboard()) return false;
        com.termux.x11.LorieView display = displayView();
        if (display == null) return false;
        if (show) {
            // Two keyboards over one display is nobody's idea of typing, so the launcher's goes
            // down — unless it is only standing there as mouse mode's touchpad frame, which is
            // not a keyboard anyone is typing on.
            if (mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isVisible() && !mHost.isDisplayTouchpadFrame())
                mHost.inAppKeyboard().hide(com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard.HideReason.KEYBOARD_ACTION);
            mHost.onSystemImeRequested();
        }
        display.setKeyboardVisible(show);
        mDisplaySystemKeyboardUp = show;
        return true;
    }

    /** The keyboard key, while the Display place takes the phone's keyboard. */
    public boolean toggleDisplaySystemKeyboard() {
        if (!displayTakesSystemKeyboard()) return false;
        return applyDisplaySystemKeyboard(!mHost.isImeVisible());
    }

    /**
     * Put the phone's keyboard down over the display, whatever raised it. Nothing happens unless
     * this activity is the one that raised it, so a place that never asked for it is untouched.
     */
    public void hideDisplaySystemKeyboard() {
        if (!mDisplaySystemKeyboardUp) return;
        mDisplaySystemKeyboardUp = false;
        com.termux.x11.LorieView display = displayView();
        if (display != null) display.setKeyboardVisible(false);
        applyDisplayImeRoom(0);
    }

    /**
     * Re-decide who takes a keyboard request on the Display place — the setting can move while a
     * display runs, and so can the place and the display. A launcher keyboard left standing on
     * the place goes down, and the place is handed the system IME or has it taken back.
     */
    public void syncDisplayKeyboardRoute() {
        if (displayTakesSystemKeyboard() && mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isVisible()
                && !mHost.isDisplayTouchpadFrame()) {
            mHost.inAppKeyboard().hide(com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard.HideReason.KEYBOARD_ACTION);
        }
        mHost.applyPlaceSystemImeOwner();
    }

    /**
     * The display's own view keeps the focus while the phone's keyboard types into it: a surface
     * that would otherwise hand focus back to a terminal pane asks here first.
     */
    public boolean holdDisplayKeyboardFocus() {
        if (!displayTakesSystemKeyboard()) return false;
        com.termux.x11.LorieView display = displayView();
        if (display == null) return false;
        display.requestFocus();
        return true;
    }

    /** The band the phone's keyboard covers on the Display place, and none anywhere else. */
    public int displayImeRoomPx(@NonNull androidx.core.view.WindowInsetsCompat insets) {
        if (!displayTakesSystemKeyboard() || !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) return 0;
        // The content root already sits above the navigation bar, so only the band the keyboard
        // adds to it is new.
        return Math.max(0, insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom);
    }

    /**
     * Make room for the phone's keyboard over the display. The window is never resized for the
     * system keyboard — the launcher's own keyboard owns that mode — so the content root gives up
     * the band it covers itself: the wall, and with it the X screen, shrinks by exactly that much,
     * and the dock, the extra keys and mouse mode's touchpad ride up above it.
     */
    public void applyDisplayImeRoom(int roomPx) {
        View root = mHost.rootView();
        if (root == null || root.getPaddingBottom() == roomPx) return;
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(),
            roomPx);
    }
}
