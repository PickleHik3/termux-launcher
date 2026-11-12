package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KittyNotification;
import com.termux.terminal.ProgramStatus;
import com.termux.terminal.ProgramStatusStore;

import java.util.List;

/**
 * Turns terminal events into one Android notification per window. It decides with
 * {@link TerminalNoticePolicy}, keeps {@link TerminalNoticeRecords}, and leaves every Android call
 * to the {@link Env} and {@link Poster} it is given. Main thread only; each entry returns at once.
 *
 * <p>A post is two-phase: the record is written when the event arrives, so order and later cancels
 * are right, and the notification itself is posted once the foreground program's name is known. If
 * the window's record changed in between (cancelled, or a newer notice), the late post is dropped.
 */
public final class TerminalNotifier {

    /** Receives a program name on the main thread; null when it could not be found. */
    public interface NameCallback {
        void onName(@Nullable String name);
    }

    /** What the notifier asks of the launcher. */
    public interface Env {
        boolean isLauncherVisible();

        /**
         * The pane in front while the launcher is visible and the Terminal page is showing at rest,
         * else null. Non-null is also what "the Terminal page is showing" means to the policy.
         */
        @Nullable
        Object frontPane();

        @NonNull
        String windowKey(@NonNull Object pane);

        /** The window's "[n] name" label, or null. */
        @Nullable
        String windowLabel(@NonNull Object pane);

        @NonNull
        String paneHandle(@NonNull Object pane);

        /** The pane's foreground process group, cheap; 0 or less when unknown. */
        int foregroundProcessGroup(@NonNull Object pane);

        /** Looks the name of a process group up off the main thread; answers on the main thread. */
        void resolveProgramName(int pgid, @NonNull NameCallback done);
    }

    /** Where notifications go. */
    public interface Poster {
        boolean canPost();

        void post(@NonNull TerminalNoticeContent content);

        /** Takes the window's notification down; {@code remaining} records are left, for the group summary. */
        void cancel(@NonNull String windowKey, int remaining);
    }

    /** oneLine adds an ellipsis after this many, so the shown text is at most 200 characters. */
    private static final int STATUS_TEXT_MAX = 199;

    private final Env mEnv;
    private final Poster mPoster;
    private final TerminalNoticeRecords mRecords;

    public TerminalNotifier(@NonNull Env env, @NonNull Poster poster, @NonNull TerminalNoticeRecords records) {
        mEnv = env;
        mPoster = poster;
        mRecords = records;
    }

    /**
     * An OSC 99 request. Returns true when an Android notification is being posted; false means the
     * caller should fall back to an in-app notice.
     */
    public boolean kitty(@NonNull Object pane, @NonNull KittyNotification n) {
        String title = n.getTitle().trim();
        String body = n.getBody().trim();
        if (title.isEmpty() && body.isEmpty()) return false;
        TerminalNoticeKind kind = n.getUrgency() == KittyNotification.URGENCY_CRITICAL
            ? TerminalNoticeKind.URGENT_MESSAGE : TerminalNoticeKind.MESSAGE;
        boolean awayOnly = KittyNotification.OCCASION_INVISIBLE.equals(n.getOccasion());
        return raise(pane, kind, awayOnly, n.getId(), null, null, title, body, null, n);
    }

    /** OSC 9 or OSC 777: a message with no id. */
    public boolean message(@NonNull Object pane, @Nullable String title, @Nullable String body) {
        String t = title == null ? "" : title.trim();
        String b = body == null ? "" : body.trim();
        if (t.isEmpty() && b.isEmpty()) return false;
        return raise(pane, TerminalNoticeKind.MESSAGE, false, "", null, null, t, b, null, null);
    }

    public void bell(@NonNull Object pane) {
        raise(pane, TerminalNoticeKind.BELL, false, "", null, null, "", "", null, null);
    }

