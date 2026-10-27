package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.AppearanceLooks.Control;

/**
 * The keyboard's opacity link: Key opacity and its Opacity. While linked, a move of Key opacity
 * on the Keyboard card moves Opacity by the same amount, held inside its range.
 *
 * <p>The amount is measured from where both stood when the move began, not tick by tick: a drag
 * that pins the partner at an end and comes back finds it where the shared offset says, so the
 * pair never drifts apart within one drag.</p>
 */
final class OpacityLink {

    private OpacityLink() {}

    /** The slider a linked move of {@code control} carries along, or null for any other. */
    @Nullable
    static Control partner(@NonNull Control control) {
        switch (control) {
            case OPACITY: return Control.KEY_OPACITY;
            case KEY_OPACITY: return Control.OPACITY;
            default: return null;
        }
    }

    /**
     * Where the partner stands after the moved slider went from {@code movedStart} to
     * {@code movedNow}: its own start moved by the same amount, held inside its range.
     */
    static int follow(int movedStart, int movedNow, int partnerStart, @NonNull Control partner) {
        return partner.clamp(partnerStart + (movedNow - movedStart));
    }
}
