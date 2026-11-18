package com.termux.app.x11;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/** The Display page's rest (ADR 0030 section 1), computed from plain booleans. */
public final class DisplayRest {

    private DisplayRest() {}

    /**
     * The display types with the phone's keyboard when that setting is on and a server runs. The
     * launcher's keyboard is up when remembered and not put away by minimal mode or the preview,
     * except while the phone's keyboard types and mouse mode's touchpad does not own the frame.
     * The bar shows the Display's set, the chips show; the page wears the Workspace's look
     * (ADR 0025).
     */
    @NonNull
    public static Rest of(@NonNull Rest.Inputs in) {
        boolean systemIme = in.androidKeyboard && in.displayRunning;
        boolean touchpadFrame = in.mouseMode && in.displayRunning && in.inAppKeyboard;
        boolean keyboardUp = !in.overviewPreviewing && in.keyboardRemembered && !in.minimal
            && !(systemIme && !touchpadFrame);
        return new Rest(keyboardUp, PageKind.DISPLAY, systemIme, in.barShown, true,
            PageKind.TERMINAL);
    }
}
