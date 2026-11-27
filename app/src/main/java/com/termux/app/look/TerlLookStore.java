package com.termux.app.look;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Terl's look model: one Look for everything, own Opacity and Blur on the control bar, the keyboard
 * and windows only, Corners on Global alone. Built beside v1's {@link LookStore} and
 * {@link PageLook}, which it never reads or writes.
 *
 * <p>A Look is a shipped Look (or one a {@link TerlLookSource} adds) or {@link #CUSTOM}, whose
 * Opacity and Blur are kept while other Looks are tried. A surface holding no value follows Global,
 * which is the Look's number.
 */
public interface TerlLookStore {

    /** The Look id of Custom, the last on the filmstrip. */
    String CUSTOM = "custom";

    /** Told that a look value changed. */
    interface Listener {
        void onLookChanged();
    }

    /** One own value a Look pick would clear. */
    final class OwnValue {
        @NonNull public final TerlSurface surface;
        @NonNull public final TerlProperty property;
        public final int value;

        public OwnValue(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value) {
            this.surface = surface;
            this.property = property;
            this.value = value;
        }

        /** The setting id, as terl.conf and launcherctl name it: {@code look.control-bar.blur}. */
        @NonNull
        public String id() {
            return TerlLookIds.of(surface, property);
        }
    }

    /** What picking a Look did, or would do. */
    final class Pick {
        /** Whether the Look is now the Look. False when own values are held and not confirmed. */
        public final boolean applied;
        /** The own values the pick cleared, or, when not applied, would clear. */
        @NonNull public final List<OwnValue> clears;

        Pick(boolean applied, @NonNull List<OwnValue> clears) {
            this.applied = applied;
            this.clears = clears;
        }
    }

    /** The Look ids in filmstrip order: every source's, then {@link #CUSTOM}. */
    @NonNull
    List<String> lookIds();

    /** The Look worn now: an id of {@link #lookIds()}. */
    @NonNull
    String look();

    /**
     * Picks a Look. When any surface holds an own value and {@code confirmed} is false, nothing
     * changes and the result names what would be cleared; otherwise every own value is cleared and
     * the Look worn. An unknown id changes nothing and clears nothing.
     */
    @NonNull
    Pick pick(@NonNull String lookId, boolean confirmed);

    /** The own values a pick of any Look would clear, now. */
    @NonNull
    List<OwnValue> ownValues();

    /** The number the surface renders: its own value if it holds one, else Global's. */
    int value(@NonNull TerlSurface surface, @NonNull TerlProperty property);

    /** The number Global gives every surface without an own value: the worn Look's. */
    int global(@NonNull TerlProperty property);

    boolean holdsOwn(@NonNull TerlSurface surface, @NonNull TerlProperty property);

    /**
     * Gives the surface its own value, clamped. False, with nothing written, for a surface that
     * wears the Look (sheets, Home widgets).
     */
    boolean setOwn(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value);

    /** Follow Global: clears one own value. */
    void followGlobal(@NonNull TerlSurface surface, @NonNull TerlProperty property);

    /** Custom's number. Kept while other Looks are worn; worn only when {@link #look()} is Custom. */
    int customValue(@NonNull TerlProperty property);

    /** Sets Custom's number, clamped; it does not pick Custom. */
    void setCustomValue(@NonNull TerlProperty property, int value);

    /** Global's corner radius in dp: the one radius every surface and window wears. */
    int cornerRadiusDp();

    void setCornerRadiusDp(int dp);

    void addListener(@NonNull Listener listener);

    void removeListener(@NonNull Listener listener);
}
