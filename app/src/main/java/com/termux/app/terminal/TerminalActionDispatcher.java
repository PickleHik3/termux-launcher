package com.termux.app.terminal;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;
import com.termux.view.TerminalRenderMetrics;
import com.termux.view.TerminalView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes registry-registered terminal hierarchy actions against the live
 * {@link TerminalHost}.
 *
 * <p>Terminal actions differ from the other tools in {@link LauncherToolRegistry}:
 * they need a foreground Activity and must run on the main thread, while callers
 * may arrive on arbitrary background threads. This class is the single seam
 * between the two.
 *
 * <p>The host is held weakly and attached only between
 * {@code onResume} and {@code onStop}. When nothing is attached, callers get a
 * {@code 409 activity_not_running} rather than a silent no-op — an agent must be
 * able to tell "did nothing" from "could not act".
 */
public final class TerminalActionDispatcher {

    private static final String LOG_TAG = "TerminalActionDispatcher";

    /** Upper bound on how long a caller thread waits for the main thread. */
    private static final long MAIN_THREAD_TIMEOUT_MS = 5_000L;

    /** Minimum fuzzy score for {@code app.launch}, matching the suggestion bar. */
    private static final int APP_MATCH_TOLERANCE = 70;

    public static final String TOOL_TERMINAL_STATE = "terminal.state";
    public static final String TOOL_WORKSPACE_SAVE = "workspace.save";
    public static final String TOOL_WORKSPACE_LOAD = "workspace.load";
    public static final String TOOL_WORKSPACE_LIST = "workspace.list";
    public static final String TOOL_WORKSPACE_DELETE = "workspace.delete";
    public static final String TOOL_WORKSPACE_PICKER = "workspace.picker";
    public static final String TOOL_WORKSPACE_SAVE_PROMPT = "workspace.save_prompt";
    public static final String TOOL_TERMINAL_TOGGLE_SCRATCHPAD = "terminal.toggle_scratchpad";
    public static final String TOOL_EXTRA_KEYS_EDIT = "extrakeys.edit";
    public static final String TOOL_PANE_SPLIT_VERTICAL = "pane.split_vertical";
    public static final String TOOL_PANE_SPLIT_HORIZONTAL = "pane.split_horizontal";
    public static final String TOOL_PANE_FOCUS_DIRECTION = "pane.focus_direction";
    public static final String TOOL_PANE_RESIZE = "pane.resize";
    public static final String TOOL_PANE_KILL_FOCUSED = "pane.kill_focused";
    public static final String TOOL_PANE_LAYOUT = "pane.layout";
    public static final String TOOL_PANE_EQUALIZE = "pane.equalize";
    public static final String TOOL_PANE_ROTATE = "pane.rotate";
    public static final String TOOL_PANE_MOVE_TO_EDGE = "pane.move_to_edge";
    public static final String TOOL_PANE_NEXT_LAYOUT = "pane.next_layout";
    public static final String TOOL_PANE_TOGGLE_FLOAT = "pane.toggle_float";
    public static final String TOOL_WINDOW_NEW = "window.new";
    public static final String TOOL_WINDOW_CLOSE = "window.close";
    public static final String TOOL_WINDOW_NEXT = "window.next";
    public static final String TOOL_WINDOW_PREVIOUS = "window.previous";
    public static final String TOOL_SESSION_NEW = "session.new";
    public static final String TOOL_SESSION_BROWSER = "session.browser";
    public static final String TOOL_SESSION_PANEL = "session.panel";
    public static final String TOOL_SESSION_CLONE_CURRENT = "session.clone_current";
    public static final String TOOL_SESSION_NEXT = "session.next";
    public static final String TOOL_SESSION_PREVIOUS = "session.previous";
    public static final String TOOL_SESSION_CLOSE_CURRENT = "session.close_current";
    public static final String TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD = "terminal.toggle_soft_keyboard";
    public static final String TOOL_TERMINAL_TOGGLE_TOOLBAR = "terminal.toggle_toolbar";
    public static final String TOOL_TERMINAL_FONT_SIZE_INCREASE = "terminal.font_size_increase";
    public static final String TOOL_TERMINAL_FONT_SIZE_DECREASE = "terminal.font_size_decrease";
    public static final String TOOL_TERMINAL_SELECT_URL = "terminal.select_url";
    public static final String TOOL_TERMINAL_SELECT_AT_CURSOR = "terminal.select_at_cursor";
    public static final String TOOL_TERMINAL_SELECT_ALL = "terminal.select_all";
    public static final String TOOL_TERMINAL_HINTS = "terminal.hints";
    public static final String TOOL_TERMINAL_SEARCH_SCROLLBACK = "terminal.search_scrollback";
    public static final String TOOL_TERMINAL_SHARE_TRANSCRIPT = "terminal.share_transcript";
    public static final String TOOL_CLIPBOARD_PASTE = "clipboard.paste";
    public static final String TOOL_WINDOW_SELECT = "window.select";
    public static final String TOOL_WINDOW_RENAME = "window.rename";
    public static final String TOOL_SESSION_RENAME = "session.rename";
    public static final String TOOL_SESSION_RENAME_AT_INDEX = "session.rename_at_index";
    public static final String TOOL_PANE_RENAME = "pane.rename";
    public static final String TOOL_TERMINAL_RESET = "terminal.reset";
    public static final String TOOL_APPEARANCE_SET_WALLPAPER = "appearance.set_wallpaper";
    public static final String TOOL_APPEARANCE_TOGGLE_WALLPAPER = "appearance.toggle_wallpaper";
    public static final String TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT = "terminal.jump_previous_prompt";
    public static final String TOOL_TERMINAL_JUMP_NEXT_PROMPT = "terminal.jump_next_prompt";
    public static final String TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL = "appearance.toggle_cursor_trail";
    public static final String TOOL_APPEARANCE_SURFACE_EDITOR = "appearance.surface_editor";
    /** Legacy alias from when the surface editor was called the glass lab; scripts may still call it. */
    public static final String TOOL_APPEARANCE_GLASS_LAB_LEGACY = "appearance.glass_lab";
    public static final String TOOL_APP_OPEN_SETTINGS = "app.open_settings";
    public static final String TOOL_APP_OPEN_LOOK_AND_FEEL = "app.open_look_and_feel";
    public static final String TOOL_APP_OPEN_APPS_BAR = "app.open_apps_bar";
    public static final String TOOL_APP_COMMAND_PALETTE = "app.command_palette";
    public static final String TOOL_APP_LAUNCH = "app.launch";
    public static final String TOOL_APP_KEY_INSPECTOR = "app.key_inspector";
    public static final String TOOL_APP_OPEN_DRAWER = "app.open_drawer";
    public static final String TOOL_APP_CLOSE_DRAWER = "app.close_drawer";
    public static final String TOOL_TERMINAL_ACTION_SHEET = "terminal.action_sheet";
    public static final String TOOL_SESSION_ACTIVATE_BY_INDEX = "session.activate_by_index";
    public static final String TOOL_WINDOW_RENAME_PROMPT = "window.rename_prompt";
    public static final String TOOL_SESSION_RENAME_PROMPT = "session.rename_prompt";
    public static final String TOOL_PANE_RENAME_PROMPT = "pane.rename_prompt";
    public static final String TOOL_TERMINAL_SHARE_SELECTED = "terminal.share_selected";
    public static final String TOOL_CLIPBOARD_COPY_SELECTED = "clipboard.copy_selected";
    public static final String TOOL_FONTS_PICK = "fonts.pick";
    public static final String TOOL_FONTS_INSTALL = "fonts.install";

