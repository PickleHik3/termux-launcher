package com.termux.app.chrome;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The one ordered list of the surfaces drawn over the terminal, and the four things the activity
 * has to do to all of them: hand a Back press to the innermost one, hand a key or committed text
 * to the one that owns typing, and drop them when the activity stops, goes HOME or rotates.
 *
 * <p>Those four used to be four hand-maintained chains in {@code TermuxActivity} that had drifted
 * apart — the key channel had no Back claim for the surface editor, a HOME press closed only the
 * drawer, a rotation closed only the palette and the sheet. Here an overlay is registered once,
 * in its z order from innermost to outermost, and every chain is derived from that order.
 *
 * <p>Back on a device travels the key channel and is consumed before {@code onBackPressed()} ever
 * runs, so both routes go through {@link #onBackPressed()} / {@link #consumeKeyDown}; the key
 * route also remembers that it claimed the press so the matching release is swallowed rather
 * than delivered to the shell behind a surface that has just closed.
 */
public final class OverlayRegistry {

    /** Why every overlay is being dropped at once. */
    public enum CloseReason {
        /** {@code onStop}: the activity is leaving the screen. */
        STOP,
        /** A HOME press while already home: back to the resting home screen. */
        HOME,
        /** {@code onConfigurationChanged}: the geometry every open surface measured is gone. */
        ROTATION
    }

    /** A surface that Back can close and a lifecycle event can drop. */
    public interface Overlay {
        /**
         * A Back press aimed at this overlay. Closes one layer — a card, not the whole stack.
         *
         * @return true when the press was spent here, whether or not anything visibly closed.
         */
        boolean onBack();

        /** Drops the overlay with no animation for {@code reason}; do nothing to ignore it. */
        default void closeImmediately(@NonNull CloseReason reason) { }
    }

    /**
     * A surface that is typed into. While it is up, every key and every committed character is
     * offered to it before the shell, and it handles its own Back and Escape through that route.
     */
    public interface TypedOverlay extends Overlay {
        boolean onKeyDown(int keyCode, @NonNull KeyEvent event);

        boolean onCodePoint(int codePoint, boolean ctrlDown);

        /**
         * Whether a key release arriving now belongs to a press this overlay consumed. Default
         * false: only the full-screen planes swallow releases, exactly as they always have.
         */
        default boolean swallowsKeyUp() {
            return false;
        }
    }

    @NonNull private final List<Overlay> mOverlays = new ArrayList<>();
    /**
     * Set when a Back-only overlay consumed the press in the key channel, so the matching release
     * is swallowed. A flag rather than "is it still open", because by the time the release arrives
     * the overlay is closing and would answer no.
     */
    private boolean mClaimedBackDown;

    /** Registers the next overlay outward; the first registered is the innermost. */
    public void register(@NonNull Overlay overlay) {
        mOverlays.add(overlay);
    }

    /** @return true when some overlay spent the press. */
    public boolean onBackPressed() {
        for (Overlay overlay : mOverlays)
            if (overlay.onBack()) return true;
        return false;
    }

    /**
     * The key channel: typed overlays see every key; the others see a Back press only. Innermost
     * first, so a stroke belongs to the surface the user is looking at.
     */
    public boolean consumeKeyDown(int keyCode, @NonNull KeyEvent event) {
        boolean backDown = keyCode == KeyEvent.KEYCODE_BACK
            && event.getAction() == KeyEvent.ACTION_DOWN;
        for (Overlay overlay : mOverlays) {
            if (overlay instanceof TypedOverlay) {
                if (((TypedOverlay) overlay).onKeyDown(keyCode, event)) return true;
            } else if (backDown && overlay.onBack()) {
                mClaimedBackDown = true;
                return true;
            }
        }
        return false;
    }

    /** The release of a stroke the channel consumed on the way down. */
    public boolean consumeKeyUp(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mClaimedBackDown) {
            mClaimedBackDown = false;
            return true;
        }
        for (Overlay overlay : mOverlays)
            if (overlay instanceof TypedOverlay && ((TypedOverlay) overlay).swallowsKeyUp())
                return true;
        return false;
    }

    /** Text a system IME committed, in the same order as the key channel. */
    public boolean consumeCodePoint(int codePoint, boolean ctrlDown) {
        for (Overlay overlay : mOverlays)
            if (overlay instanceof TypedOverlay
                && ((TypedOverlay) overlay).onCodePoint(codePoint, ctrlDown)) return true;
        return false;
    }

    /** Drops every overlay for {@code reason}, innermost first. */
    public void closeAll(@NonNull CloseReason reason) {
        for (Overlay overlay : mOverlays) overlay.closeImmediately(reason);
    }

    /** Visible for tests. */
    public int size() {
        return mOverlays.size();
    }
}