    /**
     * A pane's program status changed. The window's notification is taken down when the state it
     * reported has ended; each newly raised blocked, error or done record then notifies.
     */
    public void programStatus(@NonNull Object pane, @NonNull List<ProgramStatus> raised,
                              @NonNull ProgramStatusStore store) {
        String key = mEnv.windowKey(pane);
        TerminalNoticeRecords.Record current = mRecords.get(key);
        if (current != null && current.statusId != null) {
            ProgramStatus now = store.get(current.statusId);
            if (now == null || now.state != current.statusState) cancel(key);
        }
        for (ProgramStatus record : raised) {
            TerminalNoticeKind kind = kindOf(record.state);
            if (kind == null) continue;
            String app = store.resolvedApp(record);
            String preferred = !app.isEmpty() ? app : (record.title.isEmpty() ? null : record.title);
            raise(pane, kind, false, "", record.id, record.state, "",
                ProgramStatusLabel.oneLine(record.message, STATUS_TEXT_MAX), preferred, null);
        }
    }

    /** OSC 99 close: takes the window's notification down if it is the one the program named. */
    public void kittyClose(@NonNull Object pane, @NonNull String id) {
        if (id.isEmpty()) return;
        String key = mEnv.windowKey(pane);
        TerminalNoticeRecords.Record current = mRecords.get(key);
        if (current != null && id.equals(current.messageId)) cancel(key);
    }

    /**
     * Call when the pane in front may have changed: the Terminal page showing a window is the
     * user seeing it, so that window's notification and the front pane's are taken down.
     */
    public void frontMaybeChanged() {
        Object front = mEnv.frontPane();
        if (front == null) return;
        String key = mEnv.windowKey(front);
        cancel(key);
        cancelRecordsOf(front);
        mRecords.clearBell(key);
        mRecords.clearBellFor(front);
    }

    public void paneGone(@NonNull Object pane) {
        cancelRecordsOf(pane);
        mRecords.clearBellFor(pane);
    }

    @Nullable
    private static TerminalNoticeKind kindOf(@NonNull ProgramStatus.State state) {
        switch (state) {
            case BLOCKED: return TerminalNoticeKind.NEEDS_INPUT;
            case ERROR: return TerminalNoticeKind.ERROR;
            case DONE: return TerminalNoticeKind.FINISHED;
            default: return null;
        }
    }

    private void cancel(@NonNull String windowKey) {
        if (mRecords.remove(windowKey) != null) mPoster.cancel(windowKey, mRecords.size());
    }

    private void cancelRecordsOf(@NonNull Object pane) {
        for (TerminalNoticeRecords.Record record : mRecords.removeForPane(pane))
            mPoster.cancel(record.windowKey, mRecords.size());
    }

    /**
     * Decides, records, then posts once the program's name is known.
     *
     * @param statusName the status's own name (app, else title) that beats the program's, else null
     */
    private boolean raise(@NonNull Object pane, @NonNull TerminalNoticeKind kind, boolean awayOnly,
                          @NonNull String messageId, @Nullable String statusId,
                          @Nullable ProgramStatus.State statusState, @NonNull String messageTitle,
                          @NonNull String text, @Nullable String statusName,
                          @Nullable KittyNotification kitty) {
        if (!mPoster.canPost()) return false;
        String key = mEnv.windowKey(pane);
        TerminalNoticePolicy.Decision decision = TerminalNoticePolicy.decide(kind,
            mEnv.isLauncherVisible(), mEnv.frontPane() != null, awayOnly, mRecords.get(key),
            messageId, mRecords.bellSpent(key));
        if (decision.action == TerminalNoticePolicy.Action.NOTHING) return false;

        int generation = mRecords.put(kind, pane, key, messageId, statusId, statusState).generation;
        if (kind == TerminalNoticeKind.BELL) mRecords.markBellSpent(key, pane);
        int pgid = mEnv.foregroundProcessGroup(pane);
        boolean alert = decision.alert;
        NameCallback finish = programName -> {
            TerminalNoticeRecords.Record now = mRecords.get(key);
            if (now == null || now.generation != generation) return;
            String label = mEnv.windowLabel(pane);
            String window = label == null ? "" : label;
            String program = programName == null ? "" : programName;
            String name = statusName != null ? statusName : (program.isEmpty() ? window : program);
            boolean reply = kind.takesReply() && pgid > 0 && !program.isEmpty();
            mPoster.post(new TerminalNoticeContent(kind, key, mEnv.paneHandle(pane), pane, name,
                messageTitle, text, window, alert, reply ? pgid : 0, reply ? program : null, kitty));
        };
        if (pgid <= 0) finish.onName(null);
        else mEnv.resolveProgramName(pgid, finish);
        return true;
    }
}
