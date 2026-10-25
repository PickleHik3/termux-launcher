package com.termux.app.launcher.widget;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.app.notice.TerminalDress;

import java.util.List;

/**
 * An icon lifted from somewhere that is not a Home page (out of the dock's pinned row, or a
 * shortcut dragged out of the App menu), carried by the finger that lifted it until it lets go.
 * The touch stream drives it: the view the finger went down on passes every move and the release
 * here, in screen coordinates, so the icon can cross the dock, the page and the status chrome as
 * one picture ({@link LiftOverlay}). Where it is let go decides what happens
 * ({@link LiftDropPolicy}): a target (Remove, Uninstall), the dock's row, or a cell of the Home page
 * under the finger, which Home outlines and fills through the same hand-over the drawer uses
 * ({@link HomeAppDrop}). Anywhere else it simply goes back.
 */
public final class IconCarry {

    /** Where the carried icon can go, and what the source does about it. */
    public interface Host {
        /** Home, to put the icon down on; null where there is none. */
        @Nullable HomeAppDrop.Target home();
        /**
         * The finger is at ({@code rawX}, {@code rawY}): true while the dock's row is under it and
         * takes it (it opens a gap and turns its pages at its ends); false elsewhere.
         */
        default boolean hoverDock(float rawX, float rawY) { return false; }
        /** The finger left the dock: the gap closes. */
        default void leaveDock() { }
        /** Let go over the dock. True when the row took it. */
        default boolean dropOnDock(float rawX, float rawY) { return false; }
        /** The status chrome along the top of the screen, on screen, for the targets. */
        default boolean targetBand(@NonNull Rect outOnScreen) { return false; }
        /** What the terminal is wearing, for the targets to wear the notice pill's dress. */
        @Nullable default TerminalDress.Source targetDress() { return null; }
        /** Let go over a target. */
        default void onTarget(@NonNull HomeDropTargets.Target target) { }
        /** Put down on a Home page. */
        default void onPlacedOnHome() { }
        /** Let go anywhere that took nothing: whatever the source hid shows again. */
        default void onReturned() { }
        boolean reducedMotion();
    }

    @NonNull private final View anchor;
    @NonNull private final LiftDropPolicy.Item item;
    @NonNull private final HomeAppDrop drop;
    @NonNull private final List<HomeDropTargets.Target> targets;
    @NonNull private final Host host;
    @NonNull private final LiftOverlay overlay;
    private final Rect band = new Rect();
    @Nullable private HomeDropTargets.Layout layout;
    /** The target the finger is over, or -1. */
    private int over = -1;
    private boolean overDock;
    private boolean ended;

    /**
     * @param anchor  a view in the window the finger is in
     * @param drop    the icon as Home takes it; {@link HomeAppDrop#carriedOutside} is set here
     * @param targets the drop targets to put up ({@link LiftDropPolicy#targetsFor}), maybe none
     */
    public IconCarry(@NonNull View anchor, @NonNull LiftDropPolicy.Item item,
                     @NonNull HomeAppDrop drop, @NonNull List<HomeDropTargets.Target> targets,
                     @NonNull Host host) {
        this.anchor = anchor;
        this.item = item;
        this.drop = drop.carriedOutside();
        this.targets = targets;
        this.host = host;
        this.overlay = new LiftOverlay(anchor);
    }

    /**
     * Lifts the icon: {@code picture}, {@code sizePx} square, under the finger with its corner at
     * ({@code dx}, {@code dy}) from it, and the targets up. False when there is no window to draw
     * it in, and nothing was lifted.
     */
    public boolean begin(@NonNull Drawable picture, int sizePx, float dx, float dy, float rawX,
                         float rawY) {
        if (!targets.isEmpty()) {
            layout = overlay.showTargets(targets, host.targetBand(band) ? band : null,
                host.targetDress(), !host.reducedMotion());
        }
        if (!overlay.carry(picture, sizePx, sizePx, dx, dy, rawX, rawY)) {
            overlay.release();
            layout = null;
            return false;
        }
        move(rawX, rawY);
        return true;
    }

    /** The finger moved to ({@code rawX}, {@code rawY}). */
    public void move(float rawX, float rawY) {
        if (ended) return;
        overlay.moveTo(rawX, rawY);
        int target = layout == null ? -1 : HomeDropTargets.hit(layout, rawX, rawY);
        if (target != over) {
            over = target;
            overlay.highlight(target);
            if (target >= 0) Haptics.tick(anchor, HapticFeedbackConstants.CLOCK_TICK);
        }
        if (target >= 0) {
            leaveDock();
            leaveHome();
            return;
        }
        if (LiftDropPolicy.accepts(item, LiftDropPolicy.Place.DOCK)
                && host.hoverDock(rawX, rawY)) {
            overDock = true;
            leaveHome();
            return;
        }
        leaveDock();
        if (LiftDropPolicy.accepts(item, LiftDropPolicy.Place.HOME)) followHome(rawX, rawY);
    }

    /**
     * The finger let go at ({@code rawX}, {@code rawY}), or the stream was cancelled: the icon goes
     * where it was let go, or back.
     */
    public void end(float rawX, float rawY, boolean canceled) {
        if (ended) return;
        ended = true;
        int target = over;
        boolean dock = overDock;
        overlay.release();
        if (!canceled && target >= 0 && target < targets.size()) {
            cancelHome();
            leaveDock();
            host.onTarget(targets.get(target));
            return;
        }
        if (!canceled && dock) {
            cancelHome();
            boolean taken = host.dropOnDock(rawX, rawY);
            leaveDock();
            if (!taken) host.onReturned();
            return;
        }
        leaveDock();
        HomeAppDrop.Follower follower = drop.follower();
        boolean placed = false;
        if (follower != null) {
            if (canceled) follower.cancel();
            else placed = follower.releaseAt(rawX, rawY);
        }
        if (placed) host.onPlacedOnHome();
        else host.onReturned();
    }

    /** Whether the carry has ended. */
    public boolean isEnded() {
        return ended;
    }

    /**
     * Over Home: the first time, Home is asked to take the drag over (the drawer closes and the
     * wall turns to Home, as for an icon dragged out of the drawer); from then on it is told where
     * the finger is. Home that cannot take it (a locked layout, another drag) takes nothing.
     */
    private void followHome(float rawX, float rawY) {
        HomeAppDrop.Follower follower = drop.follower();
        if (follower == null) {
            HomeAppDrop.Target home = host.home();
            if (home == null || !home.canTakeAppDrop()) return;
            home.onAppDropStarted(drop, rawX, rawY);
            return;
        }
        follower.moveTo(rawX, rawY);
    }

    private void leaveHome() {
        HomeAppDrop.Follower follower = drop.follower();
        if (follower != null) follower.leave();
    }

    private void cancelHome() {
        HomeAppDrop.Follower follower = drop.follower();
        if (follower != null) follower.cancel();
    }

    private void leaveDock() {
        if (!overDock) return;
        overDock = false;
        host.leaveDock();
    }
}
