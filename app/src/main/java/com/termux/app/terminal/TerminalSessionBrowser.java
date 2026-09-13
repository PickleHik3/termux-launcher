package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.notice.AppNotice;

import java.util.ArrayList;
import java.util.List;

/**
 * The sessions drawer's front door: every way into it, and everything it asks the activity for.
 *
 * <p>One surface now answers what used to be two. The browser was a panel on the terminal's foot
 * that opened a second panel for a rename, a third for a close, a fourth for the workspace picker
 * and a fifth to confirm a delete; the status chip dropped a shorter copy of the same list into a
 * pop-up window. Both bound {@link TermuxActivity#getSessionBrowserSessions()} and called the same
 * methods, so the only thing the second one really added was a second set of answers to the same
 * questions.
 *
 * <p>{@link SessionsDrawerView} draws it and holds the state; this class owns the one that is open,
 * so that every trigger — the chip, the DRAWER key, {@code session.browser}, {@code session.panel},
 * the open-drawer bindings — toggles the same drawer rather than stacking another copy of it.
 */
public final class TerminalSessionBrowser {

    /** The drawer currently on the plane, or null. There is only ever one, on one activity. */
    @Nullable private static TerminalSessionBrowser sOpen;

    @NonNull private final TermuxActivity mActivity;
    @NonNull private final SessionsDrawerView mView;

    private TerminalSessionBrowser(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mView = new SessionsDrawerView(activity);
        mView.setListener(new DrawerListener());
    }

    /** True while the drawer is the card on the plane. */
    public static boolean isOpen(@NonNull TermuxActivity activity) {
        return sOpen != null && activity.getTerminalSheetController().isLeadingDrawerOpen();
    }

    /** The plain trigger: pull the drawer out, or put it away when it is already out. */
    public static void toggle(@NonNull TermuxActivity activity) {
        if (isOpen(activity)) {
            activity.getTerminalSheetController().dismiss();
            return;
        }
        open(activity, SessionsDrawerView.Tab.LIVE, false);
    }

    /** Legacy name kept for the {@code session.browser} seam; the drawer is what it opens. */
    public static void show(@NonNull TermuxActivity activity) {
        toggle(activity);
    }

    /** The workspace picker's entry: the drawer, already showing what was saved. */
    public static void showWorkspacePicker(@NonNull TermuxActivity activity) {
        if (isOpen(activity)) {
            sOpen.mView.setTab(SessionsDrawerView.Tab.SAVED);
            sOpen.reload();
            return;
        }
        open(activity, SessionsDrawerView.Tab.SAVED, false);
    }

    /** The save prompt's entry: the drawer, with its name field already taking keys. */
    public static void promptSaveWorkspace(@NonNull TermuxActivity activity) {
        if (isOpen(activity)) {
            sOpen.mView.openSaveField();
            return;
        }
        open(activity, SessionsDrawerView.Tab.LIVE, true);
    }

