package com.termux.app.terminal;

import android.content.Context;
import android.graphics.PointF;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxService;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * The activity surface the terminal clients — {@link TermuxTerminalViewClient},
 * {@link TermuxTerminalSessionActivityClient} and {@link TerminalActionDispatcher} — are allowed to
 * reach for.
 *
 * <p>Everything a client needs of its host is named here, so the clients depend on a written-down
 * surface rather than on whatever the activity happens to expose. Pure {@code Context} needs —
 * strings, resources, system services — are not part of it: a client holds a {@code Context} for
 * those, and the one client that cannot (the dispatcher is a process-wide singleton) borrows the
 * host's through {@link #context()}.
 */
public interface TerminalHost {

    // --- Views and session ---

    /** The terminal view the client's callbacks act on, i.e. the focused pane; null before one exists. */
    @Nullable TerminalView focusedView();

    /** The shell behind {@link #focusedView()}, or null when no pane holds one. */
    @Nullable TerminalSession currentSession();

    /** The extra keys row of the visible toolbar page, or null while there is none. */
    @Nullable ExtraKeysView extraKeysView();

    /** The toolbar's text input, or null while the toolbar is not inflated. */
    @Nullable EditText toolbarTextInput();

    /** Whether a terminal toolbar pager exists at all; false in layouts that have none. */
    boolean hasTerminalToolbar();

    /** Whether the toolbar pager is showing the terminal page rather than a keys page. */
    boolean isTerminalViewSelected();

    /** Mirrors the terminal view key logging toggle onto the activity root view. */
    void setRootViewLoggingEnabled(boolean enabled);

    /** Locks the drawer closed, e.g. while a selection is being copied. */
    void setDrawerLocked(boolean locked);

    /** Shows or hides the terminal toolbar, as the fn+q/fn+k writing mode keys do. */
    void toggleTerminalToolbar();

    /** Asks the flush dock to re-measure after anything that changes terminal geometry. */
    void requestFlushDockGeometryUpdate();

    // --- Settings ---

    TermuxAppSharedPreferences preferences();

    TermuxSharedProperties properties();

    // --- Activity state ---

    boolean isActivityRecreated();

    boolean isOnResumeAfterOnCreate();

    /** Whether split panes are on, which decides who owns a rename prompt. */
    boolean isSplitPanesEnabled();

    void finishActivityIfNotFinishing();

    void runOnUiThread(@NonNull Runnable runnable);

    // --- Font size ---

    /** The focused pane's pinned size, or 0 while it follows the app-wide default. */
    int activePaneFontSize();

    /** Pins a size on the focused pane; false when there is no pane controller to pin it on. */
    boolean setActivePaneFontSize(int size);

    // --- Soft keyboard ---

    /** The client is about to ask for the system IME, so inset handling may allow it. */
    void onSystemImeRequested();

    boolean shouldDelaySoftKeyboardShowOnResume();

    boolean areSoftKeyboardFlagsDisabled();

    void disableSoftKeyboard(@Nullable View view);

    void clearDisableSoftKeyboardFlags();

    void setSoftKeyboardAlwaysHiddenFlags();

    void setSoftInputModeAdjustResize();

    void setSoftKeyboardVisibility(@NonNull Runnable showSoftKeyboardRunnable, @Nullable View view,
                                   boolean visible);

    // --- Keybind hints ---

    boolean isKeybindHintPopupVisible();

    /** A stroke ended the pending sequence, so the hint legend can retire. */
    void onKeybindHintConsumed();

    void toggleKeybindHintFullPopup();

    /** What the hint slab documents right now: a held or latched prefix, or null for nothing. */
    void setHardwareKeybindHintPrefix(@Nullable String prefix, boolean shift);

    /** The pending-chord indicator. */
    @NonNull KeyChordUi keyChordUi();

    /** The click a cancelled chord plays. */
    void playKeyChordCancelledSound();

    /** The small indicator shown while a multi-stroke binding is pending or has just run. */
    interface KeyChordUi {

        void show(@NonNull String normalizedSequence);

        void showMode(@NonNull String mode);

        void showAction(@NonNull String stroke, @NonNull String name);

        void showFailure(@NonNull String stroke, @NonNull String message);

        void hide();
    }

    // --- Notices and surfaces ---

    void showToast(String text, boolean longDuration);

    /** Opens the terminal action sheet at a long press, if one can be shown. */
    boolean showTerminalActionSheet(@Nullable PointF anchor);

