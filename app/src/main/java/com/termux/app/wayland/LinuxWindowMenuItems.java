package com.termux.app.wayland;

import androidx.annotation.NonNull;

/**
 * What a Linux window's controls (on its mark and on its pane's corner tab) offer, as pure state.
 * Every window has Close, Minimise and Move. Scale is there for an app window and an X11 desktop's
 * window; a Wayland desktop has none, since it ignores the host's scale and takes its own from its
 * config. Full screen is a desktop's alone: its Maximise takes the whole screen (spec #394, "A
 * desktop's window under Wayland Display").
 */
final class LinuxWindowMenuItems {

    /** What kind of window the controls belong to. */
    enum Kind {
        /** An ordinary app window. */
        APP,
        /** An X11 desktop's window: it keeps its live Scale. */
        X11_DESKTOP,
        /** A Wayland desktop's window, recognised by its launch tag. */
        WAYLAND_DESKTOP
    }

    final boolean scale;
    final boolean fullScreen;

    private LinuxWindowMenuItems(boolean scale, boolean fullScreen) {
        this.scale = scale;
        this.fullScreen = fullScreen;
    }

    /** The rows for a window of {@code kind}. */
    @NonNull
    static LinuxWindowMenuItems of(@NonNull Kind kind) {
        return new LinuxWindowMenuItems(kind != Kind.WAYLAND_DESKTOP, kind != Kind.APP);
    }
}
