package com.termux.app.chrome;

import androidx.annotation.NonNull;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;

/**
 * What Maximise hides, pure: a window's Maximise control fills the whole screen with the page.
 * The status row (and the marks on it), the pinned apps and the A–Z bar go; the extra keys stay
 * only while the keyboard is up, so typing in a maximised Terminal still works. The system bars
 * hide too, unless the Full screen setting already hides them (it then owns them). A swipe from
 * the top shows Android's transient bars, and the row comes back while they show.
 *
 * <p>Maximised is per window and ends when the page changes away from it or it closes; the
 * activity holds which window it is and applies the answer through the place layout, as a Layout
 * page write is applied.
 */
public final class MaximisePolicy {

    /** How long the row stays back after a swipe from the top shows the bars, in ms. */
    public static final long REVEAL_MS = 3000L;

    private MaximisePolicy() {
    }

    /**
     * {@code layout} as a maximised page stands it: everything but the extra keys hidden, and
     * those too while the keyboard is down; the status row back while {@code revealed}. Returns
     * {@code layout} itself when nothing changes.
     */
    @NonNull
    public static PlaceLayout layoutFor(@NonNull PlaceLayout layout, boolean revealed,
                                        boolean keyboardUp) {
        PlaceLayout out = hide(layout, Element.APPS);
        out = hide(out, Element.AZ);
        if (!keyboardUp) out = hide(out, Element.EXTRA_KEYS);
        if (!revealed) out = hide(out, Element.STATUS);
        return out;
    }

    private static PlaceLayout hide(@NonNull PlaceLayout layout, @NonNull Element element) {
        Slot slot = layout.slot(element);
        return slot.hidden ? layout : layout.withSlot(element, slot.withHidden(true));
    }

    /** Whether Maximise hides the system bars: the Full screen setting, when on, already does. */
    public static boolean hidesSystemBars(boolean maximised, boolean fullScreenSetting) {
        return maximised && !fullScreenSetting;
    }

    /**
     * Whether a maximised page ends: the page in front is no longer the maximised window's
     * ({@code maximisedKey} against {@code frontKey}), or the window is gone.
     */
    public static boolean ends(@NonNull String maximisedKey, @NonNull String frontKey,
                               boolean windowGone) {
        return windowGone || !maximisedKey.equals(frontKey);
    }
}
