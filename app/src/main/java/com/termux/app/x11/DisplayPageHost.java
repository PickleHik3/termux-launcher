package com.termux.app.x11;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/** What the Display pages ask of their window; never the activity. At most fifteen methods. */
public interface DisplayPageHost {

    /** The display's X view while its frame is built; null before, or while it cannot be had. */
    @Nullable View displayView();

    /** The server connection the view attaches to; null while there is none. */
    @Nullable DisplayPages.Link link();

    /** Whether the display server runs. */
    boolean isRunning();

    /** The kind the wall last committed to, or null before any commit. */
    @Nullable PageKind committedKind();

    /** Whether the wall is at rest: no drag, no slide. */
    boolean wallAtRest();

    /** Whether the wall is on a Display page, mid-slide the one it committed to. */
    boolean isDisplayShowing();

    /** Whether a layer (the drawer, the palette, a sheet, help) is open over the page. */
    boolean isLayerOpen();

    /** Whether the Display place raises the keyboard as it arrives. */
    boolean wantsKeyboardOnEnter();

    /** The display window's empty state reads the keyboard-data probe again. */
    void refreshEmptyState();

    /** Typing goes to whoever owns it now: X on the page, an open layer, or the terminal. */
    void restoreTypingClaim();

    /** The keyboard's route follows the page. */
    void syncKeyboardRoute();

    /** The phone's keyboard raised on the page goes down with it. */
    void hideSystemKeyboard();

    /** One quiet focus request on the active terminal pane; true when it holds the keys. */
    boolean handKeysToTerminal();

    /** What the keyboard's frame holds: the touchpad or the keyboard. */
    @NonNull DisplayFrameContentPolicy.Content frameContent();

    /** Puts the page's keyboard down by the keyboard key's route. */
    void lowerKeyboard();
}
