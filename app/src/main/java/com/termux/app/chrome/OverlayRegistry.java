package com.termux.app.chrome;

import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        /**
         * Whether the overlay is up now, as a layer over the page below: what asks it is a page
         * that has to leave Back and the keys to it. Default false, for an overlay that has not
         * said.
         */
        default boolean isOpen() { return false; }

        /**
         * Adds the views this overlay gives to the Home exit, while it is open: they fade and
         * shrink with the other members, and {@code closeImmediately(HOME)} runs at the end. Adds
         * nothing by default: such an overlay closes at once when the exit starts. Every overlay the
         * activity registers states it, an empty body saying it has none ({@code
         * OverlayExitStatedTest}); the surface's own leave animation must not run on a HOME close.
         */
        default void exitViews(@NonNull List<View> out) { }

        /**
         * The Home exit has started with this overlay as a member: anything that settles beside
         * the fade (the editor's page growing back) starts now, over {@code durationMs}.
         */
        default void exitStarted(long durationMs) { }

        /**
         * Whether leaving now would lose something the person has not saved: a draft. Default
         * false, so a surface that has not said is closed without a question, as it always was.
         */
        default boolean holdsUnsavedChanges() { return false; }

        /**
         * Asks the person whether to leave, over this overlay: {@code leave} when they go on
         * (saved or discarded), {@code stay} when they keep what they were doing. Called only
         * while {@link #holdsUnsavedChanges()}. Default: no question, {@code leave} at once.
         */
        default void askBeforeLeaving(@NonNull Runnable leave, @NonNull Runnable stay) {
            leave.run();
        }
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
    /** An unsaved-changes question is up and has not been answered. */
    private boolean mAsking;
    /** The overlays leaving in the Home exit now. */
    @NonNull private final List<Overlay> mExiting = new ArrayList<>();
    /** Ends the Home exit at once; its end calls {@link #endExit}. */
    @Nullable private Runnable mFinishExit;

    /**
     * Sets what ends a running Home exit at once. Back during the exit does that and is spent
     * there, as a second home press is: nothing else handles input over a surface that is leaving.
     */
    public void setExitFinisher(@Nullable Runnable finishExit) {
        mFinishExit = finishExit;
    }

    private boolean finishExitIfRunning() {
        if (mExiting.isEmpty() || mFinishExit == null) return false;
        mFinishExit.run();
        return true;
    }

    /** Registers the next overlay outward; the first registered is the innermost. */
    public void register(@NonNull Overlay overlay) {
        mOverlays.add(overlay);
    }

    /** @return true when some overlay spent the press. */
    public boolean onBackPressed() {
        if (finishExitIfRunning()) return true;
        for (int i = 0; i < mOverlays.size(); i++) {
            if (mOverlays.get(i).onBack()) {
                // DIAG(back-path): which overlay spent Back; revert before merge.
                if (MotionLog.enabled) MotionLog.log("back-overlay", "index=" + i + " of="
                    + mOverlays.size() + " via=pressed");
                return true;
            }
        }
        return false;
    }

    /**
     * The key channel: typed overlays see every key; the others see a Back press only. Innermost
     * first, so a stroke belongs to the surface the user is looking at.
     */
    public boolean consumeKeyDown(int keyCode, @NonNull KeyEvent event) {
        boolean backDown = keyCode == KeyEvent.KEYCODE_BACK
            && event.getAction() == KeyEvent.ACTION_DOWN;
        if (backDown && finishExitIfRunning()) {
            mClaimedBackDown = true;
            return true;
        }
        for (int i = 0; i < mOverlays.size(); i++) {
            Overlay overlay = mOverlays.get(i);
            if (overlay instanceof TypedOverlay) {
                if (((TypedOverlay) overlay).onKeyDown(keyCode, event)) {
                    // DIAG(back-path): revert before merge.
                    if (MotionLog.enabled && keyCode == KeyEvent.KEYCODE_BACK)
                        MotionLog.log("back-overlay", "index=" + i + " via=typed-key");
                    return true;
                }
            } else if (backDown && overlay.onBack()) {
                if (MotionLog.enabled) MotionLog.log("back-overlay", "index=" + i + " via=key");
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

    /** Whether any overlay is up: a layer is open over the page, and takes Back and keys first. */
    public boolean anyOpen() {
        for (Overlay overlay : mOverlays)
            if (overlay.isOpen()) return true;
        return false;
    }

    /**
     * Starts a Home exit: every overlay that gives views to {@code out} becomes a member and is
     * left open; every other overlay closes now, as a home press always closed them.
     *
     * @return the number of members; 0 means everything is already closed
     */
    public int beginExit(@NonNull List<View> out) {
        mAsking = false;
        mExiting.clear();
        for (Overlay overlay : mOverlays) {
            int before = out.size();
            overlay.exitViews(out);
            if (out.size() > before) mExiting.add(overlay);
            else overlay.closeImmediately(CloseReason.HOME);
        }
        for (int i = 0; i < mExiting.size(); i++) mExiting.get(i).exitStarted(HomeExit.DURATION_MS);
        return mExiting.size();
    }

    /** The exit's end: each member resets its own views and closes. */
    public void endExit() {
        for (int i = 0; i < mExiting.size(); i++) mExiting.get(i).closeImmediately(CloseReason.HOME);
        mExiting.clear();
    }

    /** Whether an open overlay holds unsaved changes. */
    public boolean anyHoldsUnsavedChanges() {
        for (Overlay overlay : mOverlays)
            if (overlay.isOpen() && overlay.holdsUnsavedChanges()) return true;
        return false;
    }

    /**
     * Asks each open overlay that holds unsaved changes, innermost first, one question at a time.
     * {@code leave} runs when every one let go; {@code stay} runs at the first that did not (the
     * rest are not asked). A no-op while a question is pending; {@link #closeAll} forgets it, as
     * a dialog dropped with its surface never answers.
     */
    public void askBeforeLeaving(@NonNull Runnable leave, @NonNull Runnable stay) {
        if (mAsking) return;
        mAsking = true;
        askFrom(0, () -> {
            mAsking = false;
            leave.run();
        }, () -> {
            mAsking = false;
            stay.run();
        });
    }

    private void askFrom(int from, @NonNull Runnable leave, @NonNull Runnable stay) {
        for (int i = from; i < mOverlays.size(); i++) {
            final Overlay overlay = mOverlays.get(i);
            if (!overlay.isOpen() || !overlay.holdsUnsavedChanges()) continue;
            final int next = i + 1;
            overlay.askBeforeLeaving(() -> askFrom(next, leave, stay), stay);
            return;
        }
        leave.run();
    }

    /** Drops every overlay for {@code reason}, innermost first. */
    public void closeAll(@NonNull CloseReason reason) {
        mAsking = false;
        for (Overlay overlay : mOverlays) overlay.closeImmediately(reason);
    }

    /** Visible for tests. */
    public int size() {
        return mOverlays.size();
    }
}
