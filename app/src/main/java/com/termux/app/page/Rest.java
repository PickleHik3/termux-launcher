package com.termux.app.page;

import androidx.annotation.NonNull;

/**
 * A page's pure answer to what it asks of the chrome while it is in front and still (ADR 0030
 * section 1): the keyboard, whose extra-keys set the bar shows, whether its text fields type with
 * the phone's keyboard, what the bars show and the look it wears. Immutable; the page computes it
 * from {@link Inputs} the host knows.
 */
public final class Rest {

    /** Whether the launcher's keyboard is up. */
    public final boolean keyboardUp;
    /** Whose extra-keys set the bar shows. */
    @NonNull public final PageKind keys;
    /** Whether the page's text fields type with the phone's keyboard. */
    public final boolean systemIme;
    /** Whether the status bar shows. */
    public final boolean statusBar;
    /** Whether the window chips show. */
    public final boolean windowChips;
    /** The look kind the page wears; Workspace pages wear TERMINAL's (ADR 0025). */
    @NonNull public final PageKind look;

    public Rest(boolean keyboardUp, @NonNull PageKind keys, boolean systemIme, boolean statusBar,
                boolean windowChips, @NonNull PageKind look) {
        this.keyboardUp = keyboardUp;
        this.keys = keys;
        this.systemIme = systemIme;
        this.statusBar = statusBar;
        this.windowChips = windowChips;
        this.look = look;
    }

    /** The plain facts the host knows and a page's rest is computed from. */
    public static final class Inputs {
        /** The page's own remembered keyboard. */
        public final boolean keyboardRemembered;
        public final boolean minimal;
        public final boolean homeAlwaysOn;
        public final boolean overviewPreviewing;
        public final boolean inAppKeyboard;

        public Inputs(boolean keyboardRemembered, boolean minimal, boolean homeAlwaysOn,
                      boolean overviewPreviewing, boolean inAppKeyboard) {
            this.keyboardRemembered = keyboardRemembered;
            this.minimal = minimal;
            this.homeAlwaysOn = homeAlwaysOn;
            this.overviewPreviewing = overviewPreviewing;
            this.inAppKeyboard = inAppKeyboard;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Inputs)) return false;
            Inputs other = (Inputs) o;
            return keyboardRemembered == other.keyboardRemembered && minimal == other.minimal
                && homeAlwaysOn == other.homeAlwaysOn
                && overviewPreviewing == other.overviewPreviewing
                && inAppKeyboard == other.inAppKeyboard;
        }

        @Override
        public int hashCode() {
            int h = keyboardRemembered ? 1 : 0;
            h = h * 31 + (minimal ? 1 : 0);
            h = h * 31 + (homeAlwaysOn ? 1 : 0);
            h = h * 31 + (overviewPreviewing ? 1 : 0);
            h = h * 31 + (inAppKeyboard ? 1 : 0);
            return h;
        }

        @NonNull
        @Override
        public String toString() {
            return "Inputs{remembered=" + keyboardRemembered + ", minimal=" + minimal
                + ", homeAlwaysOn=" + homeAlwaysOn + ", previewing=" + overviewPreviewing
                + ", inAppKeyboard=" + inAppKeyboard + "}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rest)) return false;
        Rest other = (Rest) o;
        return keyboardUp == other.keyboardUp && keys == other.keys
            && systemIme == other.systemIme && statusBar == other.statusBar
            && windowChips == other.windowChips && look == other.look;
    }

    @Override
    public int hashCode() {
        int h = keyboardUp ? 1 : 0;
        h = h * 31 + keys.hashCode();
        h = h * 31 + (systemIme ? 1 : 0);
        h = h * 31 + (statusBar ? 1 : 0);
        h = h * 31 + (windowChips ? 1 : 0);
        h = h * 31 + look.hashCode();
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        return "Rest{keyboardUp=" + keyboardUp + ", keys=" + keys + ", systemIme=" + systemIme
            + ", statusBar=" + statusBar + ", windowChips=" + windowChips + ", look=" + look + "}";
    }
}
