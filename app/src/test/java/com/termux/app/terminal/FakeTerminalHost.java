package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link TerminalHost} with no activity behind it: every call is recorded in {@link #calls} and
 * answered from a field a test can set.
 *
 * <p>Shared by the tests of all three terminal clients, so the surface only has to be stubbed once.
 * Nothing here reaches for a real view, service or pane controller — a tool that needs one is
 * exercised on its refusal path instead.
 */
class FakeTerminalHost implements TerminalHost {

    /** Every host method the client under test called, in order, by name. */
    final List<String> calls = new ArrayList<>();

    final Context context;
    final TermuxSharedProperties properties;
    final TermuxAppSharedPreferences preferences;
    final FakeSessions sessions = new FakeSessions();

    // Activity state
    boolean alive = true;
    boolean visible = true;
    boolean splitPanesEnabled = true;
    boolean activityRecreated;
    boolean onResumeAfterOnCreate;

    // Views and session
    @Nullable TerminalSession currentSession;
    @Nullable TerminalView focusedView;
    final List<TerminalView> paneViews = new ArrayList<>();
    boolean hasToolbar = true;
    boolean terminalViewSelected;
    boolean drawerLocked;
    int toolbarToggles;
    int flushDockRequests;
    int paneFontSize;
    boolean overlaysConsume;

    // Panes
    @Nullable Integer lastSplitOrientation;
    @Nullable Integer lastFocusKeyCode;
    @Nullable Integer lastResizeKeyCode;
    @Nullable String lastLayout;
    @Nullable String lastEdge;
    @Nullable Boolean lastRotateClockwise;
    boolean focusPaneDirectionResult = true;
    boolean resizeActivePaneResult = true;
    boolean killFocusedPaneResult = true;
    boolean applyPaneLayoutResult = true;
    boolean cyclePaneLayoutResult = true;
    boolean equalizePaneLayoutResult = true;
    boolean rotatePaneLayoutResult = true;
    boolean moveFocusedPaneToEdgeResult = true;
    @Nullable String layoutPolicy = TerminalPaneController.LAYOUT_GRID;

    // Sessions and windows
    boolean cloneCurrentBrowserSessionResult = true;
    boolean resetCurrentSessionResult = true;
    boolean sessionsPanelShowing;
    boolean renameCurrentSessionToResult = true;
    boolean renameBrowserSessionResult = true;
    boolean renameCurrentWindowToResult = true;
    boolean selectWindowResult = true;
    boolean promptCurrentWindowRenameResult = true;
    boolean promptCurrentSessionRenameResult = true;
    boolean beginTerminalRenameResult = true;
    int windowCount = 2;
    int windowIndex = 0;
    @Nullable String sessionName = "session";
    @Nullable String windowName = "window";
    @Nullable String browserSessionName = "browser";
    @Nullable String lastRenamedSessionName;
    @Nullable String lastRenamedWindowName;
    @Nullable String lastRenamedBrowserName;
    int lastRenamedBrowserIndex = -1;
    @Nullable Integer lastSelectedWindow;
    @Nullable Boolean lastSwitchWindowForward;

    // Workspaces
    @Nullable TerminalWorkspace saveWorkspaceResult;
    @Nullable TerminalWorkspace.LoadResult loadWorkspaceResult;
    List<TerminalWorkspaceStore.Entry> workspaceEntries = Collections.emptyList();
    @Nullable TerminalWorkspace.WorkspaceException workspaceFailure;
    @Nullable String lastSavedWorkspaceName;
    boolean lastSaveOverwrite;
    boolean lastSaveCaptureCommands;
    @Nullable String lastLoadedWorkspaceName;
    boolean lastLoadReplace;
    boolean lastLoadRunCommands;
    @Nullable String lastDeletedWorkspaceName;

    // Appearance and app surfaces
    boolean wallpaperEnabled;
    boolean cursorTrailEnabled;
    boolean toggleWallpaperModeResult = true;
    boolean toggleCursorTrailResult = true;
    boolean keyInspectorOpen = true;
    boolean showTerminalActionSheetResult = true;
    @Nullable PointF lastActionSheetAnchor;
    @Nullable String lastActionHint;
    final List<String> toasts = new ArrayList<>();
    final List<String> sessionSwitchIndicators = new ArrayList<>();

    // Shells
    @Nullable TermuxService service;
    final List<TerminalSession> shellActivity = new ArrayList<>();
    final List<TerminalSession> shellAttention = new ArrayList<>();
    final List<Integer> clearedShellAttentionPids = new ArrayList<>();

    // Sibling clients
    @Nullable TermuxTerminalSessionActivityClient sessionClient;
    @Nullable TermuxTerminalViewClient viewClient;

    FakeTerminalHost(@NonNull Context context, @NonNull TermuxSharedProperties properties) {
        this.context = context;
        this.properties = properties;
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "fake-terminal-host-" + System.nanoTime(), Context.MODE_PRIVATE);
        this.preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    private void record(@NonNull String name) {
        calls.add(name);
    }

    /** Whether {@code name} was called at least once. */
    boolean called(@NonNull String name) {
        return calls.contains(name);
    }

    /** The drawer-visible session list, backed by a plain list a test fills. */
    static final class FakeSessions implements Sessions {

        final List<TerminalSession> rows = new ArrayList<>();
        final Map<TerminalSession, Integer> numbers = new LinkedHashMap<>();
        final Map<TerminalSession, String> names = new LinkedHashMap<>();
        @Nullable TerminalSession currentTabPrimary;
        int currentNumber;
        @Nullable ListView listView;

        @Override public int count() {
            return rows.size();
        }

        @Override @Nullable public TerminalSession at(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        @Override public int indexOf(@Nullable TerminalSession session) {
            return session == null ? -1 : rows.indexOf(session);
        }

        @Override @Nullable public TerminalSession currentTabPrimary() {
            return currentTabPrimary;
        }

        @Override public int currentNumber() {
            return currentNumber;
        }

        @Override public int numberOf(@Nullable TerminalSession shell) {
            Integer number = numbers.get(shell);
            return number == null ? 0 : number;
        }

        @Override @Nullable public String nameOf(@Nullable TerminalSession shell) {
            return names.get(shell);
        }

        @Override @Nullable public ListView listView() {
            return listView;
        }
    }

    // --- Views and session ---

    @Override @Nullable public TerminalView focusedView() {
        return focusedView;
    }

    @Override @Nullable public TerminalSession currentSession() {
        return currentSession;
    }

    @Override @Nullable public ExtraKeysView extraKeysView() {
        return null;
    }

    @Override @Nullable public EditText toolbarTextInput() {
        return null;
    }

    @Override public boolean hasTerminalToolbar() {
        return hasToolbar;
    }

    @Override public boolean isTerminalViewSelected() {
        return terminalViewSelected;
    }

    @Override public void setRootViewLoggingEnabled(boolean enabled) {
        record("setRootViewLoggingEnabled");
    }

    @Override public void setDrawerLocked(boolean locked) {
        drawerLocked = locked;
    }

    @Override public void toggleTerminalToolbar() {
        record("toggleTerminalToolbar");
        toolbarToggles++;
    }

    @Override public void requestFlushDockGeometryUpdate() {
        flushDockRequests++;
    }

    // --- Settings ---

    @Override public TermuxAppSharedPreferences preferences() {
        return preferences;
    }

    @Override public TermuxSharedProperties properties() {
        return properties;
    }

    // --- Activity state ---

    @Override public boolean isActivityRecreated() {
        return activityRecreated;
    }

    @Override public boolean isOnResumeAfterOnCreate() {
        return onResumeAfterOnCreate;
    }

    @Override public boolean isSplitPanesEnabled() {
        return splitPanesEnabled;
    }

    @Override public void finishActivityIfNotFinishing() {
        record("finishActivityIfNotFinishing");
    }

    @Override public void runOnUiThread(@NonNull Runnable runnable) {
        runnable.run();
    }

    // --- Font size ---

    @Override public int activePaneFontSize() {
        return paneFontSize;
    }

    @Override public boolean setActivePaneFontSize(int size) {
        paneFontSize = size;
        return true;
    }

    // --- Soft keyboard ---

    @Override public void onSystemImeRequested() {
        record("onSystemImeRequested");
    }

    @Override public boolean shouldDelaySoftKeyboardShowOnResume() {
        return false;
    }

    @Override public boolean areSoftKeyboardFlagsDisabled() {
        return false;
    }

    @Override public void disableSoftKeyboard(@Nullable View view) {
        record("disableSoftKeyboard");
    }

    @Override public void clearDisableSoftKeyboardFlags() {
        record("clearDisableSoftKeyboardFlags");
    }

    @Override public void setSoftKeyboardAlwaysHiddenFlags() {
        record("setSoftKeyboardAlwaysHiddenFlags");
    }

    @Override public void setSoftInputModeAdjustResize() {
        record("setSoftInputModeAdjustResize");
    }

    @Override public void setSoftKeyboardVisibility(@NonNull Runnable showSoftKeyboardRunnable,
                                                    @Nullable View view, boolean visible) {
        record("setSoftKeyboardVisibility");
    }

    // --- Keybind hints ---

    @Override public boolean isKeybindHintPopupVisible() {
        return false;
    }

    @Override public void onKeybindHintConsumed() {
        record("onKeybindHintConsumed");
    }

    @Override public void toggleKeybindHintFullPopup() {
        record("toggleKeybindHintFullPopup");
    }

    @Override public void setHardwareKeybindHintPrefix(@Nullable String prefix, boolean shift) {
        record("setHardwareKeybindHintPrefix");
    }

    @Override @NonNull public KeyChordUi keyChordUi() {
        return new KeyChordUi() {
            @Override public void show(@NonNull String normalizedSequence) {}

            @Override public void showMode(@NonNull String mode) {}

            @Override public void showAction(@NonNull String stroke, @NonNull String name) {}

            @Override public void showFailure(@NonNull String stroke, @NonNull String message) {}

            @Override public void hide() {}
        };
    }

    @Override public void playKeyChordCancelledSound() {
        record("playKeyChordCancelledSound");
    }

    // --- Notices and surfaces ---

    @Override public void showToast(String text, boolean longDuration) {
        record("showToast");
        toasts.add(text);
    }

    @Override public boolean showTerminalActionSheet(@Nullable PointF anchor) {
        record("showTerminalActionSheet");
        lastActionSheetAnchor = anchor;
        return showTerminalActionSheetResult;
    }

    @Override @NonNull public TerminalSheetController sheetController() {
        throw new AssertionError("no sheet plane in this fake");
    }

    @Override public boolean beginScrollbackFind() {
        record("beginScrollbackFind");
        return false;
    }

    @Override public void showHintsOverlay(@NonNull String transcript) {
        record("showHintsOverlay");
    }

    @Override public void showScrollbackSearchOverlay(@NonNull TerminalView view) {
        record("showScrollbackSearchOverlay");
    }

    @Override public boolean promptCurrentSessionRename() {
        record("promptCurrentSessionRename");
        return promptCurrentSessionRenameResult;
    }

    // --- Modal surfaces ---

    @Override public boolean overlaysConsumeKeyDown(int keyCode, @NonNull KeyEvent event) {
        return overlaysConsume;
    }

    @Override public boolean overlaysConsumeKeyUp(int keyCode) {
        return overlaysConsume;
    }

    @Override public boolean overlaysConsumeCodePoint(int codePoint, boolean ctrlDown) {
        return overlaysConsume;
    }

    // --- Suggestion bar ---

    @Override public boolean shouldProcessSuggestionBarKeyEvent(int keyCode) {
        return false;
    }

    @Override public boolean shouldProcessSuggestionBarCodePoint(int codePoint, boolean ctrlDown) {
        return false;
    }

    // --- Host identity ---

    @Override @NonNull public Context context() {
        return context;
    }

    @Override public boolean isHostAlive() {
        return alive;
    }

    @Override public boolean isVisible() {
        return visible;
    }

    @Override public void showTerminalActionHint(@NonNull String toolName) {
        record("showTerminalActionHint");
        lastActionHint = toolName;
    }

    // --- Panes and views ---

    @Override @Nullable public TerminalView viewForSession(@Nullable TerminalSession session) {
        return session != null && session == currentSession ? focusedView : null;
    }

    @Override @NonNull public List<TerminalView> paneViews() {
        return paneViews;
    }

    @Override @Nullable public TerminalPaneController paneController() {
        return null;
    }

    @Override public void splitCurrentPane(int orientation) {
        record("splitCurrentPane");
        lastSplitOrientation = orientation;
    }

    @Override public boolean focusPaneDirection(int keyCode) {
        record("focusPaneDirection");
        lastFocusKeyCode = keyCode;
        return focusPaneDirectionResult;
    }

    @Override public boolean resizeActivePane(int keyCode) {
        record("resizeActivePane");
        lastResizeKeyCode = keyCode;
        return resizeActivePaneResult;
    }

    @Override public boolean killFocusedPane() {
        record("killFocusedPane");
        return killFocusedPaneResult;
    }

    @Override public boolean applyPaneLayout(@NonNull String layout) {
        record("applyPaneLayout");
        lastLayout = layout;
        return applyPaneLayoutResult;
    }

    @Override public boolean cyclePaneLayout() {
        record("cyclePaneLayout");
        return cyclePaneLayoutResult;
    }

    @Override @Nullable public String activePaneLayoutPolicy() {
        return layoutPolicy;
    }

    @Override public boolean equalizePaneLayout() {
        record("equalizePaneLayout");
        return equalizePaneLayoutResult;
    }

    @Override public boolean rotatePaneLayout(boolean clockwise) {
        record("rotatePaneLayout");
        lastRotateClockwise = clockwise;
        return rotatePaneLayoutResult;
    }

    @Override public boolean moveFocusedPaneToEdge(@NonNull String edge) {
        record("moveFocusedPaneToEdge");
        lastEdge = edge;
        return moveFocusedPaneToEdgeResult;
    }

    // --- Shells ---

    @Override @Nullable public TermuxService service() {
        return service;
    }

    @Override public void noteShellActivity(@Nullable TerminalSession session) {
        record("noteShellActivity");
        shellActivity.add(session);
    }

    @Override public void noteShellAttention(@NonNull TerminalSession session) {
        record("noteShellAttention");
        shellAttention.add(session);
    }

    @Override public void clearShellAttention(int shellPid) {
        record("clearShellAttention");
        clearedShellAttentionPids.add(shellPid);
    }

    @Override public void showSessionSwitchIndicator(@Nullable String text) {
        record("showSessionSwitchIndicator");
        sessionSwitchIndicators.add(text);
    }

    @Override public void syncBackgroundProcessStack() {
        record("syncBackgroundProcessStack");
    }

    // --- Sessions, windows and the drawer ---

    @Override @NonNull public Sessions sessions() {
        return sessions;
    }

    @Override public void rebuildDrawerSessions() {
        record("rebuildDrawerSessions");
    }

    @Override public void notifySessionListUpdated() {
        record("notifySessionListUpdated");
    }

    @Override public boolean activateSessionInPanes(TerminalSession session) {
        record("activateSessionInPanes");
        return true;
    }

    @Override public void captureTerminalDeparture() {
        record("captureTerminalDeparture");
    }

    @Override public void animateSessionArrival(int direction) {
        record("animateSessionArrival");
    }

    @Override public void animateSessionLifecycleArrival(int direction) {
        record("animateSessionLifecycleArrival");
    }

    @Override public void onWindowEmptied(TerminalPaneController.Window window) {
        record("onWindowEmptied");
    }

    @Override public void closeCurrentSession() {
        record("closeCurrentSession");
    }

    @Override public boolean cloneCurrentBrowserSession() {
        record("cloneCurrentBrowserSession");
        return cloneCurrentBrowserSessionResult;
    }

    @Override public boolean resetCurrentSession() {
        record("resetCurrentSession");
        return resetCurrentSessionResult;
    }

    @Override public void showSessionBrowser() {
        record("showSessionBrowser");
    }

    @Override public void toggleSessionsPanel() {
        record("toggleSessionsPanel");
        sessionsPanelShowing = !sessionsPanelShowing;
    }

    @Override public boolean isSessionsPanelShowing() {
        return sessionsPanelShowing;
    }

    @Override public boolean renameCurrentSessionTo(@Nullable String name) {
        record("renameCurrentSessionTo");
        lastRenamedSessionName = name;
        return renameCurrentSessionToResult;
    }

    @Override @Nullable public String currentSessionName() {
        return sessionName;
    }

    @Override public boolean renameBrowserSession(int index, @Nullable String name) {
        record("renameBrowserSession");
        lastRenamedBrowserIndex = index;
        lastRenamedBrowserName = name;
        return renameBrowserSessionResult;
    }

    @Override @Nullable public String browserSessionName(int index) {
        return browserSessionName;
    }

    @Override public void createNewWindow() {
        record("createNewWindow");
    }

    @Override public void closeCurrentWindow() {
        record("closeCurrentWindow");
    }

    @Override public void switchWindow(boolean forward) {
        record("switchWindow");
        lastSwitchWindowForward = forward;
    }

    @Override public boolean selectWindow(int index) {
        record("selectWindow");
        lastSelectedWindow = index;
        return selectWindowResult;
    }

    @Override public int currentWindowCount() {
        return windowCount;
    }

    @Override public int currentWindowIndex() {
        return windowIndex;
    }

    @Override public boolean promptCurrentWindowRename() {
        record("promptCurrentWindowRename");
        return promptCurrentWindowRenameResult;
    }

    @Override public boolean renameCurrentWindowTo(@Nullable String name) {
        record("renameCurrentWindowTo");
        lastRenamedWindowName = name;
        return renameCurrentWindowToResult;
    }

    @Override @Nullable public String currentWindowName() {
        return windowName;
    }

    @Override public boolean beginTerminalRename(@NonNull TerminalRenameTarget target) {
        record("beginTerminalRename");
        return beginTerminalRenameResult;
    }

    @Override public void openDrawer() {
        record("openDrawer");
    }

    @Override public void closeDrawers() {
        record("closeDrawers");
    }

    // --- Workspaces ---

    @Override @NonNull public TerminalWorkspace saveWorkspace(@NonNull String requestedName,
                                                             boolean overwrite,
                                                             boolean captureCommands)
            throws TerminalWorkspace.WorkspaceException {
        record("saveWorkspace");
        lastSavedWorkspaceName = requestedName;
        lastSaveOverwrite = overwrite;
        lastSaveCaptureCommands = captureCommands;
        if (workspaceFailure != null) throw workspaceFailure;
        return saveWorkspaceResult != null ? saveWorkspaceResult
            : new TerminalWorkspace(requestedName, 0L, 0, Collections.emptyList());
    }

    @Override @NonNull public TerminalWorkspace.LoadResult loadWorkspace(@NonNull String name,
                                                                        boolean replace,
                                                                        boolean runCommands)
            throws TerminalWorkspace.WorkspaceException {
        record("loadWorkspace");
        lastLoadedWorkspaceName = name;
        lastLoadReplace = replace;
        lastLoadRunCommands = runCommands;
        if (workspaceFailure != null) throw workspaceFailure;
        return loadWorkspaceResult != null ? loadWorkspaceResult
            : new TerminalWorkspace.LoadResult(1, 2, 3, 4, 5, replace);
    }

    @Override @NonNull public List<TerminalWorkspaceStore.Entry> listWorkspaces()
            throws TerminalWorkspace.WorkspaceException {
        record("listWorkspaces");
        if (workspaceFailure != null) throw workspaceFailure;
        return workspaceEntries;
    }

    @Override public void deleteWorkspace(@NonNull String name)
            throws TerminalWorkspace.WorkspaceException {
        record("deleteWorkspace");
        lastDeletedWorkspaceName = name;
        if (workspaceFailure != null) throw workspaceFailure;
    }

    @Override public void showWorkspacePicker() {
        record("showWorkspacePicker");
    }

    @Override public void promptSaveWorkspace() {
        record("promptSaveWorkspace");
    }

    // --- Appearance ---

    @Override public void openWallpaperPicker() {
        record("openWallpaperPicker");
    }

    @Override public boolean toggleWallpaperMode() {
        record("toggleWallpaperMode");
        return toggleWallpaperModeResult;
    }

    @Override public boolean isWallpaperModeEnabled() {
        return wallpaperEnabled;
    }

    @Override public boolean toggleCursorTrail() {
        record("toggleCursorTrail");
        return toggleCursorTrailResult;
    }

    @Override public boolean isCursorTrailEnabled() {
        return cursorTrailEnabled;
    }

    @Override public void openGlassLab() {
        record("openGlassLab");
    }

    @Override public void updateWindowBackgroundForCurrentSession() {
        record("updateWindowBackgroundForCurrentSession");
    }

    // --- App surfaces ---

    @Override public void openSettings() {
        record("openSettings");
    }

    @Override public void openLookAndFeel() {
        record("openLookAndFeel");
    }

    @Override public void openAppsBar() {
        record("openAppsBar");
    }

    @Override public void showCommandPalette() {
        record("showCommandPalette");
    }

    @Override public void showExtraKeysRowEditor() {
        record("showExtraKeysRowEditor");
    }

    @Override public boolean toggleKeyInspector() {
        record("toggleKeyInspector");
        return keyInspectorOpen;
    }

    @Override public void showTextInputDialog(int titleRes, @Nullable String initialText,
                                              int confirmRes,
                                              @NonNull TextInputDialogUtils.TextSetListener onConfirm) {
        record("showTextInputDialog");
    }

    // --- Performance ---

    @Override public void resetTerminalPerformanceMetrics() {
        record("resetTerminalPerformanceMetrics");
    }

    @Override @NonNull public TerminalFrameMetricsMonitor.Snapshot frameMetricsSnapshot() {
        return new TerminalFrameMetricsMonitor.Snapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L);
    }

    // --- Sibling clients ---

    @Override @Nullable public TermuxTerminalSessionActivityClient sessionClient() {
        return sessionClient;
    }

    @Override @Nullable public TermuxTerminalViewClient viewClient() {
        return viewClient;
    }
}
