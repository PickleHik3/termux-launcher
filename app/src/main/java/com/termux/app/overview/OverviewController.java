package com.termux.app.overview;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.OverlayRegistry;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.List;

/**
 * Overview as a screen on the launcher screens host: opens {@link OverviewView} in the pages
 * window layer, and is the {@link OverlayRegistry.Overlay} the activity registers, so Back, a home
 * press and the lifecycle drops reach it. The wall never moves while it is up, so closing it
 * returns to the page it was opened from; a card's tap closes it first and then goes.
 */
public final class OverviewController implements OverlayRegistry.Overlay {

    /** The launcher's side: what the wall is, how it travels, and the look. */
    public interface Host {
        @NonNull PageBarModel wall();
        @NonNull PageSwitchTargets.Navigator navigator();
        /** The sheet material (Global's glass at the sheets' floor). */
        @NonNull Drawable background();
        int onSurface();
        int onSurfaceVariant();
        int accent();
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @Nullable private OverviewView mView;

    public OverviewController(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    /** Opens Overview, or closes it when it is up: the session chip's tap. */
    public void toggle() {
        if (mView != null) close();
        else open();
    }

    public void open() {
        if (mView != null) return;
        OverviewView view = new OverviewView(mActivity, new OverviewView.Host() {
            @Override @NonNull public Drawable background() { return mHost.background(); }
            @Override public int onSurface() { return mHost.onSurface(); }
            @Override public int onSurfaceVariant() { return mHost.onSurfaceVariant(); }
            @Override public int accent() { return mHost.accent(); }
            @Override public void onCardChosen(@NonNull OverviewModel.Card card) {
                close();
                PageSwitchTargets.carryOut(card.target, mHost.navigator());
            }
            @Override public void onClose() { close(); }
        });
        view.apply(OverviewModel.of(mHost.wall()));
        boolean added = WindowLayers.add(mActivity, WindowLayer.PAGES, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) return;
        mView = view;
        view.requestFocus();
    }

    /** Removes the screen; the wall is where it was. */
    public void close() {
        OverviewView view = mView;
        if (view == null) return;
        mView = null;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    @Override public boolean onBack() {
        if (mView == null) return false;
        close();
        return true;
    }

    @Override public boolean isOpen() {
        return mView != null;
    }

    @Override public void closeImmediately(@NonNull OverlayRegistry.CloseReason reason) {
        close();
    }

    @Override public void exitViews(@NonNull List<View> out) {
        // Closed by the Home exit's end, with no animation of its own.
        if (mView != null) out.add(mView);
    }
}
