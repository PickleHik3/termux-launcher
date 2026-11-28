package com.termux.app.azrail;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        /** The app's icon for a row; null for none. */
        @Nullable Drawable icon(@NonNull LauncherAppEntry entry);

        float cornerRadiusPx();

        int ink();

        int accent();
    }

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
        AzRailModel.Side side;
        float inset = 0f;
        float bottom;
        if (rail) {
            // The bar stands against one edge; the A–Z rail takes the thumb's side beside it.
            side = AzRailModel.sideOf((barLeft + barRight) / 2f, width);
            inset = side == AzRailModel.Side.LEFT ? barRight + gap : width - barLeft + gap;
            bottom = layer.getHeight() - gap;
        } else {
            side = AzRailModel.sideOf(downRawX - hl[0], width);
            bottom = barTop - gap;
        }
        model.layout(side, width, inset, RAIL_DP * density, gap, bottom, FAN_DP * density, ITEM_DP * density);
        AzRailView view = new AzRailView(mActivity, model, new AzRailView.Style() {
            @Nullable @Override public Drawable icon(@NonNull LauncherAppEntry entry) { return mHost.icon(entry); }
            @Override public float cornerRadiusPx() { return mHost.cornerRadiusPx(); }
            @Override public int ink() { return mHost.ink(); }
            @Override public int accent() { return mHost.accent(); }
        });
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
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
}