    private static final TerminalActionDispatcher INSTANCE = new TerminalActionDispatcher();

    @NonNull
    private final AtomicReference<WeakReference<TerminalHost>> hostRef =
        new AtomicReference<>(new WeakReference<>(null));

    private TerminalActionDispatcher() {
    }

    @NonNull
    public static TerminalActionDispatcher getInstance() {
        return INSTANCE;
    }

    /** Called from {@code TermuxActivity.onResume()}. */
    public void attach(@NonNull TerminalHost host) {
        hostRef.set(new WeakReference<>(host));
    }

    /**
     * Called from {@code TermuxActivity.onStop()} and {@code onDestroy()}. Ignores
     * the call when a different host has already attached, so an old activity's
     * teardown cannot detach its replacement during recreation.
     */
    public void detach(@NonNull TerminalHost host) {
        WeakReference<TerminalHost> current = hostRef.get();
        TerminalHost attached = current == null ? null : current.get();
        if (attached == null || attached == host) {
            hostRef.set(new WeakReference<>(null));
        }
    }

    /** Whether a foreground Activity is currently able to execute terminal actions. */
    public boolean isAttached() {
        return currentHost() != null;
    }

    /** Whether {@code toolName} is a terminal action handled by this dispatcher. */
    public static boolean handles(@Nullable String toolName) {
        if (toolName == null) return false;
        switch (toolName) {
            case TOOL_TERMINAL_STATE:
            case TOOL_WORKSPACE_SAVE:
            case TOOL_WORKSPACE_LOAD:
            case TOOL_WORKSPACE_LIST:
            case TOOL_WORKSPACE_DELETE:
            case TOOL_WORKSPACE_PICKER:
            case TOOL_WORKSPACE_SAVE_PROMPT:
            case TOOL_TERMINAL_TOGGLE_SCRATCHPAD:
            case TOOL_EXTRA_KEYS_EDIT:
            case TOOL_PANE_SPLIT_VERTICAL:
            case TOOL_PANE_SPLIT_HORIZONTAL:
            case TOOL_PANE_FOCUS_DIRECTION:
            case TOOL_PANE_RESIZE:
            case TOOL_PANE_KILL_FOCUSED:
            case TOOL_PANE_LAYOUT:
            case TOOL_PANE_EQUALIZE:
            case TOOL_PANE_ROTATE:
            case TOOL_PANE_MOVE_TO_EDGE:
            case TOOL_PANE_NEXT_LAYOUT:
            case TOOL_PANE_TOGGLE_FLOAT:
            case TOOL_WINDOW_NEW:
            case TOOL_WINDOW_CLOSE:
            case TOOL_WINDOW_NEXT:
            case TOOL_WINDOW_PREVIOUS:
            case TOOL_SESSION_NEW:
            case TOOL_SESSION_BROWSER:
            case TOOL_SESSION_PANEL:
            case TOOL_SESSION_CLONE_CURRENT:
            case TOOL_SESSION_NEXT:
            case TOOL_SESSION_PREVIOUS:
            case TOOL_SESSION_CLOSE_CURRENT:
            case TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD:
            case TOOL_TERMINAL_TOGGLE_TOOLBAR:
            case TOOL_TERMINAL_FONT_SIZE_INCREASE:
            case TOOL_TERMINAL_FONT_SIZE_DECREASE:
            case TOOL_TERMINAL_SELECT_URL:
            case TOOL_TERMINAL_SELECT_AT_CURSOR:
            case TOOL_TERMINAL_SELECT_ALL:
            case TOOL_TERMINAL_HINTS:
            case TOOL_TERMINAL_SEARCH_SCROLLBACK:
            case TOOL_TERMINAL_SHARE_TRANSCRIPT:
            case TOOL_CLIPBOARD_PASTE:
            case TOOL_WINDOW_SELECT:
            case TOOL_WINDOW_RENAME:
            case TOOL_SESSION_RENAME:
            case TOOL_PANE_RENAME:
            case TOOL_SESSION_RENAME_AT_INDEX:
            case TOOL_TERMINAL_RESET:
            case TOOL_APPEARANCE_SET_WALLPAPER:
            case TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT:
            case TOOL_TERMINAL_JUMP_NEXT_PROMPT:
            case TOOL_APPEARANCE_TOGGLE_WALLPAPER:
            case TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL:
            case TOOL_APPEARANCE_SURFACE_EDITOR:
            case TOOL_APPEARANCE_GLASS_LAB_LEGACY:
            case TOOL_APP_OPEN_SETTINGS:
            case TOOL_APP_OPEN_LOOK_AND_FEEL:
            case TOOL_APP_OPEN_APPS_BAR:
            case TOOL_APP_COMMAND_PALETTE:
            case TOOL_APP_LAUNCH:
            case TOOL_APP_KEY_INSPECTOR:
            case TOOL_APP_OPEN_DRAWER:
            case TOOL_APP_CLOSE_DRAWER:
            case TOOL_TERMINAL_ACTION_SHEET:
            case TOOL_SESSION_ACTIVATE_BY_INDEX:
            case TOOL_WINDOW_RENAME_PROMPT:
            case TOOL_SESSION_RENAME_PROMPT:
            case TOOL_PANE_RENAME_PROMPT:
            case TOOL_TERMINAL_SHARE_SELECTED:
            case TOOL_CLIPBOARD_COPY_SELECTED:
            case TOOL_FONTS_PICK:
            case TOOL_FONTS_INSTALL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Snapshot of the conditions an {@link LauncherToolRegistry.AvailabilityPredicate}
     * may inspect. Returns an all-false context when nothing is attached, so a
     * caller asking about availability while backgrounded gets "unavailable"
     * rather than an exception.
     */
    @NonNull
    public LauncherToolRegistry.ActionContext actionContext() {
        TerminalHost host = currentHost();
        final boolean splits = host != null && host.isSplitPanesEnabled();
        final boolean session = host != null && host.currentSession() != null;
        final boolean selection = host != null && hasSelectedText(host);
        return new LauncherToolRegistry.ActionContext() {
            @Override
            public boolean isSplitPanesEnabled() {
                return splits;
            }

            @Override
            public boolean hasCurrentSession() {
                return session;
            }

            @Override
            public boolean hasSelectedText() {
                return selection;
            }
        };
    }

    private static boolean hasSelectedText(@NonNull TerminalHost host) {
        com.termux.view.TerminalView view = host.focusedView();
        if (view == null) return false;
        String selected = view.getStoredSelectedText();
        return selected != null && !selected.isEmpty();
    }

    @Nullable
    private TerminalHost currentHost() {
        WeakReference<TerminalHost> ref = hostRef.get();
        TerminalHost host = ref == null ? null : ref.get();
        if (host == null || !host.isHostAlive()) {
            return null;
        }
        return host;
    }

    /**
     * Runs a terminal action on the main thread and returns a result envelope in
     * the {@code {"ok":…, "_statusCode":…}} shape that
     * {@code LauncherCtlApiServer.wrapExecutionResult} consumes.
     *
     * <p>Safe to call from any thread. Never throws for an unknown tool or a
     * missing Activity; both come back as error envelopes.
     */
    @NonNull
    public JSONObject execute(@NonNull String toolName, @NonNull JSONObject arguments) {
        if (!handles(toolName)) {
            return error(501, "not_implemented", "Not a terminal action: " + toolName);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return executeOnMainThread(toolName, arguments);
        }

        final AtomicReference<JSONObject> result = new AtomicReference<>(null);
        final CountDownLatch latch = new CountDownLatch(1);
        boolean posted = new Handler(Looper.getMainLooper()).post(() -> {
            try {
                result.set(executeOnMainThread(toolName, arguments));
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Terminal action '" + toolName + "' failed", t);
                result.set(error(500, "execution_failed", String.valueOf(t.getMessage())));
            } finally {
                latch.countDown();
            }
        });
        if (!posted) {
            return error(503, "unavailable", "Main thread is not accepting work");
        }

        try {
            if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return error(504, "timeout",
                    "Terminal action '" + toolName + "' did not complete within "
                        + MAIN_THREAD_TIMEOUT_MS + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(503, "interrupted", "Interrupted while waiting for the main thread");
        }

        JSONObject completed = result.get();
        return completed != null ? completed : error(500, "execution_failed", "Terminal action returned nothing");
    }

    /**
     * Runs the action and, when it worked, lets the UI say which action that was. Every caller —
     * an extra key, a space-bar swipe, a key binding, the palette, the agent — arrives here, so
     * this is the one place the hint can be raised without each entry point remembering to.
     */
    @NonNull
    private JSONObject executeOnMainThread(@NonNull String toolName, @NonNull JSONObject arguments) {
        JSONObject result = executeOnMainThreadInternal(toolName, arguments);
        if (result != null && result.optBoolean("ok", false)) {
            TerminalHost host = currentHost();
            if (host != null) host.showTerminalActionHint(toolName);
        }
        return result;
    }

    @NonNull
    private JSONObject executeOnMainThreadInternal(@NonNull String toolName,
                                                   @NonNull JSONObject arguments) {
        TerminalHost host = currentHost();
        if (host == null) {
            return error(409, "activity_not_running",
                "The terminal UI is not in the foreground, so '" + toolName + "' cannot run");
        }

        try {
            switch (toolName) {
                case TOOL_TERMINAL_STATE:
                    if (arguments.optBoolean("resetPerformance", false)) {
                        host.resetTerminalPerformanceMetrics();
                    }
                    return buildState(host);

                case TOOL_WORKSPACE_SAVE: {
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    TerminalWorkspace workspace = host.saveWorkspace(arguments.optString("name", ""),
                        arguments.optBoolean("overwrite", false),
                        arguments.optBoolean("captureCommands", false));
                    return ok().put("name", workspace.name)
                        .put("sessions", workspace.sessions.size())
                        .put("panes", workspace.paneCount())
                        .put("commandsCaptured", workspace.commandCount());
                }
                case TOOL_WORKSPACE_LOAD: {
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    String mode = arguments.optString("mode", "append");
                    if (!"append".equals(mode) && !"replace".equals(mode))
                        return error(400, "bad_request", "Invalid 'mode'; expected append or replace");
                    TerminalWorkspace.LoadResult result = host.loadWorkspace(
                        arguments.optString("name", ""), "replace".equals(mode),
                        arguments.optBoolean("runCommands", false));
                    return ok().put("name", arguments.optString("name", "").trim())
                        .put("mode", mode)
                        .put("sessions", result.sessions)
                        .put("windows", result.windows)
                        .put("panes", result.panes)
                        .put("commandsRun", result.commandsRun)
                        .put("commandsSkipped", result.commandsSkipped);
                }
                case TOOL_WORKSPACE_LIST: {
                    JSONArray entries = new JSONArray();
                    for (TerminalWorkspaceStore.Entry entry : host.listWorkspaces()) {
                        entries.put(new JSONObject().put("name", entry.name)
                            .put("modifiedAtEpochMs", entry.modifiedAtEpochMs)
                            .put("sizeBytes", entry.sizeBytes));
                    }
                    return ok().put("count", entries.length()).put("workspaces", entries);
                }
                case TOOL_WORKSPACE_DELETE:
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    String deletedName = TerminalWorkspaceStore.validateName(arguments.optString("name", ""));
                    host.deleteWorkspace(deletedName);
                    return ok().put("name", deletedName).put("deleted", true);

                case TOOL_PANE_SPLIT_VERTICAL:
                    return doSplit(host, LinearLayout.HORIZONTAL, "vertical");
                case TOOL_PANE_SPLIT_HORIZONTAL:
                    return doSplit(host, LinearLayout.VERTICAL, "horizontal");

                case TOOL_PANE_FOCUS_DIRECTION: {
                    Integer keyCode = directionToKeyCode(arguments.optString("direction", ""));
                    if (keyCode == null) return badDirection();
                    return ok().put("handled", host.focusPaneDirection(keyCode));
                }
                case TOOL_PANE_RESIZE: {
                    Integer keyCode = directionToKeyCode(arguments.optString("direction", ""));
                    if (keyCode == null) return badDirection();
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    return ok().put("handled", host.resizeActivePane(keyCode));
                }
                case TOOL_PANE_KILL_FOCUSED:
                    return ok().put("killed", host.killFocusedPane());

                case TOOL_PANE_LAYOUT: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!arguments.has("layout")) return error(400, "bad_request", "Missing 'layout'");
                    String layout = arguments.optString("layout", "");
                    if (!isPaneLayout(layout)) {
                        return error(400, "bad_request",
                            "Invalid 'layout'; expected stack, grid, tall, fat, horizontal, or vertical");
                    }
                    if (!host.applyPaneLayout(layout)) return noSession(toolName);
                    return ok().put("layout", layout);
                }
                case TOOL_PANE_NEXT_LAYOUT: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!host.cyclePaneLayout()) return noSession(toolName);
                    return ok().put("layout", host.activePaneLayoutPolicy());
                }
                case TOOL_PANE_EQUALIZE:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!host.equalizePaneLayout()) return noSession(toolName);
                    return ok().put("equalized", true);
                case TOOL_PANE_ROTATE: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    String direction = arguments.optString("direction", "clockwise");
                    if (!"clockwise".equals(direction) && !"counterclockwise".equals(direction)) {
                        return error(400, "bad_request",
                            "Invalid 'direction'; expected clockwise or counterclockwise");
                    }
                    if (!host.rotatePaneLayout("clockwise".equals(direction))) return noSession(toolName);
                    return ok().put("direction", direction);
                }
                case TOOL_PANE_TOGGLE_FLOAT: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    TerminalPaneController controller = host.paneController();
                    if (controller == null) return noSession(toolName);
                    switch (controller.toggleFloatActivePane()) {
                        case TerminalPaneController.FLOAT_TOGGLE_FLOATED:
                            return ok().put("floating", true);
                        case TerminalPaneController.FLOAT_TOGGLE_DOCKED:
                            return ok().put("floating", false);
                        case TerminalPaneController.FLOAT_TOGGLE_SINGLE_PANE:
                            return error(409, "single_pane",
                                "Floating requires at least two panes in the window");
                        default:
                            return noSession(toolName);
                    }
                }
                case TOOL_TERMINAL_TOGGLE_SCRATCHPAD: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    TerminalPaneController controller = host.paneController();
                    if (controller == null) return noSession(toolName);
                    switch (controller.toggleScratchpad()) {
                        case TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN:
                            return ok().put("shown", true);
                        case TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN:
                            return ok().put("shown", false);
                        default:
                            return noSession(toolName);
                    }
                }
                case TOOL_EXTRA_KEYS_EDIT:
                    host.showExtraKeysRowEditor();
                    return ok();
                case TOOL_WORKSPACE_PICKER:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    host.showWorkspacePicker();
                    return ok();
                case TOOL_WORKSPACE_SAVE_PROMPT:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    host.promptSaveWorkspace();
                    return ok();
                case TOOL_PANE_MOVE_TO_EDGE: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!arguments.has("edge")) return error(400, "bad_request", "Missing 'edge'");
                    String edge = arguments.optString("edge", "");
                    if (!isPaneEdge(edge)) {
                        return error(400, "bad_request", "Invalid 'edge'; expected left, right, up, or down");
                    }
                    if (!host.moveFocusedPaneToEdge(edge)) {
                        return error(409, "single_pane", "Moving to an edge requires at least two panes");
                    }
                    return ok().put("edge", edge);
                }

                case TOOL_WINDOW_NEW:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    host.createNewWindow();
                    return ok();
                case TOOL_WINDOW_CLOSE:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    host.closeCurrentWindow();
                    return ok();
                case TOOL_WINDOW_NEXT:
                    host.switchWindow(true);
                    return ok();
                case TOOL_WINDOW_PREVIOUS:
                    host.switchWindow(false);
                    return ok();

                case TOOL_SESSION_NEW: {
                    TermuxTerminalSessionActivityClient client = host.sessionClient();
                    if (client == null) return error(503, "unavailable", "Session client is not ready");
                    String name = arguments.optString("name", "").trim();
                    client.addNewSession(arguments.optBoolean("failsafe", false), name.isEmpty() ? null : name);
                    return ok();
                }
                case TOOL_SESSION_BROWSER:
                    host.showSessionBrowser();
                    return ok().put("browserOpen", true);
                case TOOL_SESSION_PANEL:
                    host.toggleSessionsPanel();
                    return ok().put("panelOpen", host.isSessionsPanelShowing());
                case TOOL_SESSION_CLONE_CURRENT:
                    if (!host.cloneCurrentBrowserSession()) return noSession(toolName);
                    return ok().put("cloned", true);
                case TOOL_SESSION_NEXT:
                case TOOL_SESSION_PREVIOUS: {
                    TermuxTerminalSessionActivityClient client = host.sessionClient();
                    if (client == null) return error(503, "unavailable", "Session client is not ready");
                    client.switchToSession(TOOL_SESSION_NEXT.equals(toolName));
                    return ok();
                }
                case TOOL_SESSION_CLOSE_CURRENT:
                    host.closeCurrentSession();
                    return ok();

                case TOOL_TERMINAL_TOGGLE_TOOLBAR:
                    host.toggleTerminalToolbar();
                    return ok();

                case TOOL_TERMINAL_RESET:
                    return host.resetCurrentSession() ? ok() : noSession(toolName);

                case TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT:
                case TOOL_TERMINAL_JUMP_NEXT_PROMPT: {
                    com.termux.view.TerminalView view = host.focusedView();
                    if (view == null || view.mEmulator == null) return noSession(toolName);
                    boolean backwards = TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT.equals(toolName);
                    if (!view.jumpToPrompt(backwards)) {
                        // Either the shell emits no OSC 133 marks, or there is no further prompt.
                        return error(409, "no_prompt_mark", view.mEmulator.hasShellIntegration()
                            ? "No further shell prompt in that direction"
                            : "The shell is not emitting OSC 133 prompt marks");
                    }
                    return ok().put("topRow", view.getTopRow());
                }

                case TOOL_APPEARANCE_SET_WALLPAPER:
                    host.openWallpaperPicker();
                    return ok();
                case TOOL_APPEARANCE_TOGGLE_WALLPAPER:
                    return ok().put("wallpaperEnabled", host.toggleWallpaperMode());
                case TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL:
                    return ok().put("cursorTrailEnabled", host.toggleCursorTrail());
                case TOOL_APPEARANCE_SURFACE_EDITOR:
                case TOOL_APPEARANCE_GLASS_LAB_LEGACY:
                    host.openSurfaceEditor();
                    return ok();
                case TOOL_APP_OPEN_SETTINGS:
                    host.openSettings();
                    return ok();
                case TOOL_APP_OPEN_LOOK_AND_FEEL:
                    host.openLookAndFeel();
                    return ok();
                case TOOL_APP_OPEN_APPS_BAR:
                    host.openAppsBar();
                    return ok();

                case TOOL_FONTS_PICK:
                    // Straight to the settings screen rather than through an Activity helper: the
                    // picker is an ordinary preference fragment and needs nothing from the terminal.
                    com.termux.shared.activity.ActivityUtils.startActivity(host.context(),
                        com.termux.app.activities.SettingsActivity.createFragmentIntent(host.context(),
                            com.termux.app.fragments.settings.termux.TermuxFontsPreferencesFragment.class,
                            com.termux.R.string.termux_fonts_preferences_title));
                    return ok();
                case TOOL_FONTS_INSTALL: {
                    String familyId = arguments.optString("id", "").trim();
                    if (familyId.isEmpty()) return error(400, "bad_request", "Missing 'id'");
                    com.termux.app.fonts.FontCatalog.Family family =
                        com.termux.app.fonts.FontCatalog.load(host.context()).family(familyId);
                    if (family == null) {
                        return error(404, "not_found", "No font family '" + familyId
                            + "' in the bundled catalog");
                    }
                    com.termux.app.fonts.FontInstaller.Options options =
                        new com.termux.app.fonts.FontInstaller.Options(
                            arguments.optBoolean("nerd_icons", true),
                            arguments.optString("ligatures", family.defaultLigatures),
                            true,
                            arguments.optInt("weight", 0));
                    com.termux.app.fonts.FontInstallCoordinator coordinator =
                        com.termux.app.fonts.FontInstallCoordinator.getInstance(host.context());
                    // Already on disk: no transfer, just rewrite the managed config and reload.
                    if (new com.termux.app.fonts.FontInstaller().isInstalled(family)) {
                        if (!coordinator.reapply(family, options)) {
                            return error(500, "execution_failed",
                                "Could not write ~/.termux/fonts.d/10-launcher.conf");
                        }
                        return ok().put("familyId", family.id).put("installed", true)
                            .put("downloaded", false);
                    }
                    if (!coordinator.start(family, options)) {
                        return error(409, "busy", "Another font install is already running");
                    }
                    return ok().put("familyId", family.id).put("installed", false)
                        .put("downloaded", true).put("downloadBytes", family.downloadBytes);
                }

                case TOOL_APP_COMMAND_PALETTE:
                    host.showCommandPalette();
                    return ok();
                case TOOL_APP_LAUNCH: {
                    String query = arguments.optString("query", "").trim();
                    if (query.isEmpty()) return error(400, "bad_request", "Missing 'query'");
                    LauncherAppEntry app = resolveApp(host.context(), query);
                    if (app == null) {
                        return error(404, "not_found", "No installed app matches '" + query + "'");
                    }
                    if (!LauncherAppLauncher.launchEntry(host.context(), app)) {
                        return error(500, "execution_failed", "Could not launch " + app.label);
                    }
                    // Same bookkeeping the suggestion bar does, so a launch from a
                    // binding or the palette also shapes the usage ranking.
                    LauncherUsageStatsStore.getInstance(host.context())
                        .recordLaunch(app.appRef.stableId());
                    return ok().put("package", app.appRef.packageName).put("label", app.label);
                }
                case TOOL_APP_KEY_INSPECTOR:
                    return ok().put("keyInspectorOpen", host.toggleKeyInspector());
                case TOOL_APP_OPEN_DRAWER:
                    host.openDrawer();
                    return ok();
                case TOOL_APP_CLOSE_DRAWER:
                    host.closeDrawers();
                    return ok();
                case TOOL_TERMINAL_ACTION_SHEET:
                    return host.showTerminalActionSheet(null) ? ok() : noSession(toolName);

                case TOOL_SESSION_ACTIVATE_BY_INDEX: {
                    TermuxTerminalSessionActivityClient indexClient = host.sessionClient();
                    if (indexClient == null) return error(503, "unavailable", "Session client is not ready");
                    if (!arguments.has("index")) return error(400, "bad_request", "Missing 'index'");
                    int sessionIndex = arguments.optInt("index", -1);
                    if (sessionIndex < 0 || sessionIndex >= host.sessions().count()) {
                        return error(400, "bad_request", "No session at index " + sessionIndex
                            + "; there are " + host.sessions().count());
                    }
                    indexClient.switchToSession(sessionIndex);
                    return ok().put("index", sessionIndex);
                }

                case TOOL_WINDOW_RENAME_PROMPT:
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!host.promptCurrentWindowRename())
                        return error(409, "no_window", "There is no window to rename");
                    return ok();
                case TOOL_TERMINAL_SHARE_SELECTED:
                case TOOL_CLIPBOARD_COPY_SELECTED: {
                    com.termux.view.TerminalView view = host.focusedView();
                    String selected = view == null ? null : view.getStoredSelectedText();
                    if (selected == null || selected.isEmpty()) {
                        return error(409, "no_selection",
                            "There is no selected text, so '" + toolName + "' cannot run");
                    }
                    if (TOOL_TERMINAL_SHARE_SELECTED.equals(toolName)) {
                        TermuxTerminalViewClient shareClient = host.viewClient();
                        if (shareClient == null) return error(503, "unavailable", "Terminal view client is not ready");
                        shareClient.shareSelectedText();
                    } else {
                        com.termux.shared.interact.ShareUtils.copyTextToClipboard(host.context(), selected);
                    }
                    return ok().put("characters", selected.length());
                }

                case TOOL_SESSION_RENAME_PROMPT: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!host.promptCurrentSessionRename())
                        return error(409, "no_session", "There is no session to rename");
                    return ok();
                }

                case TOOL_PANE_RENAME_PROMPT: {
                    TermuxTerminalSessionActivityClient promptClient = host.sessionClient();
                    if (promptClient == null) return error(503, "unavailable", "Session client is not ready");
                    // The pane's own shell, never the session the drawer row stands for: those are
                    // session.rename_prompt's job, and conflating them is what made Ctrl+Alt+R and
                    // Ctrl+Alt+Shift+R open the same editor.
                    if (!promptClient.promptCurrentPaneRename()) return noSession(toolName);
                    return ok();
                }

                case TOOL_WINDOW_SELECT: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    if (!arguments.has("index")) {
                        return error(400, "bad_request", "Missing 'index'");
                    }
                    int index = arguments.optInt("index", -1);
                    if (!host.selectWindow(index)) {
                        return error(400, "bad_request", "No window at index " + index
                            + "; the current session has " + host.currentWindowCount());
                    }
                    return ok().put("index", index);
                }

                case TOOL_WINDOW_RENAME: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    // An explicit empty name restores the automatic tab label; only an absent key
                    // is an error.
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    if (!host.renameCurrentWindowTo(arguments.optString("name", ""))) {
                        return error(409, "no_window", "There is no window to rename");
                    }
                    // Report the stored name: TerminalNamePolicy caps it, so what
                    // was asked for and what was kept can differ.
                    String storedWindow = host.currentWindowName();
                    return ok().put("name", storedWindow == null ? JSONObject.NULL : storedWindow);
                }

                case TOOL_SESSION_RENAME: {
                    if (!host.isSplitPanesEnabled()) return splitsDisabled();
                    // An explicit empty name clears the label; only an absent key is an error.
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    if (!host.renameCurrentSessionTo(arguments.optString("name", ""))) {
                        return error(409, "no_session", "There is no session to rename");
                    }
                    String stored = host.currentSessionName();
                    return ok().put("name", stored == null ? JSONObject.NULL : stored);
                }

                case TOOL_PANE_RENAME: {
                    TermuxTerminalSessionActivityClient renameClient = host.sessionClient();
                    if (renameClient == null) return error(503, "unavailable", "Session client is not ready");
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    String paneName = arguments.optString("name", "").trim();
                    if (!renameClient.renameCurrentPaneTo(paneName)) return noSession(toolName);
                    return ok().put("name", paneName.isEmpty() ? JSONObject.NULL : paneName);
                }

                case TOOL_SESSION_RENAME_AT_INDEX: {
                    if (!arguments.has("index")) return error(400, "bad_request", "Missing 'index'");
                    // An explicit empty name clears the label; only an absent key is an error.
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    int sessionIndex = arguments.optInt("index", -1);
                    // Deliberately the browser index, not the drawer index: rebuildDrawerSessions
                    // skips window-less sessions, so the two can diverge.
                    if (!host.renameBrowserSession(sessionIndex,
                            arguments.optString("name", ""))) {
                        return error(400, "bad_request", "No session at index " + sessionIndex);
                    }
                    // Report the stored name: TerminalNamePolicy caps it, so what was asked for
                    // and what was kept can differ.
                    String storedName = host.browserSessionName(sessionIndex);
                    return ok().put("index", sessionIndex)
                        .put("name", storedName == null ? JSONObject.NULL : storedName);
                }

                case TOOL_TERMINAL_SELECT_AT_CURSOR:
                case TOOL_TERMINAL_SELECT_ALL: {
                    // Selection lives on the view, not the view client: it has no session-level
                    // side effects, and the view is the only thing that knows the cursor cell.
                    com.termux.view.TerminalView selectView = host.focusedView();
                    if (selectView == null) return error(503, "unavailable", "Terminal view is not ready");
                    if (host.currentSession() == null) return noSession(toolName);
                    if (TOOL_TERMINAL_SELECT_ALL.equals(toolName)) {
                        selectView.selectAllText();
                    } else {
                        selectView.startTextSelectionAtCursor();
                    }
                    return ok().put("selecting", selectView.isSelectingText());
                }

                case TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD:
                case TOOL_TERMINAL_FONT_SIZE_INCREASE:
                case TOOL_TERMINAL_FONT_SIZE_DECREASE:
                case TOOL_TERMINAL_SELECT_URL:
                case TOOL_TERMINAL_HINTS:
                case TOOL_TERMINAL_SEARCH_SCROLLBACK:
                case TOOL_TERMINAL_SHARE_TRANSCRIPT:
                case TOOL_CLIPBOARD_PASTE: {
                    TermuxTerminalViewClient viewClient = host.viewClient();
                    if (viewClient == null) return error(503, "unavailable", "Terminal view client is not ready");
                    if (host.currentSession() == null) return noSession(toolName);
                    switch (toolName) {
                        case TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD:
                            viewClient.onToggleSoftKeyboardRequest();
                            break;
                        case TOOL_TERMINAL_FONT_SIZE_INCREASE:
                            viewClient.changeFontSize(true);
                            break;
                        case TOOL_TERMINAL_FONT_SIZE_DECREASE:
                            viewClient.changeFontSize(false);
                            break;
                        case TOOL_TERMINAL_SELECT_URL:
                            viewClient.showUrlSelection();
                            break;
                        case TOOL_TERMINAL_HINTS:
                            viewClient.showHintsOverlay();
                            break;
                        case TOOL_TERMINAL_SEARCH_SCROLLBACK:
                            viewClient.showScrollbackSearch();
                            break;
                        case TOOL_TERMINAL_SHARE_TRANSCRIPT:
                            viewClient.shareSessionTranscript();
                            break;
                        case TOOL_CLIPBOARD_PASTE:
                            viewClient.doPaste();
                            break;
                        default:
                            return error(501, "not_implemented", "Unhandled terminal action: " + toolName);
                    }
                    return ok();
                }

                default:
                    return error(501, "not_implemented", "Unhandled terminal action: " + toolName);
            }
        } catch (TerminalWorkspace.WorkspaceException e) {
            return workspaceError(e);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Terminal action '" + toolName + "' failed", t);
            return error(500, "execution_failed", String.valueOf(t.getMessage()));
        }
    }

    @NonNull
    private JSONObject doSplit(@NonNull TerminalHost host, int orientation, @NonNull String label) {
        if (!host.isSplitPanesEnabled()) return splitsDisabled();
        host.splitCurrentPane(orientation);
        try {
            return ok().put("split", label);
        } catch (JSONException e) {
            return ok();
        }
    }

    /**
     * Read-only snapshot, so a caller can tell what the split/window state is
     * before issuing commands. Uses only the {@link TerminalHost} surface.
     */
    @NonNull
    private JSONObject buildState(@NonNull TerminalHost host) {
        try {
            JSONObject state = ok();
            state.put("splitPanesEnabled", host.isSplitPanesEnabled());
            state.put("drawerSessions", host.sessions().count());
            state.put("hasCurrentSession", host.currentSession() != null);
            TerminalPaneController controller = host.paneController();
            state.put("visiblePanes", controller == null ? 0 : controller.getVisiblePaneViews().size());
            state.put("floatingPanes", controller == null ? 0 : controller.activeFloatingPaneCount());
            state.put("focusedPaneFloating", controller != null && controller.isActivePaneFloating());
            state.put("windows", host.currentWindowCount());
            state.put("currentWindow", host.currentWindowIndex());
            // The retained automatic layout, absent when the window is manually managed. Without
            // this the policy is invisible to agents and to any device check.
            String paneLayout = host.activePaneLayoutPolicy();
            if (paneLayout != null) state.put("paneLayout", paneLayout);
            String sessionName = host.currentSessionName();
            if (sessionName != null) state.put("windowSessionName", sessionName);
            state.put("wallpaperEnabled", host.isWallpaperModeEnabled());
            state.put("cursorTrailEnabled", host.isCursorTrailEnabled());
            state.put("performance", buildPerformanceState(host));
            return state;
        } catch (JSONException e) {
            return error(500, "execution_failed", "Failed to build terminal state");
        }
    }

    @NonNull
    private JSONObject buildPerformanceState(@NonNull TerminalHost host) throws JSONException {
        TerminalFrameMetricsMonitor.Snapshot window = host.frameMetricsSnapshot();
        JSONObject performance = new JSONObject();
        performance.put("measurementActiveMs", nanosToMillis(window.activeDurationNanos));
        performance.put("allocationScope", "whole_process_since_reset");
        performance.put("allocatedBytes", window.allocatedBytes);
        performance.put("gcCount", window.gcCount);

        JSONObject frames = new JSONObject();
        frames.put("scope", "whole_activity_window");
        frames.put("count", window.frameCount);
        frames.put("frameBudgetMs", nanosToMillis(window.frameBudgetNanos));
        frames.put("averageFrameMs", averageMillis(window.totalDurationNanos, window.frameCount));
        frames.put("medianFrameMs", nanosToMillis(window.medianTotalDurationNanos));
        frames.put("p95FrameMs", nanosToMillis(window.p95TotalDurationNanos));
        frames.put("maxFrameMs", nanosToMillis(window.maxTotalDurationNanos));
        frames.put("averageDrawMs", averageMillis(window.totalDrawDurationNanos, window.frameCount));
        frames.put("medianDrawMs", nanosToMillis(window.medianDrawDurationNanos));
        frames.put("p95DrawMs", nanosToMillis(window.p95DrawDurationNanos));
        frames.put("maxDrawMs", nanosToMillis(window.maxDrawDurationNanos));
        frames.put("jankyFrames", window.jankyFrameCount);
        frames.put("estimatedDroppedFrames", window.estimatedDroppedFrames);
        frames.put("metricsReportsDropped", window.metricsReportsDropped);
        performance.put("windowFrames", frames);

        JSONArray panes = new JSONArray();
        java.util.List<TerminalView> visiblePanes = host.paneViews();
        TerminalView activePane = host.focusedView();
        for (int i = 0; i < visiblePanes.size(); i++) {
            TerminalView pane = visiblePanes.get(i);
            TerminalRenderMetrics.Snapshot render = pane.getRenderMetricsSnapshot();
            JSONObject item = new JSONObject();
            item.put("index", i);
            item.put("active", pane == activePane);
            item.put("drawCount", render.drawCount);
            item.put("frameBudgetMs", nanosToMillis(render.frameBudgetNanos));
            item.put("averageRenderMs", averageMillis(render.totalRenderNanos, render.drawCount));
            item.put("medianRenderMs", nanosToMillis(render.medianRenderNanos));
            item.put("p95RenderMs", nanosToMillis(render.p95RenderNanos));
            item.put("maxRenderMs", nanosToMillis(render.maxRenderNanos));
            item.put("slowDraws", render.slowDrawCount);
            item.put("estimatedDroppedFrames", render.estimatedDroppedFrames);
            item.put("activeFrameTimeCount", render.activeFrameTimeCount);
            item.put("averageActiveFrameMs",
                averageMillis(render.totalActiveFrameTimeNanos, render.activeFrameTimeCount));
            item.put("medianActiveFrameMs", nanosToMillis(render.medianActiveFrameTimeNanos));
            item.put("p95ActiveFrameMs", nanosToMillis(render.p95ActiveFrameTimeNanos));
            item.put("maxActiveFrameMs", nanosToMillis(render.maxActiveFrameTimeNanos));
            panes.put(item);
        }
        performance.put("terminalPanes", panes);
        return performance;
    }

    private static double averageMillis(long totalNanos, long count) {
        return count <= 0L ? 0d : roundMillis(totalNanos / (double) count);
    }

    private static double nanosToMillis(long nanos) {
        return nanos <= 0L ? 0d : roundMillis(nanos);
    }

    private static double roundMillis(double nanos) {
        return Math.round((nanos / 1_000_000d) * 1000d) / 1000d;
    }

    @Nullable
    private static Integer directionToKeyCode(@NonNull String direction) {
        switch (direction.toLowerCase(Locale.US)) {
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            default: return null;
        }
    }

    private static boolean isPaneLayout(@NonNull String layout) {
        return TerminalPaneController.LAYOUT_STACK.equals(layout)
            || TerminalPaneController.LAYOUT_GRID.equals(layout)
            || TerminalPaneController.LAYOUT_TALL.equals(layout)
            || TerminalPaneController.LAYOUT_FAT.equals(layout)
            || TerminalPaneController.LAYOUT_HORIZONTAL.equals(layout)
            || TerminalPaneController.LAYOUT_VERTICAL.equals(layout);
    }

    private static boolean isPaneEdge(@NonNull String edge) {
        return TerminalPaneController.EDGE_LEFT.equals(edge)
            || TerminalPaneController.EDGE_RIGHT.equals(edge)
            || TerminalPaneController.EDGE_UP.equals(edge)
            || TerminalPaneController.EDGE_DOWN.equals(edge);
    }

    @NonNull
    private static JSONObject badDirection() {
        return error(400, "bad_request", "Missing or invalid 'direction'; expected left, right, up, or down");
    }

    /**
     * Exact package first, then the launcher's own ranking over labels, package
     * names, and stable ids. A cold app cache is loaded synchronously: a binding
     * that silently did nothing on its first press would be worse than paying for
     * one PackageManager query.
     */
    @Nullable
    private static LauncherAppEntry resolveApp(@NonNull android.content.Context context,
                                               @NonNull String query) {
        return resolveApp(LauncherAppDataProvider.getInstance(context), query, true);
    }

    /**
     * As above, against a caller-supplied provider. The palette resolves the chords it advertises
     * through this same method — resolution has to be identical, or a row could promise a stroke
     * that launches a different app — but passes {@code allowBlocking = false}, because the palette
     * must never block the main thread on a PackageManager sweep.
     */
    @Nullable
    static LauncherAppEntry resolveApp(@NonNull LauncherAppDataProvider provider,
                                       @NonNull String query, boolean allowBlocking) {
        if (!provider.hasLoadedApps() && !allowBlocking) return null;
        List<LauncherAppEntry> apps = provider.hasLoadedApps()
            ? provider.getAllApps()
            : provider.getAllAppsBlocking();
        LauncherAppEntry exact = provider.findDefaultByPackage(query);
        if (exact != null) return exact;
        List<LauncherAppEntry> ranked =
            LauncherRankingEngine.filterAndRank(apps, query, APP_MATCH_TOLERANCE);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    @NonNull
    private static JSONObject noSession(@NonNull String toolName) {
        return error(409, "no_session", "There is no active terminal session, so '" + toolName + "' cannot run");
    }

    @NonNull
    private static JSONObject splitsDisabled() {
        return error(409, "splits_disabled",
            "Split panes are disabled while compatibility mode is on");
    }

    @NonNull
    private static JSONObject workspaceError(@NonNull TerminalWorkspace.WorkspaceException error) {
        int status;
        switch (error.code) {
            case "invalid_name":
            case "invalid_workspace":
            case "unsupported_version":
            case "workspace_too_large":
                status = 400;
                break;
            case "not_found":
                status = 404;
                break;
            case "conflict":
            case "no_session":
            case "splits_disabled":
            case "too_many_panes":
                status = 409;
                break;
            case "unavailable":
                status = 503;
                break;
            default:
                status = 500;
        }
        return error(status, error.code, error.getMessage() == null ? error.code : error.getMessage());
    }

    @NonNull
    private static JSONObject ok() {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", true);
            json.put("_statusCode", 200);
        } catch (JSONException ignored) {
        }
        return json;
    }

    @NonNull
    private static JSONObject error(int statusCode, @NonNull String code, @NonNull String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("error", code);
            json.put("message", message);
            json.put("_statusCode", statusCode);
        } catch (JSONException ignored) {
        }
        return json;
    }
}
