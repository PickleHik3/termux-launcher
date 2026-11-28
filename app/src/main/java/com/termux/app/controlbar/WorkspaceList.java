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
 * {@link Host}. Letting go on a row goes there; a row's own Rename (its long press) renames it.
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
    /** The rows the card was drawn from, to tell when the Workspaces have changed under it. */
    @NonNull private List<WorkspaceListRows.Row> mShown = java.util.Collections.emptyList();

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

    /**
     * A held mark was carried up off {@code bar} (#425): the card rises for the window it carries.
     * Its rows light under the finger and a still finger renames nothing; {@link #drop} says where
     * it was let go.
     */
    public void raiseToCarry(@NonNull View bar, boolean rail) {
        WorkspaceListView view = open(bar, rail);
        if (view != null) view.beginCarry();
    }

    /**
     * The carried window was let go at ({@code rawX}, {@code rawY}): the card goes, and the place of
     * the row under the finger is returned for the window to move to; null over none, or when the
     * Workspaces changed under the card (its numbers are not true any more).
     */
    @Nullable
    public Place drop(float rawX, float rawY) {
        WorkspaceListView view = mView;
        if (view == null) return null;
        Place place = view.release(rawX, rawY);
        boolean fresh = fresh();
        close();
        return fresh ? place : null;
    }

    /** Opens the card with no finger (the bar's accessibility action); it stays until dismissed. */
    public void openHeld(@NonNull View bar, boolean rail) {
        WorkspaceListView view = open(bar, rail);
        if (view != null) view.announceOpen();
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
                // A card gone stale is drawn again and the tap is spent: the number it showed is not true.
                if (!fresh()) return;
                close();
                mHost.goTo(place);
            }
            @Override public void onRename(int number, @NonNull String name) {
                if (fresh()) mHost.rename(number, name);
            }
            @Override public void onDismiss() { close(); }
            @Override public void onTextInput(@NonNull EditText field, boolean begin) {
                mHost.textInput(field, begin);
            }
        });
        mShown = WorkspaceListRows.of(mHost.stack());
        view.bind(mShown);
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

    /**
     * The sliding finger lifted: it goes to the row it is on. Let go off the rows (a long slide
     * past the card, or a quick one that ended before the card was drawn) the card stays up as
     * if opened without a finger: a tap on a row goes there, a touch outside or Back puts it away.
     */
    public void release(float rawX, float rawY) {
        WorkspaceListView view = mView;
        if (view == null) return;
        Place place = view.release(rawX, rawY);
        if (view.holdsOpen()) return;
        if (place == null) {
            // Drawn again from the stack if it changed under the finger, and left up.
            fresh();
            return;
        }
        boolean fresh = fresh();
        close();
        if (fresh) mHost.goTo(place);
    }

    /**
     * The sliding finger lifted after a quick swipe from the Workspace chip: nothing is gone to, and
     * the card stays up as if opened without a finger.
     */
    public void settleOpen() {
        WorkspaceListView view = mView;
        if (view == null) return;
        view.endFinger();
        // Drawn again from the stack if it changed under the finger.
        fresh();
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

    /**
     * Whether the Workspaces are still the ones the card shows. When they are not, the numbers on
     * it must not be gone to or renamed; an idle card is drawn again from the stack as it is.
     */
    private boolean fresh() {
        WorkspaceListView view = mView;
        if (view == null) return false;
        List<WorkspaceListRows.Row> now = WorkspaceListRows.of(mHost.stack());
        if (WorkspaceListRows.same(mShown, now)) return true;
        mShown = now;
        if (!view.holdsOpen()) view.bind(now);
        return false;
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
