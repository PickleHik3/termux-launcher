package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.TerminalPaneController;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps each Terminal widget and its shell together: the shell's pane goes into the widget while
 * it is on screen (a hosted Home float, {@link TerminalPaneController#attachHostedFloat}), comes
 * back to the same running shell after the activity is recreated, starts afresh in the directory
 * it was last in after the process died, ends when the widget is removed (asking first while
 * something runs, as closing a window does), and says so on the card when the shell ends on its
 * own. The rules are {@link TerminalWidgetPolicy}'s; this reads the world and applies them.
 *
 * <p>One per activity. A widget's shell lives in the terminal service and its handle in the
 * widget's record, so neither depends on this object surviving.</p>
 */
public final class TerminalWidgetCoordinator {

    /** What the coordinator needs from the activity. */
    public interface Host {
        /** The pane controller, once there is one. */
        @Nullable TerminalPaneController panes();
        /** Whether shells may be found and started: the service bound and the start settled. */
        boolean shellsReady();
        /** The running shell with this handle, or null. */
        @Nullable TerminalSession liveShell(@NonNull String handle);
        /** A new login shell in {@code cwd} (null: the default directory), or null when none can start. */
        @Nullable TerminalSession createShell(@Nullable String cwd);
        /** Ends a shell for good. */
        void killShell(@NonNull TerminalSession shell);
        /** The settings and state of Terminal widget {@code widgetId}, or null when Home has no such widget. */
        @Nullable Bundle widgetConfig(int widgetId);
        /** Stores a Terminal widget's settings and state, without drawing Home again. */
        void writeWidgetConfig(int widgetId, @NonNull Bundle config);
        /** Every Terminal widget on Home, by id, with its settings and state. */
        @NonNull Map<Integer, Bundle> terminalWidgets();
        /**
         * The float layer is on Home: it reports a press beside the floats (focus back to Home)
         * and the keyboard's top (the lift over it).
         */
        void ensureFloatRoom();
        /** Whether this shell is running something now; null when that cannot be read. */
        @Nullable Boolean isShellIdle(@NonNull TerminalSession shell);
    }

    @NonNull private final Host host;
    /** The widgets on screen now, by id. */
    private final Map<Integer, TerminalWidgetView> mOnScreen = new HashMap<>();

    public TerminalWidgetCoordinator(@NonNull Host host) {
        this.host = host;
    }

    /** Widget {@code widgetId} came on screen as {@code widget}: its shell is shown there. */
    public void attach(int widgetId, @NonNull TerminalWidgetView widget) {
        mOnScreen.put(widgetId, widget);
        bind(widgetId);
    }

    /** {@code widget} left the screen. Its shell keeps running, drawn nowhere. */
    public void detach(int widgetId, @NonNull TerminalWidgetView widget) {
        if (mOnScreen.get(widgetId) == widget) mOnScreen.remove(widgetId);
        TerminalPaneController panes = host.panes();
        TerminalPaneController.Window window = panes == null ? null : panes.hostedFloatFor(widgetId);
        if (window != null) panes.detachHostedFloat(window, widget);
    }

    /**
     * The shells are ready ({@link Host#shellsReady}): widgets that came on screen before are
     * given their shells now.
     */
    public void onShellsReady() {
        for (Integer id : new ArrayList<>(mOnScreen.keySet())) bind(id);
    }

    /** The ended card of widget {@code widgetId} was tapped: a new shell for it. */
    public void restart(int widgetId) {
        Bundle config = host.widgetConfig(widgetId);
        if (config == null) return;
        if (TerminalWidgetPolicy.isEnded(config)) {
            config.remove(TerminalWidgetPolicy.KEY_ENDED);
            host.writeWidgetConfig(widgetId, config);
        }
        bind(widgetId);
    }

    /** Widget {@code widgetId}'s shell ended on its own: the card says so until it is tapped. */
    public void onShellEnded(int widgetId) {
        Bundle config = host.widgetConfig(widgetId);
        if (config != null) {
            config.remove(TerminalWidgetPolicy.KEY_SHELL);
            config.putString(TerminalWidgetPolicy.KEY_ENDED, TerminalWidgetPolicy.ENDED);
            host.writeWidgetConfig(widgetId, config);
        }
        TerminalWidgetView widget = mOnScreen.get(widgetId);
        if (widget != null) widget.showEnded(true);
    }

    /**
     * Home was drawn again: the shell of every widget no longer on Home ends, whichever way it
     * went (removed, its page removed, an edit discarded).
     */
    public void reconcile() {
        TerminalPaneController panes = host.panes();
        if (panes == null) return;
        List<Integer> hosting = new ArrayList<>();
        for (TerminalPaneController.Window window : panes.hostedFloats())
            hosting.add(window.hostedWidgetId());
        if (hosting.isEmpty()) return;
        Map<Integer, Bundle> live = host.terminalWidgets();
        for (Integer id : TerminalWidgetPolicy.gone(hosting, live.keySet())) {
            mOnScreen.remove(id);
            TerminalPaneController.Window window = panes.hostedFloatFor(id);
            if (window == null) continue;
            for (TerminalSession shell : panes.removeWindow(window)) host.killShell(shell);
        }
    }

    /**
     * Removing widget {@code widgetId} from Home: done at once, or after the user agrees while its
     * shell is running something. Any other widget is removed at once.
     */
    public void confirmRemoval(@NonNull Context context, int widgetId, @NonNull Runnable remove) {
        TerminalPaneController panes = host.panes();
        TerminalPaneController.Window window = panes == null ? null : panes.hostedFloatFor(widgetId);
        TerminalSession shell = window == null ? null : panes.windowActiveSession(window);
        if (shell == null || !TerminalWidgetPolicy.removalAsks(host.isShellIdle(shell))) {
            remove.run();
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.terminal_widget_remove_title)
            .setMessage(R.string.terminal_widget_remove_running_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.terminal_widget_remove, (dialog, which) -> remove.run())
            .show();
    }

    /** Widget edit mode came up: a widget's terminal gives the keyboard back while Home is edited. */
    public void onWidgetEditing(boolean editing) {
        TerminalPaneController panes = host.panes();
        if (!editing || panes == null) return;
        TerminalPaneController.Window focused = panes.focusedHomeFloat();
        if (focused != null && focused.isHostedFloat()) panes.releaseHomeFloatFocus();
    }

    /**
     * Writes each widget's shell's directory into its record, so a shell started after the
     * process died starts where the last one was. Only what changed is written.
     */
    public void rememberDirectories() {
        TerminalPaneController panes = host.panes();
        if (panes == null) return;
        for (TerminalPaneController.Window window : panes.hostedFloats()) {
            TerminalSession shell = panes.windowActiveSession(window);
            String cwd = shell == null ? null : shell.getCwd();
            if (cwd == null || cwd.isEmpty()) continue;
            Bundle config = host.widgetConfig(window.hostedWidgetId());
            if (config == null || cwd.equals(config.getString(TerminalWidgetPolicy.KEY_CWD))) continue;
            config.putString(TerminalWidgetPolicy.KEY_CWD, cwd);
            host.writeWidgetConfig(window.hostedWidgetId(), config);
        }
    }

    /**
     * Whether {@code shell} is a Terminal widget's: shown by one, or named by one's record while
     * that widget is off screen. Such a shell is never adopted as a window of the Workspace.
     */
    public boolean isWidgetShell(@Nullable TerminalSession shell) {
        if (shell == null) return false;
        TerminalPaneController panes = host.panes();
        boolean hosted = panes != null && panes.hostedFloatOf(shell) != null;
        if (hosted) return true;
        return TerminalWidgetPolicy.isWidgetShell(false, shell.mHandle,
            TerminalWidgetPolicy.claimedHandles(host.terminalWidgets()));
    }

    /** Puts widget {@code widgetId}'s shell in it, starting or finding the shell first. */
    private void bind(int widgetId) {
        TerminalWidgetView widget = mOnScreen.get(widgetId);
        TerminalPaneController panes = host.panes();
        if (widget == null || panes == null) return;
        TerminalPaneController.Window window = panes.hostedFloatFor(widgetId);
        if (window == null) {
            Bundle config = host.widgetConfig(widgetId);
            if (config == null) return;
            boolean ready = host.shellsReady();
            String handle = config.getString(TerminalWidgetPolicy.KEY_SHELL);
            TerminalSession saved = ready && handle != null ? host.liveShell(handle) : null;
            TerminalWidgetPolicy.Start start = TerminalWidgetPolicy.start(ready,
                TerminalWidgetPolicy.isEnded(config), saved != null,
                saved != null && panes.windowOf(saved) != null);
            TerminalSession shell;
            switch (start) {
                case WAIT:
                    widget.showEnded(false);
                    return;
                case ENDED:
                    widget.showEnded(true);
                    return;
                case REATTACH:
                    shell = saved;
                    break;
                default: {
                    String cwd = config.getString(TerminalWidgetPolicy.KEY_CWD);
                    shell = host.createShell(TerminalWidgetPolicy.startCwd(cwd,
                        cwd != null && new File(cwd).isDirectory()));
                    if (shell == null) {
                        // No shell could start (the terminal limit): the card offers to try again.
                        widget.showEnded(true);
                        return;
                    }
                    config.putString(TerminalWidgetPolicy.KEY_SHELL, shell.mHandle);
                    String started = shell.getCwd();
                    if (started != null && !started.isEmpty())
                        config.putString(TerminalWidgetPolicy.KEY_CWD, started);
                    host.writeWidgetConfig(widgetId, config);
                    break;
                }
            }
            window = panes.openHostedFloat(shell, widgetId);
        }
        host.ensureFloatRoom();
        widget.showEnded(false);
        panes.attachHostedFloat(window, widget);
    }
}
