package com.termux.app.azrail;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.List;

/**
 * The A–Z rail (#437): raised by pressing the control bar's search and sliding up, it puts
 * {@link AzRailView} in the SHEETS layer on the thumb's side, follows the finger through
 * {@link AzRailModel}, and on lifting opens the app the finger let go on through its {@link Host}.
 * A plain tap on search is not this: the bar only raises the rail once the finger has slid up.
 */
public final class AzRail {

    /** The launcher, as the rail reads and asks it. */
    public interface Host {
        /** The apps the rail lists: the drawer's catalogue (Linux apps and the launcher's entries included). */
        @NonNull List<LauncherAppEntry> catalogue();

        /** Opens {@code entry} as the bar's search does: Android over the launcher, Linux after the focused window. */
        void open(@NonNull LauncherAppEntry entry);

        /** The app's icon for a row; null for none. Called off the main thread. */
        @Nullable Drawable icon(@NonNull LauncherAppEntry entry);

        /** The glass the Workspace list and the bar's search wear, cut for {@code anchor}; null for none. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();

        int accent();

        /** Puts the bar's search away when it is open (raising the rail takes its place). */
        void closeSearch();
    }

    static final float MIN_COLUMN_DP = 140f;
    static final float ENTER_SLOP_DP = 24f;
    static final float RAIL_DP = 40f;
    static final float FAN_DP = 220f;
    static final float ITEM_DP = 52f;
    static final float GAP_DP = 8f;

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @Nullable private AzRailView mView;
    @Nullable private AzRailModel mModel;
    /** The SHEETS layer's origin on screen, which the finger's raw position is taken from. */
    private int mOriginX;
    private int mOriginY;

    public AzRail(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    public boolean isOpen() {
        return mView != null;
    }

    /**
     * A finger pressed on search at {@code downRawX} slid up from {@code bar}: the rail rises on its
     * side. On a rail-form bar it stands against the bar's inner edge instead of over it. False
     * when it cannot be raised (nothing to list, no layer).
     */
    public boolean raise(@NonNull View bar, boolean rail, float downRawX) {
        if (mView != null) return true;
        mHost.closeSearch();
        ViewGroup layer = WindowLayers.host(mActivity, WindowLayer.SHEETS);
        if (layer == null || layer.getWidth() <= 0 || layer.getHeight() <= 0) return false;
        AzRailModel model = new AzRailModel(mHost.catalogue());
        if (model.letterCount() == 0) return false;
        float density = mActivity.getResources().getDisplayMetrics().density;
        int[] hl = new int[2];
        int[] bl = new int[2];
        layer.getLocationOnScreen(hl);
        mOriginX = hl[0];
        mOriginY = hl[1];
        bar.getLocationOnScreen(bl);
        float width = layer.getWidth();
        float gap = GAP_DP * density;
        float barLeft = bl[0] - hl[0];
        float barRight = barLeft + bar.getWidth();
        float barTop = bl[1] - hl[1];
        // Clear of the status chrome, the camera's cutout and the keyboards that stand over the layer.
        androidx.core.graphics.Insets bars = androidx.core.graphics.Insets.NONE;
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(layer);
        if (insets != null) {
            bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
        }
        float top = Math.max(0f, bars.top - hl[1]) + gap;
        float keysTop = layer.getHeight();
        int[] at = new int[2];
        View root = layer.getRootView();
        for (int id : new int[] {R.id.inapp_keyboard_container, R.id.terminal_toolbar_host}) {
            View keys = root.findViewById(id);
            if (keys == null || !keys.isShown() || keys.getHeight() <= 0) continue;
            keys.getLocationOnScreen(at);
            keysTop = Math.min(keysTop, at[1] - hl[1]);
        }
        AzRailModel.Side side;
        float inset;
        float bottom;
        if (rail) {
            // The bar stands against one edge; the A–Z rail takes the thumb's side beside it.
            side = AzRailModel.sideOf((barLeft + barRight) / 2f, width);
            inset = side == AzRailModel.Side.LEFT ? barRight + gap : width - barLeft + gap;
            bottom = keysTop - gap;
        } else {
            side = AzRailModel.sideOf(downRawX - hl[0], width);
            float edge = side == AzRailModel.Side.LEFT ? Math.max(0f, bars.left - hl[0])
                : Math.max(0f, bars.right - (mActivity.getResources().getDisplayMetrics().widthPixels
                    - hl[0] - width));
            inset = edge;
            bottom = Math.min(barTop, keysTop) - gap;
        }
        model.layout(side, width, inset, RAIL_DP * density, top, bottom, FAN_DP * density, ITEM_DP * density,
            MIN_COLUMN_DP * density, ENTER_SLOP_DP * density);
        AzRailView view = new AzRailView(mActivity, model, mHost);
        if (!WindowLayers.add(mActivity, WindowLayer.SHEETS, view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))) {
            return false;
        }
        mView = view;
        mModel = model;
        return true;
    }

    /** The finger is at ({@code rawX}, {@code rawY}) on screen. */
    public void track(float rawX, float rawY) {
        AzRailView view = mView;
        AzRailModel model = mModel;
        if (view == null || model == null) return;
        int app = model.move(rawX - mOriginX, rawY - mOriginY);
        view.show(model.letter(), app);
    }

    /** The finger lifted: the app it let go on opens; the rail goes either way. */
    public void release(float rawX, float rawY) {
        AzRailView view = mView;
        AzRailModel model = mModel;
        if (view == null || model == null) return;
        LauncherAppEntry entry = model.release(rawX - mOriginX, rawY - mOriginY);
        close();
        if (entry != null) mHost.open(entry);
    }

    /** The finger was cancelled: nothing opens. */
    public void cancel() {
        close();
    }

    public void close() {
        AzRailView view = mView;
        mView = null;
        mModel = null;
        if (view != null) view.release();
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
}
