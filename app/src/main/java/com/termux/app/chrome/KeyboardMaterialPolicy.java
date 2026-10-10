package com.termux.app.chrome;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.KeyboardForm;

/**
 * What material the in-app keyboard renders, given the form it is in and whether the place it is
 * on lets it lie over the content instead of shrinking it.
 *
 * <p>The rule is one sentence: <em>overlays are solid, the
 * dock is glass</em>. Anything lying over content is one opaque Material surface — no wallpaper
 * crop, no frost, no glass slice, no rim — and it ignores the Keyboard surface's opacity and its
 * scheme background colour, because both describe how much of the wallpaper shows through a
 * material that is no longer there. The terminal's docked keyboard resizes the terminal around
 * itself, shares the dock's material, and keeps every one of them. The split keyboard is the
 * docked one with its keys parted: the host paints it exactly as it paints the docked keyboard,
 * and the parting shows that material.
 *
 * <p>The table this implements, {@code form × overlays → material}:
 *
 * <pre>
 * DOCKED,   resizing  -> GLASS  opacity applies      the shared dock material, unchanged
 * DOCKED,   overlay   -> SOLID  opacity ignored      one opaque fill, capsule radius or square
 * SPLIT,    resizing  -> GLASS  opacity applies      as DOCKED: only the keys part
 * SPLIT,    overlay   -> SOLID  opacity ignored      as DOCKED
 * FLOATING, always    -> NONE   opacity ignored      the card is the panel (phase 1)
 * </pre>
 *
 * <p>Pure, so the answer can be read and tested without a window: the activity only applies it.
 */
public final class KeyboardMaterialPolicy {

    private KeyboardMaterialPolicy() {}

    /** What the keyboard's surface host paints behind the keys. */
    public enum Material {
        /** The dock's blurred-wallpaper-and-tint stack, shared or keyboard-local. */
        GLASS,
        /** One opaque fill in the overlay surface role, {@code colorSurfaceContainerHigh}. */
        SOLID,
        /** Nothing at all: something else in the form already paints the panel. */
        NONE
    }

    /** The material the keyboard's surface host paints for one arrangement. */
    @NonNull
    public static Material hostMaterial(@NonNull KeyboardForm form, boolean overlays) {
        // The card behind a floating keyboard is already the solid panel; a host fill under it
        // would be a second material inside the first.
        if (form == KeyboardForm.FLOATING) return Material.NONE;
        return overlays ? Material.SOLID : Material.GLASS;
    }

    /**
     * Whether the Keyboard surface's opacity — and the scheme background colour that rides with
     * it — decides how this arrangement is painted. Only the glass does: an overlay is opaque by
     * definition, so a slider that says how much wallpaper shows through has nothing to say
     * about it. The side gap is not on this list; it applies to every form.
     */
    public static boolean opacityApplies(@NonNull KeyboardForm form, boolean overlays) {
        return hostMaterial(form, overlays) == Material.GLASS;
    }

    /**
     * Whether a {@link Material#SOLID} fill takes the capsule's corner. It follows the surface
     * shape exactly as the glass did: rounded while the shape is the capsule, square otherwise,
     * so switching a place to overlay mode never changes the keyboard's outline.
     */
    public static boolean solidFillIsRounded(@NonNull KeyboardForm form, boolean overlays,
                                             boolean capsule) {
        return hostMaterial(form, overlays) == Material.SOLID && capsule;
    }

    /** A material that is not blending between two places: the host paints one of the table's rows. */
    public static final float NO_TRAVEL = -1f;

    /**
     * How solid the docked or split keyboard's host is while the wall stands {@code fraction} of the way
     * from one place to the next with the keyboard up on both: 0 is the glass, 1 the opaque panel,
     * and the panel's alpha in between. At {@code fraction} 0 or 1 this is exactly the material
     * the place at rest paints, so the settle that follows changes nothing visible; the swap used
     * to land whole at settle, an opaque panel appearing over the glass in one frame.
     *
     * <p>{@link #NO_TRAVEL} when there is nothing to blend: the two places paint the same
     * material, one of them has the keyboard down (it travels in or out as a whole, in the
     * material of the place that has it up), or the keyboard is floating — its card is the
     * panel on every place.</p>
     */
    public static float travelSolidness(@NonNull KeyboardForm form, boolean fromOverlays,
                                        boolean towardOverlays, boolean fromKeyboardUp,
                                        boolean towardKeyboardUp, float fraction) {
        if (form == KeyboardForm.FLOATING || !fromKeyboardUp || !towardKeyboardUp) return NO_TRAVEL;
        Material from = hostMaterial(form, fromOverlays);
        Material toward = hostMaterial(form, towardOverlays);
        if (from == toward) return NO_TRAVEL;
        float clamped = fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
        float a = from == Material.SOLID ? 1f : 0f;
        float b = toward == Material.SOLID ? 1f : 0f;
        return a + (b - a) * clamped;
    }
}
