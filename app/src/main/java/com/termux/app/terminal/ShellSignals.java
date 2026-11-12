package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.KittyNotification;
import com.termux.terminal.ProgramStatus;
import com.termux.terminal.ProgramStatusStore;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

/**
 * The one path for each thing a program says to the user around the terminal rather than in it:
 * a notification in the phone's shade, the progress ring on a window chip, and the clipboard.
 *
 * <p>Two kinds of caller arrive here. A program with a terminal writes an escape ({@code OSC 99},
 * {@code OSC 9;4}, {@code OSC 52}) and the session client's callbacks land here. A process with
 * no terminal at all — a coding agent's tool runner, parented to init with stdin on
 * {@code /dev/null} — calls the local API instead, and {@link TerminalActionDispatcher} lands
 * here too. Whichever way in, the same method runs, so a notification, a ring or a clipboard
 * write is indistinguishable afterwards, and the rules (which occasions show, when the clipboard
 * may be read) live in exactly one place.
 *
 * <p>Everything here is meant to be called on the main thread, which is where both the
 * emulator's callbacks and the dispatcher's actions already run.
 */
public final class ShellSignals {

    /** Refusal code: the launcher is not on screen, so the clipboard is not touched. */
    public static final String REFUSAL_NOT_VISIBLE = "launcher_not_visible";

    /** Refusal code: the user turned "Let programs read the clipboard" off. */
    public static final String REFUSAL_READ_DISABLED = "clipboard_read_disabled";

    /** What the in-app notice needs of the session client, without holding the client itself. */
    public interface Notices {

        /** The short "[n] name" tag the notices use for a shell, or null when it has none. */
        @Nullable String describe(@NonNull TerminalSession session);

        /** Switches the panes to {@code session}, the way tapping its notice does. */
        void bringToFront(@NonNull TerminalSession session);
    }

    @NonNull private final Context mContext;

    @NonNull private final TerminalHost mHost;

    @NonNull private final Notices mNotices;

    @NonNull private final ClipboardHistory mHistory;

    /** Posts the unseen events to the phone's notification shade. */
    @NonNull private final TerminalNotifier mNotifier;

    /** Reads a program's name from /proc off the main thread. */
    private static final java.util.concurrent.ExecutorService PROGRAM_NAME_READER =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public ShellSignals(@NonNull Context context, @NonNull TerminalHost host, @NonNull Notices notices) {
        this(context, host, notices, ClipboardHistory.get(context));
    }

    public ShellSignals(@NonNull Context context, @NonNull TerminalHost host, @NonNull Notices notices,
                        @NonNull ClipboardHistory history) {
        mContext = context;
        mHost = host;
        mNotices = notices;
        mHistory = history;
        mNotifier = new TerminalNotifier(new HostEnv(), ShellNotifications.poster(context),
            TerminalNoticeRecords.shared());
    }

    /** For tests: the notifier is supplied. */
    ShellSignals(@NonNull Context context, @NonNull TerminalHost host, @NonNull Notices notices,
                 @NonNull ClipboardHistory history, @NonNull TerminalNotifier notifier) {
        mContext = context;
        mHost = host;
        mNotices = notices;
        mHistory = history;
        mNotifier = notifier;
    }

    /** What the notifier asks of the launcher, answered from the host. */
    private final class HostEnv implements TerminalNotifier.Env {
        @Override public boolean isLauncherVisible() {
            return mHost.isVisible();
        }

        @Nullable @Override public Object frontPane() {
            return mHost.isVisible() && mHost.isTerminalShowing() ? mHost.currentSession() : null;
        }

        @NonNull @Override public String windowKey(@NonNull Object pane) {
            TerminalSession session = (TerminalSession) pane;
            TerminalPaneController panes = mHost.paneController();
            TerminalPaneController.Window window = panes == null ? null : panes.windowOf(session);
            return window != null ? "w" + window.id : "s" + session.mHandle;
        }

        @Nullable @Override public String windowLabel(@NonNull Object pane) {
            return mNotices.describe((TerminalSession) pane);
        }

        @NonNull @Override public String paneHandle(@NonNull Object pane) {
            return ((TerminalSession) pane).mHandle;
        }

        @Override public int foregroundProcessGroup(@NonNull Object pane) {
            return ((TerminalSession) pane).getForegroundProcessGroup();
        }

        @Override public void resolveProgramName(int pgid, @NonNull TerminalNotifier.NameCallback done) {
            PROGRAM_NAME_READER.execute(() -> {
                String name = TerminalSession.programNameOf(pgid);
                MAIN.post(() -> done.onName(name));
            });
        }
    }

