package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.KittyNotification;
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

    public ShellSignals(@NonNull Context context, @NonNull TerminalHost host, @NonNull Notices notices) {
        mContext = context;
        mHost = host;
        mNotices = notices;
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
        boolean inFront = session == mHost.currentSession();
        if (!ShellNotifications.shouldShow(notification, visible, inFront)) return false;
        if (ShellNotifications.post(mContext, session, notification, visible, inFront) != null) return true;
        // Nothing reached the shade — notifications are turned off for the launcher — so the
        // message still gets the older in-app notice rather than being lost.
        return visible && notice(session, notification.getTitle(), notification.getBody());
    }

    /** Take down the message the program named, if it is still up. */
    public void notifyClose(@NonNull TerminalSession session, @NonNull String id) {
        ShellNotifications.close(mContext, session, id);
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
        if (headline == null && detail.isEmpty())
            return false;
        AppNotice.shell(mContext, headline == null ? detail : headline,
            headline == null || detail.isEmpty() ? null : detail,
            "" /* nf-fa-bell */, true,
            session == mHost.currentSession() ? null : () -> mNotices.bringToFront(session));
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
     * @return false when the launcher is not on screen and nothing was written
     */
    public boolean clipboardWrite(@NonNull String text) {
        if (!mHost.isVisible())
            return false;
        ShareUtils.copyTextToClipboard(mContext, text);
        return true;
    }

    /**
     * Why a read of the clipboard would be refused right now, or null when it is allowed: the
     * launcher has to be on screen, and the Terminal setting "Let programs read the clipboard"
     * has to be on. The same two rules an {@code OSC 52} query is answered under.
     */
    @Nullable
    public String clipboardReadRefusal() {
        if (!mHost.isVisible())
            return REFUSAL_NOT_VISIBLE;
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
