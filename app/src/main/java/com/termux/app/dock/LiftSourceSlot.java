package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The slot an icon lifted out of the dock's row left, hidden while the icon is in the air. The row
 * may draw itself again during the lift (its page turns, the wall turns to Home and the dock is
 * dressed again), and each new drawing hides the pin's new slot through {@link #hide}, so the slot
 * this holds is always the one on screen. However the lift ends, {@link #end} shows that slot
 * again: an icon that moved has already left it, and one that went nowhere is back in place. A
 * row that draws itself again after the end is not asked to hide anything.
 *
 * @param <V> the slot (a view in the row); pure, so it is testable without one
 */
public final class LiftSourceSlot<V> {

    /** Shows or hides a slot. */
    public interface Shade<V> {
        void setShown(@NonNull V slot, boolean shown);
    }

    @NonNull private final Shade<V> shade;
    @Nullable private V hidden;
    private boolean ended;

    public LiftSourceSlot(@NonNull Shade<V> shade) {
        this.shade = shade;
    }

    /**
     * {@code slot} now stands for the lifted pin: it is hidden, and the slot hidden before it shows
     * again (it has left the row, or it is the same slot). After the end nothing is hidden.
     *
     * @return whether the slot was hidden
     */
    public boolean hide(@NonNull V slot) {
        if (ended) return false;
        V previous = hidden;
        hidden = slot;
        if (previous != null && previous != slot) shade.setShown(previous, true);
        shade.setShown(slot, false);
        return true;
    }

    /** The lift is over, however it ended: the hidden slot shows again. Once only. */
    public void end() {
        if (ended) return;
        ended = true;
        V slot = hidden;
        hidden = null;
        if (slot != null) shade.setShown(slot, true);
    }

    public boolean isEnded() {
        return ended;
    }

    /** The slot hidden now; null after the end. */
    @Nullable
    public V hidden() {
        return hidden;
    }
}