    // --- Notifications ---

    /**
     * A message with more to it than words: it can be named, replaced, taken down again, and
     * marked urgent. That belongs in the phone's own notification shade, where the user reads it
     * with the launcher put away and taps it to come back to the pane that sent it. When the
     * shade is closed to the launcher and it is on screen, the message becomes an in-app notice
     * rather than being lost.
     *
     * @param session the shell the message is attributed to: the sender for an escape, the
     *     target pane or the current one for the API
     * @return true when the user was shown something, in the shade or in the app
     */
    public boolean notify(@NonNull TerminalSession session, @NonNull KittyNotification notification) {
        mHost.noteShellAttention(session);
        boolean visible = mHost.isVisible();
        boolean terminalShowing = visible && mHost.isTerminalShowing();
        if (!TerminalNoticePolicy.occasionAllows(notification.getOccasion(), visible, terminalShowing))
            return false;
        if (mNotifier.kitty(session, notification)) return true;
        // Nothing reached the shade (seen, or notifications are off for the launcher), so the
        // message still gets the in-app notice rather than being lost.
        return visible && notice(session, notification.getTitle(), notification.getBody());
    }

    /** Take down the message the program named, if it is still up. */
    public void notifyClose(@NonNull TerminalSession session, @NonNull String id) {
        mNotifier.kittyClose(session, id);
    }

    /**
     * OSC 9 or OSC 777: the shade when the pane is unseen, and the in-app notice as before.
     * Returns true when an in-app notice was raised.
     */
    public boolean terminalMessage(@NonNull TerminalSession session, @Nullable String title,
                                   @Nullable String body) {
        mNotifier.message(session, title, body);
        return notice(session, title, body);
    }

    /** The bell rang in {@code session}. */
    public void bell(@NonNull TerminalSession session) {
        mNotifier.bell(session);
    }

    /** A program's status moved: blocked, error and done reach the shade when unseen. */
    public void programStatus(@NonNull TerminalSession session, @NonNull java.util.List<ProgramStatus> raised,
                              @NonNull ProgramStatusStore store) {
        mNotifier.programStatus(session, raised, store);
    }

    /** The pane in front or the launcher's visibility may have changed: what it had up is seen. */
    public void frontMaybeChanged() {
        mNotifier.frontMaybeChanged();
    }

    /** A shell ended: its notification goes with it. */
    public void sessionFinished(@NonNull TerminalSession session) {
        mNotifier.paneGone(session);
    }

    /**
     * The one-line terminal notification ({@code OSC 9}, {@code OSC 777}): a bell with words. The
     * window is marked the same way a bell marks it, and the message itself is shown as an in-app
     * notice while the launcher is on screen — deliberately not the shade, which is what
     * {@link #notify} is for. Returns true when a notice was raised.
     */
    public boolean notice(@NonNull TerminalSession session, @Nullable String title, @Nullable String body) {
        mHost.noteShellAttention(session);
        if (!mHost.isVisible())
            return false;
        String where = mNotices.describe(session);
        String headline = title != null && !title.trim().isEmpty() ? title.trim()
            : (where == null || where.isEmpty() ? null
                : mContext.getString(R.string.notice_shell_wants_attention, where));
        String detail = body == null ? "" : body.trim();
        return raiseShellNotice(session, headline, detail);
    }

    /**
     * An in-app notice for a program's own status report, shown while the launcher is on screen and
     * the same way {@link #notice} shows one. Unlike it, the window is not marked: the pill reads
     * the program's status itself, and a bell mark would outlive the record. True when raised.
     */
    public boolean statusNotice(@NonNull TerminalSession session, @Nullable String headline,
                                @Nullable String detail) {
        if (!mHost.isVisible())
            return false;
        return raiseStatusNotice(session, headline == null ? null : headline.trim(),
            detail == null ? "" : detail.trim());
    }

    /** The "[n] name" tag the notices use for {@code session}, or null when it has none. */
    @Nullable
    public String sessionTag(@NonNull TerminalSession session) {
        return mNotices.describe(session);
    }

    private boolean raiseShellNotice(@NonNull TerminalSession session, @Nullable String headline,
                                     @NonNull String detail) {
        return raiseShellNotice(headline, detail,
            session == mHost.currentSession() ? null : () -> mNotices.bringToFront(session));
    }

