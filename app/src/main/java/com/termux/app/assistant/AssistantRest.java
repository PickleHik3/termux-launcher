package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/** The Assistant page's rest (ADR 0030 section 1), computed from plain booleans. */
public final class AssistantRest {

    private AssistantRest() {}

    /**
     * The keyboard is up unless the overview is previewing the page, or its remembered keyboard is
     * down or minimal mode puts it away. With the launcher's keyboard off the phone's is the only
     * one. The bar shows its own set and the chips show; the page wears the Terminal's look.
     */
    @NonNull
    public static Rest of(@NonNull Rest.Inputs in) {
        boolean keyboardUp = !in.overviewPreviewing && in.keyboardRemembered && !in.minimal;
        return new Rest(keyboardUp, PageKind.ASSISTANT, !in.inAppKeyboard, true, true,
            PageKind.TERMINAL);
    }
}