    /** The sheet plane the client puts its own cards on. */
    @NonNull TerminalSheetController sheetController();

    /** Raises the scrollback find strip. */
    boolean beginScrollbackFind();

    void showHintsOverlay(@NonNull String transcript);

    void showScrollbackSearchOverlay(@NonNull TerminalView view);

    /** Renames the current session, which only the activity owns while split panes are on. */
    boolean promptCurrentSessionRename();

    // --- Modal surfaces ---

    /**
     * Gives every modal surface — rename chip, find strip, widget and status panes, command
     * palette, sheet plane, app drawer, app search — first refusal on a key press, in the order
     * their claims outrank each other. True when one consumed the stroke and the terminal must
     * not see it.
     */
    boolean overlaysConsumeKeyDown(int keyCode, @NonNull KeyEvent event);

    /**
     * The release half of {@link #overlaysConsumeKeyDown}: a stroke a surface claimed on the way
     * down must not reach the shell behind it on the way up.
     */
    boolean overlaysConsumeKeyUp(int keyCode);

    /**
     * The text half of {@link #overlaysConsumeKeyDown}, for the surfaces a system IME reaches
     * only through committed text rather than key events.
     */
    boolean overlaysConsumeCodePoint(int codePoint, boolean ctrlDown);

    // --- Suggestion bar ---

    boolean shouldProcessSuggestionBarKeyEvent(int keyCode);

    boolean shouldProcessSuggestionBarCodePoint(int codePoint, boolean ctrlDown);

    // --- Host identity ---

    /**
     * The host as a plain {@code Context}, for strings, resources, system services and plain
     * {@code startActivity} calls. Only for a client that cannot hold a {@code Context} field of
     * its own: {@link TerminalActionDispatcher} is a singleton that attaches and detaches, so a
     * field there would outlive the activity it came from.
     */
    @NonNull Context context();

    /** Whether the host is still able to act, i.e. neither finishing nor destroyed. */
    boolean isHostAlive();

    /** Whether the host is in the foreground, i.e. between onStart and onStop. */
    boolean isVisible();

    /** Says which action just ran, once it has run. */
    void showTerminalActionHint(@NonNull String toolName);

    // --- Panes and views ---

    /** The view showing {@code session}, which may be a pane other than the focused one. */
    @Nullable TerminalView viewForSession(@Nullable TerminalSession session);

    /** Every pane view currently on screen, focused or not. */
    @NonNull List<TerminalView> paneViews();

    /** The split-pane model, or null before one exists. */
    @Nullable TerminalPaneController paneController();

    void splitCurrentPane(int orientation);

    boolean focusPaneDirection(int keyCode);

    boolean resizeActivePane(int keyCode);

    boolean killFocusedPane();

    boolean applyPaneLayout(@NonNull String layout);

    boolean cyclePaneLayout();

    /** The retained automatic layout, or null when the window is manually managed. */
    @Nullable String activePaneLayoutPolicy();

    boolean equalizePaneLayout();

    boolean rotatePaneLayout(boolean clockwise);

    boolean moveFocusedPaneToEdge(@NonNull String edge);

    // --- Shells ---

    /** The service holding every live shell, or null while it is not bound. */
    @Nullable TermuxService service();

    /** Marks output activity on a shell, which is tmux's monitor-activity. */
    void noteShellActivity(@Nullable TerminalSession session);

    /** Marks a shell as wanting attention, e.g. after a bell. */
    void noteShellAttention(@NonNull TerminalSession session);

    void clearShellAttention(int shellPid);

    /** The corner chip that reports a session switch, an exit, or a refused split. */
    void showSessionSwitchIndicator(@Nullable String text);

    /** Re-reads the standing rows for shells running in the background. */
    void syncBackgroundProcessStack();

    // --- Sessions, windows and the drawer ---

    /** The drawer-visible session list. */
    @NonNull Sessions sessions();

    /**
     * The drawer-visible session list, i.e. the tab strip: one row per session, with the secondary
     * shells of split panes filtered out.
     */
    interface Sessions {

        /** How many rows the drawer shows. */
        int count();

        /** The shell behind the row at {@code index}, or null when there is none. */
        @Nullable TerminalSession at(int index);

        /** Where {@code session} sits in the list, or -1 when it is not a row of its own. */
        int indexOf(@Nullable TerminalSession session);

