package com.termux.app.chrome;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.KeyboardForm;

/**
 * What material the in-app keyboard renders, given the form it is in and whether the place it is
 * on lets it lie over the content instead of shrinking it.
 *
 * <p>The rule (keyboard-overlays spec, D1 and D3) is one sentence: <em>overlays are solid, the
 * dock is glass</em>. Anything lying over content is one opaque Material surface — no wallpaper
 * crop, no frost, no glass slice, no rim — and it ignores the Keyboard surface's opacity and its
 * scheme background colour, because both describe how much of the wallpaper shows through a
 * material that is no longer there. The terminal's docked keyboard resizes the terminal around
 * itself, shares the dock's material, and keeps every one of them.
 *
 * <p>The table this implements, {@code form × overlays → material}:
 *
 * <pre>
 * DOCKED,   resizing  -> GLASS  opacity applies      the shared dock material, unchanged
 * DOCKED,   overlay   -> SOLID  opacity ignored      one opaque fill, capsule radius or square
 * SPLIT,    either    -> NONE   opacity ignored      the halves paint their own opaque slabs
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
        switch (form) {
            // The card behind a floating keyboard is already the solid panel, and the split
            // halves paint one slab per run of keys so the parting stays clear. A host fill
            // under either would be a second material inside the first.
            case FLOATING:
            case SPLIT:
                return Material.NONE;
            case DOCKED:
            default:
                return overlays ? Material.SOLID : Material.GLASS;
        }
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
     * Whether the keyboard view paints its own slabs in the overlay role rather than letting the
     * host's material show through. The split halves do, on every place: they are over the
     * content wherever they are, and the parting between them must stay clear of both.
     */
    public static boolean paintsOwnSolidSlabs(@NonNull KeyboardForm form) {
        return form == KeyboardForm.SPLIT;
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
}
