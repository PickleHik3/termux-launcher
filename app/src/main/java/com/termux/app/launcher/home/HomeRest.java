package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/** Home's rest (ADR 0030 section 1), computed from plain booleans. */
public final class HomeRest {

    private HomeRest() {}

    /**
     * The keyboard is up unless the overview is previewing Home; a restored float that asks for
     * it, Home's always-on setting or its remembered keyboard raise it, and minimal mode leaves
     * Home alone. The bar shows a focused float's set (a display's or a terminal's) or Home's own.
     * Home owns the phone's keyboard, the status bar follows the layout of Home's half, the chips
     * show and the page wears Home's look.
     */
    @NonNull
    public static Rest of(@NonNull Rest.Inputs in) {
        boolean keyboardUp = !in.overviewPreviewing
            && (in.floatAsksKeyboard || in.homeAlwaysOn || in.keyboardRemembered);
        PageKind keys = in.floatFocused
            ? (in.floatIsDisplay ? PageKind.DISPLAY : PageKind.TERMINAL) : PageKind.HOME;
        return new Rest(keyboardUp, keys, true, in.barShown, true, PageKind.HOME);
    }
}
