package com.termux.app.overview;

import android.app.Activity;
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
 * Overview as a screen on the launcher screens host: opens {@link OverviewView} in the sheets
 * window layer with the pages and chrome layers hidden under it ({@link LayersUnderOverview}), and
 * is the {@link OverlayRegistry.Overlay} the activity registers, so Back, a home press and the
 * lifecycle drops reach it. The wall never moves while it is up, so closing it returns to the page
 * it was opened from; a card's tap closes it first and then goes.
 */
public final class OverviewController implements OverlayRegistry.Overlay {

    /** The launcher's side: what the wall is, how it travels, and the look. */
    public interface Host {
        @NonNull PageBarModel wall();
        @NonNull PageSwitchTargets.Navigator navigator();
        @NonNull OverviewLook look();
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @NonNull private final LayersUnderOverview mUnder = new LayersUnderOverview();
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
        OverviewView view = new OverviewView(mActivity, mHost.look(), this::choose, this::close);
        view.apply(OverviewModel.of(mHost.wall()));
        // A sheet (spec, "Look"): over the pages and the chrome, so the status row, the keyboard
        // and the Home pill never draw over it.
        boolean added = WindowLayers.add(mActivity, WindowLayer.SHEETS, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) return;
        mView = view;
        mUnder.hide(WindowLayers.host(mActivity, WindowLayer.PAGES),
            WindowLayers.host(mActivity, WindowLayer.CHROME));
        view.requestFocus();
    }

    private void choose(@NonNull OverviewModel.Card card) {
        close();
        PageSwitchTargets.carryOut(card.target, mHost.navigator());
    }

    /** Removes the screen and shows the layers under it again; the wall is where it was. */
    public void close() {
        OverviewView view = mView;
        if (view == null) return;
        mView = null;
        mUnder.restore();
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
        // Faded by the Home exit and closed at its end, with no animation of its own.
        if (mView != null) out.add(mView);
    }

    @Override public void exitStarted(long durationMs) {
        // The fade reveals the page the press lands on, not the bare ground.
        mUnder.restore();
    }
}