    /**
     * A status notice is raised only when its pane is not on screen, which includes its pane being
     * the current one while Home is showing: a tap always brings the pane and the terminal in.
     */
    private boolean raiseStatusNotice(@NonNull TerminalSession session, @Nullable String headline,
                                      @NonNull String detail) {
        return raiseShellNotice(headline, detail, () -> {
            if (session != mHost.currentSession()) mNotices.bringToFront(session);
            mHost.goToWallPage("terminal");
        });
    }

    private boolean raiseShellNotice(@Nullable String headline, @NonNull String detail,
                                     @Nullable Runnable onTap) {
        if (headline != null && headline.isEmpty()) headline = null;
        if (headline == null && detail.isEmpty())
            return false;
        AppNotice.shell(mContext, headline == null ? detail : headline,
            headline == null || detail.isEmpty() ? null : detail,
            "" /* nf-fa-bell */, true, onTap);
        return true;
    }

    // --- Progress ---

    /**
     * The progress ring on the chip of the window {@code session} lives in, as {@code OSC 9;4}
     * sets it: {@code state} is a {@link TerminalEmulator}{@code .PROGRESS_STATE_*} constant,
     * {@code percent} the value or negative to keep the last one. The report is stored on the
     * shell's emulator, which is what the window bar reads, and the bar is asked to repaint.
     * False when the shell has no emulator yet or the state is not one this terminal knows.
     */
    public boolean progress(@NonNull TerminalSession session, int state, int percent) {
        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null || !emulator.setProgress(state, percent)) return false;
        mHost.scheduleWindowBarRefresh();
        return true;
    }

    // --- Clipboard ---

    /**
     * Puts {@code text} on the Android clipboard, which is the one clipboard every side here
     * shares: the terminal's own copy and paste, the in-app keyboard's edit keys, the Linux
     * display, and every other app. Only while the launcher is on screen — a program in a shell
     * nobody is looking at does not get to replace what the user just copied elsewhere.
     *
     * <p>A write that lands is also a copy made inside the launcher, so it goes into the
     * keyboard's {@link ClipboardHistory} beside it — the one path the escape, the local API and
     * the terminal's own selection copy share.
     *
     * @return false when the launcher is not on screen and nothing was written
     */
    public boolean clipboardWrite(@NonNull String text) {
        if (!clipboardWritable())
            return false;
        ShareUtils.copyTextToClipboard(mContext, text);
        mHistory.record(text);
        return true;
    }

    /**
     * Whether a program may put something on the clipboard right now: only while the launcher is
     * on screen. The rule {@link #clipboardWrite} and a program's picture write share.
     */
    public boolean clipboardWritable() {
        return mHost.isVisible();
    }

    /**
     * {@link #clipboardWrite} for the local API ({@code launcherctl clipboard copy}, and the
     * {@code termux-clipboard-set} shim over it), which writes whether or not the launcher is on
     * screen. Termux:API always has, and a script run from another app's foreground is asking on
     * purpose. The escape keeps the on-screen rule: any program printing to a pane can emit it.
     */
    public void clipboardWriteFromApi(@NonNull String text) {
        ShareUtils.copyTextToClipboard(mContext, text);
        mHistory.record(text);
    }

    /**
     * Why a read of the clipboard would be refused right now, or null when it is allowed: the
     * launcher has to be on screen, and the Terminal setting "Let programs read the clipboard"
     * has to be on. The same two rules an {@code OSC 52} query is answered under.
     */
    @Nullable
    public String clipboardReadRefusal() {
        return clipboardReadRefusal(false);
    }

    /**
     * {@link #clipboardReadRefusal()} for a read that may be fetching a paste: when
     * {@code pasted} — the program brought back the password of the paste event the user just
     * made — the paste itself is the user's consent, so the setting is not asked. The launcher
     * still has to be on screen.
     */
    @Nullable
    public String clipboardReadRefusal(boolean pasted) {
        if (!mHost.isVisible())
            return REFUSAL_NOT_VISIBLE;
        if (pasted)
            return null;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, false);
        if (preferences == null || !preferences.isOsc52ClipboardReadEnabled())
            return REFUSAL_READ_DISABLED;
        return null;
    }

    /**
     * The clipboard's text, or null when the read is refused (see {@link #clipboardReadRefusal})
     * or the clipboard holds no text.
     */
    @Nullable
    public String clipboardRead() {
        if (clipboardReadRefusal() != null)
            return null;
        return ShareUtils.getTextStringFromClipboardIfSet(mContext, true);
    }
}
