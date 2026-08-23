package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.app.TermuxService;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.terminal.io.BellHandler;
import com.termux.shared.logger.Logger;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The {@link TerminalSessionClient} implementation that may require an {@link Activity} for its
 * interface methods, which it reaches through {@link TerminalHost} rather than through the activity
 * itself.
 */
public class TermuxTerminalSessionActivityClient extends TermuxTerminalSessionClientBase {

    @NonNull private final Context mContext;

    @NonNull private final TerminalHost mHost;

    /**
     * Upper bound on live shells, counted across every session, window, and pane.
     *
     * <p>Upstream Termux allowed 8 because a session was a whole screen. Here a single session can
     * hold several windows, each with several panes, so 8 is roughly three windows of work — a
     * budget a normal split-pane layout exhausts. 32 keeps a bound on runaway shell creation
     * without treating ordinary use as abuse.
     */
    public static final int MAX_SESSIONS = 32;
    private static final long FOREGROUND_REFRESH_DEFER_MS = 120L;
    private static final ExecutorService MATERIAL_COLOR_FILE_EXECUTOR =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "termux-material-color-writer");
            thread.setDaemon(true);
            return thread;
        });

    private SoundPool mBellSoundPool;

    private int mBellSoundId;

    private static final String LOG_TAG = "TermuxTerminalSessionActivityClient";
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private boolean mTerminalScreenUpdatePending;
    /** Sessions with a coalesced screen-update posted but not yet drawn (one entry per pane). */
    private final java.util.Set<TerminalSession> mPendingScreenUpdateSessions = new java.util.HashSet<>();
    private boolean mForegroundRefreshPending;
    private int mLastMaterialTerminalPaletteSignature;
    @NonNull private String mLastFontErrorSummary = "";
    private final Runnable mForegroundTerminalRefreshRunnable;

    public TermuxTerminalSessionActivityClient(@NonNull Context context, @NonNull TerminalHost host) {
        this.mContext = context;
        this.mHost = host;
        this.mForegroundTerminalRefreshRunnable = () -> {
            mForegroundRefreshPending = false;
            if (!mHost.isVisible()) return;
            if (mHost.currentSession() != null) {
                mHost.focusedView().onScreenUpdated();
            }
        };
    }

    /**
     * Should be called when onCreate() is called
     */
    public void onCreate() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }

    /**
     * Should be called when onStart() is called
     */
    public void onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        if (mHost.service() != null) {
            setCurrentSession(getCurrentStoredSessionOrLast());
            termuxSessionListNotifyUpdated();
        }
        if (shouldDeferForegroundScreenRefresh()) {
            scheduleDeferredForegroundRefresh();
        } else {
            mHost.focusedView().onScreenUpdated();
        }
    }

    /**
     * Should be called when onResume() is called
     */
    public void onResume() {
        // Just initialize the mBellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool();
    }

    /**
     * Should be called when onStop() is called
     */
    public void onStop() {
        // Store current session in shared preferences so that it can be restored later in
        // {@link #onStart} if needed.
        setCurrentStoredSession();
        // Release mBellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool();
        mTerminalScreenUpdatePending = false;
        mPendingScreenUpdateSessions.clear();
        mForegroundRefreshPending = false;
        mUiHandler.removeCallbacks(mForegroundTerminalRefreshRunnable);
    }

    public void onImeVisibilityChanged(boolean visible) {
        if (visible && mForegroundRefreshPending) {
            mUiHandler.removeCallbacks(mForegroundTerminalRefreshRunnable);
            mForegroundTerminalRefreshRunnable.run();
        }
    }

    public void onReloadActivityStyling() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (!mHost.isVisible())
            return;
        // Every screen update is output activity — this is tmux's monitor-activity. Noted above the
        // early return below: windows other than the active one have no TerminalView, so their
        // activity would otherwise never register.
        mHost.noteShellActivity(changedSession);
        // Split-pane: redraw whichever pane is showing the changed session (may be the
        // non-active pane). Coalesce per-session so two live panes never drop each other's frames.
        if (mHost.viewForSession(changedSession) == null)
            return;
        if (!mPendingScreenUpdateSessions.add(changedSession))
            return;
        mTerminalScreenUpdatePending = true;
        Runnable redraw = () -> {
            mPendingScreenUpdateSessions.remove(changedSession);
            mTerminalScreenUpdatePending = !mPendingScreenUpdateSessions.isEmpty();
            if (!mHost.isVisible())
                return;
            com.termux.view.TerminalView view = mHost.viewForSession(changedSession);
            if (view != null)
                view.onScreenUpdated();
        };
        // Under a flood of output the main thread is mostly *parsing* bytes, and where this redraw is
        // posted decides how it interleaves with that parsing. Measured over four configurations:
        //
        //   - With the status clock animating, the frame clock is already ticking, so a plain post
        //     yields many thin frames (median 5ms, 1.9% janky) — the best of the four.
        //   - In lazy mode nothing else animates, and a plain post lands behind the queued parsing:
        //     the screen refreshed ~15 times a second in 15ms frames with 47ms gaps, 11% janky.
        //     Posting to the frame clock instead interleaves redraw with parsing, which brings that
        //     to 2.7% janky with a quarter of the slow-UI-thread events.
        //   - Using the frame clock while the clock animates is slightly worse than the plain post
        //     (median 7ms, 3% janky): it competes with an already-ticking frame source.
        //
        // So: the frame clock exactly when nothing else is driving frames.
        boolean pumpFrames = mHost.preferences() != null
            && mHost.preferences().isLazyModeEnabled();
        if (pumpFrames) {
            com.termux.view.TerminalView pendingView =
                mHost.viewForSession(changedSession);
            pendingView.postOnAnimation(redraw);
        } else {
            mUiHandler.post(redraw);
        }
    }

    private boolean shouldDeferForegroundScreenRefresh() {
        return false;
    }

    private void scheduleDeferredForegroundRefresh() {
        mForegroundRefreshPending = true;
        mUiHandler.removeCallbacks(mForegroundTerminalRefreshRunnable);
        mUiHandler.postDelayed(mForegroundTerminalRefreshRunnable, FOREGROUND_REFRESH_DEFER_MS);
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (!mHost.isVisible())
            return;
        // Deliberately no corner notice here. A title changes whenever a command starts, finishes, or
        // reports progress, which made the notice fire several times a second for one background job.
        // A window of this session says so on its own pill; another session's job gets a standing row
        // in the corner stack. Neither needs a transient copy of the same news.
        mHost.syncBackgroundProcessStack();
        termuxSessionListNotifyUpdated();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxService service = mHost.service();
        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mHost.finishActivityIfNotFinishing();
            return;
        }
        // Split panes / windows: route the finished shell through the pane controller first.
        com.termux.app.terminal.TerminalPaneController panes = mHost.paneController();
        if (panes != null) {
            com.termux.app.terminal.TerminalPaneController.Window window = panes.windowOf(finishedSession);
            int result = panes.onSessionFinished(finishedSession);
            if (result == com.termux.app.terminal.TerminalPaneController.FINISHED_PANE) {
                // A pane with siblings closed; its window lives on. Kill the shell, refresh drawer.
                service.killTermuxSession(finishedSession);
                mHost.rebuildDrawerSessions();
                termuxSessionListNotifyUpdated();
                return;
            }
            if (result == com.termux.app.terminal.TerminalPaneController.FINISHED_WINDOW) {
                // The window's last pane closed; drop the window from its session (and the session
                // if that was its last window), then switch to whatever remains.
                service.killTermuxSession(finishedSession);
                if (window != null) mHost.onWindowEmptied(window);
                termuxSessionListNotifyUpdated();
                return;
            }
            // FINISHED_UNKNOWN: shell not tracked by the controller; fall through to classic close.
        }
        int index = service.getIndexOfSession(finishedSession);
        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        boolean isPluginExecutionCommandWithPendingResult = false;
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult = termuxSession.getExecutionCommand().isPluginExecutionCommandWithPendingResult();
            if (isPluginExecutionCommandWithPendingResult)
                Logger.logVerbose(LOG_TAG, "The \"" + finishedSession.mSessionName + "\" session will be force finished automatically since result in pending.");
        }
        mHost.clearShellAttention(finishedSession.getPid());
        if (mHost.isVisible() && finishedSession != mHost.currentSession()) {
            // Show indicator for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                mHost.showSessionSwitchIndicator(toToastTitle(finishedSession) + " - exited");
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // On Android TV devices we need to use older behaviour because we may
            // not be able to have multiple launcher icons.
            if (service.getTermuxSessionsSize() > 1 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        } else {
            // Once we have a separate launcher icon for the failsafe session, it
            // should be safe to auto-close session on exit code '0' or '130'.
            if (finishedSession.getExitStatus() == 0 || finishedSession.getExitStatus() == 130 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        }
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mHost.isVisible())
            return;
        ShareUtils.copyTextToClipboard(mContext, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mHost.isVisible())
            return;
        String text = ShareUtils.getTextStringFromClipboardIfSet(mContext, true);
        if (text != null)
            mHost.focusedView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        // Marked before the visibility and behaviour gates: the pill is not a sound, and a bell that
        // rang while the launcher was in the background is exactly the one the user needs to find.
        mHost.noteShellAttention(session);
        if (!mHost.isVisible())
            return;
        raiseAttentionNotice(session);
        switch(mHost.properties().getBellBehaviour()) {
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE:
                BellHandler.getInstance(mContext).doBell();
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP:
                loadBellSoundPool();
                if (mBellSoundPool != null)
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE:
                // Ignore the bell character.
                break;
        }
    }

    /**
     * A bell from a shell the user is not looking at gets a notice they can act on: it is drawn in
     * the attention accent, and tapping it goes to that pane or window.
     *
     * <p>Only for shells that are somewhere else. A bell from the pane already on screen needs no
     * signpost — the user is looking straight at it — and a notice for it would fire on every
     * completion beep of whatever they are running.
     */
    private void raiseAttentionNotice(@NonNull TerminalSession session) {
        if (session == mHost.currentSession())
            return;
        String title = toToastTitle(session);
        if (title == null || title.isEmpty())
            return;
        AppNotice.shell(mContext,
            mContext.getString(R.string.notice_shell_wants_attention, title),
            null, "\uf0f3" /* nf-fa-bell */, true, () -> setCurrentSession(session));
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mHost.currentSession() == changedSession)
            updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        // Do not start cursor blinking thread if activity is not visible
        if (enabled && !mHost.isVisible()) {
            Logger.logVerbose(LOG_TAG, "Ignoring call to start cursor blinking since activity is not visible");
            return;
        }
        // If cursor is to enabled now, then start cursor blinking if blinking is enabled
        // otherwise stop cursor blinking
        mHost.focusedView().setTerminalCursorBlinkerState(enabled, false);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxService service = mHost.service();
        if (service == null)
            return;
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }

    /**
     * Should be called when onResetTerminalSession() is called
     */
    public void onResetTerminalSession() {
        // Ensure blinker starts again after reset if cursor blinking was disabled before reset like
        // with "tput civis" which would have called onTerminalCursorStateChange()
        mHost.focusedView().setTerminalCursorBlinkerState(true, true);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return mHost.properties().getTerminalCursorStyle();
    }

    /**
     * Load mBellSoundPool
     */
    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build();
            try {
                mBellSoundId = mBellSoundPool.load(mContext, com.termux.shared.R.raw.bell, 1);
            } catch (Exception e){
                // Catch java.lang.RuntimeException: Unable to resume activity {com.termux/com.termux.app.TermuxActivity}: android.content.res.Resources$NotFoundException: File res/raw/bell.ogg from drawable resource ID
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    /**
     * Release mBellSoundPool resources
     */
    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }

    /**
     * Try switching to session.
     */
    public void setCurrentSession(TerminalSession session) {
        if (session == null)
            return;
        // Which way the session list was walked, for the vertical arrival: sessions move on the
        // other axis from windows, so the animation itself says which switch just happened.
        // Session travel is a session-list boundary crossing, judged on session numbers, NOT on
        // drawer indices: the drawer keys off the focused pane's shell, which is absent from the
        // tab list whenever a secondary split pane holds focus — every switch made from such a
        // pane used to silently skip its animation.
        int fromNumber = mHost.sessions().currentNumber();
        int toNumber = mHost.sessions().numberOf(session);
        boolean travelled = fromNumber > 0 && toNumber > 0 && fromNumber != toNumber;
        // A brand-new shell is appended to the session list, so — niri's language — it arrives
        // the same way "next session" does: the old session is carried off and the new one
        // scrolls in from beyond the end of the list.
        boolean created = fromNumber > 0 && toNumber <= 0;
        // Captured before the pane tree is swapped so the arrival has an outgoing half to slide
        // away.
        if (travelled || created)
            mHost.captureTerminalDeparture();
        // Route through the split-pane model: shows the session's tab (primary + optional
        // secondary pane) and focuses the pane displaying this session.
        if (mHost.activateSessionInPanes(session)) {
            if (travelled)
                mHost.animateSessionArrival(toNumber >= fromNumber ? 1 : -1);
            else if (created)
                mHost.animateSessionLifecycleArrival(1);
            // No "[1] fish in ~" chip here any more: the action hint already narrates the switch,
            // and two stacked notices for one keypress read as noise. The indicator view stays for
            // notices that carry real news — an exited session, a refused split.
        }
        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.
        checkAndScrollToSession(session);
        updateBackgroundColor();
    }

    public void switchToSession(boolean forward) {
        // Cycle through drawer-visible sessions (tabs), skipping secondary pane shells.
        TerminalHost.Sessions tabs = mHost.sessions();
        int size = tabs.count();
        if (size == 0)
            return;
        TerminalSession reference = tabs.currentTabPrimary();
        if (reference == null)
            reference = mHost.currentSession();
        int index = tabs.indexOf(reference);
        if (index < 0)
            index = 0;
        if (forward) {
            if (++index >= size)
                index = 0;
        } else {
            if (--index < 0)
                index = size - 1;
        }
        setCurrentSession(tabs.at(index));
    }

    public void switchToSession(int index) {
        // Index into the drawer-visible (tab) list.
        TerminalHost.Sessions tabs = mHost.sessions();
        if (index < 0 || index >= tabs.count())
            return;
        setCurrentSession(tabs.at(index));
    }

    /**
     * Prompts for a new name for the focused pane, in the anchored editor.
     *
     * <p>Deliberately not the drawer's row rename: with split panes on a drawer row <i>is</i> a
     * session, so that path renames the session. This one always names the shell, which is what
     * {@code pane.rename_prompt} means in both modes. Returns false when there is no focused pane.
     */
    public boolean promptCurrentPaneRename() {
        if (mHost.currentSession() == null) return false;
        return mHost.beginTerminalRename(TerminalRenameTarget.PANE);
    }

    /**
     * Legacy dialog for the pane name, used only when there is no in-app keyboard for the anchored
     * editor to be typed with.
     */
    @SuppressLint("InflateParams")
    public void promptCurrentPaneRenameDialog() {
        final TerminalSession session = mHost.currentSession();
        if (session == null) return;
        mHost.showTextInputDialog(R.string.title_rename_pane, session.mSessionName,
            R.string.action_rename_session_confirm, text -> {
                renameSession(session, text);
                termuxSessionListNotifyUpdated();
            });
    }

    /**
     * Renames the focused pane without prompting.
     *
     * <p>Seam for the {@code pane.rename} registry action and for a naming backend. An empty name
     * restores the unnamed default, mirroring what an emptied editor does and keeping this
     * symmetric with {@code window.rename} and {@code session.rename}.
     */
    public boolean renameCurrentPaneTo(@Nullable String name) {
        TerminalSession session = mHost.currentSession();
        if (session == null || name == null) return false;
        String trimmed = TerminalNamePolicy.normalizePane(name) == null
            ? "" : TerminalNamePolicy.normalizePane(name);
        renameSession(session, trimmed.isEmpty() ? null : trimmed);
        termuxSessionListNotifyUpdated();
        return true;
    }

    private void renameSession(TerminalSession sessionToRename, String text) {
        if (sessionToRename == null)
            return;
        sessionToRename.mSessionName = text;
        TermuxService service = mHost.service();
        if (service != null) {
            TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename);
            if (termuxSession != null)
                termuxSession.getExecutionCommand().shellName = text;
        }
    }

    public void addNewSession(boolean isFailSafe, String sessionName) {
        TerminalSession currentSession = mHost.currentSession();
        String workingDirectory = currentSession == null
            ? mHost.properties().getDefaultWorkingDirectory()
            : currentSession.getCwd();
        addNewSessionAtWorkingDirectory(workingDirectory, isFailSafe, sessionName);
    }

    /** Create and select a fresh shell at an explicitly chosen CWD. Used by session cloning. */
    public boolean addNewSessionAtWorkingDirectory(@Nullable String workingDirectory,
                                                   boolean isFailSafe,
                                                   @Nullable String sessionName) {
        TermuxService service = mHost.service();
        if (service == null)
            return false;
        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            // A modal with an OK button interrupts a keyboard-driven flow for something the user
            // can do nothing about mid-dialog. The window and pane paths report this on the notice
            // chip; match them. This branch returns before createShellForCwd, so the same event
            // never produces two presentations.
            mHost.showSessionSwitchIndicator(
                mContext.getString(R.string.title_max_terminals_reached) + " — "
                    + mContext.getString(R.string.msg_max_terminals_reached));
            return false;
        } else {
            if (workingDirectory == null) {
                workingDirectory = mHost.properties().getDefaultWorkingDirectory();
            }
            TermuxSession newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName);
            if (newTermuxSession == null)
                return false;
            TerminalSession newTerminalSession = newTermuxSession.getTerminalSession();
            setCurrentSession(newTerminalSession);
            mHost.closeDrawers();
            return true;
        }
    }

    public void setCurrentStoredSession() {
        TerminalSession currentSession = mHost.currentSession();
        if (currentSession != null)
            mHost.preferences().setCurrentSession(currentSession.mHandle);
        else
            mHost.preferences().setCurrentSession(null);
    }

    /**
     * The current session as stored or the last one if that does not exist.
     */
    public TerminalSession getCurrentStoredSessionOrLast() {
        TerminalSession stored = getCurrentStoredSession();
        if (stored != null) {
            // If a stored session is in the list of currently running sessions, then return it
            return stored;
        } else {
            // Else return the last session currently running
            TermuxService service = mHost.service();
            if (service == null)
                return null;
            TermuxSession termuxSession = service.getLastTermuxSession();
            if (termuxSession != null)
                return termuxSession.getTerminalSession();
            else
                return null;
        }
    }

    private TerminalSession getCurrentStoredSession() {
        String sessionHandle = mHost.preferences().getCurrentSession();
        // If no session is stored in shared preferences
        if (sessionHandle == null)
            return null;
        // Check if the session handle found matches one of the currently running sessions
        TermuxService service = mHost.service();
        if (service == null)
            return null;
        return service.getTerminalSessionForHandle(sessionHandle);
    }

    public void removeFinishedSession(TerminalSession finishedSession) {
        // Return pressed with finished session - remove it.
        TermuxService service = mHost.service();
        if (service == null)
            return;
        int index = service.killTermuxSession(finishedSession);
        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            mHost.finishActivityIfNotFinishing();
        } else {
            if (index >= size) {
                index = size - 1;
            }
            TermuxSession termuxSession = service.getTermuxSession(index);
            if (termuxSession != null)
                setCurrentSession(termuxSession.getTerminalSession());
        }
    }

    public void termuxSessionListNotifyUpdated() {
        mHost.notifySessionListUpdated();
    }

    public void checkAndScrollToSession(TerminalSession session) {
        if (!mHost.isVisible())
            return;
        TermuxService service = mHost.service();
        if (service == null)
            return;
        // Use the drawer-visible index (secondary panes are filtered out).
        final int indexOfSession = mHost.sessions().indexOf(session);
        if (indexOfSession < 0)
            return;
        final ListView termuxSessionsListView = mHost.sessions().listView();
        if (termuxSessionsListView == null)
            return;
        termuxSessionsListView.setItemChecked(indexOfSession, true);
        // Delay is necessary otherwise sometimes scroll to newly added session does not happen
        termuxSessionsListView.postDelayed(() -> termuxSessionsListView.smoothScrollToPosition(indexOfSession), 1000);
    }

    String toToastTitle(TerminalSession session) {
        TermuxService service = mHost.service();
        if (service == null)
            return null;
        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0)
            return null;
        // Number by the launcher's tmux-style session, not by raw shell count: every pane and
        // window is its own service shell, so the service index kept flashing "[3]" for what the
        // user sees as their second session.
        int sessionNumber = mHost.sessions().numberOf(session);
        if (sessionNumber < 1) sessionNumber = indexOfSession + 1;
        StringBuilder toastTitle = new StringBuilder("[" + sessionNumber + "]");
        String sessionName = mHost.sessions().nameOf(session);
        if (TextUtils.isEmpty(sessionName)) sessionName = session.mSessionName;
        if (!TextUtils.isEmpty(sessionName)) {
            toastTitle.append(" ").append(sessionName);
        }
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(TextUtils.isEmpty(sessionName) ? " " : "\n");
            toastTitle.append(title);
        }
        return toastTitle.toString();
    }

    public void checkForFontAndColors() {
        applyTerminalColors();
        applyTerminalFonts();
    }

    /**
     * Rebuild the terminal palette and hand it to every session.
     *
     * <p>Split from the font half because a wallpaper change has no business re-reading font config
     * from disk and rebuilding typefaces, which is what the combined path did on every refresh.
     */
    public void applyTerminalColors() {
        try {
            boolean dynamic = mHost.preferences() != null
                && mHost.preferences().isTerminalDynamicColorsEnabled();
            final Properties props;
            if (dynamic) {
                TerminalContrastLevel level = mHost.preferences().getTerminalContrastLevel();
                props = MaterialTerminalColorScheme.create(mContext, level);
                mLastMaterialTerminalPaletteSignature =
                    MaterialTerminalColorScheme.signature(mContext, level);
                // Built here, on the main thread, and handed over as finished values: the writer thread
                // must not touch the theme or resources, and this way the files describe the same
                // palette the terminal just took.
                final Properties exported =
                    MaterialTerminalColorScheme.createMaterialRoleProperties(mContext, props, level);
                MATERIAL_COLOR_FILE_EXECUTOR.execute(() -> {
                    try {
                        MaterialTerminalColorScheme.writeMaterialColorFiles(exported);
                    } catch (Exception e) {
                        Logger.logStackTraceWithMessage(LOG_TAG,
                            "Error writing material color files", e);
                    }
                });
            } else {
                props = new Properties();
                mLastMaterialTerminalPaletteSignature = 0;
                File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
                if (colorsFile.isFile()) {
                    try (InputStream in = new FileInputStream(colorsFile)) {
                        props.load(in);
                    }
                    exportSchemeColorFiles(props);
                }
            }
            TerminalColors.COLOR_SCHEME.updateWith(colorKeysOnly(props));
            resetAllSessionColors();
            updateBackgroundColor();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in applyTerminalColors()", e);
        }
    }

    /**
     * Export the scheme-derived roles to {@code material-colors.properties} / {@code .sh}.
     *
     * <p>The wallpaper path already writes those files, and the bundled fish, tmux and Neovim
     * configs read them — so a scheme that only reached the terminal left every one of those
     * consumers describing a palette the user had just replaced. Same file, same key names, one
     * source of truth whichever way the colours were chosen.
     */
    private void exportSchemeColorFiles(@NonNull Properties terminalProps) {
        // Unlike the wallpaper export, every input here is a file and some arithmetic — no theme
        // attributes, no resources — so the whole thing including the derivation runs on the writer
        // thread rather than costing the activity two stats and a palette build during onCreate.
        final Properties snapshot = new Properties();
        snapshot.putAll(terminalProps);
        MATERIAL_COLOR_FILE_EXECUTOR.execute(() -> {
            try {
                LinkedHashMap<String, Integer> tokens = LauncherSchemeTheme.tokens();
                if (tokens == null) return;
                Properties exported = LauncherSchemeTheme.exportProperties(tokens);
                for (String key : snapshot.stringPropertyNames()) {
                    exported.setProperty("terminal_" + key, snapshot.getProperty(key));
                }
                MaterialTerminalColorScheme.writeMaterialColorFiles(exported);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error writing scheme color files", e);
            }
        });
    }

    /**
     * The colour entries of {@code props}, with everything else dropped and logged.
     *
     * <p>{@code TerminalColorScheme.updateWith()} throws on the first key it does not recognise, and it
     * throws while iterating an unordered map — so one stray line in a hand-written
     * {@code colors.properties} does not merely get ignored, it leaves the palette partially applied
     * and skips the session reset and the background update that follow. Filtering here keeps a bad
     * line cosmetic.
     */
    @VisibleForTesting
    static Properties colorKeysOnly(@NonNull Properties props) {
        Properties filtered = new Properties();
        for (String key : props.stringPropertyNames()) {
            if (isTerminalColorKey(key)) {
                filtered.setProperty(key, props.getProperty(key));
            } else {
                Logger.logWarn(LOG_TAG, "Ignoring non-colour terminal palette property '" + key + "'");
            }
        }
        return filtered;
    }

    private static boolean isTerminalColorKey(@NonNull String key) {
        switch (key) {
            case "foreground":
            case "background":
            case "cursor":
                return true;
            default:
                break;
        }
        if (!key.startsWith("color")) return false;
        String index = key.substring("color".length());
        if (index.isEmpty()) return false;
        for (int i = 0; i < index.length(); i++) {
            if (!Character.isDigit(index.charAt(i))) return false;
        }
        return true;
    }

    /** Load the configured faces and apply them to every pane that has a renderer. */
    public void applyTerminalFonts() {
        try {
            TerminalFontLoader.Faces faces = TerminalFontLoader.load(TerminalFontConfig.load());
            reportFontErrors(faces.errors);
            for (com.termux.view.TerminalView v : mHost.paneViews()) {
                if (v.isFontInitialized())
                    v.setTypeface(faces.regular, faces.bold, faces.italic, faces.boldItalic,
                        faces.symbolMaps, faces.ligaturePolicy, faces.fontFeatures,
                        faces.fontVariations, faces.fontMetricsAdjustments,
                        faces.boxDrawingPolicy, fallbackTypefaces(faces),
                        faces.symbolExpansion);
            }
            mHost.requestFlushDockGeometryUpdate();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in applyTerminalFonts()", e);
        }
    }

    /** Apply the configured terminal font (nerd-font typeface) to a single pane view. */
    public void applyFontToView(com.termux.view.TerminalView view) {
        if (view == null || !view.isFontInitialized())
            return;
        try {
            TerminalFontLoader.Faces faces = TerminalFontLoader.load(TerminalFontConfig.load());
            for (String error : faces.errors) Logger.logError(LOG_TAG, "Font config: " + error);
            view.setTypeface(faces.regular, faces.bold, faces.italic, faces.boldItalic,
                faces.symbolMaps, faces.ligaturePolicy, faces.fontFeatures,
                faces.fontVariations, faces.fontMetricsAdjustments,
                faces.boxDrawingPolicy, fallbackTypefaces(faces), faces.symbolExpansion);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in applyFontToView()", e);
        }
    }

    /** The {@code fallback_font} chain in config order, as the renderer's array form. */
    private static android.graphics.Typeface[] fallbackTypefaces(
        @NonNull TerminalFontLoader.Faces faces) {
        return faces.fallbackFonts.toArray(new android.graphics.Typeface[0]);
    }

    private void reportFontErrors(@NonNull java.util.List<String> errors) {
        String summary = android.text.TextUtils.join("\n", errors);
        if (summary.equals(mLastFontErrorSummary)) return;
        mLastFontErrorSummary = summary;
        if (errors.isEmpty()) return;
        for (String error : errors) Logger.logError(LOG_TAG, "Font config: " + error);
        mHost.showToast(mContext.getResources().getQuantityString(
            R.plurals.terminal_font_config_errors, errors.size(), errors.size()), true);
    }

    /**
     * Rebuild the palette only if the Material roles or the contrast level actually moved. This is the
     * path for resume, configuration changes and wallpaper-colour callbacks: they fire whether or not
     * anything changed, and the work behind them is a full HCT palette build, a recolour and repaint of
     * every session, and two file writes that open shells watch.
     */
    public void refreshMaterialTerminalColorsIfNeeded() {
        if (mHost.preferences() == null
            || !mHost.preferences().isTerminalDynamicColorsEnabled()) {
            return;
        }
        int signature = MaterialTerminalColorScheme.signature(mContext,
            mHost.preferences().getTerminalContrastLevel());
        if (signature == mLastMaterialTerminalPaletteSignature) return;
        applyTerminalColors();
    }

    private void resetAllSessionColors() {
        TermuxService service = mHost.service();
        if (service != null) {
            for (TermuxSession termuxSession : service.getTermuxSessions()) {
                TerminalSession session = termuxSession.getTerminalSession();
                if (session != null && session.getEmulator() != null) {
                    session.getEmulator().mColors.reset();
                }
            }
            return;
        }
        TerminalSession session = mHost.currentSession();
        if (session != null && session.getEmulator() != null) {
            session.getEmulator().mColors.reset();
        }
    }

    public void updateBackgroundColor() {
        mHost.updateWindowBackgroundForCurrentSession();
    }
}
