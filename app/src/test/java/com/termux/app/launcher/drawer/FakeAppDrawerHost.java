package com.termux.app.launcher.drawer;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
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
    /** The inflated tree {@link #findView} looks ids up in; null keeps the view-less host. */
    @Nullable View viewRoot;
    /** Handed out by {@link #dockLayout()}; null keeps the "no plane, no dock" assertion. */
    @Nullable DockLayout dockLayout;
    @Nullable SuggestionBarView suggestionBar;
    @Nullable Boolean interceptorActive;
    int flushes;

    FakeAppDrawerHost(@NonNull Context context, @Nullable TermuxAppSharedPreferences preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    @NonNull @Override public Context context() {
        return context;
    }

    @SuppressWarnings("unchecked")
    @Nullable @Override public <T extends View> T findView(int viewId) {
        return viewRoot == null ? null : (T) viewRoot.findViewById(viewId);
    }

    @Nullable @Override public TermuxAppSharedPreferences preferences() {
        return preferences;
    }

    @NonNull @Override public DockLayout dockLayout() {
        if (dockLayout != null) return dockLayout;
        throw new AssertionError("no plane, no dock geometry to capture");
    }

    @Nullable @Override public SuggestionBarView suggestionBar() {
        return suggestionBar;
    }

    @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
        return false;
    }

    /** The last opacity the drawer asked of the under-pill strip; 1 until it asks for anything. */
    float decorNavStripAlpha = 1f;

    @Override public void setDecorNavStripAlpha(float alpha) {
        decorNavStripAlpha = alpha;
    }

    @Override public void flushPendingAccessoryGeometry() {
        flushes++;
    }

    @Override public void setInterceptorActive(boolean active) {
        interceptorActive = active;
    }

    public int hideSystemKeyboardCalls;
    public int restoreSystemKeyboardCalls;

    @Override public void restoreSystemKeyboard() {
        restoreSystemKeyboardCalls++;
    }

    @Override public void hideSystemKeyboard() {
        hideSystemKeyboardCalls++;
    }

    public int searchKeyboardRequests;

    @Override public void requestSearchKeyboard() {
        searchKeyboardRequests++;
    }

    public int textFieldSearchBegins;
    public int textFieldSearchEnds;
    @Nullable public EditText textFieldSearchField;
    public int appDrawerSettingsOpens;

    @Override public void beginTextFieldSearch(@NonNull EditText field) {
        textFieldSearchBegins++;
        textFieldSearchField = field;
    }

    @Override public void endTextFieldSearch(@NonNull EditText field) {
        textFieldSearchEnds++;
    }

    @Override public void openAppDrawerSettings() {
        appDrawerSettingsOpens++;
    }
}
