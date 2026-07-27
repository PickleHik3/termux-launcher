package com.termux.app.terminal;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes registry-registered terminal hierarchy actions against the live
 * {@link TermuxActivity}.
 *
 * <p>Terminal actions differ from the other tools in {@link LauncherToolRegistry}:
 * they need a foreground Activity and must run on the main thread, while callers
 * ({@code /v1/agent/execute}, the MCP bridge, the CLI) arrive on arbitrary
 * background threads. This class is the single seam between the two.
 *
 * <p>The Activity is held weakly and attached only between
 * {@code onResume} and {@code onStop}. When nothing is attached, callers get a
 * {@code 409 activity_not_running} rather than a silent no-op — an agent must be
 * able to tell "did nothing" from "could not act".
 */
public final class TerminalActionDispatcher {

    private static final String LOG_TAG = "TerminalActionDispatcher";

    /** Upper bound on how long a caller thread waits for the main thread. */
    private static final long MAIN_THREAD_TIMEOUT_MS = 5_000L;

    public static final String TOOL_TERMINAL_STATE = "terminal.state";
    public static final String TOOL_PANE_SPLIT_VERTICAL = "pane.split_vertical";
    public static final String TOOL_PANE_SPLIT_HORIZONTAL = "pane.split_horizontal";
    public static final String TOOL_PANE_FOCUS_DIRECTION = "pane.focus_direction";
    public static final String TOOL_PANE_RESIZE = "pane.resize";
    public static final String TOOL_PANE_KILL_FOCUSED = "pane.kill_focused";
    public static final String TOOL_WINDOW_NEW = "window.new";
    public static final String TOOL_WINDOW_CLOSE = "window.close";
    public static final String TOOL_WINDOW_NEXT = "window.next";
    public static final String TOOL_WINDOW_PREVIOUS = "window.previous";
    public static final String TOOL_SESSION_NEW = "session.new";
    public static final String TOOL_SESSION_NEXT = "session.next";
    public static final String TOOL_SESSION_PREVIOUS = "session.previous";
    public static final String TOOL_SESSION_CLOSE_CURRENT = "session.close_current";
    public static final String TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD = "terminal.toggle_soft_keyboard";
    public static final String TOOL_TERMINAL_TOGGLE_TOOLBAR = "terminal.toggle_toolbar";
    public static final String TOOL_TERMINAL_FONT_SIZE_INCREASE = "terminal.font_size_increase";
    public static final String TOOL_TERMINAL_FONT_SIZE_DECREASE = "terminal.font_size_decrease";
    public static final String TOOL_TERMINAL_SELECT_URL = "terminal.select_url";
    public static final String TOOL_TERMINAL_SHARE_TRANSCRIPT = "terminal.share_transcript";
    public static final String TOOL_CLIPBOARD_PASTE = "clipboard.paste";
    public static final String TOOL_WINDOW_SELECT = "window.select";
    public static final String TOOL_WINDOW_RENAME = "window.rename";
    public static final String TOOL_SESSION_RENAME = "session.rename";
    public static final String TOOL_TERMINAL_RESET = "terminal.reset";
    public static final String TOOL_APPEARANCE_SET_WALLPAPER = "appearance.set_wallpaper";
    public static final String TOOL_APPEARANCE_TOGGLE_WALLPAPER = "appearance.toggle_wallpaper";
    public static final String TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT = "terminal.jump_previous_prompt";
    public static final String TOOL_TERMINAL_JUMP_NEXT_PROMPT = "terminal.jump_next_prompt";
    public static final String TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL = "appearance.toggle_cursor_trail";
    public static final String TOOL_APPEARANCE_GLASS_LAB = "appearance.glass_lab";
    public static final String TOOL_APP_OPEN_SETTINGS = "app.open_settings";
    public static final String TOOL_APP_OPEN_LOOK_AND_FEEL = "app.open_look_and_feel";
    public static final String TOOL_APP_OPEN_APPS_BAR = "app.open_apps_bar";
    public static final String TOOL_APP_COMMAND_PALETTE = "app.command_palette";
    public static final String TOOL_APP_KEY_INSPECTOR = "app.key_inspector";
    public static final String TOOL_APP_OPEN_DRAWER = "app.open_drawer";
    public static final String TOOL_APP_CLOSE_DRAWER = "app.close_drawer";
    public static final String TOOL_TERMINAL_ACTION_SHEET = "terminal.action_sheet";
    public static final String TOOL_SESSION_ACTIVATE_BY_INDEX = "session.activate_by_index";
    public static final String TOOL_WINDOW_RENAME_PROMPT = "window.rename_prompt";
    public static final String TOOL_SESSION_RENAME_PROMPT = "session.rename_prompt";
    public static final String TOOL_TERMINAL_SHARE_SELECTED = "terminal.share_selected";
    public static final String TOOL_CLIPBOARD_COPY_SELECTED = "clipboard.copy_selected";

