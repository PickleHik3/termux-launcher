package com.termux.app.launcher.widget;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;

/**
 * An app icon on its way to Home from the drawer: the local state of the platform drag that
 * carries it. The drawer starts the drag and hands it over; Home's pane follows it, draws it, turns
 * pages under it and puts it down, through the same drag layer, edge bands and snap ghost a widget
 * carried out of the picker uses. Nothing about the app travels in the public clip data.
 */
public final class HomeAppDrop {

    /** The two ends of the hand-over: the drawer asks, Home takes it from there. */
    public interface Target {
        /** Whether Home can take an app dragged in from outside the page right now. */
        boolean canTakeAppDrop();

        /**
         * The drag carrying {@code drop} has started, the finger at ({@code rawX}, {@code rawY})
         * on screen: the drawer goes, Home comes under the finger and picks the icon up.
         */
        void onAppDropStarted(@NonNull HomeAppDrop drop, float rawX, float rawY);
    }

    @NonNull public final AppRef app;
    @NonNull public final String label;
    /** The icon as the drawer shows it; drawn, never kept past the drag. */
    @NonNull public final Drawable icon;

    public HomeAppDrop(@NonNull AppRef app, @NonNull String label, @NonNull Drawable icon) {
        this.app = app;
        this.label = label;
        this.icon = icon;
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

            @Override public void onDrawShadow(@NonNull Canvas canvas) { }
        };
    }
}
