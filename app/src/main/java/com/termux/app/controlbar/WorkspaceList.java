package com.termux.app.controlbar;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.List;

/**
 * The Workspace list (#429): raised by sliding up on the control bar, it puts
 * {@link WorkspaceListView} in the SHEETS layer above the bar, reads its rows from the stack each
 * time it opens, and carries out what the card asks (go to a Workspace, rename one) through its
 * {@link Host}. Letting go on a row goes there; a long press on a row renames it.
 */
public final class WorkspaceList {

    /** The tiling desktop and the launcher, as the list reads and asks them. */
    public interface Host {
        /** A copy of the stack as it stands now. */
        @NonNull TilingStack stack();

        /** Goes to {@code place}. */
        boolean goTo(@NonNull Place place);

        /** Names Workspace {@code number}; blank clears it. */
        boolean rename(int number, @NonNull String name);

        /** The bar's glass at {@code radiusPx} for {@code anchor}. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();

        int accent();

        /** A Linux app's own icon by its app id; null for its kind's glyph. */
        @Nullable Drawable appIcon(@NonNull String appId);

        /** The inline name field takes the keyboard ({@code begin}) or lets it go. */
        void textInput(@NonNull EditText field, boolean begin);
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @Nullable private WorkspaceListView mView;

    public WorkspaceList(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    public boolean isOpen() {
        return mView != null;
    }

    /** A finger slid up from {@code bar}: the card rises for it. */
    public void raise(@NonNull View bar, boolean rail) {
        WorkspaceListView view = open(bar, rail);
        if (view != null) view.beginFinger();
    }

    /** Opens the card with no finger (the bar's accessibility action); it stays until dismissed. */
    public void openHeld(@NonNull View bar, boolean rail) {
        open(bar, rail);
    }

    @Nullable
    private WorkspaceListView open(@NonNull View bar, boolean rail) {
        if (mView != null) return mView;
        WorkspaceListView view = new WorkspaceListView(mActivity, new WorkspaceListView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) {
                return mHost.glass(radiusPx, anchor);
            }
            @Override public float cornerRadiusPx() { return mHost.cornerRadiusPx(); }
            @Override public int ink() { return mHost.ink(); }
            @Override public int accent() { return mHost.accent(); }
            @Nullable @Override public Drawable appIcon(@NonNull String appId) { return mHost.appIcon(appId); }
        }, new WorkspaceListView.Listener() {
            @Override public void onGo(@NonNull Place place) {
                close();
                mHost.goTo(place);
            }
            @Override public void onRename(int number, @NonNull String name) { mHost.rename(number, name); }
            @Override public void onDismiss() { close(); }
            @Override public void onTextInput(@NonNull EditText field, boolean begin) {
                mHost.textInput(field, begin);
            }
        });
        view.bind(WorkspaceListRows.of(mHost.stack()));
        ViewGroup layer = WindowLayers.host(mActivity, WindowLayer.SHEETS);
        if (layer == null || !view.placeOver(bar, layer, rail)) return null;
        boolean added = WindowLayers.add(mActivity, WindowLayer.SHEETS, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) return null;
        mView = view;
        return view;
    }

    /** The sliding finger is at ({@code rawX}, {@code rawY}) on screen. */
    public void track(float rawX, float rawY) {
        if (mView != null) mView.track(rawX, rawY);
    }

    /** The sliding finger lifted: it goes to the row it is on, or the card goes with nothing chosen. */
    public void release(float rawX, float rawY) {
        WorkspaceListView view = mView;
        if (view == null) return;
        Place place = view.release(rawX, rawY);
        if (view.holdsOpen()) return;
        close();
        if (place != null) mHost.goTo(place);
    }

    /** The sliding finger was cancelled. */
    public void cancel() {
        WorkspaceListView view = mView;
        if (view == null) return;
        view.endFinger();
        if (!view.holdsOpen()) close();
    }

    /** Back: drops a rename, else puts the card away; false when it was not up. */
    public boolean onBack() {
        WorkspaceListView view = mView;
        if (view == null) return false;
        view.abandonRename();
        close();
        return true;
    }

    /** Takes the card off the screen. */
    public void close() {
        WorkspaceListView view = mView;
        if (view == null) return;
        mView = null;
        view.abandonRename();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    /** Nothing exits on a motion: the card goes at once. */
    public void exitViews(@NonNull List<View> out) {
    }
}