    private static void open(@NonNull TermuxActivity activity, @NonNull SessionsDrawerView.Tab tab,
                             boolean saving) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        TerminalSessionBrowser drawer = new TerminalSessionBrowser(activity);
        drawer.mView.setDress(sheet.dress());
        drawer.mView.setTab(tab);
        drawer.reload();
        // The title lives in the drawer's own header, next to the segments and the +, so the card
        // adds none of its own.
        if (!sheet.show("", drawer.mView, true, drawer.mView,
            () -> {
                if (sOpen == drawer) sOpen = null;
                activity.setSessionBrowserRefreshCallback(null);
            },
            false, TerminalSheetController.Placement.terminalLeading())) return;
        sOpen = drawer;
        // Subscribed only once the drawer is actually up: a callback registered against a surface
        // that never opened would keep reloading a list nobody can see.
        activity.setSessionBrowserRefreshCallback(drawer::reload);
        activity.requestSessionBrowserForegroundRefresh();
        if (saving) drawer.mView.openSaveField();
    }

    /** Back, aimed at the drawer: an unfolded row closes before the drawer itself does. */
    public static boolean onBackPressed(@NonNull TermuxActivity activity) {
        return isOpen(activity) && sOpen.mView.collapseOne();
    }

    private void reload() {
        mView.bindLive(mActivity.getSessionBrowserSessions());
        if (mView.tab() == SessionsDrawerView.Tab.SAVED) mView.bindSaved(readSaved());
    }

    /**
     * The saved workspaces, with what each one holds.
     *
     * <p>The listing carries only names and timestamps, so the pane count and whether the file
     * captured any commands are read from the files themselves — small JSON documents in the user's
     * own directory, and the same read the old load prompt already made to decide whether to offer
     * to run them. A file that will not parse is still listed: deleting it is one of the things the
     * drawer is for.
     */
    @NonNull
    private List<SessionsDrawerView.SavedWorkspace> readSaved() {
        List<SessionsDrawerView.SavedWorkspace> out = new ArrayList<>();
        List<TerminalWorkspaceStore.Entry> entries;
        try {
            entries = mActivity.listWorkspaces();
        } catch (TerminalWorkspace.WorkspaceException e) {
            AppNotice.show(mActivity, mActivity.getString(R.string.workspace_picker_failed,
                e.getMessage()), false);
            return out;
        }
        TerminalWorkspaceStore store = new TerminalWorkspaceStore();
        for (TerminalWorkspaceStore.Entry entry : entries) {
            int panes = 0;
            boolean commands = false;
            try {
                TerminalWorkspace workspace = store.load(entry.name);
                panes = workspace.paneCount();
                commands = workspace.commandCount() > 0;
            } catch (TerminalWorkspace.WorkspaceException ignored) {
                // Unreadable, but still the user's file and still deletable from the row.
            }
            out.add(new SessionsDrawerView.SavedWorkspace(entry.name, panes,
                entry.modifiedAtEpochMs, commands));
        }
        return out;
    }

    private void dismiss() {
        mActivity.getTerminalSheetController().dismiss();
    }

    private void showActionFailed() {
        AppNotice.show(mActivity, R.string.session_browser_action_failed, false);
    }

    /** Everything the drawer can ask for, answered by the activity that owns the shells. */
    private final class DrawerListener implements SessionsDrawerView.Listener {

        @Override public void onNewSession() {
            if (mActivity.createBrowserSession()) reload();
            else showActionFailed();
        }

        @Override public void onNewSessionPrompt() {
            // A named session is asked for in a dialog of its own, which cannot share the screen
            // with a modal plane.
            dismiss();
            mActivity.promptNewSession();
        }

        @Override public void onActivateSession(long sessionId) {
            int index = mActivity.browserSessionIndex(sessionId);
            // Switching leaves the drawer describing a layout the user has just left.
            if (mActivity.activateBrowserSession(index)) dismiss();
            else showActionFailed();
        }

        @Override public void onActivateWindow(long sessionId, long windowId) {
            if (mActivity.activateBrowserWindow(sessionId, windowId)) dismiss();
            else showActionFailed();
        }

        @Override public void onCloneSession(long sessionId) {
            if (mActivity.cloneBrowserSession(mActivity.browserSessionIndex(sessionId))) reload();
            else showActionFailed();
        }

        @Override public void onRenameSession(long sessionId, @NonNull String name) {
            if (mActivity.renameBrowserSession(mActivity.browserSessionIndex(sessionId), name))
                reload();
            else showActionFailed();
        }

        @Override public void onCloseSession(long sessionId) {
            if (mActivity.closeBrowserSession(mActivity.browserSessionIndex(sessionId))) reload();
            else showActionFailed();
        }

        @Override public void onSaveWorkspace(@NonNull String name, boolean captureCommands) {
            saveWorkspace(name, false, captureCommands);
        }

        @Override public void onLoadWorkspace(@NonNull String name, boolean replace,
                                              boolean runCommands) {
            dismiss();
            try {
                mActivity.loadWorkspace(name, replace, runCommands);
                AppNotice.show(mActivity,
                    mActivity.getString(R.string.workspace_picker_loaded, name), false);
            } catch (TerminalWorkspace.WorkspaceException e) {
                AppNotice.show(mActivity, mActivity.getString(R.string.workspace_picker_failed,
                    e.getMessage()), false);
            }
        }

        @Override public void onDeleteWorkspace(@NonNull String name) {
            try {
                mActivity.deleteWorkspace(name);
                AppNotice.show(mActivity,
                    mActivity.getString(R.string.workspace_deleted, name), false);
            } catch (TerminalWorkspace.WorkspaceException e) {
                AppNotice.show(mActivity, mActivity.getString(R.string.workspace_picker_failed,
                    e.getMessage()), false);
            }
            reload();
        }

        @Override public void onTypingStarted() {
            mActivity.getTerminalSheetController().requestTypingKeyboard();
        }
    }

    /**
     * Saves, and asks again when the name is taken.
     *
     * <p>The overwrite question is the one thing here that cannot be a row: the drawer is gone by
     * then, and the answer is about a file rather than about anything the list is showing.
     */
    private void saveWorkspace(@Nullable String name, boolean overwrite, boolean captureCommands) {
        try {
            TerminalWorkspace workspace = mActivity.saveWorkspace(name == null ? "" : name,
                overwrite, captureCommands);
            AppNotice.show(mActivity, mActivity.getString(
                R.string.session_browser_workspace_saved, workspace.name), false);
        } catch (TerminalWorkspace.WorkspaceException e) {
            if (overwrite || !"conflict".equals(e.code)) {
                AppNotice.show(mActivity, e.getMessage(), true);
                return;
            }
            String requested = name == null ? "" : name.trim();
            TerminalSheetController sheet = mActivity.getTerminalSheetController();
            android.widget.LinearLayout body = TerminalSheetViews.body(mActivity);
            TerminalSheetViews.addMessage(body, mActivity.getString(
                R.string.session_browser_workspace_overwrite_message));
            android.widget.LinearLayout actions = TerminalSheetViews.addActionRow(body);
            TerminalSheetViews.addAction(actions,
                mActivity.getString(android.R.string.cancel), sheet::dismiss);
            TerminalSheetViews.addAction(actions,
                mActivity.getString(R.string.session_browser_overwrite), () -> {
                    sheet.dismiss();
                    saveWorkspace(requested, true, captureCommands);
                });
            sheet.show(mActivity.getString(
                R.string.session_browser_workspace_overwrite_title, requested), body);
        }
    }
}
