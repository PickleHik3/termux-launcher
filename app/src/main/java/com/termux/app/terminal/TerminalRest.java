package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.place.PageRoom;
import com.termux.app.place.StackLayout;

/** The Terminal page's rest (ADR 0030 section 1), computed from plain booleans. */
public final class TerminalRest {

    private TerminalRest() {}

    /**
     * The keyboard is up unless the overview is previewing the page, or its remembered keyboard
     * is down or minimal mode puts it away. With the launcher's keyboard off the phone's is the
     * only one. The bar shows the Terminal's set and the chips show; the page wears the
     * Terminal's look.
     */
    @NonNull
    public static Rest of(@NonNull Rest.Inputs in) {
        boolean keyboardUp = !in.overviewPreviewing && in.keyboardRemembered && !in.minimal;
        return new Rest(keyboardUp, PageKind.TERMINAL, !in.inAppKeyboard, in.barShown, true,
            PageKind.TERMINAL);
    }

    /**
     * The room the page keeps at {@code rest}: {@code input} with its keyboard set to the rest's,
     * read through {@link PageRoom}.
     */
    public static int roomPx(@NonNull Rest rest, @NonNull StackLayout.RestInput input) {
        input.keyboardShown = rest.keyboardUp;
        return PageRoom.bottomInsetPx(input);
    }
}
