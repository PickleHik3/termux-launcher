package com.termux.app.dock;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.Element;

/**
 * The production adapter of {@link DockRows}: the dock's accessory views in
 * {@code activity_termux.xml}, looked up through the activity each write as the activity always
 * did, so a view swapped in by a re-inflation is the one written.
 */
public final class DockRowViews implements DockRows.Views {

    /** Looks a view up by id in the activity's tree. */
    public interface Finder {
        @Nullable View find(int viewId);
    }

    /** Whether the pinned apps' ticks are bound to {@code band}, the dock's own page band. */
    public interface Ticks {
        boolean areBoundTo(@NonNull View band);
    }

    @NonNull private final Finder mFinder;
    @NonNull private final Ticks mTicks;

    public DockRowViews(@NonNull Finder finder, @NonNull Ticks ticks) {
        mFinder = finder;
        mTicks = ticks;
    }

    @Override public void setStackShown(boolean shown) {
        show(R.id.accessory_stack_container, shown);
    }

    @Override public void setSurfaceShown(boolean shown) {
        show(R.id.accessory_surface_host, shown);
    }

    @Override public void setDockGlassShown(boolean shown) {
        show(R.id.extrakeys_background, shown);
    }

    @Override public void setShelfShown(boolean shown) {
        View shelf = mFinder.find(R.id.accessory_shelf_fx);
        // Invisible rather than gone, as the layout rests it: the shelf follows the glass host.
        int visibility = shown ? View.VISIBLE : View.INVISIBLE;
        if (shelf != null && shelf.getVisibility() != visibility) shelf.setVisibility(visibility);
    }

    @Override public boolean setStatusBarShown(boolean shown) {
        View bar = mFinder.find(R.id.terminal_window_bar_host);
        if (bar == null) return false;
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (bar.getVisibility() == visibility) return false;
        bar.setVisibility(visibility);
        return true;
    }

    @Override public void setRowShown(@NonNull Element row, boolean shown) {
        int id = rowId(row);
        if (id != 0) show(id, shown);
    }

    @Override public void setPageBand(@NonNull DockRows.PageBand band) {
        View view = mFinder.find(R.id.apps_bar_indicator_band);
        if (view == null) return;
        switch (band) {
            case GONE:
                view.setVisibility(View.GONE);
                break;
            case HELD:
                view.setVisibility(View.INVISIBLE);
                break;
            case TICKS:
            default:
                // The row's ticks show or hide it themselves (SuggestionBarView#publishPageIndicator).
                break;
        }
    }

    @Override public void setAzLayersShown(boolean shown) {
        show(R.id.apps_bar_az_fx_underlay, shown);
        show(R.id.apps_bar_az_fx_overlay, shown);
        show(R.id.apps_bar_az_label_overlay, shown);
    }

    @Override public boolean setUnderKeyboardShown(boolean shown) {
        View under = mFinder.find(R.id.accessory_under_keyboard_stack);
        if (under == null) return false;
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (under.getVisibility() == visibility) return false;
        under.setVisibility(visibility);
        return true;
    }

    @Override public boolean setRowHeight(@NonNull Element row, int px) {
        int id = rowId(row);
        return id != 0 && height(id, px);
    }

    @Override public boolean setPageBandHeight(int px) {
        return height(R.id.apps_bar_indicator_band, px);
    }

    @Override public boolean pageTicksAreTheDocks() {
        View band = mFinder.find(R.id.apps_bar_indicator_band);
        return band != null && mTicks.areBoundTo(band);
    }

    private static int rowId(@NonNull Element row) {
        switch (row) {
            case APPS: return R.id.apps_bar_viewpager;
            case AZ: return R.id.apps_bar_az_row;
            case EXTRA_KEYS: return R.id.terminal_toolbar_view_pager;
            default: return 0;
        }
    }

    private void show(int viewId, boolean shown) {
        View view = mFinder.find(viewId);
        if (view != null) view.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    private boolean height(int viewId, int px) {
        View view = mFinder.find(viewId);
        if (view == null) return false;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == px) return false;
        params.height = px;
        view.setLayoutParams(params);
        return true;
    }
}
