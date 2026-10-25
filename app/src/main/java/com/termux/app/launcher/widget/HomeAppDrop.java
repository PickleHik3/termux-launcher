package com.termux.app.launcher.widget;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;

/**
 * An app icon on its way to Home from the drawer: the local state of the platform drag that
 * carries it. The drawer starts the drag as its own and hands it over only when the finger leaves
 * the drawer through its top edge ({@code DrawerHandOverPolicy}); from then Home's pane follows it,
 * draws it, turns pages under it and puts it down, through the same drag layer, edge bands and snap
 * ghost a widget carried out of the picker uses. Nothing about the app travels in the public clip
 * data.
 *
 * <p>An icon lifted out of the dock is handed over the same way, by its touch stream rather than
 * a platform drag ({@link IconCarry}): the carrier calls the {@link Follower} itself, and draws
 * the icon above the window while Home outlines the cell ({@link #carriedOutside}).
 */
public final class HomeAppDrop {

    /** The two ends of the hand-over: the drawer asks, Home takes it from there. */
    public interface Target {
        /** Whether Home can take an app dragged in from outside the page right now. */
        boolean canTakeAppDrop();

        /**
         * The drag carrying {@code drop} is Home's from now, the finger at ({@code rawX},
         * {@code rawY}) on screen: the drawer goes, Home comes under the finger and picks the icon
         * up.
         */
        void onAppDropStarted(@NonNull HomeAppDrop drop, float rawX, float rawY);
    }

    /**
     * Home following a drag it has taken over, for drag events that still land on the drawer's
     * views while the drawer is on its way out: the drawer passes them on rather than eat them.
     */
    public interface Follower {
        /** The finger moved to ({@code rawX}, {@code rawY}) on screen. */
        void moveTo(float rawX, float rawY);

        /** The finger let go there. Whether the icon was put down. */
        boolean releaseAt(float rawX, float rawY);

        /**
         * The finger went somewhere Home does not take the icon from (the dock, a drop target):
         * the page stops offering a cell until the finger comes back.
         */
        default void leave() { }

        /** The drag ended elsewhere, or was given up: nothing is put down. */
        default void cancel() { }
    }

    @NonNull public final AppRef app;
    @NonNull public final String label;
    /** The icon as the drawer shows it; drawn, never kept past the drag. */
    @NonNull public final Drawable icon;
    /**
     * The size the drawer was carrying the icon at when Home took it, so the icon under the finger
     * does not jump at the hand-over; 0 to carry it at Home's own icon size from the start.
     */
    public final int liftPx;
    @Nullable private Follower follower;
    /** Whoever carries the icon draws it too; Home only outlines the cell it would take. */
    private boolean carriedOutside;

    public HomeAppDrop(@NonNull AppRef app, @NonNull String label, @NonNull Drawable icon,
                       int liftPx) {
        this.app = app;
        this.label = label;
        this.icon = icon;
        this.liftPx = Math.max(0, liftPx);
    }

    /**
     * Marks an icon whose picture its carrier draws above the window (a dock icon or an App menu
     * shortcut, carried by {@link IconCarry}), so Home's drag layer, which the page clips, draws
     * only the cell it would land in.
     */
    @NonNull
    public HomeAppDrop carriedOutside() {
        carriedOutside = true;
        return this;
    }

    public boolean isCarriedOutside() {
        return carriedOutside;
    }

    /** Home took the drag over and follows it through {@code follower}; null once it ended. */
    public void follow(@Nullable Follower follower) {
        this.follower = follower;
    }

    @Nullable public Follower follower() {
        return follower;
    }

    /**
     * The platform's own shadow, which is nothing: a single clear pixel, because the platform
     * insists on one. What the finger carries is drawn by Home's drag layer, so it can cross the
     * drawer's close and Home's page turns as one picture with the snap ghost under it.
     */
    @NonNull
    public static View.DragShadowBuilder invisibleShadow() {
        return new View.DragShadowBuilder() {
            @Override public void onProvideShadowMetrics(@NonNull Point outSize,
                                                         @NonNull Point outTouch) {
                outSize.set(1, 1);
                outTouch.set(0, 0);
            }

            // Cleared, not left alone: a shadow swapped in mid-drag lands on the surface the
            // drawer's icon was drawn on, which is bigger than this one pixel.
            @Override public void onDrawShadow(@NonNull Canvas canvas) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            }
        };
    }
}