        /** The primary shell of the visible tab, which is what the drawer keys off. */
        @Nullable TerminalSession currentTabPrimary();

        /** The launcher's tmux-style number of the focused session, or 0 when there is none. */
        int currentNumber();

        /** The launcher's tmux-style number of {@code shell}'s session, or 0 when untracked. */
        int numberOf(@Nullable TerminalSession shell);

        /** The name of {@code shell}'s session, or null when it is unnamed. */
        @Nullable String nameOf(@Nullable TerminalSession shell);

        /** The drawer's session list view, or null while the drawer is not inflated. */
        @Nullable ListView listView();
    }

    /** Rebuilds the drawer rows from the live session/window/pane topology. */
    void rebuildDrawerSessions();

    /** Tells the drawer adapter its rows changed. */
    void notifySessionListUpdated();

    /** Shows the session's tab and focuses the pane displaying it. */
    boolean activateSessionInPanes(TerminalSession session);

    /** Snapshots the outgoing terminal so an arrival has something to slide away. */
    void captureTerminalDeparture();

    /** The horizontal session-travel arrival, in the direction the list was walked. */
    void animateSessionArrival(int direction);

    /** The vertical arrival a created or closed session gets. */
    void animateSessionLifecycleArrival(int direction);

    /** The window's last pane closed, so drop the window from its session. */
    void onWindowEmptied(TerminalPaneController.Window window);

    void closeCurrentSession();

    boolean cloneCurrentBrowserSession();

    boolean resetCurrentSession();

    void showSessionBrowser();

    void toggleSessionsPanel();

    boolean isSessionsPanelShowing();

    boolean renameCurrentSessionTo(@Nullable String name);

    @Nullable String currentSessionName();

    /** Renames by browser index, which skips no session and so differs from the drawer index. */
    boolean renameBrowserSession(int index, @Nullable String name);

    @Nullable String browserSessionName(int index);

    void createNewWindow();

    void closeCurrentWindow();

    void switchWindow(boolean forward);

    boolean selectWindow(int index);

    int currentWindowCount();

    int currentWindowIndex();

    boolean promptCurrentWindowRename();

    boolean renameCurrentWindowTo(@Nullable String name);

    @Nullable String currentWindowName();

    /** Opens the rename editor for {@code target}. */
    boolean beginTerminalRename(@NonNull TerminalRenameTarget target);

    void openDrawer();

    void closeDrawers();

    // --- Workspaces ---

    @NonNull TerminalWorkspace saveWorkspace(@NonNull String requestedName, boolean overwrite,
                                             boolean captureCommands)
        throws TerminalWorkspace.WorkspaceException;

    @NonNull TerminalWorkspace.LoadResult loadWorkspace(@NonNull String name, boolean replace,
                                                        boolean runCommands)
        throws TerminalWorkspace.WorkspaceException;

    @NonNull List<TerminalWorkspaceStore.Entry> listWorkspaces()
        throws TerminalWorkspace.WorkspaceException;

    void deleteWorkspace(@NonNull String name) throws TerminalWorkspace.WorkspaceException;

    void showWorkspacePicker();

    void promptSaveWorkspace();

    // --- Appearance ---

    void openWallpaperPicker();

    boolean toggleWallpaperMode();

    boolean isWallpaperModeEnabled();

    boolean toggleCursorTrail();

    boolean isCursorTrailEnabled();

    void openSurfaceEditor();

    /** Repaints the window ground for whichever session is now current. */
    void updateWindowBackgroundForCurrentSession();

    // --- App surfaces ---

    void openSettings();

    void openLookAndFeel();

    void openAppsBar();

    void showCommandPalette();

    void showExtraKeysRowEditor();

    /** Toggles the key inspector, answering whether it is now open. */
    boolean toggleKeyInspector();

    /** The legacy text-input dialog, which needs a themed activity window to be shown in. */
    void showTextInputDialog(int titleRes, @Nullable String initialText, int confirmRes,
                             @NonNull TextInputDialogUtils.TextSetListener onConfirm);

    // --- Performance ---

    void resetTerminalPerformanceMetrics();

    @NonNull TerminalFrameMetricsMonitor.Snapshot frameMetricsSnapshot();

    // --- Sibling clients ---

    @Nullable TermuxTerminalSessionActivityClient sessionClient();

    @Nullable TermuxTerminalViewClient viewClient();
}
