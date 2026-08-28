package com.termux.app.launcher.drawer;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
import com.termux.app.dock.DockLayout;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * An {@link AppDrawerController.Host} with no activity behind it: a context, the preferences a
 * test hands it, no views and no dock. What the controller asked of it is counted in fields.
 */
final class FakeAppDrawerHost implements AppDrawerController.Host {

    @NonNull private final Context context;
    @Nullable final TermuxAppSharedPreferences preferences;
    @Nullable Boolean interceptorActive;
    int flushes;

    FakeAppDrawerHost(@NonNull Context context, @Nullable TermuxAppSharedPreferences preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    @NonNull @Override public Context context() {
        return context;
    }

    @Nullable @Override public <T extends View> T findView(int viewId) {
        return null;
    }

    @Nullable @Override public TermuxAppSharedPreferences preferences() {
        return preferences;
    }

    @NonNull @Override public DockLayout dockLayout() {
        throw new AssertionError("no plane, no dock geometry to capture");
    }

    @Nullable @Override public SuggestionBarView suggestionBar() {
        return null;
    }

    @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
        return false;
    }

    @Override public void flushPendingAccessoryGeometry() {
        flushes++;
    }

    @Override public void setInterceptorActive(boolean active) {
        interceptorActive = active;
    }

    @Override public void requestSearchKeyboard() { }
}