    private static final TerminalActionDispatcher INSTANCE = new TerminalActionDispatcher();

    @NonNull
    private final AtomicReference<WeakReference<TermuxActivity>> activityRef =
        new AtomicReference<>(new WeakReference<>(null));

    private TerminalActionDispatcher() {
    }

    @NonNull
    public static TerminalActionDispatcher getInstance() {
        return INSTANCE;
    }

    /** Called from {@code TermuxActivity.onResume()}. */
    public void attach(@NonNull TermuxActivity activity) {
        activityRef.set(new WeakReference<>(activity));
    }

    /**
     * Called from {@code TermuxActivity.onStop()} and {@code onDestroy()}. Ignores
     * the call when a different Activity instance has already attached, so an
     * old instance's teardown cannot detach its replacement during recreation.
     */
    public void detach(@NonNull TermuxActivity activity) {
        WeakReference<TermuxActivity> current = activityRef.get();
        TermuxActivity attached = current == null ? null : current.get();
        if (attached == null || attached == activity) {
            activityRef.set(new WeakReference<>(null));
        }
    }

    /** Whether a foreground Activity is currently able to execute terminal actions. */
    public boolean isAttached() {
        return currentActivity() != null;
    }

    /** Whether {@code toolName} is a terminal action handled by this dispatcher. */
    public static boolean handles(@Nullable String toolName) {
        if (toolName == null) return false;
        switch (toolName) {
            case TOOL_TERMINAL_STATE:
            case TOOL_PANE_SPLIT_VERTICAL:
            case TOOL_PANE_SPLIT_HORIZONTAL:
            case TOOL_PANE_FOCUS_DIRECTION:
            case TOOL_PANE_RESIZE:
            case TOOL_PANE_KILL_FOCUSED:
            case TOOL_WINDOW_NEW:
            case TOOL_WINDOW_CLOSE:
            case TOOL_WINDOW_NEXT:
            case TOOL_WINDOW_PREVIOUS:
            case TOOL_SESSION_NEW:
            case TOOL_SESSION_NEXT:
            case TOOL_SESSION_PREVIOUS:
            case TOOL_SESSION_CLOSE_CURRENT:
            case TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD:
            case TOOL_TERMINAL_TOGGLE_TOOLBAR:
            case TOOL_TERMINAL_FONT_SIZE_INCREASE:
            case TOOL_TERMINAL_FONT_SIZE_DECREASE:
            case TOOL_TERMINAL_SELECT_URL:
            case TOOL_TERMINAL_SHARE_TRANSCRIPT:
            case TOOL_CLIPBOARD_PASTE:
            case TOOL_WINDOW_SELECT:
            case TOOL_WINDOW_RENAME:
            case TOOL_SESSION_RENAME:
            case TOOL_TERMINAL_RESET:
            case TOOL_APPEARANCE_SET_WALLPAPER:
            case TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT:
            case TOOL_TERMINAL_JUMP_NEXT_PROMPT:
            case TOOL_APPEARANCE_TOGGLE_WALLPAPER:
            case TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL:
            case TOOL_APPEARANCE_GLASS_LAB:
            case TOOL_APP_OPEN_SETTINGS:
            case TOOL_APP_OPEN_LOOK_AND_FEEL:
            case TOOL_APP_OPEN_APPS_BAR:
            case TOOL_APP_COMMAND_PALETTE:
            case TOOL_APP_KEY_INSPECTOR:
            case TOOL_APP_OPEN_DRAWER:
            case TOOL_APP_CLOSE_DRAWER:
            case TOOL_TERMINAL_ACTION_SHEET:
            case TOOL_SESSION_ACTIVATE_BY_INDEX:
            case TOOL_WINDOW_RENAME_PROMPT:
            case TOOL_SESSION_RENAME_PROMPT:
            case TOOL_TERMINAL_SHARE_SELECTED:
            case TOOL_CLIPBOARD_COPY_SELECTED:
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
        TermuxActivity activity = currentActivity();
        final boolean splits = activity != null && activity.isSplitPanesEnabled();
        final boolean session = activity != null && activity.getCurrentSession() != null;
        final boolean selection = activity != null && hasSelectedText(activity);
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

    private static boolean hasSelectedText(@NonNull TermuxActivity activity) {
        com.termux.view.TerminalView view = activity.getTerminalView();
        if (view == null) return false;
        String selected = view.getStoredSelectedText();
        return selected != null && !selected.isEmpty();
    }

    @Nullable
    private TermuxActivity currentActivity() {
        WeakReference<TermuxActivity> ref = activityRef.get();
        TermuxActivity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
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

    @NonNull
    private JSONObject executeOnMainThread(@NonNull String toolName, @NonNull JSONObject arguments) {
        TermuxActivity activity = currentActivity();
        if (activity == null) {
            return error(409, "activity_not_running",
                "The terminal UI is not in the foreground, so '" + toolName + "' cannot run");
        }

        try {
            switch (toolName) {
                case TOOL_TERMINAL_STATE:
                    return buildState(activity);

                case TOOL_PANE_SPLIT_VERTICAL:
                    return doSplit(activity, LinearLayout.HORIZONTAL, "vertical");
                case TOOL_PANE_SPLIT_HORIZONTAL:
                    return doSplit(activity, LinearLayout.VERTICAL, "horizontal");

                case TOOL_PANE_FOCUS_DIRECTION: {
                    Integer keyCode = directionToKeyCode(arguments.optString("direction", ""));
                    if (keyCode == null) return badDirection();
                    return ok().put("handled", activity.focusPaneDirection(keyCode));
                }
                case TOOL_PANE_RESIZE: {
                    Integer keyCode = directionToKeyCode(arguments.optString("direction", ""));
                    if (keyCode == null) return badDirection();
                    if (!activity.isSplitPanesEnabled()) return splitsDisabled();
                    return ok().put("handled", activity.resizeActivePane(keyCode));
                }
                case TOOL_PANE_KILL_FOCUSED:
                    return ok().put("killed", activity.killFocusedPane());

                case TOOL_WINDOW_NEW:
                    if (!activity.isSplitPanesEnabled()) return splitsDisabled();
                    activity.createNewWindow();
                    return ok();
                case TOOL_WINDOW_CLOSE:
                    if (!activity.isSplitPanesEnabled()) return splitsDisabled();
                    activity.closeCurrentWindow();
                    return ok();
                case TOOL_WINDOW_NEXT:
                    activity.switchWindow(true);
                    return ok();
                case TOOL_WINDOW_PREVIOUS:
                    activity.switchWindow(false);
                    return ok();

                case TOOL_SESSION_NEW: {
                    TermuxTerminalSessionActivityClient client = activity.getTermuxTerminalSessionClient();
                    if (client == null) return error(503, "unavailable", "Session client is not ready");
                    String name = arguments.optString("name", "").trim();
                    client.addNewSession(arguments.optBoolean("failsafe", false), name.isEmpty() ? null : name);
                    return ok();
                }
                case TOOL_SESSION_NEXT:
                case TOOL_SESSION_PREVIOUS: {
                    TermuxTerminalSessionActivityClient client = activity.getTermuxTerminalSessionClient();
                    if (client == null) return error(503, "unavailable", "Session client is not ready");
                    client.switchToSession(TOOL_SESSION_NEXT.equals(toolName));
                    return ok();
                }
                case TOOL_SESSION_CLOSE_CURRENT:
                    activity.closeCurrentSession();
                    return ok();

                case TOOL_TERMINAL_TOGGLE_TOOLBAR:
                    activity.toggleTerminalToolbar();
                    return ok();

                case TOOL_TERMINAL_RESET:
                    return activity.resetCurrentSession() ? ok() : noSession(toolName);

                case TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT:
                case TOOL_TERMINAL_JUMP_NEXT_PROMPT: {
                    com.termux.view.TerminalView view = activity.getTerminalView();
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
                    activity.openWallpaperPicker();
                    return ok();
                case TOOL_APPEARANCE_TOGGLE_WALLPAPER:
                    return ok().put("wallpaperEnabled", activity.toggleWallpaperMode());
                case TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL:
                    return ok().put("cursorTrailEnabled", activity.toggleCursorTrail());
                case TOOL_APPEARANCE_GLASS_LAB:
                    activity.openGlassLab();
                    return ok();
                case TOOL_APP_OPEN_SETTINGS:
                    activity.openSettings();
                    return ok();
                case TOOL_APP_OPEN_LOOK_AND_FEEL:
                    activity.openLookAndFeel();
                    return ok();
                case TOOL_APP_OPEN_APPS_BAR:
                    activity.openAppsBar();
                    return ok();

                case TOOL_APP_COMMAND_PALETTE:
                    TerminalCommandPalette.show(activity);
                    return ok();
                case TOOL_APP_KEY_INSPECTOR:
                    return ok().put("keyInspectorOpen", TerminalKeyInspector.toggle(activity));
                case TOOL_APP_OPEN_DRAWER:
                    activity.getDrawer().openDrawer(android.view.Gravity.LEFT);
                    return ok();
                case TOOL_APP_CLOSE_DRAWER:
                    activity.getDrawer().closeDrawers();
                    return ok();
                case TOOL_TERMINAL_ACTION_SHEET:
                    return activity.showTerminalActionSheet() ? ok() : noSession(toolName);

                case TOOL_SESSION_ACTIVATE_BY_INDEX: {
                    TermuxTerminalSessionActivityClient indexClient = activity.getTermuxTerminalSessionClient();
                    if (indexClient == null) return error(503, "unavailable", "Session client is not ready");
                    if (!arguments.has("index")) return error(400, "bad_request", "Missing 'index'");
                    int sessionIndex = arguments.optInt("index", -1);
                    if (sessionIndex < 0 || sessionIndex >= activity.mDrawerSessions.size()) {
                        return error(400, "bad_request", "No session at index " + sessionIndex
                            + "; there are " + activity.mDrawerSessions.size());
                    }
                    indexClient.switchToSession(sessionIndex);
                    return ok().put("index", sessionIndex);
                }

                case TOOL_WINDOW_RENAME_PROMPT:
                    activity.renameCurrentWindowSession();
                    return ok();
                case TOOL_TERMINAL_SHARE_SELECTED:
                case TOOL_CLIPBOARD_COPY_SELECTED: {
                    com.termux.view.TerminalView view = activity.getTerminalView();
                    String selected = view == null ? null : view.getStoredSelectedText();
                    if (selected == null || selected.isEmpty()) {
                        return error(409, "no_selection",
                            "There is no selected text, so '" + toolName + "' cannot run");
                    }
                    if (TOOL_TERMINAL_SHARE_SELECTED.equals(toolName)) {
                        TermuxTerminalViewClient shareClient = activity.getTermuxTerminalViewClient();
                        if (shareClient == null) return error(503, "unavailable", "Terminal view client is not ready");
                        shareClient.shareSelectedText();
                    } else {
                        com.termux.shared.interact.ShareUtils.copyTextToClipboard(activity, selected);
                    }
                    return ok().put("characters", selected.length());
                }

                case TOOL_SESSION_RENAME_PROMPT: {
                    TermuxTerminalSessionActivityClient promptClient = activity.getTermuxTerminalSessionClient();
                    if (promptClient == null) return error(503, "unavailable", "Session client is not ready");
                    if (activity.getCurrentSession() == null) return noSession(toolName);
                    promptClient.renameSession(activity.getCurrentSession());
                    return ok();
                }

                case TOOL_WINDOW_SELECT: {
                    if (!activity.isSplitPanesEnabled()) return splitsDisabled();
                    if (!arguments.has("index")) {
                        return error(400, "bad_request", "Missing 'index'");
                    }
                    int index = arguments.optInt("index", -1);
                    if (!activity.selectWindow(index)) {
                        return error(400, "bad_request", "No window at index " + index
                            + "; the current session has " + activity.getCurrentWindowCount());
                    }
                    return ok().put("index", index);
                }

                case TOOL_WINDOW_RENAME: {
                    if (!activity.isSplitPanesEnabled()) return splitsDisabled();
                    // An explicit empty name clears the label, matching what the
                    // rename dialog does. Only an absent key is an error.
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    if (!activity.renameCurrentWindowSessionTo(arguments.optString("name", ""))) {
                        return error(409, "no_session", "There is no window session to rename");
                    }
                    // Report the stored name: WindowSessionName caps it, so what
                    // was asked for and what was kept can differ.
                    String stored = activity.getCurrentWindowSessionName();
                    return ok().put("name", stored == null ? JSONObject.NULL : stored);
                }

                case TOOL_SESSION_RENAME: {
                    TermuxTerminalSessionActivityClient renameClient = activity.getTermuxTerminalSessionClient();
                    if (renameClient == null) return error(503, "unavailable", "Session client is not ready");
                    if (!arguments.has("name")) return error(400, "bad_request", "Missing 'name'");
                    String sessionName = arguments.optString("name", "").trim();
                    if (!renameClient.renameCurrentSessionTo(sessionName)) return noSession(toolName);
                    return ok().put("name", sessionName.isEmpty() ? JSONObject.NULL : sessionName);
                }

                case TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD:
                case TOOL_TERMINAL_FONT_SIZE_INCREASE:
                case TOOL_TERMINAL_FONT_SIZE_DECREASE:
                case TOOL_TERMINAL_SELECT_URL:
                case TOOL_TERMINAL_SHARE_TRANSCRIPT:
                case TOOL_CLIPBOARD_PASTE: {
                    TermuxTerminalViewClient viewClient = activity.getTermuxTerminalViewClient();
                    if (viewClient == null) return error(503, "unavailable", "Terminal view client is not ready");
                    if (activity.getCurrentSession() == null) return noSession(toolName);
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
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Terminal action '" + toolName + "' failed", t);
            return error(500, "execution_failed", String.valueOf(t.getMessage()));
        }
    }

    @NonNull
    private JSONObject doSplit(@NonNull TermuxActivity activity, int orientation, @NonNull String label) {
        if (!activity.isSplitPanesEnabled()) return splitsDisabled();
        activity.splitCurrentPane(orientation);
        try {
            return ok().put("split", label);
        } catch (JSONException e) {
            return ok();
        }
    }

    /**
     * Read-only snapshot, so a caller can tell what the split/window state is
     * before issuing commands. Uses only public {@link TermuxActivity} API.
     */
    @NonNull
    private JSONObject buildState(@NonNull TermuxActivity activity) {
        try {
            JSONObject state = ok();
            state.put("splitPanesEnabled", activity.isSplitPanesEnabled());
            state.put("drawerSessions", activity.mDrawerSessions.size());
            state.put("hasCurrentSession", activity.getCurrentSession() != null);
            TerminalPaneController controller = activity.getPaneController();
            state.put("visiblePanes", controller == null ? 0 : controller.getVisiblePaneViews().size());
            state.put("windows", activity.getCurrentWindowCount());
            state.put("currentWindow", activity.getCurrentWindowIndex());
            String sessionName = activity.getCurrentWindowSessionName();
            if (sessionName != null) state.put("windowSessionName", sessionName);
            state.put("wallpaperEnabled", activity.isWallpaperModeEnabled());
            state.put("cursorTrailEnabled", activity.isCursorTrailEnabled());
            return state;
        } catch (JSONException e) {
            return error(500, "execution_failed", "Failed to build terminal state");
        }
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

    @NonNull
    private static JSONObject badDirection() {
        return error(400, "bad_request", "Missing or invalid 'direction'; expected left, right, up, or down");
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
