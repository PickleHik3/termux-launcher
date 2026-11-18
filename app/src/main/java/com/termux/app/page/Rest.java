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
        /** Read for the status bar: the layout of the page's half shows it. Defaults to true. */
        public final boolean barShown;
        /** Read by Home: a restored Home float asks for the keyboard as the wall lands on Home. */
        public final boolean floatAsksKeyboard;
        /** Read by Home: a Home float holds the focus, so its extra-keys set is the bar's. */
        public final boolean floatFocused;
        /** Read by Home: the focused float is a display window. */
        public final boolean floatIsDisplay;
        /** Read by the Display: the "Use Android keyboard" setting (the display types with the phone's keyboard). */
        public final boolean androidKeyboard;
        /** Read by the Display: a display server runs, so there is something to type into. */
        public final boolean displayRunning;
        /** Read by the Display: mouse mode's touchpad owns the keyboard frame. */
        public final boolean mouseMode;

        public Inputs(boolean keyboardRemembered, boolean minimal, boolean homeAlwaysOn,
                      boolean overviewPreviewing, boolean inAppKeyboard) {
            this(new Builder().keyboardRemembered(keyboardRemembered).minimal(minimal)
                .homeAlwaysOn(homeAlwaysOn).overviewPreviewing(overviewPreviewing)
                .inAppKeyboard(inAppKeyboard));
        }

        private Inputs(Builder b) {
            this.keyboardRemembered = b.keyboardRemembered;
            this.minimal = b.minimal;
            this.homeAlwaysOn = b.homeAlwaysOn;
            this.overviewPreviewing = b.overviewPreviewing;
            this.inAppKeyboard = b.inAppKeyboard;
            this.barShown = b.barShown;
            this.floatAsksKeyboard = b.floatAsksKeyboard;
            this.floatFocused = b.floatFocused;
            this.floatIsDisplay = b.floatIsDisplay;
            this.androidKeyboard = b.androidKeyboard;
            this.displayRunning = b.displayRunning;
            this.mouseMode = b.mouseMode;
        }

        /** Sets the facts by name; every one it is not told is false, except {@code barShown}. */
        public static final class Builder {
            private boolean keyboardRemembered;
            private boolean minimal;
            private boolean homeAlwaysOn;
            private boolean overviewPreviewing;
            private boolean inAppKeyboard;
            private boolean barShown = true;
            private boolean floatAsksKeyboard;
            private boolean floatFocused;
            private boolean floatIsDisplay;
            private boolean androidKeyboard;
            private boolean displayRunning;
            private boolean mouseMode;

            public Builder keyboardRemembered(boolean v) { keyboardRemembered = v; return this; }
            public Builder minimal(boolean v) { minimal = v; return this; }
            public Builder homeAlwaysOn(boolean v) { homeAlwaysOn = v; return this; }
            public Builder overviewPreviewing(boolean v) { overviewPreviewing = v; return this; }
            public Builder inAppKeyboard(boolean v) { inAppKeyboard = v; return this; }
            public Builder barShown(boolean v) { barShown = v; return this; }
            public Builder floatAsksKeyboard(boolean v) { floatAsksKeyboard = v; return this; }
            public Builder floatFocused(boolean v) { floatFocused = v; return this; }
            public Builder floatIsDisplay(boolean v) { floatIsDisplay = v; return this; }
            public Builder androidKeyboard(boolean v) { androidKeyboard = v; return this; }
            public Builder displayRunning(boolean v) { displayRunning = v; return this; }
            public Builder mouseMode(boolean v) { mouseMode = v; return this; }

            @NonNull
            public Inputs build() {
                return new Inputs(this);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Inputs)) return false;
            Inputs other = (Inputs) o;
            return keyboardRemembered == other.keyboardRemembered
                && minimal == other.minimal
                && homeAlwaysOn == other.homeAlwaysOn
                && overviewPreviewing == other.overviewPreviewing
                && inAppKeyboard == other.inAppKeyboard
                && barShown == other.barShown
                && floatAsksKeyboard == other.floatAsksKeyboard
                && floatFocused == other.floatFocused
                && floatIsDisplay == other.floatIsDisplay
                && androidKeyboard == other.androidKeyboard
                && displayRunning == other.displayRunning
                && mouseMode == other.mouseMode;
        }

        @Override
        public int hashCode() {
            int h = 0;
            h = h * 31 + (keyboardRemembered ? 1 : 0);
            h = h * 31 + (minimal ? 1 : 0);
            h = h * 31 + (homeAlwaysOn ? 1 : 0);
            h = h * 31 + (overviewPreviewing ? 1 : 0);
            h = h * 31 + (inAppKeyboard ? 1 : 0);
            h = h * 31 + (barShown ? 1 : 0);
            h = h * 31 + (floatAsksKeyboard ? 1 : 0);
            h = h * 31 + (floatFocused ? 1 : 0);
            h = h * 31 + (floatIsDisplay ? 1 : 0);
            h = h * 31 + (androidKeyboard ? 1 : 0);
            h = h * 31 + (displayRunning ? 1 : 0);
            h = h * 31 + (mouseMode ? 1 : 0);
            return h;
        }

        @NonNull
        @Override
        public String toString() {
            return "Inputs{keyboardRemembered=" + keyboardRemembered
                + ", minimal=" + minimal
                + ", homeAlwaysOn=" + homeAlwaysOn
                + ", overviewPreviewing=" + overviewPreviewing
                + ", inAppKeyboard=" + inAppKeyboard
                + ", barShown=" + barShown
                + ", floatAsksKeyboard=" + floatAsksKeyboard
                + ", floatFocused=" + floatFocused
                + ", floatIsDisplay=" + floatIsDisplay
                + ", androidKeyboard=" + androidKeyboard
                + ", displayRunning=" + displayRunning
                + ", mouseMode=" + mouseMode + "}";
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
