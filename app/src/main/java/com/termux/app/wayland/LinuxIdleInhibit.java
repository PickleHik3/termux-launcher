package com.termux.app.wayland;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * When Linux windows keep the screen on: while a window that holds an idle inhibitor (a video
 * player playing, {@code zwp_idle_inhibit_v1}) is shown in front. The host says which windows
 * hold one ({@link #inhibit}); the controller asks again whenever what is in front may have
 * changed ({@link #update}). It lets go when the inhibitor is destroyed, the window leaves the
 * front, the window closes ({@link #forget}) or the host is gone ({@link #clear}).
 *
 * <p>Pure: the window flag is behind {@link Screen}. Main thread.
 */
final class LinuxIdleInhibit {

    /** The activity window's keep-screen-on hold for Linux windows. */
    interface Screen {
        void keepOn(boolean on);
    }

    /** Whether a window is shown in front right now. */
    interface Front {
        boolean isInFront(long id);
    }

    private final Screen screen;
    private final Set<Long> inhibiting = new HashSet<>();
    private boolean on;

    LinuxIdleInhibit(@NonNull Screen screen) {
        this.screen = screen;
    }

    /** Window {@code id} holds an idle inhibitor ({@code held}) or no longer does. */
    void inhibit(long id, boolean held) {
        if (held) inhibiting.add(id);
        else inhibiting.remove(id);
    }

    /** Window {@code id} closed. */
    void forget(long id) {
        inhibiting.remove(id);
    }

    /** The host ended: no window holds anything, and the screen may sleep. */
    void clear() {
        inhibiting.clear();
        set(false);
    }

    /** Whether any window holds an inhibitor, for a caller that can skip its front test. */
    boolean any() {
        return !inhibiting.isEmpty();
    }

    boolean isOn() {
        return on;
    }

    /** Sets the hold from what is in front now; the screen is touched only on a change. */
    void update(@NonNull Front front) {
        boolean want = false;
        for (Long id : inhibiting) {
            if (front.isInFront(id)) {
                want = true;
                break;
            }
        }
        set(want);
    }

    private void set(boolean want) {
        if (want == on) return;
        on = want;
        screen.keepOn(want);
    }
}
