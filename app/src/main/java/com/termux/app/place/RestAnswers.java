package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/**
 * Where the host reads a page's {@link Rest} for the answers it used to compute itself: whether a
 * page wants its keyboard as the wall lands on it, and who owns the system IME. Pure; the host
 * hands in the page's rest (a registered page's, else the kind's static one).
 */
public final class RestAnswers {

    private RestAnswers() {}

    /**
     * Whether a page of {@code kind} wants its keyboard up as the wall lands on it, from its
     * {@code rest}. The Display keeps its own rule: its rest also puts the launcher's keyboard
     * away while the phone's keyboard types, which the landing never did.
     */
    public static boolean keyboardOnEnter(@NonNull PageKind kind, @NonNull Rest.Inputs in,
                                          @NonNull Rest rest) {
        if (kind == PageKind.DISPLAY) {
            return !in.overviewPreviewing && PlaceKeyboard.keyboardOnEnter(kind,
                in.keyboardRemembered, in.minimal, in.homeAlwaysOn);
        }
        return rest.keyboardUp;
    }

    /**
     * Whether the Display is typed into with the phone's keyboard: its page is in front and its
     * rest says the phone's keyboard types.
     */
    public static boolean displayTypesWithSystemIme(boolean displayInFront, @NonNull Rest displayRest) {
        return displayInFront && displayRest.systemIme;
    }

    /**
     * Whether the page the wall committed to owns the system IME: Home, by its rest, or the Display
     * while it takes the phone's keyboard. The Terminal and Assistant rests also say "phone's
     * keyboard" with the launcher's keyboard off, which the owner never did.
     */
    public static boolean systemImeOwner(boolean committedHome, @NonNull Rest homeRest,
                                         boolean displayTypesWithSystemIme) {
        return (committedHome && homeRest.systemIme) || displayTypesWithSystemIme;
    }
}
