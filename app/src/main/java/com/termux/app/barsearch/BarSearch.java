package com.termux.app.barsearch;

import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.drawer.AppDrawerSearchModel;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.terminal.FocuslessKeyIntake;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.app.x11.X11Apps;

import java.util.Collections;
import java.util.List;

/**
 * The control bar's search (#435): Terminal, Linux apps, then Android apps, filtered as the user
 * types; typing arrives on the three routes of {@link FocuslessKeyIntake}. A tap opens a result as
 * {@link SearchOpen} says; a hold on one with a window open offers New window here. With nothing
 * typed the first row is All apps, which opens the drawer sheet.
 */
public final class BarSearch extends FocuslessKeyIntake {

    /** What the search reads and does through the activity. */
    public interface Host {
        /** The apps to search: the drawer's catalogue, Linux apps and the launcher's entries included. */
        @NonNull List<LauncherAppEntry> catalogue();

        /** Whether a window of {@code entry} is open on the desktop. */
        boolean windowOpen(@NonNull SearchOpen.Target target, @NonNull LauncherAppEntry entry);

        /** Does {@code action} for {@code entry} on the desktop. */
        void perform(@NonNull SearchOpen.Action action, @NonNull LauncherAppEntry entry);

        /** Opens the drawer sheet (All apps). */
        void openDrawer();

        /** The bar's view, which the panel stands above. */
        @Nullable View bar();

        /** Raises the in-app keyboard for typing; false when there is none. */
        boolean ensureTypingKeyboard();

        /** Hands the in-app keyboard's typing to {@code interceptor}, or back to its owner on null. */
        void claimTyping(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor);

        /** The app's icon for a row. */
        @Nullable Drawable icon(@NonNull LauncherAppEntry entry);

        /** The panel's glass cut for {@code radiusPx}, or null for a plain one. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();
    }

    @NonNull private final Host mHost;
    @NonNull private final AppDrawerSearchModel mQuery = new AppDrawerSearchModel();
    @Nullable private BarSearchView mView;
    @NonNull private List<SearchSections.Row> mRows = Collections.emptyList();
    private int mHighlight;
    private boolean mActive;

    public BarSearch(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public boolean isActive() {
        return mActive;
    }

    /** Opens the search, or puts it away when it is open (the Super key). */
    public void toggle() {
        if (mActive) close();
        else open();
    }

    public void open() {
        View bar = mHost.bar();
        if (mActive || bar == null) return;
        BarSearchView view = new BarSearchView(bar.getContext(), this, mHost);
        if (!view.show(bar)) return;
        mView = view;
        mActive = true;
        mQuery.clear();
        resetFieldEdits();
        mHost.ensureTypingKeyboard();
        mHost.claimTyping(this);
        refresh();
    }

    public void close() {
        if (!mActive) return;
        mActive = false;
        BarSearchView view = mView;
        mView = null;
        if (view != null) view.dismiss();
        mHost.claimTyping(null);
    }

    @NonNull String query() {
        return mQuery.query();
    }

    @NonNull List<SearchSections.Row> rows() {
        return mRows;
    }

    int highlight() {
        return mHighlight;
    }

    private void refresh() {
        mRows = SearchSections.arrange(mHost.catalogue(), mQuery.query());
        mHighlight = 0;
        if (mView != null) mView.showRows();
    }

    private static SearchOpen.Target targetOf(@NonNull LauncherAppEntry entry) {
        if (LauncherEntries.TERMINAL.equals(LauncherEntries.idOf(entry.appRef))) {
            return SearchOpen.Target.TERMINAL;
        }
        if (LauncherEntries.isEntry(entry.appRef)) return SearchOpen.Target.LAUNCHER_ENTRY;
        return X11Apps.isLinuxApp(entry.appRef) ? SearchOpen.Target.LINUX_APP : SearchOpen.Target.ANDROID_APP;
    }

    /** A row was tapped or Enter chose it. */
    void choose(@NonNull SearchSections.Row row) {
        if (row.entry == null) {
            close();
            mHost.openDrawer();
            return;
        }
        LauncherAppEntry entry = row.entry;
        SearchOpen.Target target = targetOf(entry);
        SearchOpen.Action action = SearchOpen.tap(target, mHost.windowOpen(target, entry));
        close();
        mHost.perform(action, entry);
    }

    /**
     * Opens {@code entry} as a tap on its result does (#437: the A–Z rail's release), search open
     * or not: an Android app over the launcher, a Linux app after the focused window.
     */
    public void openEntry(@NonNull LauncherAppEntry entry) {
        SearchOpen.Target target = targetOf(entry);
        mHost.perform(SearchOpen.tap(target, mHost.windowOpen(target, entry)), entry);
    }

    /** The action a hold on {@code row} offers, or NONE. */
    @NonNull SearchOpen.Action holdAction(@NonNull SearchSections.Row row) {
        if (row.entry == null) return SearchOpen.Action.NONE;
        SearchOpen.Target target = targetOf(row.entry);
        return SearchOpen.hold(target, mHost.windowOpen(target, row.entry));
    }

    void performHold(@NonNull SearchSections.Row row, @NonNull SearchOpen.Action action) {
        if (row.entry == null) return;
        close();
        mHost.perform(action, row.entry);
    }

    // ---- typing ----------------------------------------------------------------------------------

    @Override
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        if (!mActive) return false;
        if (!ctrlDown && mQuery.insertCodePoint(codePoint)) refresh();
        return true;
    }

    @Override
    protected void onText(@NonNull String text, boolean ctrl, boolean alt) {
        if (ctrl || alt) return;
        if (mQuery.insert(text)) refresh();
    }

    @Override
    protected void onBackspace() {
        if (mQuery.backspace()) refresh();
    }

    @Override
    protected void onCommit() {
        if (mRows.isEmpty()) return;
        choose(mRows.get(Math.max(0, Math.min(mHighlight, mRows.size() - 1))));
    }

    @Override
    protected void onCursor(int delta) {
        // Left and right move through the results, as the arrows do.
        move(delta);
    }

    private void move(int by) {
        if (mRows.isEmpty()) return;
        mHighlight = Math.max(0, Math.min(mRows.size() - 1, mHighlight + by));
        if (mView != null) mView.showHighlight();
    }

    @Override
    protected boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                close();
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                onCommit();
                return true;
            case KeyEvent.KEYCODE_DEL:
                onBackspace();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1);
                return true;
            default:
                return false;
        }
    }
}
