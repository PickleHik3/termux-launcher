package com.termux.app.terminal;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link TerminalCommandPaletteController.Host} with no activity behind it: a context, the views
 * a test hands it, no keyboard, no sessions and no sheets. What the controller asked of it is
 * counted in fields.
 */
final class FakeCommandPaletteHost implements TerminalCommandPaletteController.Host {

    @NonNull private final Context context;
    /** The tree {@link #findView} looks ids up in; null keeps the view-less host. */
    @Nullable View viewRoot;
    @NonNull private final View decor;

    /** Every interceptor switch, in order. */
    final List<Boolean> interceptorCalls = new ArrayList<>();
    int cornersDp = -1;
    boolean floatingStyle;
    int cornersReads;
    int floatingStyleReads;
    int drawerCloses;
    int sheetDismissals;
    int contactsRequests;
    final List<Intent> startedActivities = new ArrayList<>();

    FakeCommandPaletteHost(@NonNull Context context) {
        this.context = context;
        this.decor = new View(context);
    }

    @NonNull @Override public Context context() {
        return context;
    }

    @SuppressWarnings("unchecked")
    @Nullable @Override public <T extends View> T findView(int viewId) {
        return viewRoot == null ? null : (T) viewRoot.findViewById(viewId);
    }

    @NonNull @Override public View decorView() {
        return viewRoot != null ? viewRoot.getRootView() : decor;
    }

    @Nullable @Override public TermuxInAppKeyboard inAppKeyboard() {
        return null;
    }

    @Override public boolean inAppKeyboardSpaceBarRect(@NonNull Rect out) {
        return false;
    }

    @Override public boolean isInAppKeyboardEnabled() {
        return false;
    }

    @NonNull @Override public List<String> inAppKeyboardLayoutRing() {
        return Collections.emptyList();
    }

    @NonNull @Override public String activeInAppKeyboardLayoutId() {
        return com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts.LAYOUT_MAIN;
    }

    @Override public void setCommandPaletteInterceptorActive(boolean active) {
        interceptorCalls.add(active);
    }

    @Override public void closeAppDrawerImmediate() {
        drawerCloses++;
    }

    @Override public void dismissTerminalSheets() {
        sheetDismissals++;
    }

    @NonNull @Override public TerminalSheetController terminalSheet() {
        throw new AssertionError("no sheet plane behind the fake host");
    }

    @NonNull @Override public List<SessionBrowserModel.Session> sessionBrowserSessions() {
        return Collections.emptyList();
    }

    @Nullable @Override public TerminalSession currentSession() {
        return null;
    }

    @Override public boolean applyCommandPaletteWallpaperFrost(@NonNull ImageView frost) {
        return false;
    }

    @Override public int commandPaletteCornersDp() {
        cornersReads++;
        return cornersDp;
    }

    @Override public boolean isFloatingStyle() {
        floatingStyleReads++;
        return floatingStyle;
    }

    @Override public boolean isRowHapticsEnabled() {
        return false;
    }

    @Override public void startActivity(@NonNull Intent intent) {
        startedActivities.add(intent);
    }

    @Override public void requestContactsPermission() {
        contactsRequests++;
    }
}
