package com.termux.app.look;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Terl's look model: one Look for everything, own Opacity and Blur on the control bar, the keyboard
 * and windows only, Corners on Global alone. Built beside v1's {@link LookStore} and
 * {@link PageLook}, which it never reads or writes.
 *
 * <p>A Look is a shipped Look or {@link #CUSTOM}, whose Opacity and Blur are kept while other
 * Looks are tried (the Looks once made from the wallpaper are gone, decided 2026-11-28). A surface
 * holding no value follows Global, which is the Look's number. Corners and the Tint colour are
 * Global's alone, and a Look pick leaves them as they are.
 */
public interface TerlLookStore {

    /** The Look id of Custom, the last on the filmstrip. */
    String CUSTOM = "custom";

    /**
     * The Tint colour that mixes in no accent: the glass in the theme's own neutral. The default,
     * as the prototype's Tint row starts on Auto.
     */
    String TINT_AUTO = "auto";
    /**
     * The Tint colours that mix in one of the wallpaper's accents (the theme's colours come only
     * from the wallpaper). The names are v1's, so the two agree.
     */
    String TINT_PRIMARY = TERMUX_APP.GLASS_TINT_HUE_PRIMARY;
    String TINT_SECONDARY = TERMUX_APP.GLASS_TINT_HUE_SECONDARY;
    String TINT_TERTIARY = TERMUX_APP.GLASS_TINT_HUE_TERTIARY;
    /** Every Tint colour, in the order the All glass card offers them: Auto, then the dots. */
    List<String> TINTS = Collections.unmodifiableList(
        Arrays.asList(TINT_AUTO, TINT_PRIMARY, TINT_SECONDARY, TINT_TERTIARY));
    /**
     * How much of its accent a Tint colour other than Auto mixes into every glass, in percent: the
     * Tinted Look's Material blend, one number with v1's.
     */
    int TINT_AMOUNT_PERCENT = com.termux.shared.termux.settings.preferences.GlassMaterial.TINT_LOOK_PERCENT;

    /**
     * Where the app-wide physical parameters live (ADR 0033: Depth and Shine are every glass's,
     * kept where the renderer reads them). A store without one keeps them under its own keys.
     */
    interface Physical {
        /** The slab's thickness, dp. */
        int bendDp();

        void setBendDp(int dp);

        /** Shine, 0 to 100. */
        int shine();

        void setShine(int shine);
    }

    /**
     * How much of the accent {@code tint} mixes into the glass, in percent, the Look aside: none
     * for Auto ({@link #tintAmountPercent()} gives the worn Look's own then).
     */
    static int tintAmountPercent(@NonNull String tint) {
        return TINT_AUTO.equals(tint) || !TINTS.contains(tint) ? 0 : TINT_AMOUNT_PERCENT;
    }

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

    /** A Look's own number: Custom's for {@link #CUSTOM}, a source's for any other id. */
    int lookValue(@NonNull String lookId, @NonNull TerlProperty property);

    /** A Look's physical parameters (its preset's), or null for {@link #CUSTOM} and unknown ids. */
    @androidx.annotation.Nullable
    TerlLookSource.Look preset(@NonNull String lookId);

    /** The Look worn now: an id of {@link #lookIds()}. */
    @NonNull
    String look();

    /**
     * Picks a Look. When any surface holds an own value and {@code confirmed} is false, nothing
     * changes and the result names what would be cleared; otherwise every own value is cleared, the
     * Look worn and, for a shipped Look, its preset's Depth and Shine set (Custom keeps the values
     * in force). An unknown id changes nothing and clears nothing.
     */
    @NonNull
    Pick pick(@NonNull String lookId, boolean confirmed);

    /**
     * Wears a Look as it is, own values, Depth and Shine untouched: for Undo and Revert of a pick,
     * which put back what it changed themselves. An unknown id changes nothing.
     */
    void wear(@NonNull String lookId);

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

    /** Global's Tint colour: one of {@link #TINTS}, {@link #TINT_AUTO} until one is picked. */
    @NonNull
    String tint();

    /** Sets the Tint colour; anything not in {@link #TINTS} writes {@link #TINT_AUTO}. */
    void setTint(@NonNull String tint);

    /**
     * The accent every glass mixes in now, percent: under Auto the worn Look's own (Tinted's
     * blend; none for the others and Custom), under a wallpaper colour {@link #TINT_AMOUNT_PERCENT}.
     */
    int tintAmountPercent();

    /**
     * Depth, 0 to 100, app-wide (ADR 0033): the position the slab's thickness reads as, keeping
     * the one last set while it still writes that thickness.
     */
    int depth();

    /** Sets Depth (the slab's thickness), clamped. Not a Look pick: Custom and the Look stay. */
    void setDepth(int depth);

    /** Shine, 0 to 100, app-wide (ADR 0033). */
    int shine();

    void setShine(int shine);

    void addListener(@NonNull Listener listener);

    void removeListener(@NonNull Listener listener);
}
