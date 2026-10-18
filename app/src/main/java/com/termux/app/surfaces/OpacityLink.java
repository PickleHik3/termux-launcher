package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.AppearanceLooks.Control;

/**
 * The keyboard's opacity link: Key opacity and BG opacity, two adjacent sliders of its Custom row
 * under one link button. While linked, a move of either moves the other by the same amount, held
 * inside the other's range. Pure, so the arithmetic and the leader lines' shape are asserted
 * without a panel.
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

    /**
     * Where the link button's centre stands across: midway between the two tracks, moved toward
     * the far track just enough to stand clear of the heading beside it, never past the far
     * track. The heading leads the row: on the left, or on the right in a right-to-left locale.
     *
     * @param trackA one track's centre
     * @param trackB the other's
     * @param halfWidth the button's half width
     * @param nameOnLeft whether the heading stands on the left
     * @param nameEdge the heading's inner edge (its right edge when on the left), or NaN while
     *                 it is not shown
     */
    static float buttonCentreX(float trackA, float trackB, float halfWidth, boolean nameOnLeft,
                               float nameEdge) {
        float lo = Math.min(trackA, trackB);
        float hi = Math.max(trackA, trackB);
        float mid = (lo + hi) / 2f;
        if (Float.isNaN(nameEdge))
            return mid;
        return nameOnLeft ? Math.min(hi, Math.max(mid, nameEdge + halfWidth))
            : Math.max(lo, Math.min(mid, nameEdge - halfWidth));
    }

    /**
     * The widest the heading may be and still end before the button at its farthest place (its
     * centre on the far track), measured from the heading's outer edge, the row's leading one.
     */
    static float nameRoom(float trackA, float trackB, float halfWidth, boolean nameOnLeft,
                          float nameOuterEdge) {
        float lo = Math.min(trackA, trackB);
        float hi = Math.max(trackA, trackB);
        return Math.max(0f, nameOnLeft ? hi - halfWidth - nameOuterEdge
            : nameOuterEdge - (lo + halfWidth));
    }

    /**
     * One leader line, from the link button down to a slider: a stem under the button, a bar
     * along the gap above the sliders, a drop to the slider's track. Returned as the four points
     * of that polyline, {@code {x0, y0, x1, y1, x2, y2, x3, y3}}:
     * the button's bottom centre, the stem's foot, the point over the track, the track's top.
     *
     * @param buttonCentreX the link button's centre, across
     * @param buttonBottom the link button's bottom edge
     * @param trackCentreX the slider track's centre, across
     * @param sliderTop the slider's top edge, where its track starts
     */
    @NonNull
    static float[] leader(float buttonCentreX, float buttonBottom, float trackCentreX,
                          float sliderTop) {
        // The bar runs midway down the gap between the button and the sliders.
        float barY = buttonBottom + Math.max(0f, sliderTop - buttonBottom) / 2f;
        return new float[] {buttonCentreX, buttonBottom, buttonCentreX, barY,
            trackCentreX, barY, trackCentreX, sliderTop};
    }
}
